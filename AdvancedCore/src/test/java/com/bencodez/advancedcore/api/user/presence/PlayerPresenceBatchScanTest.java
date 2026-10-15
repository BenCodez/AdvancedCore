package com.bencodez.advancedcore.api.user.presence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerPresenceBatchScanTest {
    @TempDir Path directory;
    private static BasicFileAttributes attributes(Path path) throws java.io.IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    @Test void admittedMissingIdentityBurstSharesOneFreshDirectoryEnumeration() throws Exception {
        var scans = new AtomicInteger();
        var queued = new ArrayDeque<Runnable>();
        var results = new ArrayList<CompletionStage<PlayerPresence>>();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, PlayerPresenceBatchScanTest::attributes,
                path -> { scans.incrementAndGet(); return Files.newDirectoryStream(path); });
                var service = new PlayerPresenceService(queued::add, lookup)) {
            for (int i = 0; i < 128; i++)
                results.add(service.checkAsync(new UUID(0x1234400080000000L, i)));
            assertEquals(0, scans.get(), "Caller never reads history");
            while (!queued.isEmpty()) queued.remove().run();
            for (var result : results) assertEquals(PlayerPresence.NEVER_JOINED, result.toCompletableFuture().join());
            assertEquals(1, scans.get(), "One pending cohort must not enumerate once per missing player");
        }
    }
    @Test void aLaterRequestCannotOvertakeEarlierSharedWorkerMutation() throws Exception {
        UUID first = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");
        UUID second = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        var queued = new ArrayDeque<Runnable>();
        try (var lookup = new BukkitPlayerPresenceLookup(directory);
                var service = new PlayerPresenceService(queued::add, lookup)) {
            var earlier = service.checkAsync(first);
            queued.add(() -> {
                try { Files.createTempFile(directory, second + "-", ".dat"); }
                catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
            var later = service.checkAsync(second);
            while (!queued.isEmpty()) queued.remove().run();
            assertNotNull(earlier.toCompletableFuture().join());
            assertEquals(PlayerPresence.UNKNOWN, later.toCompletableFuture().join(),
                    "A read submitted after queued persistence must not use an earlier cohort's absence");
        }
    }

    private static final UUID FIRST = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");
    private static final UUID SECOND = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
    private void drain(ArrayDeque<Runnable> worker) {
        int turns = 0;
        while (!worker.isEmpty()) { assertTrue(++turns < 1024); worker.remove().run(); }
    }
    private BasicFileAttributes fixedDirectory() throws Exception {
        BasicFileAttributes snapshot = attributes(directory);
        var fixed = org.mockito.Mockito.mock(BasicFileAttributes.class,
                org.mockito.AdditionalAnswers.delegatesTo(snapshot));
        org.mockito.Mockito.when(fixed.fileKey()).thenReturn(null);
        return fixed;
    }
    @Test void laterCohortRescansKeylessDirectoryEvenWhenTimesCollide() throws Exception {
        BasicFileAttributes fixed = fixedDirectory();
        var worker = new ArrayDeque<Runnable>(); var opens = new AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> path.equals(directory) ? fixed : attributes(path),
                path -> { opens.incrementAndGet(); return Files.newDirectoryStream(path); });
                var service = new PlayerPresenceService(worker::add, lookup)) {
            var first = service.checkAsync(FIRST); drain(worker);
            assertEquals(PlayerPresence.NEVER_JOINED, first.toCompletableFuture().join());
            Path recovery = Files.createTempFile(directory, FIRST + "-", ".dat");
            var later = service.checkAsync(FIRST); drain(worker);
            assertEquals(PlayerPresence.UNKNOWN, later.toCompletableFuture().join());
            assertEquals(2, opens.get()); assertTrue(Files.exists(recovery));
        }
    }
    @Test void canonicalRecordAppearingAfterPointChecksCannotBecomeNegative() throws Exception {
        BasicFileAttributes fixed = fixedDirectory(); var worker = new ArrayDeque<Runnable>();
        Path retained = directory.resolve(FIRST + ".dat");
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> path.equals(directory) ? fixed : attributes(path), path -> {
                    Files.write(retained, new byte[] {0}); return Files.newDirectoryStream(path);
                }); var service = new PlayerPresenceService(worker::add, lookup)) {
            var result = service.checkAsync(FIRST); drain(worker);
            assertEquals(PlayerPresence.UNKNOWN, result.toCompletableFuture().join());
            assertArrayEquals(new byte[] {0}, Files.readAllBytes(retained));
        }
    }
    @Test void sharedScanPreservesIndividualAndCompleteOnlineScope() throws Exception {
        UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSyntheticBatch".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(directory.resolve(former + ".dat"), new byte[] {0});
        var worker = new ArrayDeque<Runnable>(); var opens = new AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, PlayerPresenceBatchScanTest::attributes,
                path -> { opens.incrementAndGet(); return Files.newDirectoryStream(path); });
                var service = new PlayerPresenceService(worker::add, lookup)) {
            var individual = service.checkAsync(FIRST);
            var complete = service.checkIdentitiesAsync(java.util.List.of(SECOND), true);
            drain(worker);
            assertEquals(PlayerPresence.NEVER_JOINED, individual.toCompletableFuture().join());
            assertEquals(PlayerPresence.UNKNOWN, complete.toCompletableFuture().join());
            assertEquals(1, opens.get());
        }
    }
    @Test void eightAliasFamilySharesOneScanAndStillReservesAggregate() throws Exception {
        var worker = new ArrayDeque<Runnable>(); var opens = new AtomicInteger();
        var aliases = new ArrayList<UUID>();
        for (int i = 0; i < 8; i++) aliases.add(new UUID(0x1234400080000000L, i));
        try (var lookup = new BukkitPlayerPresenceLookup(directory, PlayerPresenceBatchScanTest::attributes,
                path -> { opens.incrementAndGet(); return Files.newDirectoryStream(path); });
                var service = new PlayerPresenceService(worker::add, lookup)) {
            var result = service.checkIdentitiesAsync(aliases);
            assertFalse(result.toCompletableFuture().isDone()); drain(worker);
            assertEquals(PlayerPresence.NEVER_JOINED, result.toCompletableFuture().join());
            assertEquals(1, opens.get());
        }
    }
    @Test void failedSharedScanCannotEraseObservedPositiveEvidence() throws Exception {
        var worker = new ArrayDeque<Runnable>();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, PlayerPresenceBatchScanTest::attributes,
                path -> { throw new java.nio.file.AccessDeniedException("synthetic-history"); });
                var service = new PlayerPresenceService(worker::add, lookup)) {
            var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
            org.mockito.Mockito.when(player.getUniqueId()).thenReturn(FIRST);
            lookup.onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, null));
            var positive = service.checkAsync(FIRST); var absent = service.checkAsync(SECOND); drain(worker);
            assertEquals(PlayerPresence.JOINED, positive.toCompletableFuture().join());
            assertEquals(PlayerPresence.UNKNOWN, absent.toCompletableFuture().join());
        }
    }
    @Test void retiredProviderDoesNotReadDirectoryForQueuedCohort() throws Exception {
        var worker = new ArrayDeque<Runnable>(); var reads = new AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> { reads.incrementAndGet(); return attributes(path); });
                var service = new PlayerPresenceService(worker::add, lookup)) {
            var result = service.checkAsync(FIRST); lookup.close(); drain(worker);
            assertEquals(PlayerPresence.UNKNOWN, result.toCompletableFuture().join());
            assertEquals(0, reads.get());
        }
    }
    @Test void closeDuringScanCannotPublishNegativeOrRepopulateRetiredLookup() throws Exception {
        var worker = new ArrayDeque<Runnable>(); var holder = new BukkitPlayerPresenceLookup[1];
        holder[0] = new BukkitPlayerPresenceLookup(directory, PlayerPresenceBatchScanTest::attributes,
                path -> { holder[0].close(); return Files.newDirectoryStream(path); });
        try (var lookup = holder[0]; var service = new PlayerPresenceService(worker::add, lookup)) {
            var result = service.checkAsync(FIRST); drain(worker);
            assertEquals(PlayerPresence.UNKNOWN, result.toCompletableFuture().join());
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(FIRST));
        }
    }
    @Test void rejectedFlushSettlesRequestsAndNextCohortCanProceed() throws Exception {
        var worker = new ArrayDeque<Runnable>(); var submissions = new AtomicInteger();
        java.util.concurrent.Executor executor = task -> {
            if (submissions.incrementAndGet() == 2) throw new java.util.concurrent.RejectedExecutionException();
            worker.add(task);
        };
        try (var lookup = new BukkitPlayerPresenceLookup(directory);
                var service = new PlayerPresenceService(executor, lookup)) {
            var rejected = service.checkAsync(FIRST); drain(worker);
            assertEquals(PlayerPresence.UNKNOWN, rejected.toCompletableFuture().join());
            var later = service.checkAsync(SECOND); drain(worker);
            assertEquals(PlayerPresence.NEVER_JOINED, later.toCompletableFuture().join());
        }
    }

}
