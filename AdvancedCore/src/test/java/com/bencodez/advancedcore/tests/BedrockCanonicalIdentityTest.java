package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.bencodez.advancedcore.api.player.UuidLookup;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.user.*;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

/** Exercises the real detector with the optional Floodgate reflection contract, without a server. */
class BedrockCanonicalIdentityTest {
    private final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
    private final UserManager users = mock(UserManager.class);
    private final UserDataManager manager = mock(UserDataManager.class);
    private final ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final FloodgateContract api = new FloodgateContract();
    private final Map<UUID, AdvancedCoreUser> records = new HashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private BedrockNameResolver resolver;

    @BeforeEach void setup() throws Exception {
        when(plugin.getUserManager()).thenReturn(users);
        when(plugin.getOptions().getBedrockPlayerPrefix()).thenReturn(".");
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(users.getDataManager()).thenReturn(manager);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(users).withUserDataChangeDispatcher(any(), any());
        when(manager.getTimer()).thenReturn(worker);
        doAnswer(call -> { pending.add(call.getArgument(0)); return null; }).when(worker).execute(any(Runnable.class));
        when(users.getUser(any(UUID.class), eq(false))).thenAnswer(call -> records.get(call.getArgument(0)));
        bukkit = mockStatic(Bukkit.class);
        online();
        resolver = newResolver();
    }
    @AfterEach void close() { bukkit.close(); }

    @Test void linkedOnlineIdentityIsCanonicalAcrossAllResolverEntrypoints() {
        Player linked = player("SyntheticLinked", true);
        online(linked);
        assertEquals("SyntheticLinked", resolver.resolve("SyntheticLinked").finalName);
        assertEquals("SyntheticLinked", resolver.resolveWithoutDb("SyntheticLinked").finalName);
        assertEquals("SyntheticLinked", resolver.getPrefixedIfBedrock("SyntheticLinked"));
        assertTrue(resolver.resolve("SyntheticLinked").isBedrock, "client type stays Bedrock");
        resolver.resolveAsync("SyntheticLinked", result -> assertEquals("SyntheticLinked", result.finalName),
                failure -> fail(failure));
    }

    @Test void liveCanonicalSpellingOutranksStaleCaseEquivalentStoredMetadata() {
        Player linked = player("MixedCaseJava", true);
        AdvancedCoreUser record = record(linked);
        record.setBedrockUser(true);
        record.getData().setString("BedrockCanonicalName", "mixedcasejava");
        resolver.learn(linked);
        resolver.learn(record);
        online();
        assertEquals("MixedCaseJava", resolver.resolve("mixedcasejava").finalName);
        assertEquals("MixedCaseJava", resolver.resolveWithoutDb("MIXEDCASEJAVA").finalName);
        assertTrue(resolver.isBedrockName("mixedcasejava"));
    }

    @Test void anObservationCannotExposeBedrockFlagBeforeItsCanonicalName() throws Exception {
        Player linked = player("PublicationJava", true);
        record(linked);
        online();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        for (String fieldName : List.of("canonicalNames", "observedIdentities")) {
            Field target;
            try { target = BedrockNameResolver.class.getDeclaredField(fieldName); }
            catch (NoSuchFieldException oldSnapshot) { continue; }
            Map<String, Object> publication = new ConcurrentHashMap<>() {
                @Override public Object put(String key, Object value) {
                    if (key.equals("publicationjava")) {
                        entered.countDown();
                        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    }
                    return super.put(key, value);
                }
            };
            target.setAccessible(true); target.set(resolver, publication);
        }
        var worker = Executors.newSingleThreadExecutor();
        try {
            var learning = worker.submit(() -> resolver.learn(linked));
            assertTrue(entered.await(4, TimeUnit.SECONDS));
            var during = resolver.resolveWithoutDb("publicationjava");
            if (during.isBedrock) assertEquals("PublicationJava", during.finalName,
                    "a new true observation must carry its matching canonical name");
            release.countDown();
            learning.get(4, TimeUnit.SECONDS);
            assertEquals("PublicationJava", resolver.resolveWithoutDb("publicationjava").finalName);
            assertTrue(resolver.resolveWithoutDb("publicationjava").isBedrock);
        } finally {
            release.countDown(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(4, TimeUnit.SECONDS));
        }
    }

    @Test void offlineObservedUuidRemainsTheActualStorageOwner() throws Exception {
        observedUuidRemainsTheActualStorageOwner(false);
    }

    @Test void onlineObservedUuidRemainsTheActualStorageOwner() throws Exception {
        observedUuidRemainsTheActualStorageOwner(true);
    }

    private void observedUuidRemainsTheActualStorageOwner(boolean onlineMode) throws Exception {
        Player linked = player("StorageOwnerJava", true);
        UUID original = linked.getUniqueId();
        UUID different = UUID.fromString("11111111-2222-4333-8444-666666666666");
        when(plugin.getOptions().isOnlineMode()).thenReturn(onlineMode);
        when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
        when(manager.effectiveStorageType(UserStorage.SQLITE)).thenReturn(UserStorage.SQLITE);
        field(users, "plugin", plugin);
        doCallRealMethod().when(users).getUser(any(UUID.class), anyString());
        doCallRealMethod().when(users).getUser(any(UUID.class), anyBoolean());
        doAnswer(call -> call.getArgument(2, Supplier.class).get())
                .when(manager).withSharedSqlBackendOrLegacy(any(Supplier.class), any(), any());
        List<String> writtenIds = new ArrayList<>();
        var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        when(plugin.getSQLiteUserTable()).thenReturn(table);
        doAnswer(call -> {
            writtenIds.add(call.getArgument(0, com.bencodez.simpleapi.sql.Column.class).getValue().getString());
            return null;
        }).when(table).update(any(com.bencodez.simpleapi.sql.Column.class), anyList());
        var lookup = mock(com.bencodez.advancedcore.api.misc.PlayerManager.class);
        when(lookup.getUUID("StorageOwnerJava")).thenReturn(different.toString());
        try (var staticLookup = mockStatic(com.bencodez.advancedcore.api.misc.PlayerManager.class)) {
            staticLookup.when(com.bencodez.advancedcore.api.misc.PlayerManager::getInstance).thenReturn(lookup);
            resolver.learn(linked);
            drain();
            assertEquals(List.of(original.toString(), original.toString()), writtenIds,
                    "both metadata writes must stay on the observed UUID even when name lookup disagrees");
        }
    }

    @Test void observedLinkedNameSurvivesDisconnectStoredLearningAndResolverRestart() throws Exception {
        Player linked = player("SyntheticLinked", true);
        AdvancedCoreUser record = record(linked);
        online(linked);
        resolver.learn(linked);
        verify(users, never()).getUser(any(UUID.class), anyBoolean());
        online();
        assertEquals("SyntheticLinked", resolver.resolve("SyntheticLinked").finalName);
        // Even a disconnected/stale Player must never be accessed by persistence.
        when(linked.getName()).thenThrow(new AssertionError("worker accessed Player name"));
        drain();
        verify(record).setBedrockUser(true);
        resolver.clearCache();
        resolver.learn(record);
        assertEquals("SyntheticLinked", resolver.resolve("syntheticlinked").finalName);
        resolver = newResolver();
        resolver.learn(record);
        assertEquals("SyntheticLinked", resolver.resolve("SyntheticLinked").finalName);
        assertTrue(resolver.isBedrockName("SyntheticLinked"));
        verify(users, times(1)).getUser(linked.getUniqueId(), false);
        verify(users, never()).getUser(any(Player.class));
    }

    @Test void unlinkedBedrockAndDistinctJavaAccountKeepTheirOwnNames() {
        Player unlinked = player(".SharedName", false);
        AdvancedCoreUser bedrock = record(unlinked);
        AdvancedCoreUser java = mock(AdvancedCoreUser.class);
        when(java.getPlayerName()).thenReturn("SharedName");
        resolver.learn(java);
        online(unlinked);
        assertEquals("SharedName", resolver.resolve("SharedName").finalName);
        assertEquals(".SharedName", resolver.resolve(".SharedName").finalName);
        resolver.learn(unlinked);
        drain(); online();
        assertEquals("SharedName", resolver.resolve("SharedName").finalName);
        assertEquals(".SharedName", resolver.resolve(".SharedName").finalName);
        verify(bedrock).setBedrockUser(true);
    }

    @Test void legacyFlagWithoutCanonicalEvidenceDoesNotReassignExistingIdentity() {
        AdvancedCoreUser legacy = mock(AdvancedCoreUser.class);
        when(legacy.getPlayerName()).thenReturn("LegacyName");
        when(legacy.isBedrockUser()).thenReturn(true);
        resolver.learn(legacy);
        assertEquals(".LegacyName", resolver.resolve("LegacyName").finalName);
        verify(legacy, never()).setBedrockUser(anyBoolean());
    }

    @Test void unlinkAndRelinkDoNotRetargetEarlierJavaIdentityOrWriteAnotherUuid() {
        Player first = player("FirstJava", true);
        AdvancedCoreUser firstRecord = record(first);
        resolver.learn(first); drain();
        api.players.remove(first.getUniqueId()); // unlink: the Bedrock session gets its own identity
        Player unlinked = player(".BedrockName", false);
        AdvancedCoreUser unlinkedRecord = record(unlinked);
        resolver.learn(unlinked); drain();
        Player second = player("SecondJava", true);
        AdvancedCoreUser secondRecord = record(second);
        api.players.remove(unlinked.getUniqueId());
        resolver.learn(second); drain(); online();
        assertEquals("FirstJava", resolver.resolve("FirstJava").finalName);
        assertEquals("SecondJava", resolver.resolve("SecondJava").finalName);
        assertEquals(".BedrockName", resolver.resolve(".BedrockName").finalName);
        for (Player player : List.of(first, unlinked, second)) verify(users).getUser(player.getUniqueId(), false);
        for (AdvancedCoreUser record : List.of(firstRecord, unlinkedRecord, secondRecord)) {
            verify(record).setBedrockUser(true);
        }
        verify(users, never()).getUser(anyString());
    }

    @Test void rejectedPersistenceRetainsObservedSessionIdentityAndReportsFailure() {
        Player linked = player("RejectedJava", true);
        doThrow(new RejectedExecutionException("synthetic rejection")).when(worker).execute(any(Runnable.class));
        resolver.learn(linked); online();
        assertEquals("RejectedJava", resolver.resolve("RejectedJava").finalName);
        verify(users, never()).getUser(any(UUID.class), anyBoolean());
        verify(plugin.getLogger()).warning(anyString());
    }

    @Test void optionalApiFailureDoesNotErasePreviouslyObservedIdentity() {
        Player linked = player("KnownJava", true);
        record(linked);
        resolver.learn(linked); drain();
        api.failed = true;
        online(linked);
        assertEquals("KnownJava", resolver.resolve("KnownJava").finalName);
        resolver.learn(linked); // failed optional detection must not rewrite the stored observation
        online();
        assertEquals("KnownJava", resolver.resolve("KnownJava").finalName);
        assertTrue(pending.isEmpty());
    }

    @Test void failedStorageWriteReportsFailureWithoutClaimingRestartRecovery() throws Exception {
        Player linked = player("FailedJava", true);
        AdvancedCoreUser record = record(linked);
        UserData recordData = record.getData();
        doThrow(new IllegalStateException("synthetic private failure"))
                .when(recordData).setString(anyString(), anyString());
        resolver.learn(linked); online(); drain();
        assertEquals("FailedJava", resolver.resolve("FailedJava").finalName);
        verify(plugin.getLogger()).warning("Unable to persist observed Bedrock identity; observation remains session-local");
        verify(record, never()).setBedrockUser(true);
        resolver = newResolver();
        // No persisted observation is invented after the failed task.
        resolver.learn(record);
        assertFalse(resolver.isBedrockName("FailedJava"));
    }

    @Test void missingOptionalApiKeepsExactJavaIdentityAndDoesNotWriteStorage() {
        Player java = mock(Player.class);
        when(java.getName()).thenReturn("PlainJava");
        when(java.getUniqueId()).thenReturn(UUID.fromString("11111111-2222-4333-8444-555555555555"));
        online(java);
        assertEquals("PlainJava", resolver.resolve("PlainJava").finalName);
        resolver.learn(java);
        assertTrue(pending.isEmpty());
        verifyNoInteractions(worker);
    }

    @Test void storedMetadataCannotRedirectAnExactNameToAnotherAccount() {
        Player linked = player("SafeJava", true);
        AdvancedCoreUser record = record(linked);
        record.getData().setString("BedrockCanonicalName", "DifferentAccount");
        record.setBedrockUser(true);
        resolver.learn(record);
        assertEquals(".SafeJava", resolver.resolve("SafeJava").finalName, "ambiguous legacy flag stays legacy");
    }

    @Test void directPersistedLookupUsesOnlyMatchingCanonicalObservation() throws Exception {
        Player linked = player("StoredJava", true);
        AdvancedCoreUser record = record(linked);
        resolver.learn(linked); drain();
        resolver = newResolver();
        when(users.userExistStored("StoredJava")).thenReturn(true);
        when(users.getUser("StoredJava")).thenReturn(record);
        assertEquals("StoredJava", resolver.resolve("StoredJava").finalName);
    }

    @Test void independentObservationsRemainOwnedWhileStorageWorkerIsBlocked() {
        Player first = player("QueuedFirst", true);
        Player second = player("QueuedSecond", true);
        record(first); record(second);
        resolver.learn(first); resolver.learn(second);
        assertEquals(2, pending.size());
        verify(users, never()).getUser(any(UUID.class), anyBoolean());
        online();
        assertEquals("QueuedFirst", resolver.resolve("QueuedFirst").finalName);
        assertEquals("QueuedSecond", resolver.resolve("QueuedSecond").finalName);
        drain();
        verify(users).getUser(first.getUniqueId(), false);
        verify(users).getUser(second.getUniqueId(), false);
    }

    @Test void asynchronousPersistedLookupReadsCanonicalEvidenceOnItsWorker() throws Exception {
        Player linked = player("AsyncJava", true);
        UUID id = linked.getUniqueId();
        AdvancedCoreUser record = record(linked);
        resolver.learn(linked); drain();
        resolver = newResolver();
        when(manager.mustDeferSharedStorageAccess()).thenReturn(true);
        when(users.getUser(id, false)).thenReturn(record);
        when(record.userDataFetechMode(UserDataFetchMode.NO_CACHE)).thenReturn(record);
        AtomicReference<Runnable> storageTask = new AtomicReference<>();
        doAnswer(call -> {
            Supplier<?> work = call.getArgument(0);
            @SuppressWarnings("unchecked") Consumer<Object> success = call.getArgument(1);
            storageTask.set(() -> success.accept(work.get()));
            return true;
        }).when(manager).deferSharedStorageResult(any(), any(), any());
        UuidLookup lookup = mock(UuidLookup.class);
        when(lookup.getUUIDFromStorage("AsyncJava")).thenReturn(id.toString());
        AtomicReference<BedrockNameResolver.Result> result = new AtomicReference<>();
        try (MockedStatic<UuidLookup> lookups = mockStatic(UuidLookup.class)) {
            lookups.when(UuidLookup::getInstance).thenReturn(lookup);
            resolver.resolveAsync("AsyncJava", result::set, failure -> fail(failure));
            assertNull(result.get());
            verifyNoInteractions(lookup);
            assertNotNull(storageTask.get());
            bukkit.when(Bukkit::getOnlinePlayers).thenThrow(new AssertionError("storage accessed Bukkit"));
            storageTask.get().run();
            assertEquals("AsyncJava", result.get().finalName);
            assertTrue(result.get().isBedrock);
            verify(lookup, never()).getUUIDFromStorage(".AsyncJava");
        }
    }

    @Test void unreadableNewMetadataPreservesLegacyLoadingAndReportsFixedFailure() {
        Player linked = player("UnreadableJava", true);
        AdvancedCoreUser record = record(linked);
        record.setBedrockUser(true);
        UserData data = record.getData();
        when(data.getString(anyString(), eq(UserDataFetchMode.DEFAULT)))
                .thenThrow(new IllegalStateException("synthetic private cause"));
        assertDoesNotThrow(() -> resolver.learn(record));
        assertEquals(".UnreadableJava", resolver.resolve("UnreadableJava").finalName);
        verify(plugin.getLogger()).warning("Unable to read observed Bedrock identity; retaining legacy resolution");
    }

    @Test void persistedCanonicalMetadataAloneCannotForceAnUpdatedStoredFlagToTrue() {
        Player linked = player("StoredFlagControl", true);
        AdvancedCoreUser record = record(linked);
        record.getData().setString("BedrockCanonicalName", "StoredFlagControl");
        record.setBedrockUser(true);
        resolver.learn(record); // No Player learning in this resolver.
        record.setBedrockUser(false);
        resolver.learn(record);
        assertFalse(resolver.isBedrockName("StoredFlagControl"));
        assertEquals("StoredFlagControl", resolver.resolve("StoredFlagControl").finalName);
    }

    @Test void sharedStorageRetainsItsExistingNotificationLane() throws Exception {
        assertNotificationDelivery(false, true);
    }

    @Test void pendingStoredFalseCannotDowngradeObservedClientBeforeOrAfterWorkerCompletion() {
        Player linked = player("PendingJava", true);
        AdvancedCoreUser record = record(linked);
        resolver.learn(linked);
        assertFalse(record.isBedrockUser(), "persistence is deliberately blocked");
        resolver.learn(record);
        online();
        assertTrue(resolver.isBedrockName("PendingJava"));
        assertTrue(resolver.isBedrockName("pendingjava"));
        drain();
        assertTrue(record.isBedrockUser());
        assertTrue(resolver.isBedrockName("PendingJava"));
        assertEquals("PendingJava", resolver.resolve("PendingJava").finalName);
    }

    @Test void legacyInlineNotificationsReturnToPlatformOwnerWhileStorageStaysOnWorker() throws Exception {
        assertNotificationDelivery(false, false);
    }

    @Test void rejectedOwnerNotificationDoesNotRunOnStorageWorkerOrPreventTheMutation() throws Exception {
        assertNotificationDelivery(true, false);
    }

    private void assertNotificationDelivery(boolean rejectNotification, boolean shared) throws Exception {
        Player linked = player("NotificationJava", true);
        UUID id = linked.getUniqueId();
        // The actual callback entry point is used, not a mocked notification implementation.
        UserManager callbackUsers = spy(new UserManager(plugin));
        callbackUsers.getDataManager().getTimer().shutdownNow();
        doReturn(manager).when(callbackUsers).getDataManager();
        when(plugin.getUserManager()).thenReturn(callbackUsers);
        when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
        when(manager.effectiveStorageType(UserStorage.SQLITE)).thenReturn(UserStorage.SQLITE);
        AdvancedCoreUser record = mock(AdvancedCoreUser.class);
        when(record.getPlugin()).thenReturn(plugin);
        when(record.getUUID()).thenReturn(id.toString());
        when(record.getPlayerName()).thenReturn("NotificationJava");
        when(record.isCached()).thenReturn(true);
        UserDataCache cache = mock(UserDataCache.class);
        when(record.getCache()).thenReturn(cache);
        UserData data = new UserData(record);
        when(record.getData()).thenReturn(data);
        doCallRealMethod().when(record).setBedrockUser(true);
        if (shared) {
            when(manager.hasSharedSqlBackend()).thenReturn(true);
            when(manager.usesSharedSqlStorage(UserStorage.SQLITE)).thenReturn(true);
            doAnswer(call -> { call.getArgument(2, Runnable.class).run(); return null; })
                    .when(manager).withSharedSqlStorage(eq(id), eq(UserStorage.SQLITE), any(Runnable.class));
            when(manager.captureSharedUserDataNotification(any(Runnable.class)))
                    .thenAnswer(call -> call.getArgument(0));
            doAnswer(call -> { pending.add(call.getArgument(0)); return null; })
                    .when(manager).dispatchSharedUserDataNotification(any(Runnable.class));
        }
        doReturn(record).when(callbackUsers).getUser(id, false);
        Map<String, Thread> callbackThreads = new ConcurrentHashMap<>();
        callbackUsers.getUserDataChange().add(new UserDataChanged() {
            @Override public void onChange(AdvancedCoreUser ignored, String... keys) {
                for (String key : keys) callbackThreads.put(key, Thread.currentThread());
            }
        });
        ArrayDeque<Runnable> platformTasks = new ArrayDeque<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        doAnswer(call -> {
            if (rejectNotification) throw new RejectedExecutionException("synthetic owner stopped");
            platformTasks.add(call.getArgument(1)); return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(linked));
        resolver = newResolver();
        Thread platformThread = Thread.currentThread();
        AtomicReference<Thread> storageThread = new AtomicReference<>();
        doAnswer(call -> {
            assertNotSame(platformThread, Thread.currentThread(), "storage ran on platform owner");
            storageThread.set(Thread.currentThread());
            return null;
        }).when(cache).addChange(any(), eq(true));
        resolver.learn(linked);
        assertTrue(callbackThreads.isEmpty());
        Thread storage = new Thread(() -> {
            drain();
            callbackUsers.onChange(record, "unrelated"); // dispatch scope must not leak
        }, "synthetic-storage-worker");
        storage.start(); storage.join(5000);
        assertFalse(storage.isAlive());
        while (!platformTasks.isEmpty()) platformTasks.remove().run();
        assertNotNull(storageThread.get());
        if (rejectNotification) {
            assertNull(callbackThreads.get("isBedrock"));
            assertNull(callbackThreads.get("BedrockCanonicalName"));
            verify(plugin.getLogger(), times(2))
                    .warning("Unable to deliver observed Bedrock identity notification");
        } else {
            Thread expected = shared ? storageThread.get() : platformThread;
            assertSame(expected, callbackThreads.get("isBedrock"));
            assertSame(expected, callbackThreads.get("BedrockCanonicalName"));
            if (shared) assertTrue(platformTasks.isEmpty());
        }
        verify(record).setBedrockUser(true);
        verify(cache, times(2)).addChange(any(), eq(true));
        assertSame(storageThread.get(), callbackThreads.get("unrelated"));
    }

    private void online(Player... players) { bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(players)); }
    private void drain() { while (!pending.isEmpty()) pending.remove().run(); }
    private Player player(String name, boolean linked) {
        Player player = mock(Player.class);
        UUID uuid = UUID.nameUUIDFromBytes(("synthetic:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        api.players.put(uuid, new Session(linked, uuid, name));
        return player;
    }
    private AdvancedCoreUser record(Player player) {
        String observedName = player.getName();
        UUID observedId = player.getUniqueId();
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        UserData data = mock(UserData.class);
        Map<String, String> stored = new HashMap<>();
        when(user.getData()).thenReturn(data);
        when(user.getPlayerName()).thenReturn(observedName);
        when(user.getUserDataFetchMode()).thenReturn(UserDataFetchMode.DEFAULT);
        when(user.isBedrockUser()).thenAnswer(call -> Boolean.parseBoolean(stored.get("isBedrock")));
        when(data.getString(anyString(), eq(UserDataFetchMode.DEFAULT))).thenAnswer(call -> stored.get(call.getArgument(0)));
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(data).setString(anyString(), anyString());
        doAnswer(call -> { stored.put("isBedrock", String.valueOf(call.getArgument(0, Boolean.class))); return null; })
                .when(user).setBedrockUser(anyBoolean());
        records.put(observedId, user);
        return user;
    }
    private BedrockNameResolver newResolver() throws Exception {
        BedrockNameResolver result = new BedrockNameResolver(plugin);
        var detector = new BedrockNameResolver.BedrockDetect();
        field(detector, "floodgateApi", api);
        field(detector, "fgIsFloodgatePlayer", FloodgateContract.class.getMethod("isFloodgatePlayer", UUID.class));
        field(detector, "fgGetPlayer", FloodgateContract.class.getMethod("getPlayer", UUID.class));
        field(detector, "floodgateAvailable", true);
        field(result, "bedrockDetect", detector);
        return result;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    // Public reflection fixtures implement the optional API contract, not platform class identities.
    public record Session(boolean linked, UUID id, String name) {
        public boolean isLinked() { return linked; }
        public UUID getCorrectUniqueId() { return id; }
        public String getCorrectUsername() { return name; }
    }
    public static class FloodgateContract {
        final Map<UUID, Session> players = new HashMap<>();
        boolean failed;
        public boolean isFloodgatePlayer(UUID uuid) {
            if (failed) throw new IllegalStateException("synthetic unavailable API");
            return players.containsKey(uuid);
        }
        public Session getPlayer(UUID uuid) { return players.get(uuid); }
    }
}
