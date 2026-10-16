package com.bencodez.advancedcore.tests.core.user.storage.sql;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.tests.support.ReflectiveAccess;
import com.bencodez.simpleapi.sql.mysql.DbType;

/** Constructs the real package-private implementation, then uses its public storage contract. */
final class JdbcStorageAccess {
    private static final Class<?> STORAGE = ReflectiveAccess.type(
            "com.bencodez.advancedcore.core.user.storage.sql.JdbcSqlUserStorage");
    private static final Class<?> DIALECT = ReflectiveAccess.type(STORAGE.getName() + "$Dialect");
    private static final Class<?> CONNECTIONS = ReflectiveAccess.type(STORAGE.getName() + "$ConnectionProvider");

    private JdbcStorageAccess() { }

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }

    static SqlUserStorage create(UserStorage storage, UUID uuid, String table, SqlUserSchema schema,
            ConnectionProvider connections, Dialect dialect, SqlBackendLogger logger) {
        Object provider = Proxy.newProxyInstance(CONNECTIONS.getClassLoader(), new Class<?>[] { CONNECTIONS },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("open")) return connections.open();
                    throw new AssertionError("Unexpected connection-provider method: " + method);
                });
        return ReflectiveAccess.construct(STORAGE,
                new Class<?>[] { UserStorage.class, UUID.class, String.class, SqlUserSchema.class,
                        CONNECTIONS, DIALECT, SqlBackendLogger.class },
                storage, uuid, table, schema, provider, dialect.productionValue(), logger);
    }

    /** Each handle refers to a real production enum constant; values() preserves production coverage. */
    static final class Dialect {
        private static final Dialect[] ALL = java.util.Arrays.stream(DIALECT.getEnumConstants())
                .map(Dialect::new).toArray(Dialect[]::new);
        static final Dialect SQLITE = named("SQLITE");
        static final Dialect MYSQL = named("MYSQL");
        static final Dialect POSTGRESQL = named("POSTGRESQL");

        private final Object value;

        private Dialect(Object value) { this.value = value; }

        private Object productionValue() { return value; }

        static Dialect[] values() { return ALL.clone(); }

        private static Dialect named(String name) {
            for (Dialect dialect : ALL) {
                if (((Enum<?>) dialect.value).name().equals(name)) return dialect;
            }
            throw new AssertionError("Missing production dialect: " + name);
        }

        static Dialect fromDbType(DbType type) {
            Object value = ReflectiveAccess.invoke(DIALECT, null, "fromDbType", new Class<?>[] { DbType.class }, type);
            return named(((Enum<?>) value).name());
        }

        String quote(String identifier) {
            return ReflectiveAccess.invoke(DIALECT, productionValue(), "quote", new Class<?>[] { String.class }, identifier);
        }

        @Override public String toString() { return value.toString(); }
    }
}
