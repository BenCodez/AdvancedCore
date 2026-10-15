package com.bencodez.advancedcore.api.user.presence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompleteOnlinePresenceTest {
    private static final UUID USER = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");

    @Test void legacyProviderKeepsPositiveAndLegacyNegativeButCannotCertifyOnlineScope() {
        try (var legacy = new PlayerPresenceService(Runnable::run, id -> PlayerPresence.NEVER_JOINED)) {
            assertEquals(PlayerPresence.NEVER_JOINED, legacy.checkAsync(USER).toCompletableFuture().join());
            assertEquals(PlayerPresence.UNKNOWN, legacy.checkIdentitiesAsync(List.of(USER), true).toCompletableFuture().join());
        }
        try (var positive = new PlayerPresenceService(Runnable::run, id -> PlayerPresence.JOINED)) {
            assertEquals(PlayerPresence.JOINED, positive.checkIdentitiesAsync(List.of(USER), true).toCompletableFuture().join());
        }
    }

    @Test void completeScopeProviderRunsOnlyAfterWorkerAdmission() {
        var tasks = new ArrayDeque<Runnable>(); var calls = new AtomicInteger();
        PlayerPresenceLookup provider = new PlayerPresenceLookup() {
            public PlayerPresence check(UUID id) { fail("This operation requires the wider scope"); return null; }
            public PlayerPresence checkOnlineHistory(UUID id) { calls.incrementAndGet(); return PlayerPresence.NEVER_JOINED; }
        };
        try (var service = new PlayerPresenceService(tasks::add, provider)) {
            var result = service.checkIdentitiesAsync(List.of(USER), true).toCompletableFuture();
            assertFalse(result.isDone()); assertEquals(0, calls.get());
            tasks.remove().run(); assertEquals(PlayerPresence.NEVER_JOINED, result.join()); assertEquals(1, calls.get());
        }
    }

    @Test void verifiedHistoricalPositiveWinsOverUnmappedOnlineUncertainty() {
        UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PlayerPresenceLookup provider = new PlayerPresenceLookup() {
            public PlayerPresence check(UUID id) { return id.equals(former) ? PlayerPresence.JOINED : PlayerPresence.NEVER_JOINED; }
            public PlayerPresence checkOnlineHistory(UUID id) { return id.equals(former) ? PlayerPresence.JOINED : PlayerPresence.UNKNOWN; }
        };
        try (var service = new PlayerPresenceService(Runnable::run, provider)) {
            assertEquals(PlayerPresence.JOINED, service.checkIdentitiesAsync(List.of(USER, former), true).toCompletableFuture().join());
        }
    }

    @Test void completeFamilyReservesAllChildrenAndRetiredQueueNeverTouchesProvider() {
        var tasks = new ArrayDeque<Runnable>(); var calls = new AtomicInteger();
        PlayerPresenceLookup provider = new PlayerPresenceLookup() {
            public PlayerPresence check(UUID id) { calls.incrementAndGet(); return PlayerPresence.NEVER_JOINED; }
            public PlayerPresence checkOnlineHistory(UUID id) { calls.incrementAndGet(); return PlayerPresence.NEVER_JOINED; }
        };
        try (var service = new PlayerPresenceService(tasks::add, provider)) {
            for (int count = 0; count < 126; count++) service.checkAsync(USER);
            var rejected = service.checkIdentitiesAsync(List.of(USER, UUID.randomUUID()), true).toCompletableFuture();
            assertEquals(PlayerPresence.UNKNOWN, rejected.join()); assertEquals(126, tasks.size());
            service.close(); while (!tasks.isEmpty()) tasks.remove().run(); assertEquals(0, calls.get());
        }
    }

    @Test void rejectedCompleteScopeWorkerSettlesAggregateAsUnknown() {
        try (var service = new PlayerPresenceService(task -> { throw new java.util.concurrent.RejectedExecutionException("synthetic"); },
                id -> PlayerPresence.NEVER_JOINED)) {
            assertEquals(PlayerPresence.UNKNOWN, service.checkIdentitiesAsync(List.of(USER), true).toCompletableFuture().join());
        }
    }
}
