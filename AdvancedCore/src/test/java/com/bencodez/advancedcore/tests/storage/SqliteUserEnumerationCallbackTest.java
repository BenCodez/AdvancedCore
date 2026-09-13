package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
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
}
