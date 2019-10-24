package com.pty4j.unix

import com.pty4j.AdditionalPtyProcess
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import com.pty4j.util.PtyUtil
import java.io.File

internal class BashSupportProcessBuilderUnixLauncher @Throws(Exception::class) constructor(
  command: MutableList<String>,
  environmentMap: MutableMap<String, String>,
  workingDirectory: String,
  pty: Pty,
  errPty: Pty?,
  additionalPty: Pty?, // BashSupport Pro
  consoleMode: Boolean,
  initialColumns: Int?,
  initialRows: Int?,
  ptyProcess: PtyProcess
) {

  val process: Process

  init {
    // BashSupport Pro
    if (additionalPty != null) {
      for (i in 0 until command.size) {
        if (AdditionalPtyProcess.PTY_PLACEHOLDER == command[i]) {
          command[i] = additionalPty.slaveName
        }
      }
    }

    val spawnHelper = PtyUtil.resolveNativeFile("pty4j-unix-spawn-helper")
    val builder = ProcessBuilder()
    builder.command(listOf(spawnHelper.absolutePath,
                           workingDirectory,
                           (if (consoleMode) 1 else 0).toString(),
                           pty.slaveName,
                           pty.masterFD.toString(),
                           errPty?.slaveName.orEmpty(),
                           (errPty?.masterFD ?: -1).toString()
                           , additionalPty?.slaveName.orEmpty() // BashSupport Pro
                           , (additionalPty?.masterFD ?: -1).toString() // BashSupport Pro
                           ) + command)
    val environment = builder.environment()
    environment.clear()
    environment.putAll(environmentMap)
    builder.directory(File(workingDirectory))
    builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    if (errPty == null) {
      builder.redirectErrorStream(true)
    }
    else {
      builder.redirectError(ProcessBuilder.Redirect.DISCARD)
    }
    process = builder.start()

    if (initialColumns != null || initialRows != null) {
      val size = WinSize(initialColumns ?: 80, initialRows ?: 25)

      // On OSX, there is a race condition with pty initialization
      // If we call com.pty4j.unix.Pty.setTerminalSize(com.pty4j.WinSize) too early, we can get ENOTTY
      for (attempt in 0..999) {
        try {
          pty.setWindowSize(size, ptyProcess)
          break
        }
        catch (e: UnixPtyException) {
          if (e.errno != UnixPtyProcess.ENOTTY) {
            break
          }
        }
      }

      // BashSupport Pro
      if (additionalPty != null) {
        for (attempt in 0..999) {
          try {
            additionalPty.setWindowSize(size, ptyProcess)
            break
          }
          catch (e: UnixPtyException) {
            if (e.errno != UnixPtyProcess.ENOTTY) {
              break
            }
          }
        }
      }
    }

  }

}
