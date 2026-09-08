package com.bencodez.advancedcore.core.user;

import java.util.List;
import java.util.Objects;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * Existing scalar-read policy extracted from UserData. Fetch-mode semantics,
 * cache precedence, conversion quirks and fallback behavior are preserved. This
 * class neither resolves UUIDs nor changes caches, storage formats or writes.
 */
public final class UserDataReader {
    private final UserDataReadContext context;

    public UserDataReader(UserDataReadContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @SuppressWarnings("deprecation")
    public int getInt(UserStorage storage, String key, int def, UserDataFetchMode mode) {
        if (key == null || key.isEmpty()) {
            if (storage.equals(UserStorage.FLAT)) {
                try { return context.flatInt(key, def); } catch (Exception ignored) { }
            }
            return def;
        }
        if (mode.allowTempCache() && context.tempCache() != null) {
            DataValue value = context.tempCache().get(key);
            if (value != null) {
                if (value.isInt()) return value.getInt();
                if (value.isString()) {
                    try { return Integer.parseInt(value.getString()); } catch (Exception ignored) { }
                }
            } else if (!mode.allowUserCache() && !mode.allowStorageLookup()) {
                return def;
            }
        }
        if (mode.allowUserCache()) {
            UserDataReadContext.Cache cache = context.userCache();
            if (cache != null) {
                context.cacheIfNeeded();
                if (cache.isCached(key)) {
                    DataValue value = cache.get(key);
                    if (value != null) {
                        if (value.isInt()) return value.getInt();
                        String str = value.getString();
                        if (str != null && !str.equalsIgnoreCase("null")) {
                            try { return Integer.parseInt(str); } catch (Exception ignored) { }
                        }
                    }
                }
            } else {
                context.cache();
            }
            if (!mode.allowStorageLookup()) return def;
        } else if (!mode.allowStorageLookup()) {
            return def;
        }
        if (storage.equals(UserStorage.SQLITE)) {
            return integerRow(context.sqliteRow(), key, def);
        } else if (storage.equals(UserStorage.MYSQL)) {
            return integerRow(context.mysqlRow(), key, def);
        } else if (storage.equals(UserStorage.FLAT)) {
            try { return context.flatInt(key, def); } catch (Exception ignored) { }
        }
        return def;
    }

    private static int integerRow(List<Column> row, String key, int def) {
        if (row != null) {
            for (Column column : row) {
                if (column.getName().equals(key)) {
                    DataValue value = column.getValue();
                    if (value.isInt()) return value.getInt();
                    if (value.isString()) {
                        String str = value.getString();
                        if (str != null) {
                            try { return Integer.parseInt(str); } catch (Exception ignored) { }
                        }
                        return def;
                    }
                }
            }
        }
        return def;
    }

    @SuppressWarnings("deprecation")
    public String getString(UserStorage storage, String key, UserDataFetchMode mode) {
        if (key == null || key.isEmpty()) return "";
        if (mode.allowTempCache() && context.tempCache() != null) {
            DataValue value = context.tempCache().get(key);
            if (value != null) {
                if (value.isString() || value.isBoolean()) {
                    String str = value.getString();
                    return str != null ? str : "";
                }
            } else if (!mode.allowUserCache() && !mode.allowStorageLookup()) {
                return "";
            }
        }
        if (mode.allowUserCache()) {
            UserDataReadContext.Cache cache = context.userCache();
            if (cache != null) {
                // Unlike integer reads, the existing string path does not call cacheIfNeeded.
                if (cache.isCached(key)) {
                    DataValue value = cache.get(key);
                    if (value != null) {
                        String str = value.getString();
                        return str != null ? str : "";
                    }
                    return "";
                }
            } else {
                context.cache();
            }
            if (!mode.allowStorageLookup()) return "";
        } else if (!mode.allowStorageLookup()) {
            return "";
        }
        if (storage.equals(UserStorage.SQLITE)) {
            return stringRow(context.sqliteRow(), key);
        } else if (storage.equals(UserStorage.MYSQL)) {
            return stringRow(context.mysqlRow(), key);
        } else if (storage.equals(UserStorage.FLAT)) {
            try { return context.flatString(key); } catch (Exception ignored) { }
        }
        return "";
    }

    private static String stringRow(List<Column> row, String key) {
        if (row != null) {
            for (Column column : row) {
                if (column.getName().equals(key) && (column.getValue().isString() || column.getValue().isBoolean())) {
                    String str = column.getValue().getString();
                    return str != null && !str.equalsIgnoreCase("null") ? str : "";
                }
            }
        }
        return "";
    }
}
