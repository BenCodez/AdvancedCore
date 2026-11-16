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
        when(manager.getTimer()).thenReturn(worker);
        doAnswer(call -> { pending.add(call.getArgument(0)); return null; }).when(worker).execute(any(Runnable.class));
        when(users.getUser(any(UUID.class), anyString())).thenAnswer(call -> records.get(call.getArgument(0)));
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

    @Test void observedLinkedNameSurvivesDisconnectStoredLearningAndResolverRestart() throws Exception {
        Player linked = player("SyntheticLinked", true);
        AdvancedCoreUser record = record(linked);
        online(linked);
        resolver.learn(linked);
        verify(users, never()).getUser(any(UUID.class), anyString());
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
        verify(users, times(1)).getUser(linked.getUniqueId(), "SyntheticLinked");
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
        for (Player player : List.of(first, unlinked, second)) verify(users).getUser(player.getUniqueId(), player.getName());
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
        verify(users, never()).getUser(any(UUID.class), anyString());
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
        verify(users, never()).getUser(any(UUID.class), anyString());
        online();
        assertEquals("QueuedFirst", resolver.resolve("QueuedFirst").finalName);
        assertEquals("QueuedSecond", resolver.resolve("QueuedSecond").finalName);
        drain();
        verify(users).getUser(first.getUniqueId(), "QueuedFirst");
        verify(users).getUser(second.getUniqueId(), "QueuedSecond");
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
        doAnswer(call -> { stored.put("isBedrock", "true"); return null; }).when(user).setBedrockUser(true);
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
