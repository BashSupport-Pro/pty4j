package bashpro.pty4j.unix;

/**
 * @author traff
 */
public interface PtyExecutor {
  int execPty(String full_path, String[] argv, String[] envp, String dirpath,
              String pts_name, int fdm, String err_pts_name, int err_fdm, boolean console,
              String add_pts_name, int add_pty_fdm);
}
