package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class LiteralActionSnapshotBoundsTest {
    @Test
    void oversizedRealActionCollectionNeverPublishesOrStartsAnEffect() throws Exception {
        var fixture = new Fixture();
        var before = new HashMap<>(fixture.metadata);
        var collection = fixture.begin();
        for (int index = 0; index < 6000; index++) fixture.add(collection, index);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> fixture.user.endAsyncActionCollection(collection).toCompletableFuture().join());
        assertEquals(0, fixture.checkpoints.get());
        assertEquals(0, fixture.effects.get());
        assertEquals(before, fixture.metadata, "oversize admission must not poison the durable owner");
    }

    @Test
    void unchangedSnapshotReservesCompletionSpaceBeforeRetrying() throws Exception {
        var fixture = new Fixture();
        var collection = fixture.begin();
        var snapshot = new HashMap<String, String>();
        for (int index = 0; index < 4500; index++) {
            fixture.add(collection, index);
            String fingerprint = Reward.legacyActionFingerprint("literal-message:false:synthetic-line-" + index);
            snapshot.put(fingerprint + "/0", fingerprint);
        }
        var encode = AdvancedCoreUser.AsyncActionCollection.class.getDeclaredMethod("encodeSnapshot", HashMap.class);
        encode.setAccessible(true);
        fixture.metadata.put(Reward.legacyActionReplayKey("synthetic-message-injector") + "_snapshot",
                (String) encode.invoke(null, snapshot));
        assertDoesNotThrow(() -> Reward.copyWithoutReplayMetadata(fixture.metadata),
                "the stored snapshot alone fits, but its future completion ledger does not");
        var before = new HashMap<>(fixture.metadata);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> fixture.user.endAsyncActionCollection(collection).toCompletableFuture().join());
        assertEquals(0, fixture.effects.get());
        assertEquals(0, fixture.checkpoints.get());
        assertEquals(before, fixture.metadata);
    }

    @Test
    void normalAndAdditiveCollectionsRetainCompletedActions() throws Exception {
        var fixture = new Fixture();
        var initial = fixture.begin();
        for (int index = 0; index < 4; index++) fixture.add(initial, index);
        fixture.user.endAsyncActionCollection(initial).toCompletableFuture().join();
        assertEquals(4, fixture.effects.get());
        var restored = fixture.begin();
        for (int index = 0; index < 5; index++) fixture.add(restored, index);
        fixture.user.endAsyncActionCollection(restored).toCompletableFuture().join();
        assertEquals(5, fixture.effects.get(), "only the added occurrence may execute");
        assertDoesNotThrow(() -> Reward.copyWithoutReplayMetadata(fixture.metadata));
    }

    @Test
    void registrationOverflowCannotExecuteTheCollectedPrefix() throws Exception {
        var fixture = new Fixture();
        var before = new HashMap<>(fixture.metadata);
        var collection = fixture.begin();
        for (int index = 0; index < 8192; index++) fixture.add(collection, index);
        try {
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> fixture.add(collection, 8192));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        } finally { fixture.user.restoreAsyncActionCollectionScope(collection); }
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> fixture.user.endAsyncActionCollection(collection).toCompletableFuture().join());
        assertEquals(0, fixture.effects.get());
        assertEquals(0, fixture.checkpoints.get());
        assertEquals(before, fixture.metadata);
    }

    private static final class Fixture {
        final AtomicInteger effects = new AtomicInteger();
        final AtomicInteger checkpoints = new AtomicInteger();
        final HashMap<String, String> metadata = new HashMap<>();
        final AdvancedCoreUser user;
        final Reward.ReplayState state;
        final Method add;

        Fixture() throws Exception {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
            when(config.isOnlineMode()).thenReturn(true);
            when(plugin.getOptions()).thenReturn(config);
            var timer = mock(ScheduledExecutorService.class);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
                    .when(timer).execute(any(Runnable.class));
            user = new AdvancedCoreUser(plugin, UUID.randomUUID(), "SyntheticPlayer");
            var options = new RewardOptions();
            options.setAsyncReplayCheckpointConsumer(checkpoint -> checkpoints.incrementAndGet());
            state = Reward.replayStateFor(options);
            metadata.put("ordinary", "unchanged");
            add = AdvancedCoreUser.AsyncActionCollection.class.getDeclaredMethod("addRegistration",
                    Supplier.class, String.class);
            add.setAccessible(true);
        }

        AdvancedCoreUser.AsyncActionCollection begin() {
            return user.beginAsyncActionCollection(state, metadata, "synthetic-message-injector");
        }

        void add(AdvancedCoreUser.AsyncActionCollection collection, int index) throws Exception {
            Supplier<CompletionStage<Void>> action = () -> {
                Reward.copyWithoutReplayMetadata(metadata);
                effects.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            };
            add.invoke(collection, action, "literal-message:false:synthetic-line-" + index);
        }
    }
}
