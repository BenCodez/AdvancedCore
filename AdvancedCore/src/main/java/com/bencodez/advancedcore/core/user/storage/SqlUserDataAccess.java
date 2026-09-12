package com.bencodez.advancedcore.core.user.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

/**
 * Shared SQL row interpretation and user-data access, without a Bukkit plugin.
 * Cache selection and asynchronous write ordering remain with the caller.
 */
public final class SqlUserDataAccess {
    private final SqlUserStorage storage;
    private final Function<UserStorage, List<Column>> rows;

    public SqlUserDataAccess(SqlUserStorage storage) {
        this(storage, Objects.requireNonNull(storage, "storage")::readRow);
    }

    /**
     * A separate live row reader preserves existing facade overrides. Neither
     * callback is invoked during construction, and returned rows are not cached.
     */
    public SqlUserDataAccess(SqlUserStorage storage, Function<UserStorage, List<Column>> rows) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.rows = Objects.requireNonNull(rows, "rows");
    }

    public List<Column> readRow(UserStorage backend) {
        return rows.apply(Objects.requireNonNull(backend, "backend"));
    }

    public int getInt(UserStorage backend, String key, int def) {
        if (key == null || key.isEmpty()) return def;
        List<Column> row = readRow(backend);
        if (row != null) {
            for (Column element : row) {
                if (element.getName().equals(key)) {
                    DataValue value = element.getValue();
                    if (value.isInt()) return value.getInt();
                    if (value.isString()) {
                        String str = value.getString();
                        if (str != null) {
                            try {
                                return Integer.parseInt(str);
                            } catch (Exception ignored) {
                                // Preserve the legacy invalid-string default, including duplicates.
                            }
                        }
                        return def;
                    }
                }
            }
        }
        return def;
    }

    public String getString(UserStorage backend, String key) {
        if (key == null || key.isEmpty()) return "";
        List<Column> row = readRow(backend);
        if (row != null) {
            for (Column element : row) {
                if (element.getName().equals(key)
                        && (element.getValue().isString() || element.getValue().isBoolean())) {
                    String value = element.getValue().getString();
                    return value != null && !value.equalsIgnoreCase("null") ? value : "";
                }
            }
        }
        return "";
    }

    public ArrayList<String> getKeys(UserStorage backend) {
        ArrayList<String> keys = new ArrayList<>();
        List<Column> row = readRow(backend);
        if (row != null && !row.isEmpty()) {
            for (Column column : row) keys.add(column.getName());
        }
        return keys;
    }

    /** Mutable map with the original value objects; duplicate names are last-wins. */
    public static HashMap<String, DataValue> convert(List<Column> columns) {
        HashMap<String, DataValue> values = new HashMap<>();
        if (columns != null) {
            for (Column column : columns) values.put(column.getName(), column.getValue());
        }
        return values;
    }

    public HashMap<String, DataValue> getValues(UserStorage backend) {
        return convert(readRow(backend));
    }

    public boolean hasData(UserStorage backend) {
        return storage.contains(Objects.requireNonNull(backend, "backend"));
    }

    public void remove(UserStorage backend) {
        storage.delete(Objects.requireNonNull(backend, "backend"));
    }

    public void setInt(UserStorage backend, String key, int value) {
        storage.write(Objects.requireNonNull(backend, "backend"), key, new DataValueInt(value));
    }

    public void setString(UserStorage backend, String key, String value) {
        storage.write(Objects.requireNonNull(backend, "backend"), key, new DataValueString(value));
    }

    public void setValues(UserStorage backend, HashMap<String, DataValue> values) {
        storage.writeValues(Objects.requireNonNull(backend, "backend"), values);
    }
}
