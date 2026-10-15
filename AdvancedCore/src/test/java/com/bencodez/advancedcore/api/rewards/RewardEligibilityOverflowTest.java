package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;

class RewardEligibilityOverflowTest {
    @Test void saturatedTransientAdmissionIsRediscoveredAfterCapacityReturns() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i <= RewardEligibilityOverflow.BATCH; i++) nodes.add(fixture.node("Synthetic" + i));
            fixture.overflow.enable();
            for (Node node : nodes) fixture.overflow.signal(node.player);
            for (int i = 0; i < RewardEligibilityOverflow.BATCH; i++)
                verify(nodes.get(i).user).checkOfflineRewardsAsync();
            verify(nodes.get(RewardEligibilityOverflow.BATCH).user, never()).checkOfflineRewardsAsync();
            for (int i = 0; i < RewardEligibilityOverflow.BATCH; i++) nodes.get(i).work.complete(null);
            fixture.online = nodes.stream().map(node -> node.player).toList();
            fixture.clock.set(TimeUnit.SECONDS.toNanos(11));
            fixture.overflow.sweep();
            // A changed online collection cannot displace the unvisited member
            // of the already captured bounded discovery cycle.
            fixture.online = List.of();
            fixture.clock.set(TimeUnit.SECONDS.toNanos(32));
            fixture.overflow.sweep();
            verify(nodes.get(RewardEligibilityOverflow.BATCH).user).checkOfflineRewardsAsync();
            // No incoming event was needed for the entry rejected at admission.
            verify(fixture.users).getUserAsync(eq("Synthetic128"), any(), any());
        }
    }

    @Test void repeatedSignalsShareOneLiveResolutionAndReplay() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node node = fixture.node("Synthetic");
            fixture.overflow.enable();
            var first = fixture.overflow.signal(node.player);
            assertSame(first, fixture.overflow.signal(node.player));
            verify(fixture.users, times(1)).getUserAsync(eq("Synthetic"), any(), any());
            verify(node.user, times(1)).checkOfflineRewardsAsync();
            node.work.complete(null);
            assertTrue(first.toCompletableFuture().isDone());
        }
    }

    @Test void unauthenticatedNativeSessionCannotResolveOrReadQueues() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node node = fixture.node("Synthetic");
            node.accepted = false;
            fixture.overflow.enable();
            fixture.overflow.signal(node.player).toCompletableFuture().join();
            verifyNoInteractions(fixture.users);
            verify(node.user, never()).checkOfflineRewardsAsync();
        }
    }

    @Test void quitRetainsClaimGateUntilWorkerFinishesAndReplacementNeedsReceipt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node node = fixture.node("Synthetic");
            fixture.overflow.enable();
            var result = fixture.overflow.signal(node.player);
            fixture.onOwner(() -> fixture.eligibility.disconnected(node.player));
            node.accepted = false;
            fixture.onOwner(() -> assertFalse(fixture.eligibility.isEligible(fixture.policy, node.user)));
            node.work.complete(null);
            result.toCompletableFuture().join();
            // Even after the temporary admission retires, the replacement may not
            // become eligible merely because its gamemode matches.
            fixture.onOwner(() -> assertFalse(fixture.eligibility.isEligible(fixture.policy, node.user)));
            fixture.onOwner(() -> fixture.eligibility.signalLogin(node.user, node.player));
            node.accepted = true;
            fixture.onOwner(() -> assertTrue(fixture.eligibility.isEligible(fixture.policy, node.user)));
        }
    }

    @Test void lateUserResolutionCannotRetargetAReplacementNativeSession() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node node = fixture.node("Synthetic");
            var resolution = new java.util.concurrent.atomic.AtomicReference<Consumer<AdvancedCoreUser>>();
            doAnswer(call -> { resolution.set(call.getArgument(1)); return null; })
                    .when(fixture.users).getUserAsync(eq("Synthetic"), any(), any());
            fixture.overflow.enable();
            var result = fixture.overflow.signal(node.player);
            assertNotNull(resolution.get());
            when(node.user.getPlayer()).thenReturn(mock(Player.class));
            resolution.get().accept(node.user);
            result.toCompletableFuture().join();
            verify(node.user, never()).checkOfflineRewardsAsync();
            verify(node.user, never()).checkDelayedTimedRewards();
        }
    }

    @Test void closeCompletesLiveDiscoveryAndLateCompletionCannotStartAnotherReplay() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node node = fixture.node("Synthetic");
            fixture.overflow.enable();
            var result = fixture.overflow.signal(node.player);
            fixture.eligibility.close();
            assertTrue(result.toCompletableFuture().isCompletedExceptionally());
            node.work.complete(null);
            fixture.overflow.signal(node.player).toCompletableFuture().join();
            verify(node.user, times(1)).checkOfflineRewardsAsync();
        }
    }

    @Test void disconnectedAdmissionsRetireAfterTheirOutstandingClaimsFinish() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.overflow.enable();
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < RewardEligibilityOverflow.BATCH; i++) {
                Node node = fixture.node("Retained" + i);
                nodes.add(node);
                node.claimed.set(true);
                node.work.complete(null);
                fixture.overflow.signal(node.player).toCompletableFuture().join();
                fixture.onOwner(() -> fixture.eligibility.disconnected(node.player));
                node.accepted = false;
                node.claimed.set(false);
            }
            Node next = fixture.node("NextOnline");
            fixture.online = List.of(next.player);
            fixture.clock.set(TimeUnit.SECONDS.toNanos(11));
            assertNotNull(fixture.fallbackSweep.get());
            fixture.fallbackSweep.get().run();
            verify(next.user).checkOfflineRewardsAsync();
            for (Node node : nodes) verify(node.user, times(1)).checkOfflineRewardsAsync();
        }
    }

    @Test void onePendingReplayDoesNotBlockTheRemainingDiscoverySlots() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Node slow = fixture.node("SlowOnline");
            Node second = fixture.node("SecondOnline");
            Node third = fixture.node("ThirdOnline");
            second.work.complete(null);
            third.work.complete(null);
            fixture.online = List.of(slow.player, second.player, third.player);
            fixture.overflow.enable();
            fixture.clock.set(TimeUnit.SECONDS.toNanos(11));
            fixture.overflow.sweep();
            assertFalse(slow.work.isDone());
            verify(second.user).checkOfflineRewardsAsync();
            verify(third.user).checkOfflineRewardsAsync();
        }
    }

    private static final class Node {
        final Player player = mock(Player.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final CompletableFuture<Void> work = new CompletableFuture<>();
        boolean accepted = true;
        final java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final RewardHandler handler = mock(RewardHandler.class);
        final UserManager users = mock(UserManager.class);
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        final ScheduledExecutorService delayed = mock(ScheduledExecutorService.class);
        final com.bencodez.simpleapi.scheduler.BukkitScheduler scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        final ThreadLocal<Boolean> owner = ThreadLocal.withInitial(() -> false);
        final AtomicLong clock = new AtomicLong();
        final java.util.concurrent.atomic.AtomicReference<Runnable> fallbackSweep = new java.util.concurrent.atomic.AtomicReference<>();
        final HashMap<String, Node> named = new HashMap<>();
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final RewardEligibility eligibility;
        final RewardEligibilityOverflow overflow;
        final YamlConfiguration policy = new YamlConfiguration();
        List<Player> online = List.of();

        Fixture() throws Exception {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getRewardHandler()).thenReturn(handler);
            when(plugin.getUserManager()).thenReturn(users);
            when(plugin.getTimer()).thenReturn(timer);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(handler.getDelayedTimer()).thenReturn(delayed);
            when(delayed.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any()))
                    .thenAnswer(call -> { fallbackSweep.set(call.getArgument(0)); return mock(ScheduledFuture.class); });
            doAnswer(call -> {
                boolean previous = owner.get(); owner.set(false);
                try { call.getArgument(0, Runnable.class).run(); } finally { owner.set(previous); }
                return null;
            }).when(timer).execute(any());
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> { onOwner(call.getArgument(1)); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            doAnswer(call -> {
                assertFalse(owner.get(), "user resolution must not run on the native owner");
                Node node = named.get(call.getArgument(0));
                call.getArgument(1, Consumer.class).accept(node.user);
                return null;
            }).when(users).getUserAsync(anyString(), any(), any());
            bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> online);
            eligibility = new RewardEligibility(plugin, handler, clock::get);
            when(handler.getRewardEligibility()).thenReturn(eligibility);
            var field = RewardEligibility.class.getDeclaredField("overflow");
            field.setAccessible(true);
            overflow = (RewardEligibilityOverflow) field.get(eligibility);
            policy.set("GameModes", List.of("SURVIVAL"));
        }

        Node node(String name) {
            Node node = new Node(); named.put(name, node);
            when(node.player.getName()).thenAnswer(call -> { assertTrue(owner.get()); return name; });
            when(node.player.isOnline()).thenAnswer(call -> { assertTrue(owner.get()); return true; });
            when(node.player.getGameMode()).thenAnswer(call -> { assertTrue(owner.get()); return org.bukkit.GameMode.SURVIVAL; });
            when(node.player.getMetadata(anyString())).thenAnswer(call -> {
                assertTrue(owner.get());
                return List.of(new FixedMetadataValue(plugin, node.accepted));
            });
            when(node.user.getPlayer()).thenReturn(node.player);
            when(node.user.getPlugin()).thenReturn(plugin);
            when(node.user.getUUID()).thenReturn(UUID.randomUUID().toString());
            when(node.user.hasPendingRewardReplay()).thenAnswer(call -> node.claimed.get());
            when(node.user.checkOfflineRewardsAsync()).thenAnswer(call -> { assertFalse(owner.get()); return node.work; });
            return node;
        }
        void onOwner(Runnable action) {
            boolean previous = owner.get(); owner.set(true);
            try { action.run(); } finally { owner.set(previous); }
        }
        @Override public void close() { eligibility.close(); bukkit.close(); }
    }
}
