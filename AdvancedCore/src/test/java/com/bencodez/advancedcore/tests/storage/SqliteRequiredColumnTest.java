package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.sql.*;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.*;

/** Real SQLite required-column persistence and rollback regressions. */
class SqliteRequiredColumnTest {
    @TempDir Path directory;
    private final SqlUserSchema schema = SqlUserSchema.builder()
            .column("PlayerName", "VARCHAR(30) NOT NULL", DataType.STRING)
            .column("Points", "INTEGER DEFAULT 7", DataType.INTEGER).build();

    @Test void suppliedRequiredValueIsInsertedAndRetainsDefaultsAfterReopen() {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = open()) {
            backend.user(uuid).write(UserStorage.SQLITE, "PlayerName", new DataValueString("Ben"));
        }
        try (SqliteUserBackend backend = open()) {
            var values = new SqlUserDataAccess(backend.user(uuid)).getValues(UserStorage.SQLITE);
            assertEquals("Ben", values.get("PlayerName").getString());
            assertEquals(7, values.get("Points").getInt());
            assertEquals(List.of(uuid), backend.enumerateUsers());
        }
    }

    @Test void existingRowCanBeUpdatedWithoutResupplyingRequiredColumns() {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = open()) {
            var user = backend.user(uuid);
            user.write(UserStorage.SQLITE, "PlayerName", new DataValueString("Ben"));
            user.write(UserStorage.SQLITE, "Points", new DataValueInt(12));
        }
        try (SqliteUserBackend backend = open()) {
            var values = new SqlUserDataAccess(backend.user(uuid)).getValues(UserStorage.SQLITE);
            assertEquals("Ben", values.get("PlayerName").getString());
            assertEquals(12, values.get("Points").getInt());
        }
    }

    @Test void missingRequiredValueCannotReportASuccessfulWriteToANonexistentRow() {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = open()) {
            assertThrows(IllegalStateException.class,
                    () -> backend.user(uuid).write(UserStorage.SQLITE, "Points", new DataValueInt(12)));
            assertFalse(backend.user(uuid).contains(UserStorage.SQLITE));
        }
        try (SqliteUserBackend backend = open()) { assertTrue(backend.enumerateUsers().isEmpty()); }
    }

    @Test void caseAliasesAndCopiedUuidMetadataKeepOneColumnAndTheBoundIdentity() {
        UUID uuid = UUID.randomUUID();
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("PlayerName", new DataValueString("Ben"));
        values.put("playername", new DataValueString("Ben"));
        values.put("UUID", new DataValueString(UUID.randomUUID().toString()));
        HashMap<String, DataValue> original = new HashMap<>(values);
        try (SqliteUserBackend backend = open()) {
            backend.user(uuid).writeValues(UserStorage.SQLITE, values);
            assertEquals(original, values);
            assertEquals(List.of(uuid), backend.enumerateUsers());
            assertEquals("Ben", new SqlUserDataAccess(backend.user(uuid)).getString(UserStorage.SQLITE, "PlayerName"));
        }
    }

    private SqliteUserBackend open() {
        return new SqliteUserBackend(directory, "Users", "Users", schema, SqlBackendLogger.NO_OP);
    }
}
