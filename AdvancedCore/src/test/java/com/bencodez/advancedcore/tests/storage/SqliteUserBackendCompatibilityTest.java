package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyBoolean;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Real Xerial SQLite regressions; no Bukkit plugin or mocked database. */
class SqliteUserBackendCompatibilityTest {
    @TempDir
    Path directory;

    @Test
    void booleanKeysRetainTextEncodingAcrossUpdatesAndReopens() throws Exception {
        SqlUserSchema schema = SqlUserSchema.fromKeys(List.of(
                new UserDataKeyBoolean("Enabled"), new UserDataKeyBoolean("Disabled"),
                new UserDataKeyBoolean("Unset")));
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = open("Users", schema)) {
            HashMap<String, DataValue> values = new HashMap<>();
            values.put("Enabled", new DataValueBoolean(true));
            values.put("Disabled", new DataValueBoolean(false));
            backend.user(uuid).writeValues(UserStorage.SQLITE, values);
            assertTrue(value(backend.user(uuid).readRow(UserStorage.SQLITE), "Enabled").getBoolean());
        }
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT Enabled, Disabled FROM Users WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("true", result.getString(1));
                assertEquals("false", result.getString(2));
            }
        }
        try (SqliteUserBackend backend = open("Users", schema)) {
            SqlUserStorage user = backend.user(uuid);
            List<Column> row = user.readRow(UserStorage.SQLITE);
            assertTrue(value(row, "Enabled").getBoolean());
            assertFalse(value(row, "Disabled").getBoolean());
            assertFalse(value(row, "Unset").getBoolean());
            user.write(UserStorage.SQLITE, "Enabled", new DataValueBoolean(false));
            user.write(UserStorage.SQLITE, "Disabled", new DataValueBoolean(true));
        }
        try (SqliteUserBackend backend = open("Users", schema)) {
            List<Column> row = backend.user(uuid).readRow(UserStorage.SQLITE);
            assertFalse(value(row, "Enabled").getBoolean());
            assertTrue(value(row, "Disabled").getBoolean());
        }
    }

    @Test
    void readsLegacyNumericTextAndNullBooleansWithoutMigration() throws Exception {
        SqlUserSchema schema = SqlUserSchema.fromKeys(List.of(new UserDataKeyBoolean("Flag")));
        List<String> encoded = Arrays.asList("1", "0", "true", "false", "TRUE", null);
        List<Boolean> expected = List.of(true, false, true, false, true, false);
        UUID[] users = new UUID[encoded.size()];
        try (SqliteUserBackend ignored = open("Users", schema);
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO Users (uuid, Flag) VALUES (?, ?)")) {
            for (int i = 0; i < users.length; i++) {
                users[i] = UUID.randomUUID();
                statement.setString(1, users[i].toString());
                if (i < 2) {
                    // Reproduce the old binding, which Xerial encodes as 1/0.
                    statement.setBoolean(2, expected.get(i));
                } else {
                    statement.setString(2, encoded.get(i));
                }
                statement.executeUpdate();
            }
        }
        try (SqliteUserBackend backend = open("Users", schema)) {
            for (int i = 0; i < users.length; i++) {
                DataValue flag = value(backend.user(users[i]).readRow(UserStorage.SQLITE), "Flag");
                assertTrue(flag.isBoolean());
                assertEquals(expected.get(i).booleanValue(), flag.getBoolean(), "encoding " + encoded.get(i));
            }
        }
    }

    @Test
    void quotedCustomNamesWorkForCreateAlterCrudAndRestart() {
        String table = "Users - `archive` \"copy\"";
        String originalKey = "Player-Name `label`";
        List<String> addedKeys = List.of("Vote Note", "quote\"field", "Total-votes", "累计票数",
                "field`=?; DROP TABLE `Users`; --");
        SqlUserSchema initial = SqlUserSchema.fromKeys(List.of(new UserDataKeyString(originalKey)));
        UUID uuid = UUID.randomUUID();
        String data = "Ben's value `quoted` \"text\"; not SQL";
        try (SqliteUserBackend backend = open(table, initial)) {
            SqlUserStorage user = backend.user(uuid);
            assertFalse(user.contains(UserStorage.SQLITE));
            user.write(UserStorage.SQLITE, originalKey, new DataValueString(data));
            assertEquals(data, value(user.readRow(UserStorage.SQLITE), originalKey).getString());
        }

        var keys = new java.util.ArrayList<UserDataKeyString>();
        keys.add(new UserDataKeyString(originalKey));
        addedKeys.forEach(key -> keys.add(new UserDataKeyString(key)));
        SqlUserSchema expanded = SqlUserSchema.fromKeys(keys);
        try (SqliteUserBackend backend = open(table, expanded)) {
            // Existing table forces ALTER TABLE and PRAGMA through the same quoting rules.
            SqlUserStorage user = backend.user(uuid);
            HashMap<String, DataValue> values = new HashMap<>();
            addedKeys.forEach(key -> values.put(key, new DataValueString(data + key)));
            user.writeValues(UserStorage.SQLITE, values);
            assertTrue(user.contains(UserStorage.SQLITE));
            assertEquals(List.of(uuid), backend.enumerateUsers());
            assertThrows(IllegalArgumentException.class, () -> user.write(UserStorage.SQLITE,
                    "unregistered`=?; DELETE FROM Users; --", new DataValueString("no")));
        }
        try (SqliteUserBackend backend = open(table, expanded)) {
            SqlUserStorage user = backend.user(uuid);
            List<Column> row = user.readRow(UserStorage.SQLITE);
            assertEquals(data, value(row, originalKey).getString());
            for (String key : addedKeys) {
                assertEquals(data + key, value(row, key).getString());
            }
            user.delete(UserStorage.SQLITE);
            assertFalse(user.contains(UserStorage.SQLITE));
            assertTrue(backend.enumerateUsers().isEmpty());
        }
    }

    private SqliteUserBackend open(String table, SqlUserSchema schema) {
        return new SqliteUserBackend(directory, "Users", table, schema, SqlBackendLogger.NO_OP);
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("Users.db").toAbsolutePath());
    }

    private static DataValue value(List<Column> row, String key) {
        return row.stream().filter(column -> column.getName().equals(key)).findFirst().orElseThrow().getValue();
    }
}
