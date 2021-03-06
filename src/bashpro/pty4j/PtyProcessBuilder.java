package bashpro.pty4j;

import bashpro.pty4j.unix.UnixPtyProcess;
import bashpro.pty4j.windows.CygwinPtyProcess;
import bashpro.pty4j.windows.WinPtyProcess;
import com.pty4j.PtyProcess;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class PtyProcessBuilder {
  private String[] myCommand;
  private Map<String, String> myEnvironment;
  private String myDirectory;
  private boolean myConsole;
  private boolean myCygwin;
  private File myLogFile;
  private boolean myRedirectErrorStream = false;
  private Integer myInitialColumns;
  private Integer myInitialRows;
  private boolean myWindowsAnsiColorEnabled = false;
  private boolean myPassAdditionalPty;

  public PtyProcessBuilder() {
  }

  public PtyProcessBuilder(@NotNull String[] command) {
    myCommand = command;
  }

  @NotNull
  public PtyProcessBuilder setCommand(@NotNull String[] command) {
    myCommand = command;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setEnvironment(@Nullable Map<String, String> environment) {
    myEnvironment = environment;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setDirectory(String directory) {
    myDirectory = directory;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setConsole(boolean console) {
    myConsole = console;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setCygwin(boolean cygwin) {
    myCygwin = cygwin;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setLogFile(@Nullable File logFile) {
    myLogFile = logFile;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setRedirectErrorStream(boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setInitialColumns(@Nullable Integer initialColumns) {
    myInitialColumns = initialColumns;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setInitialRows(@Nullable Integer initialRows) {
    myInitialRows = initialRows;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setWindowsAnsiColorEnabled(boolean windowsAnsiColorEnabled) {
    myWindowsAnsiColorEnabled = windowsAnsiColorEnabled;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setPassAdditionalPty(boolean passAdditionalPty) {
    myPassAdditionalPty = passAdditionalPty;
    return this;
  }

  @NotNull
  public PtyProcess start() throws IOException {
    if (myEnvironment == null) {
      myEnvironment = System.getenv();
    }
    PtyProcessOptions options = new PtyProcessOptions(myCommand,
                                                      myEnvironment,
                                                      myDirectory,
                                                      myRedirectErrorStream,
                                                      myInitialColumns,
                                                      myInitialRows,
                                                      myWindowsAnsiColorEnabled,
                                                      myPassAdditionalPty);
    if (Platform.isWindows()) {
      if (myCygwin) {
        // fixme pass additional pty
        return new CygwinPtyProcess(myCommand, myEnvironment, myDirectory, myLogFile, myConsole);
      }
      return new WinPtyProcess(options, myConsole);
    }
    return new UnixPtyProcess(options, myConsole);
  }
}
