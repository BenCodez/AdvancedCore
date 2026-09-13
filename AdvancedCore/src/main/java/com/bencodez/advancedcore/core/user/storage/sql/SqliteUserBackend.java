package com.bencodez.advancedcore.core.user.storage.sql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;

public final class SqliteUserBackend implements SqlUserBackend {
    private final Path databaseFile;
    private final String tableName;
    private final SqlUserSchema schema;
    private final SqlBackendLogger logger;
    private final AtomicBoolean open = new AtomicBoolean();

    public SqliteUserBackend(Path dataDirectory, String databaseName, String tableName,
            SqlUserSchema schema, SqlBackendLogger logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(databaseName, "databaseName");
        this.tableName = requireIdentifier(tableName, "tableName");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.logger = logger == null ? SqlBackendLogger.NO_OP : logger;
        if (databaseName.isBlank() || databaseName.contains("/") || databaseName.contains("\\")) {
            throw new IllegalArgumentException("databaseName must be a simple file name");
        }
        this.databaseFile = dataDirectory.resolve(databaseName + ".db");
        initialize();
    }

    public Path databaseFile() {
        return databaseFile;
    }

    @Override
    public UserStorage storageType() {
        return UserStorage.SQLITE;
    }

    @Override
    public SqlUserStorage user(UUID uuid) {
        requireOpen();
        return new JdbcSqlUserStorage(UserStorage.SQLITE, uuid, tableName, schema, this::openConnection,
                JdbcSqlUserStorage.Dialect.SQLITE, logger);
    }

    @Override
    public List<UUID> enumerateUsers() {
        requireOpen();
        String sql = "SELECT `uuid` FROM `" + tableName + "`";
        ArrayList<UUID> users = new ArrayList<>();
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String value = result.getString(1);
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    users.add(UUID.fromString(value));
                } catch (IllegalArgumentException invalid) {
                    logger.warn("Skipping invalid UUID in " + tableName + ": " + value, invalid);
                }
            }
            return users;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to enumerate SQLite users", e);
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        open.set(false);
    }

    private void initialize() {
        try {
            Files.createDirectories(databaseFile.getParent());
            Class.forName("org.sqlite.JDBC");
            open.set(true);
            try (Connection connection = openConnection();
                    PreparedStatement statement = connection.prepareStatement(createTableSql())) {
                statement.executeUpdate();
            }
            ensureRegisteredColumns();
        } catch (IOException | ClassNotFoundException | SQLException | RuntimeException e) {
            open.set(false);
            throw new IllegalStateException("Failed to initialize SQLite user backend at " + databaseFile, e);
        }
    }

    private void ensureRegisteredColumns() throws SQLException {
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())) {
                continue;
            }
            if (!hasColumn(column.name())) {
                String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + column.name() + "` " + column.sqlType();
                try (Connection connection = openConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                }
            }
        }
    }

    private boolean hasColumn(String name) throws SQLException {
        String sql = "PRAGMA table_info(`" + tableName + "`)";
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private String createTableSql() {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (");
        boolean first = true;
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append('`').append(column.name()).append("` ").append(column.sqlType());
        }
        sql.append(", PRIMARY KEY (`uuid`))");
        return sql.toString();
    }

    private Connection openConnection() throws SQLException {
        requireOpen();
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    }

    private void requireOpen() {
        if (!open.get()) {
            throw new IllegalStateException("SQLite user backend is closed");
        }
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(label + " is not a safe SQL identifier: " + value);
        }
        return value;
    }
}
