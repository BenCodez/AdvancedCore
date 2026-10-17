package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.data.ServerData;

class FinalPublicationSequenceTest {
    @Test void asynchronousOwnersPublishEachFinalSnapshotBeforeStartingTheNext() throws Exception {
        verifySequence(false);
    }

    @Test void failedInventoryPublicationStillOrdersIndependentFinalCleanup() throws Exception {
        verifySequence(true);
    }

    private void verifySequence(boolean failInventory) throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        FullInventoryHandler inventory = mock(FullInventoryHandler.class);
        PermissionHandler permissions = mock(PermissionHandler.class);
        ServerData owner = mock(ServerData.class);
        when(plugin.getFullInventoryHandler()).thenReturn(inventory);
        when(plugin.getPermissionHandler()).thenReturn(permissions);
        when(plugin.getServerDataFile()).thenReturn(owner);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var inventorySave = new CompletableFuture<Void>();
        var timestampSave = new CompletableFuture<Void>();
        var permissionSave = new CompletableFuture<Void>();
        List<String> started = new CopyOnWriteArrayList<>();
        when(inventory.shutdownAsync(any())).thenAnswer(call -> {
            call.getArgument(0, Executor.class).execute(() -> started.add("inventory"));
            return inventorySave;
        });
        when(owner.mutateFinalData(any())).thenAnswer(call -> {
            started.add("timestamp");
            return timestampSave;
        });
        when(permissions.shutDownAsync(any())).thenAnswer(call -> {
            call.getArgument(0, Executor.class).execute(() -> started.add("permissions"));
            return permissionSave;
        });
        AtomicReference<CompletionStage<Void>> finalFiles = new AtomicReference<>();
        doAnswer(call -> { finalFiles.set(call.getArgument(0)); return null; })
                .when(plugin).onShutdownComplete(any(CompletionStage.class));
        var platform = new BukkitRuntimePlatform(plugin);
        var workerField = BukkitRuntimePlatform.class.getDeclaredField("persistenceWorker");
        workerField.setAccessible(true);
        var worker = (ThreadPoolExecutor) workerField.get(platform);
        try {
            platform.beforeExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("full inventory handler")
                            || cleanup.name().equals("server data timestamp"))
                    .forEach(cleanup -> cleanup.action().run());
            platform.afterExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("permission handler"))
                    .forEach(cleanup -> cleanup.action().run());
            worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(List.of("inventory"), started, "returning from the worker task is not publication");
            if (failInventory) inventorySave.completeExceptionally(new IllegalStateException("synthetic failure"));
            else inventorySave.complete(null);
            worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(List.of("inventory", "timestamp"), started);
            platform.afterExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("shutdown persistence worker")
                            || cleanup.name().equals("plugin shutdown completion hook"))
                    .forEach(cleanup -> cleanup.action().run());
            timestampSave.complete(null);
            // Completion remains observable after submission closes; the final
            // permission task must still be accepted by the retained worker.
            permissionSave.complete(null);
            if (failInventory) assertThrows(ExecutionException.class,
                    () -> finalFiles.get().toCompletableFuture().get(2, TimeUnit.SECONDS));
            else finalFiles.get().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(List.of("inventory", "timestamp", "permissions"), started);
        } finally {
            inventorySave.complete(null); timestampSave.complete(null); permissionSave.complete(null);
            worker.shutdownNow();
        }
    }
}
