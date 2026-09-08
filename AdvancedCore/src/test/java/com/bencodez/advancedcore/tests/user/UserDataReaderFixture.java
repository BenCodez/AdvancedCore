package com.bencodez.advancedcore.tests.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.UserDataReadContext;
import com.bencodez.advancedcore.core.user.UserDataReader;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Dependency-clean behavioral fixture, also executed in the isolated headless test. */
public final class UserDataReaderFixture {
    private UserDataReaderFixture() { }

    public static void run() {
        fetchModePrecedence();
        invalidTemporaryValueFallsThrough();
        capturedCacheAndReadCallbacks();
        missingCacheDoesNotReread();
        stringAndIntegerConversionDifferences();
        sqlDuplicatesAndProviderFailures();
        emptyKeysAndFlatFallbacks();
        temporaryMapIsNotCopied();
    }

    public static void main(String[] args) {
        run();
        System.out.println("PASS: shared user-data read policy without Bukkit");
    }

    public static void fetchModePrecedence() {
        for (UserDataFetchMode mode : UserDataFetchMode.values()) {
            Context context = new Context();
            context.temporary = new HashMap<>(Map.of("Points", new DataValueInt(7)));
            context.cache = cache(new HashMap<>(Map.of("Points", new DataValueInt(8))));
            context.row = List.of(new Column("Points", new DataValueInt(9)));
            int expected = mode.allowTempCache() ? 7 : 8;
            eq(expected, new UserDataReader(context).getInt(UserStorage.MYSQL, "Points", -1, mode));
            eq(0, context.storageReads);
        }
        for (UserDataFetchMode mode : UserDataFetchMode.values()) {
            Context context = new Context();
            context.row = List.of(new Column("Points", new DataValueInt(9)));
            eq(mode.allowStorageLookup() ? 9 : -1,
                    new UserDataReader(context).getInt(UserStorage.SQLITE, "Points", -1, mode));
            eq(mode.allowStorageLookup() ? 1 : 0, context.storageReads);
            eq(mode.allowUserCache() ? 1 : 0, context.cacheRequests);
        }
    }

    public static void invalidTemporaryValueFallsThrough() {
        Context context = new Context();
        context.temporary = new HashMap<>(Map.of("Points", new DataValueString("invalid")));
        context.cache = cache(new HashMap<>(Map.of("Points", new DataValueString("12"))));
        context.row = List.of(new Column("Points", new DataValueInt(13)));
        UserDataReader reader = new UserDataReader(context);
        eq(12, reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.DEFAULT));
        eq(13, reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.NO_CACHE));
        eq(-1, reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.TEMP_ONLY));
        eq(1, context.storageReads);
    }

    public static void capturedCacheAndReadCallbacks() {
        Context context = new Context();
        context.cache = cache(new HashMap<>(Map.of("Points", new DataValueInt(7))));
        context.beforeRead = () -> context.cache = cache(Map.of("Points", new DataValueInt(99)));
        UserDataReader reader = new UserDataReader(context);
        eq(7, reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.CACHE_ONLY));
        eq(1, context.cacheIfNeeded);
        eq(1, context.cacheLookups);
        context.beforeRead = () -> { throw new AssertionError("String read must not call cacheIfNeeded"); };
        context.cache = cache(Map.of("Name", new DataValueString("player")));
        eq("player", reader.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.CACHE_ONLY));
        eq(1, context.cacheIfNeeded);
        eq(0, context.storageReads);
    }

    public static void missingCacheDoesNotReread() {
        Context context = new Context();
        context.onCache = () -> context.cache = cache(Map.of("Points", new DataValueInt(100)));
        context.row = List.of(new Column("Points", new DataValueInt(9)));
        eq(9, new UserDataReader(context).getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.DEFAULT));
        eq(1, context.cacheLookups);
        eq(1, context.cacheRequests);
        eq(1, context.storageReads);
    }

    public static void stringAndIntegerConversionDifferences() {
        Context context = new Context();
        UserDataReader reader = new UserDataReader(context);
        context.temporary = new HashMap<>(Map.of("Name", new DataValueString("null"), "Points", new DataValueInt(7)));
        eq("null", reader.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.TEMP_ONLY));
        eq("", reader.getString(UserStorage.MYSQL, "Points", UserDataFetchMode.TEMP_ONLY));
        context.temporary = null;
        context.cache = cache(Map.of("Name", new DataValueString("null")));
        eq("null", reader.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.CACHE_ONLY));
        context.row = List.of(new Column("Name", new DataValueString("NULL")), new Column("Points", new DataValueInt(7)));
        eq("", reader.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.NO_CACHE));
        eq("", reader.getString(UserStorage.MYSQL, "Points", UserDataFetchMode.NO_CACHE));
        HashMap<String, DataValue> map = new HashMap<>(); map.put("Name", null);
        context.cache = cache(map);
        context.storageReads = 0;
        eq("", reader.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.DEFAULT));
        eq(0, context.storageReads);
    }

    public static void sqlDuplicatesAndProviderFailures() {
        Context context = new Context();
        context.row = List.of(new Column("Points", new DataValueString("bad")), new Column("Points", new DataValueInt(99)));
        UserDataReader reader = new UserDataReader(context);
        eq(-1, reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.NO_CACHE));
        context.failure = new IllegalStateException("database fixture");
        try {
            reader.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.NO_CACHE);
            throw new AssertionError("Database failure was swallowed");
        } catch (IllegalStateException failure) {
            if (failure != context.failure) throw new AssertionError("Wrong database failure", failure);
        }
    }

    @SuppressWarnings("deprecation")
    public static void emptyKeysAndFlatFallbacks() {
        Context context = new Context();
        UserDataReader reader = new UserDataReader(context);
        eq("", reader.getString(null, null, null));
        eq("", reader.getString(null, "", null));
        eq(5, reader.getInt(UserStorage.MYSQL, null, 5, null));
        context.flatNumber = 17;
        eq(17, reader.getInt(UserStorage.FLAT, "", 5, null));
        eq(0, context.cacheLookups);
        context.failure = new IllegalStateException("file fixture");
        eq(5, reader.getInt(UserStorage.FLAT, "Points", 5, UserDataFetchMode.NO_CACHE));
        eq("", reader.getString(UserStorage.FLAT, "Name", UserDataFetchMode.NO_CACHE));
    }

    public static void temporaryMapIsNotCopied() {
        Context context = new Context();
        context.temporary = new HashMap<>();
        UserDataReader reader = new UserDataReader(context);
        eq(0, reader.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.TEMP_ONLY));
        context.temporary.put("Points", new DataValueInt(4));
        eq(4, reader.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.TEMP_ONLY));
        context.temporary = new HashMap<>(Map.of("Points", new DataValueInt(5)));
        eq(5, reader.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.TEMP_ONLY));
        eq(0, context.storageReads);
    }

    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static UserDataReadContext.Cache cache(Map<String, DataValue> values) {
        return new UserDataReadContext.Cache() {
            public boolean isCached(String key) { return values.containsKey(key); }
            public DataValue get(String key) { return values.get(key); }
        };
    }

    private static final class Context implements UserDataReadContext {
        private Map<String, DataValue> temporary;
        private Cache cache;
        private List<Column> row = List.of();
        private int storageReads, cacheLookups, cacheRequests, cacheIfNeeded, flatNumber;
        private RuntimeException failure;
        private Runnable beforeRead = () -> { };
        private Runnable onCache = () -> { };
        public Map<String, DataValue> tempCache() { return temporary; }
        public Cache userCache() { cacheLookups++; return cache; }
        public void cacheIfNeeded() { cacheIfNeeded++; beforeRead.run(); }
        public void cache() { cacheRequests++; onCache.run(); }
        public List<Column> sqliteRow() { storageReads++; if (failure != null) throw failure; return row; }
        public List<Column> mysqlRow() { return sqliteRow(); }
        public int flatInt(String key, int fallback) { if (failure != null) throw failure; return flatNumber; }
        public String flatString(String key) { if (failure != null) throw failure; return "flat"; }
    }
}
