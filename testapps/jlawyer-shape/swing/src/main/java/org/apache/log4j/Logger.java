package org.apache.log4j;

/**
 * Minimal mock of log4j 1.x {@code Logger}, delegating to {@code java.util.logging}
 * so the {@code InvoicePositionEntryPanel} slice's logging compiles and runs
 * without adding a log4j dependency. Only the handful of methods the slice uses
 * are provided; JLawyer's real logging stack is out of scope for the exit gate.
 */
public class Logger {

    private final java.util.logging.Logger delegate;

    private Logger(String name) {
        this.delegate = java.util.logging.Logger.getLogger(name);
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getName());
    }

    public void info(Object message) {
        delegate.info(String.valueOf(message));
    }

    public void warn(Object message) {
        delegate.warning(String.valueOf(message));
    }

    public void warn(Object message, Throwable t) {
        delegate.log(java.util.logging.Level.WARNING, String.valueOf(message), t);
    }

    public void error(Object message) {
        delegate.severe(String.valueOf(message));
    }

    public void error(Object message, Throwable t) {
        delegate.log(java.util.logging.Level.SEVERE, String.valueOf(message), t);
    }

    public void debug(Object message) {
        delegate.fine(String.valueOf(message));
    }
}
