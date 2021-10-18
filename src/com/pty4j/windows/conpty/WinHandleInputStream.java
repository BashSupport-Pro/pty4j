package com.pty4j.windows.conpty;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

class WinHandleInputStream extends InputStream {

  private final WinNT.HANDLE myReadPipe;
  private volatile boolean myClosed;
  private long myLastRead;

  public WinHandleInputStream(@NotNull WinNT.HANDLE readPipe) {
    myReadPipe = readPipe;
  }

  @Override
  public int read() throws IOException {
    byte[] buf = new byte[1];
    int readBytes = read(buf, 0, 1);
    return readBytes == 1 ? buf[0] : -1;
  }

  @Override
  public int read(byte @NotNull [] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len == 0) {
      return 0;
    }
    if (myClosed) {
      throw new IOException("Closed stdin");
    }
    byte[] buffer = new byte[len];
    IntByReference lpNumberOfBytesRead = new IntByReference(0);
    boolean result = Kernel32.INSTANCE.ReadFile(myReadPipe, buffer, buffer.length, lpNumberOfBytesRead, null);
    myLastRead = System.currentTimeMillis();
    if (!result) {
      int lastError = Native.getLastError();
      if (lastError == WinError.ERROR_BROKEN_PIPE) {
        // https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-readfile
        // If an anonymous pipe is being used and the write handle has been closed,
        // when ReadFile attempts to read using the pipe's corresponding read handle,
        // the function returns FALSE and GetLastError returns ERROR_BROKEN_PIPE.
        return -1;
      }
      throw new LastErrorExceptionEx("ReadFile stdin", lastError);
    }
    int bytesRead = lpNumberOfBytesRead.getValue();
    if (bytesRead == 0) {
      // If lpOverlapped is NULL, then when a synchronous read operation reaches the end of a file,
      // ReadFile returns TRUE and sets *lpNumberOfBytesRead to zero.
      return -1;
    }
    System.arraycopy(buffer, 0, b, off, len);
    return bytesRead;
  }

  long getLastReadMillis() {
    return myLastRead;
  }

  @Override
  public void close() throws IOException {
    if (!myClosed) {
      myClosed = true;
      if (!Kernel32.INSTANCE.CloseHandle(myReadPipe)) {
        throw new LastErrorExceptionEx("CloseHandle stdin");
      }
    }
  }
}
