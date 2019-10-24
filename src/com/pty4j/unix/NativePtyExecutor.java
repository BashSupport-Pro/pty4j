package com.pty4j.unix;

import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
class NativePtyExecutor implements PtyExecutor {

  private final Pty4J myPty4j;

  NativePtyExecutor(@NotNull String libraryName) {
    myPty4j = Native.loadLibrary(libraryName, Pty4J.class);
  }

  @Override
  public int execPty(String full_path, String[] argv, String[] envp, String dirpath, String pts_name, int fdm,
                     String err_pts_name, int err_fdm, boolean console,
                     int additionalPtyClientFD, String additionalPtySlaveName, int additionalPtyMasterFD) {
    return myPty4j.exec_pty(full_path, argv, envp, dirpath, pts_name, fdm, err_pts_name, err_fdm, console,
            additionalPtyClientFD, additionalPtySlaveName, additionalPtyMasterFD);
  }

  public interface Pty4J extends com.sun.jna.Library {
    int exec_pty(String full_path, String[] argv, String[] envp, String dirpath, String pts_name, int fdm,
                 String err_pts_name, int err_fdm, boolean console,
                 int additionalPtyClientFD, String additionalPtySlaveName, int additionalPtyMasterFD);
  }
}
