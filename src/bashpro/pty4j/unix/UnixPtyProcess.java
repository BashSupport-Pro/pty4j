/*
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package bashpro.pty4j.unix;

import bashpro.pty4j.util.PtyUtil;
import com.google.common.base.MoreObjects;
import bashpro.pty4j.AdditionalPtyProcess;
import bashpro.pty4j.PtyProcessOptions;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class UnixPtyProcess extends PtyProcess implements AdditionalPtyProcess {
  private static final int NOOP = 0;

  // Signals with portable numbers (https://en.wikipedia.org/wiki/Signal_(IPC)#POSIX_signals)
  private static final int SIGHUP = 1;
  private static final int SIGKILL = 9;
  private static final int SIGTERM = 15;

  private static final int ENOTTY = 25; // Not a typewriter

  private int pid = 0;
  private int myExitCode;
  private boolean isDone;
  private OutputStream out;
  private InputStream in;
  private InputStream err;
  private final Pty myPty;
  private final Pty myErrPty;

  private final Pty myAdditionalPty;
  private OutputStream myAdditionalPtyOut;
  private InputStream myAdditionalPtyIn;

  public UnixPtyProcess(@NotNull PtyProcessOptions options, boolean consoleMode) throws IOException {
    myPty = new Pty(consoleMode, options.isUnixOpenTtyToPreserveOutputAfterTermination());
    myErrPty = options.isRedirectErrorStream() ? null : (consoleMode ? new Pty() : null);
    myAdditionalPty = options.isPassAdditionalPtyFD() ? new Pty(consoleMode) : null;
    String dir = MoreObjects.firstNonNull(options.getDirectory(), ".");
    execInPty(options.getCommand(), PtyUtil.toStringArray(options.getEnvironment()), dir, myPty, myErrPty, myAdditionalPty,
      options.getInitialColumns(), options.getInitialRows());
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

  @Override
  public synchronized int waitFor() throws InterruptedException {
    while (!isDone) {
      wait();
    }
    return myExitCode;
  }

  /**
   * See java.lang.Process#exitValue ();
   */
  @Override
  public synchronized int exitValue() {
    if (!isDone) {
      throw new IllegalThreadStateException("process hasn't exited");
    }
    return myExitCode;
  }

  /**
   * See java.lang.Process#destroy ();
   * <p/>
   * Clients are responsible for explicitly closing any streams that they have requested through getErrorStream(),
   * getInputStream() or getOutputStream()
   */
  @Override
  public synchronized void destroy() {
    Pty.raise(pid, SIGTERM);
    closeUnusedStreams();
  }

  @Override
  public synchronized Process destroyForcibly() {
    Pty.raise(pid, SIGKILL);
    closeUnusedStreams();
    return this;
  }

  @Override
  public boolean isRunning() {
    return Pty.raise(pid, NOOP) == 0;
  }

  public int hangup() {
    return Pty.raise(pid, SIGHUP);
  }

  private void execInPty(String[] command, String[] environment, String workingDirectory, Pty pty, Pty errPty, Pty additionalPty,
                         @Nullable Integer initialColumns,
                         @Nullable Integer initialRows) throws IOException {
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

      boolean init = Boolean.getBoolean("unix.pty.init") || initialColumns != null || initialRows != null;
      if (init) {
        int cols = initialColumns != null ? initialColumns : Integer.getInteger("unix.pty.cols", 80);
        int rows = initialRows != null ? initialRows : Integer.getInteger("unix.pty.rows", 25);
        WinSize size = new WinSize(cols, rows);

        // On OSX, there is a race condition with pty initialization
        // If we call com.pty4j.unix.Pty.setTerminalSize(com.pty4j.WinSize) too early, we can get ENOTTY
        for (int attempt = 0; attempt < 1000; attempt++) {
          try {
            myPty.setWindowSize(size, this);
            break;
          }
          catch (UnixPtyException e) {
            if (e.getErrno() != ENOTTY) {
              break;
            }
          }
        }

        if (myAdditionalPty != null) {
          for (int attempt = 0; attempt < 1000; attempt++) {
            try {
              myAdditionalPty.setWindowSize(size, this);
              break;
            }
            catch (UnixPtyException e) {
              if (e.getErrno() != ENOTTY) {
                break;
              }
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

  @Override
  public void setWinSize(WinSize winSize) {
    try {
      myPty.setWindowSize(winSize, this);
    }
    catch (UnixPtyException e) {
      throw new IllegalStateException(e);
    }
    if (myErrPty != null) {
      try {
        myErrPty.setWindowSize(winSize, this);
      }
      catch (UnixPtyException e) {
        throw new IllegalStateException(e);
      }
    }
    if (myAdditionalPty != null) {
      try {
        myAdditionalPty.setWindowSize(winSize, this);
      }
      catch (UnixPtyException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public @NotNull WinSize getWinSize() throws IOException {
    return myPty.getWinSize(this);
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
        myExitCode = PtyHelpers.getPtyExecutor().waitForProcessExitAndGetExitCode(pid);
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
