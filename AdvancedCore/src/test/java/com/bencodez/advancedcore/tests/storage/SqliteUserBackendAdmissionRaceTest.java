package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend;

class SqliteUserBackendAdmissionRaceTest {
    @TempDir Path directory;

    @Test
    void callerQueuedBeforeCloseCannotStartAfterCloseReturns() throws Exception {
        SqliteUserBackend backend = new SqliteUserBackend(directory, "Users", "Users",
                SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP);
        SqlUserStorage retained = backend.user(UUID.randomUUID());

        Field operationsField = SqliteUserBackend.class.getDeclaredField("operations");
        operationsField.setAccessible(true);
        ReentrantReadWriteLock operations = (ReentrantReadWriteLock) operationsField.get(backend);
        AtomicReference<Throwable> outcome = new AtomicReference<>();

        operations.writeLock().lock();
        Thread caller = new Thread(() -> {
            try { retained.contains(UserStorage.SQLITE); }
            catch (Throwable failure) { outcome.set(failure); }
        }, "sqlite-admission-race");
        try {
            caller.start();
            for (int i = 0; i < 200 && !operations.hasQueuedThreads(); i++) Thread.sleep(5);
            assertTrue(operations.hasQueuedThreads(), "storage caller did not reach the read-lock admission boundary");
            // Reentrant for this test thread: close returns while our outer write hold
            // keeps the delayed reader queued, exactly modeling the pre-lock race.
            backend.close();
        } finally {
            operations.writeLock().unlock();
        }

        caller.join(5000);
        assertTrue(!caller.isAlive(), "delayed storage caller did not finish");
        assertInstanceOf(IllegalStateException.class, outcome.get());
    }
}
