package bashpro.pty4j;

import bashpro.pty4j.unix.Pty;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Marks a process to be capable of the additional input and output streams to separate bashdb's commands from the
 * debugged program's input and output.
 *
 * @author jansorg
 */
@SuppressWarnings("unused")
public interface AdditionalPtyProcess {
    String PTY_PLACEHOLDER = "_DBG_PTY_";

    @Nullable
    Pty getAdditionalPty();

    @Nullable
    InputStream getAdditionalPtyInputStream();

    @Nullable
    OutputStream getAdditionalPtyOutputStream();
}
