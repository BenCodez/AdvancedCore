package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/**
 * Constructs SQL user backends from explicit platform-neutral inputs. Native
 * platform adapters are responsible for translating their configuration and
 * data-directory concepts before calling this factory.
 */
public final class SqlUserBackendFactory {
    private SqlUserBackendFactory() {
    }

    public static SqliteUserBackend sqlite(Path dataDirectory, String databaseName, String tableName,
            Collection<? extends UserDataKey> keys, SqlBackendLogger logger) {
        Objects.requireNonNull(keys, "keys");
        return new SqliteUserBackend(dataDirectory, databaseName, tableName, SqlUserSchema.fromKeys(keys), logger);
    }

    public static MysqlUserBackend mysql(String baseTableName, MysqlConfig config,
            Collection<? extends UserDataKey> keys, SqlBackendLogger logger) {
        Objects.requireNonNull(keys, "keys");
        return new MysqlUserBackend(baseTableName, config, SqlUserSchema.fromKeys(keys), logger);
    }

    @FunctionalInterface
    public interface ConnectionOpener { Connection open() throws SQLException; }

    /** Adapt an existing platform-owned SQL provider to the shared JDBC user transaction. */
    public static SqlUserStorage existingUser(UserStorage storage, UUID uuid, String tableName,
            SqlUserSchema schema, ConnectionOpener connections, DbType dbType, SqlBackendLogger logger) {
        Objects.requireNonNull(storage, "storage");
        JdbcSqlUserStorage.Dialect dialect = storage == UserStorage.SQLITE
                ? JdbcSqlUserStorage.Dialect.SQLITE
                : JdbcSqlUserStorage.Dialect.fromDbType(Objects.requireNonNull(dbType, "dbType"));
        return new JdbcSqlUserStorage(storage, uuid, tableName, schema,
                connections::open, dialect, logger);
    }
}
