package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class SqliteUserBackendPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAcrossCloseAndReopenWithoutBukkit() {
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("PlayerName", "VARCHAR(30)", DataType.STRING)
                .column("Points", "INTEGER", DataType.INTEGER)
                .column("OfflineRewards", "MEDIUMTEXT", DataType.STRING)
                .build();
        UUID uuid = UUID.randomUUID();

        try (SqliteUserBackend backend = new SqliteUserBackend(tempDir, "Users", "Users", schema,
                SqlBackendLogger.NO_OP)) {
            SqlUserStorage user = backend.user(uuid);
            HashMap<String, DataValue> values = new HashMap<>();
            values.put("PlayerName", new DataValueString("Ben"));
            values.put("Points", new DataValueInt(42));
            values.put("OfflineRewards", new DataValueString("RewardA;;RewardB"));
            user.writeValues(UserStorage.SQLITE, values);

            assertTrue(user.contains(UserStorage.SQLITE));
            assertEquals(List.of(uuid), backend.enumerateUsers());
            assertTrue(backend.databaseFile().toFile().isFile());
        }

        try (SqliteUserBackend reopened = new SqliteUserBackend(tempDir, "Users", "Users", schema,
                SqlBackendLogger.NO_OP)) {
            SqlUserStorage user = reopened.user(uuid);
            List<Column> row = user.readRow(UserStorage.SQLITE);
            assertEquals("Ben", value(row, "PlayerName").getString());
            assertEquals(42, value(row, "Points").getInt());
            assertEquals("RewardA;;RewardB", value(row, "OfflineRewards").getString());

            user.delete(UserStorage.SQLITE);
            assertFalse(user.contains(UserStorage.SQLITE));
        }
    }

    @Test
    void failedInitializationDoesNotRemainOpen() {
        Path regularFile = tempDir.resolve("not-a-directory");
        try {
            java.nio.file.Files.writeString(regularFile, "x");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        boolean failed = false;
        try {
            new SqliteUserBackend(regularFile, "Users", "Users",
                    SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        assertTrue(failed);
    }

    private static DataValue value(List<Column> row, String name) {
        return row.stream()
                .filter(column -> name.equalsIgnoreCase(column.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
