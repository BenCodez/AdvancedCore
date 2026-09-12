package com.bencodez.advancedcore.tests.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Runs with only shared types; the in-memory port is not a live SQL database. */
public final class SqlAccessHeadlessFixture {
    public static void run() {
        for (UserStorage backend : UserStorage.values()) {
            MemoryPort port = new MemoryPort();
            SqlUserDataAccess data = new SqlUserDataAccess(port);
            check(!data.hasData(backend));
            check(data.getInt(backend, "Points", -1) == -1);
            check(!data.hasData(backend));
            data.setInt(backend, "Points", 7);
            data.setString(backend, "PlayerName", "Ben");
            check(data.hasData(backend));
            check(data.getInt(backend, "Points", 0) == 7);
            check(data.getString(backend, "PlayerName").equals("Ben"));
            check(data.getKeys(backend).size() == 2);
            SqlUserDataAccess second = new SqlUserDataAccess(port);
            check(second.getValues(backend).size() == 2);
            second.remove(backend);
            check(!data.hasData(backend));
        }
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Shared SQL-access fixture failed");
    }

    private static final class MemoryPort implements SqlUserStorage {
        private final HashMap<String, DataValue> values = new HashMap<>();
        @Override public List<Column> readRow(UserStorage backend) {
            ArrayList<Column> row = new ArrayList<>();
            values.forEach((key, value) -> row.add(new Column(key, value)));
            return row;
        }
        @Override public boolean contains(UserStorage backend) { return !values.isEmpty(); }
        @Override public void delete(UserStorage backend) { values.clear(); }
        @Override public void write(UserStorage backend, String key, DataValue value) { values.put(key, value); }
        @Override public void writeValues(UserStorage backend, HashMap<String, DataValue> incoming) { values.putAll(incoming); }
    }
}
