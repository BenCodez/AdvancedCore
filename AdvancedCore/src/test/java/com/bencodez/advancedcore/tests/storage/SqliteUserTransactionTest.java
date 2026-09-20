package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

@Timeout(20)
class SqliteUserTransactionTest {
    @TempDir Path tempDir;
    private static final UserStorage TYPE = UserStorage.SQLITE;

    private SqliteUserBackend backend() {
        return new SqliteUserBackend(tempDir, "Users", "Users", SqlUserSchema.builder()
                .column("Points", "INTEGER", DataType.INTEGER)
                .column("Votes", "INTEGER", DataType.INTEGER).build(), SqlBackendLogger.NO_OP);
    }

    private void createReceipts(Path file) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE Receipts (operation_key TEXT PRIMARY KEY, intent TEXT NOT NULL)")) {
            statement.executeUpdate();
        }
    }

    private static int value(List<Column> row, String key) {
        return row.stream().filter(column -> key.equalsIgnoreCase(column.getName()))
                .findFirst().orElseThrow().getValue().getInt();
    }

    private static void insert(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO Receipts (operation_key, intent) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, "immutable-plan");
            statement.executeUpdate();
        }
    }

    private static boolean receipt(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT intent FROM Receipts WHERE operation_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "immutable-plan".equals(result.getString(1));
            }
        }
    }

    private static HashMap<String, DataValue> values(int points, int votes) {
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("Points", new DataValueInt(points));
        values.put("Votes", new DataValueInt(votes));
        return values;
    }

    @Test void commitsUserValuesAndCallerReceiptTogetherAcrossRestart() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = backend()) {
            createReceipts(backend.databaseFile());
            SqlUserStorage user = backend.user(uuid);
            assertEquals("done", user.transaction(TYPE, scope -> {
                scope.writeValues(values(7, 2));
                insert(scope.connection(), "one");
                return "done";
            }));
        }
        try (SqliteUserBackend reopened = backend();
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + reopened.databaseFile())) {
            assertEquals(7, value(reopened.user(uuid).readRow(TYPE), "Points"));
            assertEquals(2, value(reopened.user(uuid).readRow(TYPE), "Votes"));
            assertTrue(receipt(connection, "one"));
        }
    }

    @Test void rollsBackBothFailureOrders() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = backend()) {
            createReceipts(backend.databaseFile());
            SqlUserStorage user = backend.user(uuid);
            user.writeValues(TYPE, values(3, 4));
            assertThrows(IllegalStateException.class, () -> user.transaction(TYPE, scope -> {
                scope.writeValues(values(9, 10));
                insert(scope.connection(), "after");
                throw new SQLException("fail after both writes");
            }));
            assertThrows(IllegalStateException.class, () -> user.transaction(TYPE, scope -> {
                insert(scope.connection(), "before");
                throw new SQLException("fail before user write");
            }));
            UUID absent = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> backend.user(absent).transaction(TYPE, scope -> {
                insert(scope.connection(), "new-user");
                throw new SQLException("fail before creating user values");
            }));
            assertFalse(backend.user(absent).contains(TYPE));
            assertThrows(IllegalStateException.class, () -> user.transaction(TYPE, scope -> {
                scope.writeValues(values(20, 21));
                insert(scope.connection(), "same");
                insert(scope.connection(), "same");
                return null;
            }));
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile())) {
                assertEquals(3, value(user.readRow(TYPE), "Points"));
                assertEquals(4, value(user.readRow(TYPE), "Votes"));
                assertFalse(receipt(connection, "after"));
                assertFalse(receipt(connection, "before"));
                assertFalse(receipt(connection, "new-user"));
                assertFalse(receipt(connection, "same"));
            }
        }
    }

    @Test void concurrentDuplicateAndLostAcknowledgementApplyOnce() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = backend(); SqliteUserBackend otherBackend = backend()) {
            createReceipts(backend.databaseFile());
            SqlUserStorage user = backend.user(uuid);
            // Independent backend instances have no shared JVM operation lock.
            SqlUserStorage otherUser = otherBackend.user(uuid);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                java.util.function.Function<SqlUserStorage, java.util.concurrent.Callable<Boolean>> attempt = candidate -> () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return applyOnce(candidate, "duplicate");
                };
                Future<Boolean> first = workers.submit(attempt.apply(user));
                Future<Boolean> second = workers.submit(attempt.apply(otherUser));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                // The first success acknowledgement is deliberately discarded.
                assertFalse(applyOnce(user, "duplicate"));
                assertEquals(1, value(user.readRow(TYPE), "Points"));
                assertEquals(1, value(user.readRow(TYPE), "Votes"));
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile());
                     PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM Receipts")) {
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                }
            } finally {
                start.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static boolean applyOnce(SqlUserStorage user, String key) {
        return user.transaction(TYPE, scope -> {
            if (receipt(scope.connection(), key)) return false;
            List<Column> row = scope.readRow();
            scope.writeValues(values(value(row, "Points") + 1, value(row, "Votes") + 1));
            insert(scope.connection(), key);
            return true;
        });
    }

    @Test void runtimePublishesOnlyCommittedValuesToItsCacheOwner() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = backend()) {
            backend.user(uuid).writeValues(TYPE, values(2, 3));
            SimpleCacheOwner cache = new SimpleCacheOwner();
            SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
            UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
            manager.bindSharedRuntime(runtime);
            try {
            assertEquals(2, runtime.populate(uuid).get("Points").getInt());
            assertThrows(IllegalStateException.class, () -> manager.withAtomicUserTransaction(uuid, TYPE, Map.of(), scope -> {
                scope.writeValues(values(8, 9));
                throw new SQLException("abort");
            }));
            assertEquals(2, runtime.read(uuid, "Points", UserDataFetchMode.CACHE_ONLY,
                    null, new DataValueInt(-1)).getInt());
            manager.withAtomicUserTransaction(uuid, TYPE, Map.of(), scope -> {
                scope.writeValues(values(10, 11));
                return null;
            });
            assertFalse(cache.isCached(uuid));
            assertEquals(10, runtime.read(uuid, "Points", UserDataFetchMode.DEFAULT,
                    null, new DataValueInt(-1)).getInt());
            assertEquals(11, value(backend.user(uuid).readRow(TYPE), "Votes"));
            } finally { manager.getTimer().shutdownNow(); }
        }
    }

    @Test void productionBukkitSqliteRouteCanCommitCallerRecordWithUserValues() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend standalone = backend()) {
            createReceipts(standalone.databaseFile());
            String url = "jdbc:sqlite:" + standalone.databaseFile();
            try (Connection legacyConnection = DriverManager.getConnection(url)) {
                AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
                UserTable table = mock(UserTable.class);
                SQLite sqlite = mock(SQLite.class);
                UserManager users = mock(UserManager.class);
                UserDataManager manager = mock(UserDataManager.class);
                when(plugin.getStorageType()).thenReturn(TYPE);
                when(plugin.getSQLiteUserTable()).thenReturn(table);
                when(plugin.getUserManager()).thenReturn(users);
                when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
                when(users.getDataManager()).thenReturn(manager);
                when(manager.getKeys()).thenReturn(new java.util.ArrayList<>(List.of(
                        new UserDataKeyInt("Points"), new UserDataKeyInt("Votes"))));
                when(table.getSqLite()).thenReturn(sqlite);
                when(table.getName()).thenReturn("Users");
                when(sqlite.getSQLConnection()).thenReturn(legacyConnection);

                SharedUserDataRuntime runtime = new SharedUserDataRuntime(new BukkitSqlUserBackend(plugin),
                        new SimpleCacheOwner());
                assertEquals("saved", runtime.transaction(uuid, scope -> {
                    scope.writeValues(values(4, 5));
                    insert(scope.connection(), "bukkit");
                    return "saved";
                }));
                assertEquals(4, value(standalone.user(uuid).readRow(TYPE), "Points"));
                try (Connection check = DriverManager.getConnection(url)) {
                    assertTrue(receipt(check, "bukkit"));
                }
            }
        }
    }

    @Test void transactionFencesBypassPublishersAndReopensCacheAfterRollback() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SqliteUserBackend backend = backend()) {
            backend.user(uuid).writeValues(TYPE, values(1, 1));
            FencedCacheOwner cache = new FencedCacheOwner();
            SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
            runtime.populate(uuid);
            CountDownLatch inCallback = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> transaction = worker.submit(() -> runtime.transaction(uuid, scope -> {
                    inCallback.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("timed out"); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("interrupted", interrupted);
                    }
                    scope.writeValues(values(2, 2));
                    return null;
                }));
                assertTrue(inCallback.await(5, TimeUnit.SECONDS));
                assertFalse(cache.tryPublishBypass());
                release.countDown();
                transaction.get(5, TimeUnit.SECONDS);
                assertEquals(2, value(backend.user(uuid).readRow(TYPE), "Points"));
                runtime.populate(uuid);
                assertThrows(IllegalStateException.class, () -> runtime.transaction(uuid, scope -> {
                    throw new SQLException("rollback");
                }));
                assertTrue(cache.tryPublishBypass());
            } finally {
                release.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void requiredRowPrerequisiteAllowsQueuedCacheFlushBeforeAtomicWork() throws Exception {
        UUID uuid = UUID.randomUUID();
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("PlayerName", "VARCHAR(30) NOT NULL", DataType.STRING)
                .column("Points", "INTEGER DEFAULT 0", DataType.INTEGER).build();
        try (SqliteUserBackend backend = new SqliteUserBackend(tempDir, "Required", "Users", schema,
                SqlBackendLogger.NO_OP)) {
            createReceipts(backend.databaseFile());
            PendingCacheOwner cache = new PendingCacheOwner();
            SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
            UUID pristine = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> runtime.transaction(pristine, TYPE,
                    Map.of("PlayerName", new DataValueString("Ben")), scope -> {
                        throw new SQLException("abort before prior cache work");
                    }));
            assertFalse(backend.user(pristine).contains(TYPE));
            runtime.queueChange(uuid, "Points", new DataValueInt(5));
            assertThrows(IllegalStateException.class, () -> runtime.transaction(uuid, TYPE,
                    Map.of("PlayerName", new DataValueString("Ben")), scope -> {
                        assertEquals(5, value(scope.readRow(), "Points"));
                        scope.writeValues(Map.of("Points", new DataValueInt(99)));
                        insert(scope.connection(), "failed-vote");
                        throw new SQLException("abort accepted operation");
                    }));
            List<Column> row = backend.user(uuid).readRow(TYPE);
            assertEquals(5, value(row, "Points"));
            assertEquals("Ben", row.stream().filter(c -> c.getName().equals("PlayerName"))
                    .findFirst().orElseThrow().getValue().getString());
            try (Connection check = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile())) {
                assertFalse(receipt(check, "failed-vote"));
            }
            runtime.transaction(uuid, TYPE, Map.of("PlayerName", new DataValueString("Ben")), scope -> {
                scope.writeValues(Map.of("Points", new DataValueInt(6)));
                insert(scope.connection(), "accepted-vote");
                return null;
            });
            assertEquals(6, value(backend.user(uuid).readRow(TYPE), "Points"));
            try (Connection check = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile())) {
                assertTrue(receipt(check, "accepted-vote"));
            }
        }
    }

    private static class SimpleCacheOwner implements UserCacheOwner {
        private final Map<UUID, HashMap<String, DataValue>> cached = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public boolean isCached(UUID uuid) { return cached.containsKey(uuid); }
        @Override public DataValue getIfPresent(UUID uuid, String key) {
            HashMap<String, DataValue> values = cached.get(uuid);
            return values == null ? null : values.get(key);
        }
        @Override public void populate(UUID uuid, HashMap<String, DataValue> values) { cached.put(uuid, new HashMap<>(values)); }
        @Override public void queueChange(UUID uuid, String key, DataValue value) { cached.get(uuid).put(key, value); }
        @Override public void flush(UUID uuid, SqlUserStorage storage) {}
        @Override public Set<UUID> cachedUsers() { return Set.copyOf(cached.keySet()); }
        @Override public void remove(UUID uuid) { cached.remove(uuid); }
        @Override public void clearAfterFlush() { cached.clear(); }
        @Override public void shutdown() {}
    }

    private static final class FencedCacheOwner extends SimpleCacheOwner {
        private boolean removing;
        private boolean queued;
        synchronized boolean tryPublishBypass() {
            if (removing) return false;
            queued = true;
            return true;
        }
        @Override public synchronized void beginRemoval(UUID uuid) { removing = true; }
        @Override public synchronized void cancelRemoval(UUID uuid) { removing = false; }
        @Override public synchronized void flush(UUID uuid, SqlUserStorage storage) { queued = false; }
        @Override public synchronized void remove(UUID uuid) {
            if (queued) throw new IllegalStateException("cache has unflushed work");
            super.remove(uuid);
        }
    }

    private static final class PendingCacheOwner extends SimpleCacheOwner {
        private final HashMap<String, DataValue> pending = new HashMap<>();
        @Override public boolean hasPendingChanges(UUID uuid) { return !pending.isEmpty(); }
        @Override public void queueChange(UUID uuid, String key, DataValue value) {
            super.queueChange(uuid, key, value);
            pending.put(key, value);
        }
        @Override public void flush(UUID uuid, SqlUserStorage storage) {
            if (pending.isEmpty()) return;
            storage.writeValues(TYPE, new HashMap<>(pending));
            pending.clear();
        }
    }
}
