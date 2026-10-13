package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.advancedcore.data.ServerData;

class TimeCheckerShutdownAdmissionTest {
    @TempDir File directory;

    @Test void inventorySaveDoesNotBlockShutdownAdmissionOrCompleteItsDrainEarly() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.startCalendarCheck();
            CompletionStage<Void> inventorySave = fixture.startInventorySave();
            fixture.allowValidationBehindSave();

            CompletionStage<Void> firstDrain = fixture.beginShutdownPromptly();
            CompletionStage<Void> repeatedDrain = fixture.beginShutdownPromptly();
            assertFalse(firstDrain.toCompletableFuture().isDone());
            assertFalse(repeatedDrain.toCompletableFuture().isDone());
            assertFalse(inventorySave.toCompletableFuture().isDone());
            assertTrue(fixture.checker.isActiveProcessing());
            assertEquals(0, fixture.events.get());

            fixture.data.releaseCheckedSave.countDown();
            inventorySave.toCompletableFuture().get(5, TimeUnit.SECONDS);
            firstDrain.toCompletableFuture().get(5, TimeUnit.SECONDS);
            repeatedDrain.toCompletableFuture().get(5, TimeUnit.SECONDS);
            fixture.calendarCheck.get(5, TimeUnit.SECONDS);

            assertEquals(3, fixture.events.get(), "already admitted events still finish normally");
            assertFalse(fixture.checker.isActiveProcessing());
            assertNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
            YamlConfiguration stored = YamlConfiguration.loadConfiguration(fixture.data.getdFile());
            assertFalse(stored.getBoolean("TimeTransitions.DAY.Pending"));
            assertEquals(12, stored.getInt("PrevDay"), "the drain follows the actual durable completion");
        }
    }

    @Test void expiredLifecycleDeadlineReturnsBehindAnIndependentSaveAndRetainsStorage() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.enableUserStorage();
            fixture.data.blockTimestampPublication.set(true);
            fixture.startCalendarCheck();
            CompletableFuture<Void> unrelatedSave = fixture.startIndependentSave();
            fixture.allowValidationBehindSave();
            BukkitRuntimePlatform platform = spy(new BukkitRuntimePlatform(fixture.plugin));
            // Model the non-blocking Bukkit owner; the actual cleanup and storage stages remain real.
            doReturn(false).when(platform).canBlockForPreExecutorShutdown();
            AdvancedCoreRuntime runtime = new AdvancedCoreRuntime(platform,
                    () -> TimeUnit.SECONDS.toNanos(15));

            fixture.expectFinalFilePersistence = true;
            fixture.caller.submit(() -> runtime.shutdown(0L)).get(2, TimeUnit.SECONDS);

            assertFalse(unrelatedSave.isDone(), "caller return is not an I/O acknowledgement");
            assertFalse(fixture.calendarCheck.isDone(), "the calendar worker still waits for ServerData");
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            assertFalse(fixture.manager.getTimer().isShutdown());
            verify(fixture.nativeOwner, never()).close();
            assertEquals(0, fixture.events.get());

            // A replacement timer and repeated shutdown cannot revive the retired admission.
            ScheduledExecutorService replacement = mock(ScheduledExecutorService.class);
            fixture.checker.setTimer(replacement);
            fixture.checker.requestUpdate();
            fixture.checker.forceChanged(TimeType.DAY);
            fixture.beginShutdownPromptly();
            verify(replacement, never()).execute(any(Runnable.class));

            fixture.data.releaseCheckedSave.countDown();
            unrelatedSave.get(5, TimeUnit.SECONDS);
            fixture.calendarCheck.get(5, TimeUnit.SECONDS);
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(fixture.manager.getTimer().awaitTermination(5, TimeUnit.SECONDS));
            verify(fixture.nativeOwner, times(1)).close();
            verify(fixture.backend, times(1)).close();
            assertEquals(0, fixture.events.get(), "late validation cannot dispatch after platform cleanup");
            assertTrue(fixture.data.timestampPublicationEntered.await(5, TimeUnit.SECONDS));
            assertFalse(fixture.finalFilePersistence.toCompletableFuture().isDone(),
                    "storage retirement does not confirm final file publication");
            fixture.data.releaseTimestampPublication.countDown();
            fixture.awaitFinalFilePersistence();
            assertTrue(YamlConfiguration.loadConfiguration(fixture.data.getdFile())
                    .getBoolean("TimeTransitions.DAY.Pending"), "cancelled work remains recoverable");
        }
    }

    @Test void reloadInvalidatesAReservedRecordWithoutBlockingTheNextCheck() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.startCalendarCheck();
            fixture.data.getData().set("TimeTransitions.DAY.Pending", false);
            fixture.data.saveDataChecked();
            fixture.data.reloadData();
            fixture.data.allowValidation.countDown();
            fixture.calendarCheck.get(5, TimeUnit.SECONDS);

            assertFalse(fixture.checker.isActiveProcessing());
            assertEquals(0, fixture.events.get(), "the stale record must not publish an event");
            fixture.data.beginTimeChangeTransition(TimeType.DAY, "2026-10-13", "13");
            fixture.checker.requestUpdate();
            fixture.calendar.submit(() -> { }).get(5, TimeUnit.SECONDS);

            assertEquals(3, fixture.events.get());
            assertNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
            assertEquals(13, YamlConfiguration.loadConfiguration(fixture.data.getdFile()).getInt("PrevDay"));
            assertTrue(fixture.beginShutdownPromptly().toCompletableFuture().isDone());
            assertTrue(fixture.beginShutdownPromptly().toCompletableFuture().isDone());
        }
    }

    @Test void failedValidationReleasesOnlyItsReservationAndLeavesTheRecordRecoverable() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.startCalendarCheck();
            fixture.data.failValidation.set(true);
            fixture.data.allowValidation.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> fixture.calendarCheck.get(5, TimeUnit.SECONDS));
            assertEquals("validation failure", failure.getCause().getMessage());

            assertFalse(fixture.checker.isActiveProcessing());
            assertEquals(0, fixture.events.get());
            assertNotNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
            assertTrue(YamlConfiguration.loadConfiguration(fixture.data.getdFile())
                    .getBoolean("TimeTransitions.DAY.Pending"));
            fixture.checker.requestUpdate();
            fixture.calendar.submit(() -> { }).get(5, TimeUnit.SECONDS);

            assertEquals(3, fixture.events.get(), "a failed validation must not strand later checks");
            assertNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
            assertTrue(fixture.beginShutdownPromptly().toCompletableFuture().isDone());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final AtomicReference<Thread> calendarThread = new AtomicReference<>();
        final AtomicInteger events = new AtomicInteger();
        final ScheduledExecutorService calendar = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread worker = AdvancedCoreRuntime.daemonThreads("calendar-admission-test").newThread(task);
            calendarThread.set(worker);
            return worker;
        });
        final ExecutorService persistence = Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("checked-save-test"));
        final ExecutorService caller = Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("shutdown-caller-test"));
        final GatedServerData data;
        final TimeChecker checker;
        final FullInventoryHandler inventory;
        Future<?> calendarCheck;
        UserDataManager manager;
        MySQL nativeOwner;
        SqlUserBackend backend;
        final CompletableFuture<Void> finalFilePersistence = new CompletableFuture<>();
        boolean expectFinalFilePersistence;

        Fixture(File directory) {
            Server server = mock(Server.class);
            PluginManager eventManager = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
            when(server.getPluginManager()).thenReturn(eventManager);
            when(plugin.getDataFolder()).thenReturn(directory);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getOptions()).thenReturn(mock(AdvancedCoreConfigOptions.class));
            doAnswer(call -> {
                CompletionStage<Void> finalFilesStage = call.getArgument(0);
                finalFilesStage.whenComplete((ignored, failure) -> {
                    if (failure == null) finalFilePersistence.complete(null);
                    else finalFilePersistence.completeExceptionally(failure);
                });
                return null;
            }).when(plugin).onShutdownComplete(any());
            doAnswer(call -> {
                Event event = call.getArgument(0);
                assertTrue(event.isAsynchronous());
                assertSame(calendarThread.get(), Thread.currentThread());
                events.incrementAndGet();
                return null;
            }).when(eventManager).callEvent(any(Event.class));
            data = new GatedServerData(plugin, calendarThread);
            data.setup();
            when(plugin.getServerDataFile()).thenReturn(data);
            data.beginTimeChangeTransition(TimeType.DAY, "2026-10-12", "12");
            checker = new TimeChecker(plugin);
            checker.setTimer(calendar);
            when(plugin.getTimeChecker()).thenReturn(checker);
            inventory = new FullInventoryHandler(plugin);
            when(plugin.getFullInventoryHandler()).thenReturn(inventory);
        }

        void enableUserStorage() {
            UserManager users = mock(UserManager.class);
            manager = new UserDataManager(plugin);
            nativeOwner = mock(MySQL.class);
            backend = mock(SqlUserBackend.class);
            UserCacheOwner cache = mock(UserCacheOwner.class);
            when(cache.cachedUsers()).thenReturn(Set.of());
            when(backend.storageType()).thenReturn(UserStorage.MYSQL);
            manager.bindSharedRuntime(new SharedUserDataRuntime(backend, cache));
            when(plugin.isLoadUserData()).thenReturn(true);
            when(plugin.getLoadedUserManager()).thenReturn(users);
            when(users.getDataManager()).thenReturn(manager);
            when(plugin.getNativeUserStorageOwner()).thenReturn(
                    new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, nativeOwner, null));
        }

        void startCalendarCheck() throws Exception {
            calendarCheck = calendar.submit(checker::update);
            assertTrue(data.pendingRead.await(2, TimeUnit.SECONDS));
        }

        CompletionStage<Void> startInventorySave() {
            data.blockNextSave.set(true);
            return inventory.shutdownAsync(persistence);
        }

        CompletableFuture<Void> startIndependentSave() {
            data.blockNextSave.set(true);
            return CompletableFuture.runAsync(data::saveDataChecked, persistence);
        }

        void allowValidationBehindSave() throws Exception {
            assertTrue(data.checkedSaveEntered.await(2, TimeUnit.SECONDS));
            data.allowValidation.countDown();
            assertTrue(data.validationAttempted.await(2, TimeUnit.SECONDS));
            assertFalse(calendarCheck.isDone());
        }

        CompletionStage<Void> beginShutdownPromptly() throws Exception {
            return caller.submit(() -> { return checker.beginShutdown(); }).get(2, TimeUnit.SECONDS);
        }

        void awaitFinalFilePersistence() throws Exception {
            if (expectFinalFilePersistence) finalFilePersistence.get(5, TimeUnit.SECONDS);
        }

        @Override public void close() throws Exception {
            data.allowValidation.countDown();
            data.releaseCheckedSave.countDown();
            data.releaseTimestampPublication.countDown();
            calendar.shutdownNow();
            persistence.shutdownNow();
            caller.shutdownNow();
            inventory.getTimer().shutdownNow();
            if (manager != null) manager.getTimer().shutdownNow();
            assertTrue(calendar.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(persistence.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
            awaitFinalFilePersistence();
        }
    }

    private static final class GatedServerData extends ServerData {
        final CountDownLatch pendingRead = new CountDownLatch(1);
        final CountDownLatch allowValidation = new CountDownLatch(1);
        final CountDownLatch validationAttempted = new CountDownLatch(1);
        final CountDownLatch checkedSaveEntered = new CountDownLatch(1);
        final CountDownLatch releaseCheckedSave = new CountDownLatch(1);
        final CountDownLatch timestampPublicationEntered = new CountDownLatch(1);
        final CountDownLatch releaseTimestampPublication = new CountDownLatch(1);
        final AtomicBoolean blockNextSave = new AtomicBoolean();
        final AtomicBoolean blockTimestampPublication = new AtomicBoolean();
        final AtomicBoolean failValidation = new AtomicBoolean();
        private final AtomicInteger finalMutations = new AtomicInteger();
        private final AtomicBoolean firstRead = new AtomicBoolean(true);
        private final AtomicReference<Thread> calendarThread;

        GatedServerData(AdvancedCorePlugin plugin, AtomicReference<Thread> calendarThread) {
            super(plugin);
            this.calendarThread = calendarThread;
        }

        @Override public TimeChangeTransitionState getPendingTimeChangeTransition(TimeType type) {
            TimeChangeTransitionState pending = super.getPendingTimeChangeTransition(type);
            if (pending != null && Thread.currentThread() == calendarThread.get()
                    && firstRead.compareAndSet(true, false)) {
                // The real read has released ServerData; a save can now acquire its monitor.
                pendingRead.countDown();
                await(allowValidation);
            }
            return pending;
        }

        @Override public boolean isPendingTimeChangeTransition(TimeChangeTransitionState transition) {
            validationAttempted.countDown();
            if (failValidation.compareAndSet(true, false)) throw new IllegalStateException("validation failure");
            return super.isPendingTimeChangeTransition(transition);
        }

        @Override public CompletionStage<Void> mutateFinalData(
                java.util.function.Consumer<org.bukkit.configuration.file.FileConfiguration> mutation) {
            // The platform submits inventory first, then timestamp. Gate the latter
            // outside the YAML monitor so storage retirement can finish independently.
            if (finalMutations.incrementAndGet() == 2 && blockTimestampPublication.get()) {
                timestampPublicationEntered.countDown();
                await(releaseTimestampPublication);
            }
            return super.mutateFinalData(mutation);
        }

        @Override protected synchronized void saveTimeChangeTransitionData() {
            if (blockNextSave.compareAndSet(true, false)) {
                checkedSaveEntered.countDown();
                await(releaseCheckedSave);
            }
            super.saveTimeChangeTransitionData();
        }

        private static void await(CountDownLatch latch) {
            try { latch.await(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture interrupted", interrupted);
            }
        }
    }
}
