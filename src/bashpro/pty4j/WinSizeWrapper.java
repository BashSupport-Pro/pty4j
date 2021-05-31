package bashpro.pty4j;

import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * Older versions of WinSize don't have
 * "int getColumns()", but "int ws_col" and "int getRows()", but "int ws_row".
 * <p>
 * This wrapper first tries to use the method, then falls back to use the field.
 */
public class WinSizeWrapper {
    public static int getRows(@NotNull WinSize delegate) {
        return methodOrField(delegate, "getRows", "ws_row");
    }

    public static int getColumns(@NotNull WinSize delegate) {
        return methodOrField(delegate, "getColumns", "ws_col");
    }

    private static int methodOrField(@NotNull WinSize delegate, @NotNull String methodName, @NotNull String fieldName) {
        try {
            return (int) delegate.getClass().getMethod(methodName).invoke(delegate);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            try {
                return delegate.getClass().getField(fieldName).getInt(delegate);
            } catch (IllegalAccessException | NoSuchFieldException illegalAccessException) {
                throw new UnsupportedOperationException("Both method " + methodName + " and field " + fieldName + " were not found");
            }
        }
    }
}
