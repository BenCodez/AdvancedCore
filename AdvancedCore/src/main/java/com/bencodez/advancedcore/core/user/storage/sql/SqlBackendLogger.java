package com.bencodez.advancedcore.core.user.storage.sql;

public interface SqlBackendLogger {
    SqlBackendLogger NO_OP = new SqlBackendLogger() {
        @Override public void info(String message) {}
        @Override public void warn(String message, Throwable error) {}
    };

    void info(String message);

    void warn(String message, Throwable error);
}
