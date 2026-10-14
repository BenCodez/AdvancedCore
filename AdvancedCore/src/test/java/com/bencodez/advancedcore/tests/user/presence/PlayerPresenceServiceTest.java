package com.bencodez.advancedcore.tests.user.presence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import com.bencodez.advancedcore.api.user.presence.PlayerPresence;
import com.bencodez.advancedcore.api.user.presence.PlayerPresenceService;
import org.junit.jupiter.api.Test;

class PlayerPresenceServiceTest {
    private static final UUID USER = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");

    private static PlayerPresence value(CompletionStage<PlayerPresence> result) {
        return result.toCompletableFuture().join();
    }

    @Test void callerDoesNotReadHistoryAndPreservesAllThreeEvidenceOutcomes() {
        for (PlayerPresence evidence : PlayerPresence.values()) {
            var queued = new ArrayDeque<Runnable>();
            var reads = new AtomicInteger();
            try (var service = new PlayerPresenceService(queued::add, identity -> {
                assertEquals(USER, identity); reads.incrementAndGet(); return evidence;
            })) {
                var result = service.checkAsync(USER);
                assertFalse(result.toCompletableFuture().isDone());
                assertEquals(0, reads.get());
                queued.remove().run();
                assertEquals(evidence, value(result));
                assertEquals(1, reads.get());
            }
        }
    }

    @Test void absentIdentityAndRejectedWorkerDoNotBecomeNegativeHistory() {
        var reads = new AtomicInteger();
        try (var service = new PlayerPresenceService(task -> { throw new RejectedExecutionException(); }, id -> {
            reads.incrementAndGet(); return PlayerPresence.NEVER_JOINED;
        })) {
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(null)));
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
            assertEquals(0, reads.get());
        }
    }

    @Test void failedAndMissingProviderEvidenceRemainUnknown() {
        try (var service = new PlayerPresenceService(Runnable::run, id -> { throw new IOException("synthetic"); })) {
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        }
        try (var service = new PlayerPresenceService(Runnable::run, id -> null)) {
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        }
    }

    @Test void saturationIsBoundedAndCompletedReadFreesAdmission() {
        var queued = new ArrayDeque<Runnable>();
        try (var service = new PlayerPresenceService(queued::add, id -> PlayerPresence.JOINED)) {
            for (int i = 0; i < 128; i++) assertFalse(service.checkAsync(USER).toCompletableFuture().isDone());
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
            assertEquals(128, queued.size());
            queued.remove().run();
            assertFalse(service.checkAsync(USER).toCompletableFuture().isDone());
            assertEquals(128, queued.size());
        }
    }

    @Test void retirementCompletesQueuedRequestsWithoutReadingOrStoppingSharedWorker() {
        var queued = new ArrayDeque<Runnable>();
        var reads = new AtomicInteger();
        var service = new PlayerPresenceService(queued::add, id -> { reads.incrementAndGet(); return PlayerPresence.JOINED; });
        var result = service.checkAsync(USER);
        service.close(); service.close();
        assertEquals(PlayerPresence.UNKNOWN, value(result));
        queued.remove().run();
        assertEquals(0, reads.get());
        assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        queued.add(reads::incrementAndGet); queued.remove().run();
        assertEquals(1, reads.get());
    }

    @Test void retiredStartedReadCannotPublishPositiveIntoItsSuccessor() {
        var queued = new ArrayDeque<Runnable>();
        var holder = new PlayerPresenceService[1];
        holder[0] = new PlayerPresenceService(queued::add, id -> {
            holder[0].close(); return PlayerPresence.JOINED;
        });
        var predecessor = holder[0].checkAsync(USER);
        queued.remove().run();
        assertEquals(PlayerPresence.UNKNOWN, value(predecessor));
        try (var successor = new PlayerPresenceService(queued::add, id -> PlayerPresence.NEVER_JOINED)) {
            var result = successor.checkAsync(USER); queued.remove().run();
            assertEquals(PlayerPresence.NEVER_JOINED, value(result));
            assertEquals(PlayerPresence.UNKNOWN, value(predecessor));
        }
    }

    @Test void verifiedHistoricalUuidCanSupplyPositiveMigrationEvidence() {
        var worker = new ArrayDeque<Runnable>();
        UUID historical = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        try (var service = new PlayerPresenceService(worker::add,
                id -> id.equals(historical) ? PlayerPresence.JOINED : PlayerPresence.NEVER_JOINED)) {
            var result = service.checkIdentitiesAsync(java.util.List.of(USER, historical));
            worker.remove().run();
            assertFalse(result.toCompletableFuture().isDone());
            worker.remove().run();
            assertEquals(PlayerPresence.JOINED, value(result));
        }
    }

    @Test void incompleteMigrationProofAndUnboundedAliasInputsStayUnknown() {
        var worker = new ArrayDeque<Runnable>();
        UUID historical = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        try (var service = new PlayerPresenceService(worker::add,
                id -> id.equals(historical) ? PlayerPresence.UNKNOWN : PlayerPresence.NEVER_JOINED)) {
            var result = service.checkIdentitiesAsync(java.util.List.of(USER, historical));
            worker.remove().run(); worker.remove().run();
            assertEquals(PlayerPresence.UNKNOWN, value(result));
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkIdentitiesAsync(java.util.Arrays.asList(USER, null))));
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkIdentitiesAsync(java.util.List.of())));
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkIdentitiesAsync(java.util.Collections.nCopies(9, USER))));
            assertTrue(worker.isEmpty());
        }
    }

    @Test void completeNegativeAliasesAreDeduplicatedAndRetirementStillWins() {
        var worker = new ArrayDeque<Runnable>();
        var service = new PlayerPresenceService(worker::add, id -> PlayerPresence.NEVER_JOINED);
        var negative = service.checkIdentitiesAsync(java.util.List.of(USER, USER));
        assertEquals(1, worker.size()); worker.remove().run();
        assertEquals(PlayerPresence.NEVER_JOINED, value(negative));
        var retiring = service.checkIdentitiesAsync(java.util.List.of(USER));
        service.close();
        assertEquals(PlayerPresence.UNKNOWN, value(retiring));
        worker.remove().run();
    }

    @Test void retirementInvalidatesUnfinishedAggregateEvenAfterOneAliasJoined() {
        var worker = new ArrayDeque<Runnable>();
        UUID historical = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        var reads = new AtomicInteger();
        var service = new PlayerPresenceService(worker::add, id -> {
            reads.incrementAndGet();
            return id.equals(USER) ? PlayerPresence.JOINED : PlayerPresence.NEVER_JOINED;
        });
        var aggregate = service.checkIdentitiesAsync(java.util.List.of(USER, historical));
        worker.remove().run();
        assertFalse(aggregate.toCompletableFuture().isDone());
        service.close();
        assertEquals(PlayerPresence.UNKNOWN, value(aggregate));
        worker.remove().run();
        assertEquals(1, reads.get());
        assertEquals(PlayerPresence.UNKNOWN, value(aggregate));
    }

    @Test void completedAggregateKeepsPositiveEvidenceAfterRetirement() {
        var worker = new ArrayDeque<Runnable>();
        UUID historical = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        var service = new PlayerPresenceService(worker::add,
                id -> id.equals(USER) ? PlayerPresence.JOINED : PlayerPresence.UNKNOWN);
        var aggregate = service.checkIdentitiesAsync(java.util.List.of(USER, historical));
        worker.remove().run(); worker.remove().run();
        assertEquals(PlayerPresence.JOINED, value(aggregate));
        service.close();
        assertEquals(PlayerPresence.JOINED, value(aggregate));
    }

    @Test void aggregateAdmissionCannotGrowPastTheSharedPendingBound() {
        var worker = new ArrayDeque<Runnable>();
        var service = new PlayerPresenceService(worker::add, id -> PlayerPresence.JOINED);
        for (int index = 0; index < 64; index++)
            assertFalse(service.checkIdentitiesAsync(java.util.List.of(USER)).toCompletableFuture().isDone());
        assertEquals(64, worker.size());
        assertEquals(PlayerPresence.UNKNOWN, value(service.checkIdentitiesAsync(java.util.List.of(USER))));
        assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        worker.remove().run();
        assertFalse(service.checkIdentitiesAsync(java.util.List.of(USER)).toCompletableFuture().isDone());
        service.close();
        while (!worker.isEmpty()) worker.remove().run();
    }
    @Test void completionCanReentrantlyRetireQueuedIndividualAndAggregateRequests() {
        var worker = new ArrayDeque<Runnable>();
        var reads = new AtomicInteger();
        var service = new PlayerPresenceService(worker::add, id -> {
            reads.incrementAndGet(); return PlayerPresence.JOINED;
        });
        var first = service.checkAsync(USER);
        var queued = service.checkAsync(USER);
        var aggregate = service.checkIdentitiesAsync(java.util.List.of(USER));
        var callback = first.thenAccept(evidence -> {
            assertEquals(PlayerPresence.JOINED, evidence);
            service.close();
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        });
        worker.remove().run();
        callback.toCompletableFuture().join();
        assertEquals(PlayerPresence.JOINED, value(first));
        assertEquals(PlayerPresence.UNKNOWN, value(queued));
        assertEquals(PlayerPresence.UNKNOWN, value(aggregate));
        while (!worker.isEmpty()) worker.remove().run();
        assertEquals(1, reads.get());
    }

    @Test void aggregateCompletionCanReentrantlyRetireAnotherAggregate() {
        var worker = new ArrayDeque<Runnable>();
        var service = new PlayerPresenceService(worker::add, id -> PlayerPresence.NEVER_JOINED);
        var first = service.checkIdentitiesAsync(java.util.List.of(USER));
        var queued = service.checkIdentitiesAsync(java.util.List.of(USER));
        var callback = first.thenRun(service::close);
        worker.remove().run();
        callback.toCompletableFuture().join();
        assertEquals(PlayerPresence.NEVER_JOINED, value(first));
        assertEquals(PlayerPresence.UNKNOWN, value(queued));
        while (!worker.isEmpty()) worker.remove().run();
    }

    @Test void aliasFamilyRejectsWithoutQueueingPartialReadsWhenCapacityIsInsufficient() {
        var worker = new ArrayDeque<Runnable>();
        try (var service = new PlayerPresenceService(worker::add, id -> PlayerPresence.NEVER_JOINED)) {
            for (int index = 0; index < 126; index++) service.checkAsync(USER);
            var result = service.checkIdentitiesAsync(java.util.List.of(USER, new UUID(0, 2)));
            assertTrue(result.toCompletableFuture().isDone(), "Insufficient capacity rejects the complete request immediately");
            assertEquals(PlayerPresence.UNKNOWN, value(result));
            assertEquals(126, worker.size(), "No partial alias work is admitted");
        }
    }

    @Test void aliasFamilyReservesEveryChildBeforeReentrantWorkerAdmission() {
        var worker = new ArrayDeque<Runnable>();
        var service = new PlayerPresenceService[1];
        var arm = new java.util.concurrent.atomic.AtomicBoolean();
        var competing = new java.util.concurrent.atomic.AtomicReference<CompletionStage<PlayerPresence>>();
        service[0] = new PlayerPresenceService(task -> {
            if (arm.compareAndSet(true, false)) competing.set(service[0].checkAsync(new UUID(0, 3)));
            worker.add(task);
        }, id -> PlayerPresence.NEVER_JOINED);
        try (var current = service[0]) {
            for (int index = 0; index < 125; index++) current.checkAsync(USER);
            arm.set(true);
            var result = current.checkIdentitiesAsync(java.util.List.of(USER, new UUID(0, 2)));
            assertNotNull(competing.get());
            assertTrue(competing.get().toCompletableFuture().isDone());
            assertEquals(PlayerPresence.UNKNOWN, value(competing.get()));
            assertEquals(127, worker.size());
            while (!worker.isEmpty()) worker.remove().run();
            assertEquals(PlayerPresence.NEVER_JOINED, value(result));
        }
    }

    @Test void rejectedAliasSubmissionSettlesFamilyAndReleasesEveryReservation() {
        var worker = new ArrayDeque<Runnable>(); var submissions = new AtomicInteger();
        try (var service = new PlayerPresenceService(task -> {
            if (submissions.incrementAndGet() == 1) throw new RejectedExecutionException();
            worker.add(task);
        }, id -> PlayerPresence.NEVER_JOINED)) {
            var family = service.checkIdentitiesAsync(java.util.List.of(USER, new UUID(0, 2)));
            assertFalse(family.toCompletableFuture().isDone());
            assertEquals(1, worker.size()); worker.remove().run();
            assertEquals(PlayerPresence.UNKNOWN, value(family));
            for (int index = 0; index < 128; index++)
                assertFalse(service.checkAsync(USER).toCompletableFuture().isDone());
            assertEquals(128, worker.size());
            assertEquals(PlayerPresence.UNKNOWN, value(service.checkAsync(USER)));
        }
    }

    @Test void retirementDuringFirstAliasSubmissionCancelsEveryReservedChild() {
        var worker = new ArrayDeque<Runnable>(); var reads = new AtomicInteger();
        var holder = new PlayerPresenceService[1];
        holder[0] = new PlayerPresenceService(task -> {
            holder[0].close(); worker.add(task);
        }, id -> { reads.incrementAndGet(); return PlayerPresence.JOINED; });
        var family = holder[0].checkIdentitiesAsync(java.util.List.of(USER, new UUID(0, 2)));
        assertEquals(PlayerPresence.UNKNOWN, value(family));
        while (!worker.isEmpty()) worker.remove().run();
        assertEquals(0, reads.get());
        assertEquals(PlayerPresence.UNKNOWN, value(holder[0].checkAsync(USER)));
    }

}
