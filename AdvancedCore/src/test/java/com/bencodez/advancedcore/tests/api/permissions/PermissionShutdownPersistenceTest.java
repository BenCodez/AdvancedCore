package com.bencodez.advancedcore.tests.api.permissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.permissions.PlayerPermissionHandler;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.time.ParsedDuration;

class PermissionShutdownPersistenceTest {
    @Test void shutdownFencesExpirationAndReturnsBeforeBlockedCheckedSave() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ServerData serverData = mock(ServerData.class);
        doCallRealMethod().when(serverData).mutateFinalData(any());
        YamlConfiguration data = new YamlConfiguration();
        when(plugin.getServerDataFile()).thenReturn(serverData);
        when(serverData.getData()).thenReturn(data);
        PermissionHandler manager = new PermissionHandler(plugin);
        UUID uuid = UUID.randomUUID();
        PermissionAttachment attachment = mock(PermissionAttachment.class);
        PlayerPermissionHandler handle = new PlayerPermissionHandler(uuid, attachment, manager)
                .addExpiration("example.use", ParsedDuration.ofMillis(60_000));
        manager.getPerms().put(uuid, handle);
        long expiry = handle.getTimedPermissions().get("example.use");
        clearInvocations(attachment);
        CountDownLatch saving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> persistenceThread = new AtomicReference<>();
        doAnswer(call -> {
            persistenceThread.set(Thread.currentThread()); saving.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS)); return null;
        }).when(serverData).saveDataChecked();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("permission-save-fixture"));
        try {
            assertTrue(manager.getTimer().submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
            var completion = manager.shutDownAsync(worker);
            assertTrue(saving.await(2, TimeUnit.SECONDS));
            assertFalse(completion.toCompletableFuture().isDone());
            assertTrue(manager.getTimer().isShutdown());
            assertTrue(persistenceThread.get().isDaemon());
            assertNotSame(Thread.currentThread(), persistenceThread.get());
            assertSame(completion, manager.shutDownAsync(worker));
            PermissionAccess.dispatchExpiration(manager, handle, "example.use", expiry);
            verifyNoInteractions(attachment);
            assertFalse(data.contains("TimedPermissions." + uuid), "pending publication cannot change live YAML");
            release.countDown();
            completion.toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(java.util.List.of("example.use%line%" + expiry), data.getStringList("TimedPermissions." + uuid));
            verify(serverData, times(1)).saveDataChecked();
        } finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
    }

    @Test void failedSaveRemainsExceptionalAndNeverCallsLegacySave() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ServerData serverData = mock(ServerData.class);
        doCallRealMethod().when(serverData).mutateFinalData(any());
        when(plugin.getServerDataFile()).thenReturn(serverData);
        when(serverData.getData()).thenReturn(new YamlConfiguration());
        PermissionHandler manager = new PermissionHandler(plugin);
        var failure = new java.io.UncheckedIOException(new java.io.IOException("disk failure"));
        doThrow(failure).when(serverData).saveDataChecked();
        var completion = manager.shutDownAsync(Runnable::run);
        assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
        verify(serverData, never()).saveData();
        assertTrue(manager.getTimer().isShutdown());
    }

    @Test void rejectionDoesNotFallBackToCallerThreadPersistence() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ServerData serverData = mock(ServerData.class);
        doCallRealMethod().when(serverData).mutateFinalData(any());
        when(plugin.getServerDataFile()).thenReturn(serverData);
        when(serverData.getData()).thenReturn(new YamlConfiguration());
        PermissionHandler manager = new PermissionHandler(plugin);
        var completion = manager.shutDownAsync(task -> {
            throw new java.util.concurrent.RejectedExecutionException("stopped");
        });
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class,
                assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
        verify(serverData, never()).saveDataChecked();
        assertTrue(manager.getTimer().isShutdown());
    }
}
