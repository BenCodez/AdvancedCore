package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.command.CommandLoader;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle;
import com.bencodez.simpleapi.dialog.UniDialogService;
import com.bencodez.simpleapi.messages.actionbar.ActionBar;
import com.bencodez.simpleapi.messages.actionbar.ActionBarManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

/** Exercises real enable/disable admission and native publication; game integrations are external fixtures. */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NativeServerDataAdmissionTest {
    @TempDir Path directory;
    private Properties original;
    private final List<ScheduledExecutorService> workers = new ArrayList<>();
    private MockedStatic<AdvancedCoreLifecycle> lifecycle;
    private MockedStatic<ActionBar> actionBar;
    private MockedStatic<ValueRequest> values;
    private MockedStatic<CommandLoader> commands;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ActionBarManager> actionBars;
    private MockedConstruction<UniDialogService> dialogs;

    @BeforeEach void isolatePlatformIntegrations() {
        original = System.getProperties();
        var isolated = new Properties();
        isolated.putAll(original);
        System.setProperties(isolated);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        lifecycle = mockStatic(AdvancedCoreLifecycle.class);
        lifecycle.when(() -> AdvancedCoreLifecycle.createRuntimeExecutors(any(AdvancedCorePlugin.class)))
                .thenAnswer(call -> {
                    var group = AdvancedCoreRuntime.createExecutors();
                    workers.addAll(List.of(group.timer(), group.loginTimer(), group.inventoryTimer()));
                    return new AdvancedCoreLifecycle.RuntimeExecutors(mock(BukkitScheduler.class), group.timer(),
                            group.loginTimer(), group.inventoryTimer());
                });
        actionBar = mockStatic(ActionBar.class);
        values = mockStatic(ValueRequest.class);
        commands = mockStatic(CommandLoader.class);
        actionBars = mockConstruction(ActionBarManager.class);
        dialogs = mockConstruction(UniDialogService.class);
    }

    @AfterEach void restorePlatform() throws Exception {
        try {
            for (ScheduledExecutorService worker : workers) worker.shutdownNow();
            for (ScheduledExecutorService worker : workers) assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        } finally {
            dialogs.close(); actionBars.close(); commands.close(); values.close(); actionBar.close(); lifecycle.close();
            bukkit.close();
            var singleton = AdvancedCorePlugin.class.getDeclaredField("javaPlugin");
            singleton.setAccessible(true);
            singleton.set(null, null);
            System.setProperties(original);
        }
    }

    @Test void pendingNativeSaveRejectsSuccessorBeforeItsEarlyReadAndAllowsRetryAfterPublication() throws Exception {
        Files.writeString(directory.resolve("ServerData.yml"), "LastUpdated: 1\nPrevDay: 20\n");
        Fixture predecessor = fixture();
        BlockingServerData owner = new BlockingServerData(predecessor.plugin);
        owner.setup();
        when(predecessor.plugin.getServerDataFile()).thenReturn(owner);
        predecessor.plugin.onEnable();
        owner.block = true;
        predecessor.plugin.onDisable();
        assertTrue(owner.saving.await(2, TimeUnit.SECONDS));
        assertFalse(predecessor.finalFiles.get().toCompletableFuture().isDone());
        Fixture successor = fixture();
        ServerData successorData = new ServerData(successor.plugin);
        when(successor.plugin.getServerDataFile()).thenReturn(successorData);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> { successorData.setup(); reads.incrementAndGet(); return null; }).when(successor.plugin).onPreLoad();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThrows(IllegalStateException.class, successor.plugin::onEnable));
            assertEquals(0, reads.get());
            verify(successor.plugin, never()).onPreLoad();
            verify(successor.plugin, never()).loadHook();
            successor.plugin.onDisable();
            verify(successor.plugin, never()).onBeforeStorageShutdown();
            predecessor.plugin.onDisable();
            assertEquals(1, owner.saves.get(), "duplicate disable cannot admit another stale write");
        } finally { owner.release.countDown(); }
        predecessor.awaitRelease();
        assertFalse(predecessor.plugin.hasServerDataOwnership());
        assertTrue(YamlConfiguration.loadConfiguration(directory.resolve("ServerData.yml").toFile())
                .getLong("LastUpdated") > 1);
        successor.plugin.onEnable();
        assertEquals(1, reads.get());
        assertTrue(successorData.getLastUpdated() > 1, "native startup reads the confirmed predecessor snapshot");
        assertEquals(20, successorData.getPrevDay());
        predecessor.plugin.onDisable();
        assertEquals(1, owner.saves.get(), "released predecessors cannot write over the active successor");
        assertTrue(successor.plugin.hasServerDataOwnership());
        successor.plugin.onDisable();
        successor.awaitRelease();
        verify(predecessor.plugin, times(1)).onUnLoad();
    }

    @Test void failedStartupWithoutAnAdmittedNativeWriterReleasesAndRetiresItsCreatedExecutors() throws Exception {
        Fixture failed = fixture();
        var failure = new IllegalStateException("early consumer startup failed");
        doThrow(failure).when(failed.plugin).onPreLoad();
        assertSame(failure, assertThrows(IllegalStateException.class, failed.plugin::onEnable));
        failed.awaitRelease();
        assertFalse(failed.plugin.hasServerDataOwnership());
        assertFalse(Files.exists(directory.resolve("ServerData.yml")));
        verify(failed.plugin, never()).loadHook();
        assertTrue(workers.stream().allMatch(ScheduledExecutorService::isTerminated));
        Fixture successor = fixture();
        successor.plugin.onEnable();
        successor.plugin.onDisable();
        successor.awaitRelease();
    }

    @Test void successfulShutdownAllowsSameInstanceReenableAndDuplicateDisableStaysInert() throws Exception {
        Fixture plugin = fixture();
        ServerData owner = new ServerData(plugin.plugin);
        owner.setup();
        when(plugin.plugin.getServerDataFile()).thenReturn(owner);
        for (int generation = 0; generation < 2; generation++) {
            plugin.plugin.onEnable();
            assertTrue(plugin.plugin.hasServerDataOwnership());
            plugin.plugin.onDisable();
            plugin.awaitRelease();
            assertFalse(plugin.plugin.hasServerDataOwnership());
            plugin.plugin.onDisable();
        }
        verify(plugin.plugin, times(2)).onPreLoad();
        verify(plugin.plugin, times(2)).onBeforeStorageShutdown();
        verify(plugin.plugin, times(2)).onUnLoad();
        verify(plugin.plugin, times(2)).onShutdownComplete(any(CompletionStage.class));
        assertEquals(6, workers.size(), "reenable keeps the established fresh shared executor creation");
    }

    @Test void admittedPreparationRetainsAdmissionUntilItsActualSuccess() throws Exception {
        Fixture predecessor = fixture();
        var preparation = new CompletableFuture<Void>();
        when(predecessor.plugin.onBeforeStorageShutdown()).thenReturn(preparation);
        predecessor.plugin.onEnable();
        predecessor.plugin.onDisable();
        assertFalse(predecessor.releaseFence.get().isDone());
        Fixture successor = fixture();
        assertThrows(IllegalStateException.class, successor.plugin::onEnable);
        preparation.complete(null);
        predecessor.awaitRelease();
        successor.plugin.onEnable();
        successor.plugin.onDisable();
        successor.awaitRelease();
    }

    @Test void failedOrCancelledPreparationRetainsLeaseEvenWhenAnOlderNativeSaveLaterFinishes() throws Exception {
        for (boolean cancelled : new boolean[] {false, true}) {
            Path dataDirectory = directory.resolve(cancelled ? "cancelled" : "failed");
            Files.createDirectories(dataDirectory);
            Fixture predecessor = fixture(dataDirectory);
            var preparation = new CompletableFuture<Void>();
            when(predecessor.plugin.onBeforeStorageShutdown()).thenReturn(preparation);
            BlockingServerData owner = new BlockingServerData(predecessor.plugin);
            owner.setup(); owner.block = true;
            when(predecessor.plugin.getServerDataFile()).thenReturn(owner);
            predecessor.plugin.onEnable();
            predecessor.plugin.onDisable();
            assertTrue(owner.saving.await(2, TimeUnit.SECONDS));
            if (cancelled) preparation.cancel(false);
            else preparation.completeExceptionally(new IllegalStateException("preparation failed"));
            Fixture successor = fixture(dataDirectory);
            try {
                assertThrows(IllegalStateException.class, successor.plugin::onEnable);
                assertFalse(predecessor.finalFiles.get().toCompletableFuture().isDone());
            } finally { owner.release.countDown(); }
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> predecessor.releaseFence.get().get(2, TimeUnit.SECONDS));
            predecessor.finalFiles.get().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(predecessor.plugin.hasServerDataOwnership(), "failure settlement is not proven owner drain");
            assertThrows(IllegalStateException.class, successor.plugin::onEnable,
                    "a later successful save cannot turn failed preparation into safe admission");
        }
    }

    @Test void cancelledPublicationDoesNotReleaseWhileItsNativeWriterIsStillExecuting() throws Exception {
        assertNativeWriterRetirement(false);
    }

    @Test void successfulPublicationStillWaitsForActualNativeExecutorTermination() throws Exception {
        assertNativeWriterRetirement(true);
    }

    private void assertNativeWriterRetirement(boolean success) throws Exception {
        Fixture predecessor = fixture();
        var publication = new CompletableFuture<Void>();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ScheduledExecutorService nativeWriter = Executors.newSingleThreadScheduledExecutor(
                AdvancedCoreRuntime.daemonThreads("native-owner-fixture"));
        workers.add(nativeWriter);
        TimeChecker checker = mock(TimeChecker.class);
        when(checker.getTimer()).thenReturn(nativeWriter);
        when(checker.beginShutdown()).thenReturn(CompletableFuture.completedFuture(null));
        when(predecessor.plugin.getTimeChecker()).thenReturn(checker);
        ServerData owner = mock(ServerData.class);
        when(owner.mutateFinalData(any())).thenReturn(publication);
        when(predecessor.plugin.getServerDataFile()).thenReturn(owner);
        nativeWriter.execute(() -> {
            writing.countDown();
            while (releaseWriter.getCount() != 0) {
                try { releaseWriter.await(); }
                catch (InterruptedException ignored) { /* Interrupt is deliberately not completion. */ }
            }
        });
        assertTrue(writing.await(2, TimeUnit.SECONDS));
        predecessor.plugin.onEnable();
        try {
            // The injected clock expires only AdvancedCore's owned caller waits.
            new AdvancedCoreRuntime(new BukkitRuntimePlatform(predecessor.plugin),
                    () -> TimeUnit.SECONDS.toNanos(15)).shutdown(0L);
            if (success) publication.complete(null);
            else publication.cancel(false);
            assertFalse(nativeWriter.isTerminated());
            assertTrue(predecessor.plugin.hasServerDataOwnership());
            assertThrows(IllegalStateException.class, fixture().plugin::onEnable);
            if (success) assertFalse(predecessor.releaseFence.get().isDone());
            else assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> predecessor.releaseFence.get().get(2, TimeUnit.SECONDS));
        } finally { releaseWriter.countDown(); }
        assertTrue(nativeWriter.awaitTermination(2, TimeUnit.SECONDS));
        if (success) {
            predecessor.awaitRelease();
            Fixture successor = fixture();
            successor.plugin.onEnable();
            successor.plugin.onDisable();
            successor.awaitRelease();
        } else {
            assertTrue(predecessor.plugin.hasServerDataOwnership(), "a cancelled stage never proves native drain");
            assertThrows(IllegalStateException.class, fixture().plugin::onEnable);
        }
    }

    @Test void directLoadHookRejectsBeforeConstructingANativeFileOwner() throws Exception {
        Fixture predecessor = fixture();
        predecessor.plugin.onEnable();
        Fixture successor = fixture();
        doCallRealMethod().when(successor.plugin).loadHook();
        try (var nativeFiles = mockConstruction(ServerData.class)) {
            assertThrows(IllegalStateException.class, successor.plugin::loadHook);
            assertTrue(nativeFiles.constructed().isEmpty());
        } finally {
            predecessor.plugin.onDisable();
            predecessor.awaitRelease();
        }
    }

    @Test void synchronousFinalSaveAdmissionFailureCannotAppearAsSuccessfulPublication() throws Exception {
        Fixture predecessor = fixture();
        PermissionHandler permissions = mock(PermissionHandler.class);
        when(permissions.shutDownAsync(any())).thenThrow(new IllegalStateException("snapshot admission failed"));
        when(predecessor.plugin.getPermissionHandler()).thenReturn(permissions);
        predecessor.plugin.onEnable();
        predecessor.plugin.onDisable();
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> predecessor.releaseFence.get().get(2, TimeUnit.SECONDS));
        assertTrue(predecessor.finalFiles.get().toCompletableFuture().isCompletedExceptionally());
        assertTrue(predecessor.plugin.hasServerDataOwnership());
        assertThrows(IllegalStateException.class, fixture().plugin::onEnable);
    }

    @Test void cancellingTheConsumersFinalFilesObservationDoesNotCancelNativePublicationOrAdmissionRelease()
            throws Exception {
        Fixture predecessor = fixture();
        var publication = new CompletableFuture<Void>();
        ServerData owner = mock(ServerData.class);
        when(owner.mutateFinalData(any())).thenReturn(publication);
        when(predecessor.plugin.getServerDataFile()).thenReturn(owner);
        predecessor.plugin.onEnable();
        predecessor.plugin.onDisable();
        assertTrue(predecessor.finalFiles.get().toCompletableFuture().cancel(false));
        assertFalse(publication.isDone());
        publication.complete(null);
        predecessor.awaitRelease();
        Fixture successor = fixture();
        successor.plugin.onEnable();
        successor.plugin.onDisable();
        successor.awaitRelease();
    }

    @Test void failedUnloadOrCompletionHookCannotReleaseUncertainAdmission() throws Exception {
        for (boolean completionHook : new boolean[] {false, true}) {
            Fixture predecessor = fixture(directory.resolve(completionHook ? "completion" : "unload"));
            var failure = new IllegalStateException("consumer cleanup failed");
            if (completionHook) doThrow(failure).when(predecessor.plugin).onShutdownComplete(any(CompletionStage.class));
            else doThrow(failure).when(predecessor.plugin).onUnLoad();
            predecessor.plugin.onEnable();
            predecessor.plugin.onDisable();
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> predecessor.releaseFence.get().get(2, TimeUnit.SECONDS));
            assertTrue(predecessor.plugin.hasServerDataOwnership());
            assertThrows(IllegalStateException.class, fixture(predecessor.plugin.getDataFolder().toPath()).plugin::onEnable);
        }
    }

    private Fixture fixture() { return fixture(directory); }

    private Fixture fixture(Path dataDirectory) {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        Server server = mock(Server.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getName()).thenReturn("native-admission-fixture");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(plugin.getRewardHandler()).thenReturn(mock(RewardHandler.class));
        doCallRealMethod().when(plugin).onEnable();
        doCallRealMethod().when(plugin).onDisable();
        doCallRealMethod().when(plugin).hasServerDataOwnership();
        doCallRealMethod().when(plugin).onBeforeStorageShutdown();
        doCallRealMethod().when(plugin).getTimer();
        doCallRealMethod().when(plugin).getLoginTimer();
        doCallRealMethod().when(plugin).getInventoryTimer();
        var finalFiles = new AtomicReference<CompletionStage<Void>>();
        doAnswer(call -> { finalFiles.set(call.getArgument(0)); return null; })
                .when(plugin).onShutdownComplete(any(CompletionStage.class));
        var releaseFence = new AtomicReference<CompletableFuture<Void>>();
        doAnswer(call -> {
            CompletionStage<Void> completion = call.getArgument(0);
            var observed = new CompletableFuture<Void>();
            releaseFence.set(observed);
            call.callRealMethod();
            completion.whenComplete((ignored, failure) -> {
                if (failure == null) observed.complete(null);
                else observed.completeExceptionally(failure);
            });
            return null;
        }).when(plugin).releaseServerDataOwnershipAfter(any(CompletionStage.class));
        return new Fixture(plugin, finalFiles, releaseFence);
    }

    private record Fixture(AdvancedCorePlugin plugin, AtomicReference<CompletionStage<Void>> finalFiles,
            AtomicReference<CompletableFuture<Void>> releaseFence) {
        void awaitRelease() throws Exception {
            releaseFence.get().get(2, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (plugin.hasServerDataOwnership()) {
                if (System.nanoTime() >= deadline) fail("successful native drain did not release admission");
                Thread.sleep(1);
            }
        }
    }

    private static final class BlockingServerData extends ServerData {
        private final CountDownLatch saving = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger saves = new AtomicInteger();
        private volatile boolean block;
        BlockingServerData(AdvancedCorePlugin plugin) { super(plugin); }
        @Override public synchronized void saveDataChecked() {
            saves.incrementAndGet();
            if (block) {
                saving.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
            super.saveDataChecked();
        }
    }
}
