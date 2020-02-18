/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package bashpro.pty4j.unix;

import bashpro.pty4j.util.PtyUtil;
import com.google.common.base.MoreObjects;
import bashpro.pty4j.AdditionalPtyProcess;
import bashpro.pty4j.PtyProcessOptions;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import jtermios.JTermios;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class UnixPtyProcess extends PtyProcess implements AdditionalPtyProcess {
  public int NOOP = 0;
  public int SIGHUP = 1;
  public int SIGINT = 2;
  public int SIGKILL = 9;
  public int SIGTERM = 15;
  public int ENOTTY = 25; // Not a typewriter

  /**
   * On Windows, what this does is far from easy to explain. Some of the logic is in the JNI code, some in the
   * spawner.exe code.
   * <p/>
   * <ul>
   * <li>If the process this is being raised against was launched by us (the Spawner)
   * <ul>
   * <li>If the process is a cygwin program (has the cygwin1.dll loaded), then issue a 'kill -SIGINT'. If the 'kill'
   * utility isn't available, send the process a CTRL-C
   * <li>If the process is <i>not</i> a cygwin program, send the process a CTRL-C
   * </ul>
   * <li>If the process this is being raised against was <i>not</i> launched by us, use DebugBreakProcess to interrupt
   * it (sending a CTRL-C is easy only if we share a console with the target process)
   * </ul>
   * <p/>
   * On non-Windows, raising this just raises a POSIX SIGINT
   */
  public int INT = 2;

  /**
   * A fabricated signal number for use on Windows only. Tells the starter program to send a CTRL-C regardless of
   * whether the process is a Cygwin one or not.
   *
   * @since 5.2
   */
  public int CTRLC = 1000; // arbitrary high number to avoid collision
  private static final int SIGWINCH = 28;

  private int pid = 0;
  private int myStatus;
  private boolean isDone;
  private OutputStream out;
  private InputStream in;
  private InputStream err;
  private final Pty myPty;
  private final Pty myErrPty;

  private final Pty myAdditionalPty;
  private OutputStream myAdditionalPtyOut;
  private InputStream myAdditionalPtyIn;

//  @Deprecated
//  public UnixPtyProcess(String[] cmdarray, String[] envp, String dir, Pty pty, Pty errPty) throws IOException {
//    if (dir == null) {
//      dir = ".";
//    }
//    if (pty == null) {
//      throw new IOException("pty cannot be null");
//    }
//    myPty = pty;
//    myErrPty = errPty;
//    execInPty(cmdarray, envp, dir, pty, errPty);
//  }

  public UnixPtyProcess(@NotNull PtyProcessOptions options, boolean consoleMode) throws IOException {
    myPty = new Pty(consoleMode);
    myErrPty = options.isRedirectErrorStream() ? null : (consoleMode ? new Pty() : null);
    myAdditionalPty = options.isPassAdditionalPtyFD() ? new Pty(consoleMode) : null;
    String dir = MoreObjects.firstNonNull(options.getDirectory(), ".");
    execInPty(options.getCommand(), PtyUtil.toStringArray(options.getEnvironment()), dir, myPty, myErrPty, myAdditionalPty);
  }

  public Pty getPty() {
    return myPty;
  }

  @Override
  protected void finalize() throws Throwable {
    closeUnusedStreams();
    super.finalize();
  }

  /**
   * See java.lang.Process#getInputStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized InputStream getInputStream() {
    if (null == in) {
      in = myPty.getInputStream();
    }
    return in;
  }

  /**
   * See java.lang.Process#getOutputStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized OutputStream getOutputStream() {
    if (null == out) {
      out = myPty.getOutputStream();
    }
    return out;
  }

  public synchronized Pty getAdditionalPty() {
    return myAdditionalPty;
  }

  public synchronized InputStream getAdditionalPtyInputStream() {
    if (null == myAdditionalPtyIn && myAdditionalPty != null) {
      myAdditionalPtyIn = myAdditionalPty.getInputStream();
    }
    return myAdditionalPtyIn;
  }

  public synchronized OutputStream getAdditionalPtyOutputStream() {
    if (null == myAdditionalPtyOut && myAdditionalPty != null) {
      myAdditionalPtyOut = myAdditionalPty.getOutputStream();
    }
    return myAdditionalPtyOut;
  }

  /**
   * See java.lang.Process#getErrorStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized InputStream getErrorStream() {
    if (null == err) {
      if (myErrPty == null || !myPty.isConsole()) {
        // If Pty is used and it's not in "Console" mode, then stderr is redirected to the Pty's output stream.
        // Therefore, return a dummy stream for error stream.
        err = new InputStream() {
          @Override
          public int read() {
            return -1;
          }
        };
      }
      else {
        err = myErrPty.getInputStream();
      }
    }
    return err;
  }

  /**
   * See java.lang.Process#waitFor ();
   */
  @Override
  public synchronized int waitFor() throws InterruptedException {
    while (!isDone) {
      wait();
    }

    return myStatus;
  }

  /**
   * See java.lang.Process#exitValue ();
   */
  @Override
  public synchronized int exitValue() {
    if (!isDone) {
      throw new IllegalThreadStateException("Process not Terminated");
    }
    return myStatus;
  }

  /**
   * See java.lang.Process#destroy ();
   * <p/>
   * Clients are responsible for explicitly closing any streams that they have requested through getErrorStream(),
   * getInputStream() or getOutputStream()
   */
  @Override
  public synchronized void destroy() {
    // Sends the TERM
    terminate();

    closeUnusedStreams();

    // Grace before using the heavy gone.
    if (!isDone) {
      try {
        wait(1000);
      }
      catch (InterruptedException e) {
      }
    }
    if (!isDone) {
      kill();
    }
  }

  public int interrupt() {
    return Pty.raise(pid, INT);
  }

  public int interruptCTRLC() {
    //    if (Platform.getOS().equals(Platform.OS_WIN32)) {
    //      return raise(pid, CTRLC);
    //    }
    return interrupt();
  }

  public int hangup() {
    return Pty.raise(pid, SIGHUP);
  }

  public int kill() {
    return Pty.raise(pid, SIGKILL);
  }

  public int terminate() {
    return Pty.raise(pid, SIGTERM);
  }

  @Override
  public boolean isRunning() {
    return (Pty.raise(pid, NOOP) == 0);
  }

  private void execInPty(String[] command, String[] environment, String workingDirectory, Pty pty, Pty errPty, Pty additionalPty) throws IOException {
    String cmd = command[0];
    SecurityManager s = System.getSecurityManager();
    if (s != null) {
      s.checkExec(cmd);
    }
    if (environment == null) {
      environment = new String[0];
    }
    final String slaveName = pty.getSlaveName();
    final int masterFD = pty.getMasterFD();
    final String errSlaveName = errPty == null ? null : errPty.getSlaveName();
    final int errMasterFD = errPty == null ? -1 : errPty.getMasterFD();
    final boolean console = pty.isConsole();

    final String addPtySlaveName = additionalPty == null ? null : additionalPty.getSlaveName();
    final int addPtyMasterFD = additionalPty == null ? -1 : additionalPty.getMasterFD();

    // int fdm = pty.get
    Reaper reaper = new Reaper(command, environment, workingDirectory, slaveName, masterFD, errSlaveName, errMasterFD, console, addPtySlaveName, addPtyMasterFD);

    reaper.setDaemon(true);
    reaper.start();
    // Wait until the subprocess is started or error.
    synchronized (this) {
      while (pid == 0) {
        try {
          wait();
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      boolean init = Boolean.getBoolean("unix.pty.init");
      if (init) {
        int cols = Integer.getInteger("unix.pty.cols", 80);
        int rows = Integer.getInteger("unix.pty.rows", 25);
        WinSize size = new WinSize(cols, rows);

        // On OSX, there is a race condition with pty initialization
        // If we call Pty.setTerminalSize(WinSize) too early, we can get ENOTTY
        boolean retry = true;
        for (int attempt = 0; attempt < 1000 && retry; attempt++) {
          retry = false;
          try {
            myPty.setTerminalSize(size);
          } catch (IllegalStateException e) {
            if (JTermios.errno() == ENOTTY)
              retry = true;
          }
        }

        if (myAdditionalPty != null) {
          for (int attempt = 0; attempt < 1000 && retry; attempt++) {
            retry = false;
            try {
              myAdditionalPty.setTerminalSize(size);
            } catch (IllegalStateException e) {
              if (JTermios.errno() == ENOTTY)
                retry = true;
            }
          }
        }
      }
    }
    if (pid == -1) {
      throw new IOException("Exec_tty error:" + reaper.getErrorMessage(), reaper.getException());
    }
  }

  /**
   * Close the streams on this side.
   * <p/>
   * We only close the streams that were
   * never used by any client.
   * So, if the stream was not created yet,
   * we create it ourselves and close it
   * right away, so as to release the pipe.
   * Note that even if the stream was never
   * created, the pipe has been allocated in
   * native code, so we need to create the
   * stream and explicitly close it.
   * <p/>
   * We don't close streams the clients have
   * created because we don't know when the
   * client will be finished using them.
   * It is up to the client to close those
   * streams.
   * <p/>
   * But 345164
   */
  private synchronized void closeUnusedStreams() {
    try {
      if (null == err) {
        getErrorStream().close();
      }
    }
    catch (IOException e) {
    }
    try {
      if (null == in) {
        getInputStream().close();
      }
    }
    catch (IOException e) {
    }
    try {
      if (null == out) {
        getOutputStream().close();
      }
    }
    catch (IOException e) {
    }
  }

  int exec(String[] cmd, String[] envp, String dirname, String slaveName, int masterFD,
           String errSlaveName, int errMasterFD, boolean console,
           String additionalPtyName, int additionalPtyMasterFD) throws IOException {
    int pid = -1;

    if (cmd == null) {
      return pid;
    }

    if (envp == null) {
      return pid;
    }

    return PtyHelpers.execPty(cmd[0], cmd, envp, dirname, slaveName, masterFD, errSlaveName, errMasterFD, console,
            additionalPtyName, additionalPtyMasterFD);
  }

  int waitFor(int processID) {
    return Pty.wait0(processID);
  }


  @Override
  public void setWinSize(WinSize winSize) {
    myPty.setTerminalSize(winSize);
    if (myErrPty != null) {
      myErrPty.setTerminalSize(winSize);
    }
    if (myAdditionalPty != null) {
      myAdditionalPty.setTerminalSize(winSize);
    }
    Pty.raise(pid, SIGWINCH);
  }

  @Override
  public WinSize getWinSize() throws IOException {
    return myPty.getWinSize();
  }

  @Override
  public int getPid() {
    return pid;
  }

  // Spawn a thread to handle the forking and waiting.
  // We do it this way because on linux the SIGCHLD is send to the one thread. So do the forking and then wait in the
  // same thread.
  class Reaper extends Thread {
    private String[] myCommand;
    private String[] myEnv;
    private String myDir;
    private String mySlaveName;
    private int myMasterFD;
    private String myErrSlaveName;
    private int myErrMasterFD;
    private boolean myConsole;
    private int myAdditionalPtyMasterFD;
    private String myAdditionalPtySlaveName;
    volatile Throwable myException;

    public Reaper(String[] command, String[] environment, String workingDirectory, String slaveName, int masterFD, String errSlaveName,
                  int errMasterFD, boolean console, String additionalPtySlaveName, int additionalPtyMasterFD) {
      super("PtyProcess Reaper for " + Arrays.toString(command));
      myCommand = command;
      myEnv = environment;
      myDir = workingDirectory;
      mySlaveName = slaveName;
      myMasterFD = masterFD;
      myErrSlaveName = errSlaveName;
      myErrMasterFD = errMasterFD;
      myConsole = console;
      myException = null;
      myAdditionalPtySlaveName = additionalPtySlaveName;
      myAdditionalPtyMasterFD = additionalPtyMasterFD;
    }

    int execute(String[] cmd, String[] env, String dir) throws IOException {
      return exec(cmd, env, dir, mySlaveName, myMasterFD, myErrSlaveName, myErrMasterFD, myConsole,
              myAdditionalPtySlaveName, myAdditionalPtyMasterFD);
    }

    @Override
    public void run() {
      try {
        pid = execute(myCommand, myEnv, myDir);
      }
      catch (Exception e) {
        pid = -1;
        myException = e;
      }
      // Tell spawner that the process started.
      synchronized (UnixPtyProcess.this) {
        UnixPtyProcess.this.notifyAll();
      }
      if (pid != -1) {
        // Sync with spawner and notify when done.
        myStatus = waitFor(pid);
        synchronized (UnixPtyProcess.this) {
          isDone = true;
          UnixPtyProcess.this.notifyAll();
        }
        myPty.breakRead();
        if (myErrPty != null) myErrPty.breakRead();
        if (myAdditionalPty != null) myAdditionalPty.breakRead();
      }
    }

    public String getErrorMessage() {
      return myException != null ? myException.getMessage() : "Unknown reason";
    }

    @Nullable
    public Throwable getException() {
      return myException;
    }
  }
}
