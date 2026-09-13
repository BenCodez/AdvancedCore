package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Real Xerial SQLite transactions, with latches at deterministic operation boundaries. */
@Timeout(15)
class SqliteUserBackendLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void concurrentCloseCallsDrainCommitAndRejectStaleUsers() throws Exception {
        closeDrainsWrite(false);
    }

    @Test
    void failedWriteRollsBackBeforeCloseReturns() throws Exception {
        closeDrainsWrite(true);
    }

    private void closeDrainsWrite(boolean failWrite) throws Exception {
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("PlayerName", "VARCHAR(30)", DataType.STRING).build();
        SqliteUserBackend backend = new SqliteUserBackend(tempDir, "Users", "Users", schema, SqlBackendLogger.NO_OP);
        UUID uuid = UUID.randomUUID();
        SqlUserStorage user = backend.user(uuid);
        user.write(UserStorage.SQLITE, "PlayerName", new DataValueString("before"));
        CountDownLatch transactionOpen = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        IllegalStateException writeFailure = new IllegalStateException("injected value failure");
        DataValue value = mock(DataValue.class);
        when(value.isString()).thenReturn(true);
        when(value.getString()).thenAnswer(ignored -> {
            // JdbcSqlUserStorage has opened the connection, begun the transaction,
            // and ensured the row before it asks for this value.
            transactionOpen.countDown();
            await(releaseWrite);
            if (failWrite) throw writeFailure;
            return "after";
        });
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("PlayerName", value);
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            Future<?> write = workers.submit(() -> {
                // Cover both public write entry points, not just the delegate.
                if (failWrite) user.writeValues(UserStorage.SQLITE, values);
                else user.write(UserStorage.SQLITE, "PlayerName", value);
            });
            await(transactionOpen);
            CountDownLatch closersStarted = new CountDownLatch(2);
            Future<?> close = workers.submit(() -> {
                closersStarted.countDown();
                backend.close();
            });
            Future<Boolean> interruptedClose = workers.submit(() -> {
                Thread.currentThread().interrupt();
                closersStarted.countDown();
                backend.close();
                return Thread.currentThread().isInterrupted();
            });
            await(closersStarted);
            awaitStopping(backend);
            assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class, () -> interruptedClose.get(100, TimeUnit.MILLISECONDS));

            assertThrows(IllegalStateException.class, () -> backend.user(uuid));
            assertThrows(IllegalStateException.class, backend::enumerateUsers);
            assertThrows(IllegalStateException.class, () -> user.readRow(UserStorage.SQLITE));
            assertThrows(IllegalStateException.class, () -> user.contains(UserStorage.SQLITE));
            assertThrows(IllegalStateException.class, () -> user.delete(UserStorage.SQLITE));
            assertThrows(IllegalStateException.class,
                    () -> user.write(UserStorage.SQLITE, "PlayerName", new DataValueString("stale")));
            assertThrows(IllegalStateException.class, () -> user.writeValues(UserStorage.SQLITE, values));

            releaseWrite.countDown();
            if (failWrite) {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> write.get(5, TimeUnit.SECONDS));
                assertSame(writeFailure, failure.getCause());
            } else {
                write.get(5, TimeUnit.SECONDS);
            }
            close.get(5, TimeUnit.SECONDS);
            assertTrue(interruptedClose.get(5, TimeUnit.SECONDS));
            assertFalse(backend.isOpen());
            backend.close();

            try (SqliteUserBackend reopened = new SqliteUserBackend(tempDir, "Users", "Users", schema,
                    SqlBackendLogger.NO_OP)) {
                SqlUserStorage replacement = reopened.user(uuid);
                assertEquals(failWrite ? "before" : "after", playerName(replacement));
                replacement.write(UserStorage.SQLITE, "PlayerName", new DataValueString("replacement"));
                assertThrows(IllegalStateException.class,
                        () -> user.write(UserStorage.SQLITE, "PlayerName", new DataValueString("late")));
                assertEquals("replacement", playerName(replacement));
                assertEquals(List.of(uuid), reopened.enumerateUsers());
            }
        } finally {
            releaseWrite.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            backend.close();
        }
    }

    @Test
    void closeAlsoWaitsForActiveEnumeration() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        SqlBackendLogger logger = mock(SqlBackendLogger.class);
        doAnswer(ignored -> {
            // The invalid-row diagnostic runs while the result set/connection is open.
            reading.countDown();
            await(releaseRead);
            return null;
        }).when(logger).warn(anyString(), any(Throwable.class));
        SqliteUserBackend backend = new SqliteUserBackend(tempDir, "Users", "Users",
                SqlUserSchema.builder().build(), logger);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backend.databaseFile().toAbsolutePath());
                PreparedStatement statement = connection.prepareStatement("INSERT INTO `Users` (`uuid`) VALUES (?)")) {
            statement.setString(1, "not-a-uuid");
            statement.executeUpdate();
        }
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> enumeration = workers.submit(backend::enumerateUsers);
            await(reading);
            Future<?> close = workers.submit(backend::close);
            awaitStopping(backend);
            assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, backend::enumerateUsers);
            releaseRead.countDown();
            assertEquals(List.of(), enumeration.get(5, TimeUnit.SECONDS));
            close.get(5, TimeUnit.SECONDS);
        } finally {
            releaseRead.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            backend.close();
        }
    }

    private static String playerName(SqlUserStorage user) {
        return user.readRow(UserStorage.SQLITE).stream()
                .filter(column -> "PlayerName".equals(column.getName()))
                .findFirst().orElseThrow().getValue().getString();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "operation did not reach the expected boundary");
    }

    private static void awaitStopping(SqliteUserBackend backend) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (backend.isOpen() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertFalse(backend.isOpen(), "close did not stop admission");
    }
}
