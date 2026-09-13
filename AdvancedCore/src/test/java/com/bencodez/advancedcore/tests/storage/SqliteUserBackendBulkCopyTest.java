package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Real SQLite copies through the shared row access API, without Bukkit. */
class SqliteUserBackendBulkCopyTest {
    @TempDir
    Path directory;

    private static final SqlUserSchema SCHEMA = SqlUserSchema.builder()
            .column("Points", "INTEGER", DataType.INTEGER)
            .column("Enabled", "VARCHAR(5)", DataType.BOOLEAN)
            .column("Custom-note", "TEXT", DataType.STRING).build();

    @Test
    void copiesReadRowValuesToBoundUserAndPersistsAcrossReopen() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        try (SqliteUserBackend source = open("Source"); SqliteUserBackend target = open("Target")) {
            SqlUserDataAccess sourceData = new SqlUserDataAccess(source.user(sourceId));
            sourceData.setValues(UserStorage.SQLITE, sampleValues(42));
            HashMap<String, DataValue> copied = sourceData.getValues(UserStorage.SQLITE);
            assertEquals(sourceId.toString(), copied.get("uuid").getString());
            HashMap<String, DataValue> before = new HashMap<>(copied);

            new SqlUserDataAccess(target.user(targetId)).setValues(UserStorage.SQLITE, copied);
            assertEquals(before, copied);
            assertFalse(target.user(sourceId).contains(UserStorage.SQLITE));
        }
        try (SqliteUserBackend source = open("Source"); SqliteUserBackend target = open("Target")) {
            HashMap<String, DataValue> row = new SqlUserDataAccess(target.user(targetId)).getValues(UserStorage.SQLITE);
            assertEquals(targetId.toString(), row.get("uuid").getString());
            assertEquals(42, row.get("Points").getInt());
            assertTrue(row.get("Enabled").getBoolean());
            assertEquals("RewardA;;RewardB", row.get("Custom-note").getString());
            assertEquals(List.of(targetId), target.enumerateUsers());
            assertEquals(List.of(sourceId), source.enumerateUsers());
            assertEquals(42, new SqlUserDataAccess(source.user(sourceId)).getInt(UserStorage.SQLITE, "Points", 0));
        }
    }

    @Test
    void uuidOnlyMapDoesNotCreateARowAndMixedCaseMetadataIsIgnored() {
        UUID targetId = UUID.randomUUID();
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("uuid", new DataValueString(UUID.randomUUID().toString()));
        values.put("UUID", null);
        values.put("uUiD", new DataValueString("not a UUID"));
        try (SqliteUserBackend target = open("Target")) {
            target.user(targetId).writeValues(UserStorage.SQLITE, values);
            assertFalse(target.user(targetId).contains(UserStorage.SQLITE));
            assertTrue(target.enumerateUsers().isEmpty());
            values.putAll(sampleValues(9));
            HashMap<String, DataValue> before = new HashMap<>(values);
            target.user(targetId).writeValues(UserStorage.SQLITE, values);
            assertEquals(before, values);
        }
        try (SqliteUserBackend target = open("Target")) {
            SqlUserDataAccess data = new SqlUserDataAccess(target.user(targetId));
            assertEquals(targetId.toString(), data.getString(UserStorage.SQLITE, "uuid"));
            assertEquals(9, data.getInt(UserStorage.SQLITE, "Points", 0));
            assertEquals(List.of(targetId), target.enumerateUsers());
        }
    }

    @Test
    void ignoringCopiedUuidDoesNotHideInvalidColumnsOrPartialWrites() {
        UUID targetId = UUID.randomUUID();
        try (SqliteUserBackend target = open("Target")) {
            SqlUserDataAccess data = new SqlUserDataAccess(target.user(targetId));
            data.setValues(UserStorage.SQLITE, sampleValues(7));
            HashMap<String, DataValue> invalid = new LinkedHashMap<>();
            invalid.put("uuid", new DataValueString(UUID.randomUUID().toString()));
            invalid.put("Points", new DataValueInt(99));
            invalid.put("Unregistered", new DataValueString("reject"));
            assertThrows(IllegalArgumentException.class, () -> data.setValues(UserStorage.SQLITE, invalid));
        }
        try (SqliteUserBackend target = open("Target")) {
            SqlUserDataAccess data = new SqlUserDataAccess(target.user(targetId));
            assertEquals(7, data.getInt(UserStorage.SQLITE, "Points", 0));
            assertEquals(List.of(targetId), target.enumerateUsers());
        }
    }

    private SqliteUserBackend open(String database) {
        return new SqliteUserBackend(directory, database, "Users", SCHEMA, SqlBackendLogger.NO_OP);
    }

    private HashMap<String, DataValue> sampleValues(int points) {
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("Points", new DataValueInt(points));
        values.put("Enabled", new DataValueBoolean(true));
        values.put("Custom-note", new DataValueString("RewardA;;RewardB"));
        return values;
    }
}
