package com.bencodez.advancedcore.core.user.storage.sql;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
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
}
