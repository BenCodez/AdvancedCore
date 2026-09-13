package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class SqliteUserEnumerationCallbackTest {
    @TempDir Path directory;

    @Test
    void callbacksCanWriteThroughTheSameBackendWithoutHoldingTheEnumerationReadTransaction() {
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();
        try (SqliteUserBackend backend = new SqliteUserBackend(directory, "Users", "Users", schema, SqlBackendLogger.NO_OP)) {
            List<UUID> users = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            for (UUID uuid : users) {
                HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values = new HashMap<>();
                values.put("Points", new DataValueInt(1));
                backend.user(uuid).writeValues(UserStorage.SQLITE, values);
            }
            backend.forEachUser(uuid -> backend.user(uuid).write(UserStorage.SQLITE, "Points", new DataValueInt(2)));
            for (UUID uuid : users) {
                assertEquals(2, backend.user(uuid).readRow(UserStorage.SQLITE).stream()
                        .filter(column -> "Points".equals(column.getName())).findFirst().orElseThrow().getValue().getInt());
            }
        }
    }

    @Test
    void nullUuidRowsDoNotShortenTheFirstPaginationPage() throws Exception {
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();
        try (SqliteUserBackend backend = new SqliteUserBackend(directory, "PagedUsers", "Users", schema, SqlBackendLogger.NO_OP)) {
            List<UUID> expected = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile().toAbsolutePath())) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `Users` (`uuid`, `Points`) VALUES (?, ?)")) {
                    statement.setObject(1, null);
                    statement.setInt(2, 0);
                    statement.addBatch();
                    for (int i = 0; i < 513; i++) {
                        UUID uuid = new UUID(0L, i + 1L);
                        expected.add(uuid);
                        statement.setString(1, uuid.toString());
                        statement.setInt(2, i);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            }
            List<UUID> actual = new ArrayList<>();
            backend.forEachUser(actual::add);
            assertEquals(513, actual.size());
            assertEquals(expected.stream().sorted().toList(), actual);
        }
    }

    @Test
    void malformedUuidWarningIsBoundedAndOmitsStoredValue() throws Exception {
        String malformed = "private-value-".repeat(1_000);
        List<String> warnings = new ArrayList<>();
        SqlBackendLogger logger = new SqlBackendLogger() {
            @Override public void info(String message) {}
            @Override public void warn(String message, Throwable error) {
				assertEquals("Malformed SQLite UUID value", error.getMessage());
                warnings.add(message);
            }
        };
        try (SqliteUserBackend backend = new SqliteUserBackend(directory, "Users", "Users",
                SqlUserSchema.builder().build(), logger);
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile().toAbsolutePath());
                PreparedStatement statement = connection.prepareStatement("INSERT INTO `Users` (`uuid`) VALUES (?)")) {
            statement.setString(1, malformed);
            statement.executeUpdate();
            statement.setString(1, malformed + "another");
            statement.executeUpdate();

            assertEquals(List.of(), backend.enumerateUsers());
        }
        assertEquals(List.of("Skipping malformed UUID entries while enumerating SQLite users; further diagnostics suppressed"), warnings);
    }
}
