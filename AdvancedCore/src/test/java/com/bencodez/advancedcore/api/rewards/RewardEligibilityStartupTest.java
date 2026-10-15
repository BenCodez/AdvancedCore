package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.listeners.AdvancedCoreLoginEvent;
import com.bencodez.advancedcore.listeners.RewardEligibilityListener;

class RewardEligibilityStartupTest {
    @Test void existingOnlineSessionWithoutAnAuthenticationPluginGetsAReceipt() {
        try (Fixture fixture = new Fixture()) {
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(1, fixture.receipts.size(), "an existing native session must not wait for a new login");
            assertSame(fixture.plugin, fixture.receipts.get(0).getOwningPlugin());
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verify(fixture.users).getUserAsync(eq("StartupPlayer"), any(), any());
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            verify(fixture.user, never()).checkDelayedTimedRewards();
        }
    }

    @Test void noDataNativeJoinDoesNotLeaveTheSessionPending() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.plugin.isLoadUserData()).thenReturn(false);
            fixture.onOwner(() -> new RewardEligibilityListener(fixture.plugin)
                    .onNativeJoin(new PlayerJoinEvent(fixture.player, "")));
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value(), "native join alone accepts the unmanaged session");
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void noDataStartupAcceptsExistingSessionWithoutLoadingUserOrAuthenticationProvider() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.plugin.isLoadUserData()).thenReturn(false);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verifyNoInteractions(fixture.users);
            verify(fixture.plugin, never()).isAuthMeLoaded();
            verify(fixture.plugin, never()).isNLoginLoaded();
            verify(fixture.plugin, never()).isLoginSecurityLoaded();
        }
    }

    @Test void enabledNativeJoinRemainsPendingUntilTheOrdinaryLoginPathAcceptsIt() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.plugin.isLoadUserData()).thenReturn(true);
            fixture.onOwner(() -> new RewardEligibilityListener(fixture.plugin)
                    .onNativeJoin(new PlayerJoinEvent(fixture.player, "")));
            assertEquals(Boolean.FALSE, fixture.receipts.get(0).value());
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.FALSE, fixture.receipts.get(0).value());
            verifyNoInteractions(fixture.users);
            new RewardEligibilityListener(fixture.plugin).onLogin(
                    new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), fixture.player));
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
        }
    }

    @Test void noDataStartupRebindsOnlyKnownWorkForTheExactNativeSession() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.plugin.isLoadUserData()).thenReturn(false);
            when(fixture.user.checkOfflineRewardsAsync()).thenReturn(CompletableFuture.completedFuture(null));
            AdvancedCoreUser unrelated = mock(AdvancedCoreUser.class);
            when(unrelated.getPlugin()).thenReturn(fixture.plugin);
            when(unrelated.getUUID()).thenReturn(UUID.randomUUID().toString());
            when(unrelated.getPlayer()).thenReturn(mock(Player.class));
            fixture.eligibility.queued(fixture.user);
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            fixture.eligibility.queued(unrelated);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
            verify(unrelated, never()).checkDelayedTimedRewards();
            verify(unrelated, never()).checkOfflineRewardsAsync();
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void enabledUserDataCannotUseTheNativeSessionShortcut() {
        try (Fixture fixture = new Fixture()) {
            fixture.onOwner(() -> {
                fixture.eligibility.loginPending(fixture.player);
                fixture.eligibility.nativeSessionReady(fixture.player).toCompletableFuture().join();
            });
            assertEquals(Boolean.FALSE, fixture.receipts.get(0).value());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void startupRebindsAnAlreadyAwaitingUnknownHintAndWakesItsRealQueue() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.user.checkOfflineRewardsAsync()).thenReturn(CompletableFuture.completedFuture(null));
            fixture.eligibility.queued(fixture.user);
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
        }
    }

    @Test void waitingWithoutALoadedProviderStillBootstrapsExistingPlayers() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
        }
    }

    @Test void authMeDenialDoesNotStampOrLoadAUser() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(fr.xephi.authme.api.v3.AuthMeApi.class)) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            when(fixture.plugin.isAuthMeLoaded()).thenReturn(true);
            var provider = mock(fr.xephi.authme.api.v3.AuthMeApi.class);
            api.when(fr.xephi.authme.api.v3.AuthMeApi::getInstance).thenReturn(provider);
            when(provider.isAuthenticated(fixture.player)).thenAnswer(call -> {
                assertTrue(fixture.owner.get(), "AuthMe accesses the native name only on its owner"); return false;
            });
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertTrue(fixture.receipts.isEmpty());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void authenticatedAuthMeSessionUsesTheExistingLoginReceiptPath() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(fr.xephi.authme.api.v3.AuthMeApi.class)) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            when(fixture.plugin.isAuthMeLoaded()).thenReturn(true);
            var provider = mock(fr.xephi.authme.api.v3.AuthMeApi.class);
            api.when(fr.xephi.authme.api.v3.AuthMeApi::getInstance).thenReturn(provider);
            when(provider.isAuthenticated(fixture.player)).thenReturn(true);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verify(fixture.user, never()).checkOfflineRewardsAsync();
        }
    }

    @Test void unavailableNLoginFailsClosedOnItsWorker() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(com.nickuc.login.api.nLoginAPI.class)) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            when(fixture.plugin.isNLoginLoaded()).thenReturn(true);
            var provider = mock(com.nickuc.login.api.nLoginAPI.class);
            api.when(com.nickuc.login.api.nLoginAPI::getApi).thenReturn(provider);
            when(provider.isAvailable()).thenAnswer(call -> { assertFalse(fixture.owner.get()); return false; });
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertTrue(fixture.receipts.isEmpty());
            verifyNoInteractions(fixture.users);
            verify(provider, never()).isAuthenticated(anyString());
        }
    }

    @Test void loginSecurityChecksRunOffTheGameplayOwnerAndDenialIsNotAccepted() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(RewardEligibilityStartup.class, CALLS_REAL_METHODS)) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            when(fixture.plugin.isLoginSecurityLoaded()).thenReturn(true);
            var session = mock(com.lenis0012.bukkit.loginsecurity.session.PlayerSession.class);
            // Isolate the provider's lookup; the real startup, scheduling,
            // authorization predicate and receipt publication still execute.
            api.when(() -> RewardEligibilityStartup.loginSecuritySession(fixture.player))
                    .thenAnswer(call -> { assertFalse(fixture.owner.get()); return session; });
            when(session.isLoggedIn()).thenReturn(false);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertTrue(fixture.receipts.isEmpty());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void loginSecurityAuthorizedSessionDoesNotRequireAnAdditionalPasswordCheck() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(RewardEligibilityStartup.class, CALLS_REAL_METHODS)) {
            when(fixture.options.isWaitUntilLoggedIn()).thenReturn(true);
            when(fixture.plugin.isLoginSecurityLoaded()).thenReturn(true);
            var session = mock(com.lenis0012.bukkit.loginsecurity.session.PlayerSession.class);
            api.when(() -> RewardEligibilityStartup.loginSecuritySession(fixture.player)).thenReturn(session);
            // The pinned API's isLoggedIn additionally requires a password,
            // while isAuthorized is its authoritative AUTHENTICATED state.
            when(session.isAuthorized()).thenReturn(true);
            when(session.isLoggedIn()).thenReturn(false);
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.TRUE, fixture.receipts.get(0).value());
            verify(session, never()).isLoggedIn();
        }
    }

    @Test void nativeJoinPendingReceiptCannotBeBypassedByTheStartupSnapshot() {
        try (Fixture fixture = new Fixture()) {
            fixture.onOwner(() -> fixture.eligibility.loginPending(fixture.player));
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertEquals(Boolean.FALSE, fixture.receipts.get(0).value());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void changedHandlerCannotPublishTheDelayedSessionReceipt() {
        try (Fixture fixture = new Fixture()) {
            var resolution = new java.util.concurrent.atomic.AtomicReference<Consumer<AdvancedCoreUser>>();
            doAnswer(call -> { resolution.set(call.getArgument(1)); return null; })
                    .when(fixture.users).getUserAsync(anyString(), any(), any());
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertNotNull(resolution.get());
            when(fixture.plugin.getRewardHandler()).thenReturn(mock(RewardHandler.class));
            resolution.get().accept(fixture.user);
            assertTrue(fixture.receipts.isEmpty());
        }
    }

    @Test void disconnectedSessionCannotPublishTheDelayedSessionReceipt() {
        try (Fixture fixture = new Fixture()) {
            var resolution = new java.util.concurrent.atomic.AtomicReference<Consumer<AdvancedCoreUser>>();
            doAnswer(call -> { resolution.set(call.getArgument(1)); return null; })
                    .when(fixture.users).getUserAsync(anyString(), any(), any());
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertNotNull(resolution.get());
            doAnswer(call -> { assertTrue(fixture.owner.get()); return false; }).when(fixture.player).isOnline();
            resolution.get().accept(fixture.user);
            assertTrue(fixture.receipts.isEmpty());
        }
    }

    @Test void closedEligibilityCannotBootstrapANativeSession() {
        try (Fixture fixture = new Fixture()) {
            fixture.eligibility.close();
            RewardEligibilityStartup.start(fixture.plugin, fixture.eligibility);
            assertTrue(fixture.receipts.isEmpty());
            verifyNoInteractions(fixture.users);
        }
    }

    @Test void timedOutProviderKeepsItsPhysicalPermitUntilItActuallyReturns() {
        try (Fixture fixture = new Fixture(); var api = mockStatic(com.nickuc.login.api.nLoginAPI.class)) {
            var queued = new ArrayList<Runnable>();
            doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(fixture.timer).execute(any());
            when(fixture.plugin.isNLoginLoaded()).thenReturn(true);
            var provider = mock(com.nickuc.login.api.nLoginAPI.class);
            api.when(com.nickuc.login.api.nLoginAPI::getApi).thenReturn(provider);
            when(provider.isAvailable()).thenReturn(true);
            var inFlight = new java.util.concurrent.atomic.AtomicBoolean();
            var first = RewardEligibilityStartup.authenticate(fixture.plugin, fixture.eligibility,
                    fixture.player, "StartupPlayer", true, inFlight);
            when(provider.isAuthenticated("StartupPlayer")).thenAnswer(call -> {
                // Same result transition as orTimeout while the provider is physically running.
                first.completeExceptionally(new java.util.concurrent.TimeoutException());
                var second = RewardEligibilityStartup.authenticate(fixture.plugin, fixture.eligibility,
                        fixture.player, "AnotherPlayer", true, inFlight);
                assertTrue(second.isCompletedExceptionally());
                assertEquals(1, queued.size(), "a logical timeout must not enqueue another provider query");
                return true;
            });
            queued.get(0).run();
            assertTrue(first.isCompletedExceptionally(), "late provider acceptance cannot replace timeout");
            assertFalse(inFlight.get(), "physical return releases the query slot");
            verify(provider, never()).isAuthenticated("AnotherPlayer");
            verifyNoInteractions(fixture.users);
            assertTrue(fixture.receipts.isEmpty());
        }
    }

    @Test void successfulInlineContinuationCanAcquireTheNextQueryPermit() {
        try (Fixture fixture = new Fixture()) {
            var queued = new ArrayList<Runnable>();
            doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(fixture.timer).execute(any());
            var inFlight = new java.util.concurrent.atomic.AtomicBoolean();
            var first = RewardEligibilityStartup.authenticate(fixture.plugin, fixture.eligibility,
                    fixture.player, "StartupPlayer", false, inFlight);
            var next = first.thenCompose(accepted -> RewardEligibilityStartup.authenticate(fixture.plugin,
                    fixture.eligibility, fixture.player, "AnotherPlayer", false, inFlight));
            queued.get(0).run();
            assertEquals(2, queued.size());
            queued.get(1).run();
            assertTrue(next.join());
            assertFalse(inFlight.get());
        }
    }

    @Test void rejectedProviderSubmissionReleasesItsPermitForRecovery() {
        try (Fixture fixture = new Fixture()) {
            var inFlight = new java.util.concurrent.atomic.AtomicBoolean();
            doThrow(new java.util.concurrent.RejectedExecutionException()).when(fixture.timer).execute(any());
            var result = RewardEligibilityStartup.authenticate(fixture.plugin, fixture.eligibility,
                    fixture.player, "StartupPlayer", false, inFlight);
            assertTrue(result.isCompletedExceptionally());
            assertFalse(inFlight.get());
            verifyNoInteractions(fixture.users);
            assertTrue(fixture.receipts.isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final RewardHandler handler = mock(RewardHandler.class);
        final AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
        final UserManager users = mock(UserManager.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final Player player = mock(Player.class);
        final List<MetadataValue> receipts = new ArrayList<>();
        final ThreadLocal<Boolean> owner = ThreadLocal.withInitial(() -> false);
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final com.bencodez.simpleapi.scheduler.BukkitScheduler scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        final RewardEligibility eligibility;

        Fixture() {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.isLoadUserData()).thenReturn(true);
            when(plugin.getOptions()).thenReturn(options);
            when(plugin.getUserManager()).thenReturn(users);
            when(plugin.getRewardHandler()).thenReturn(handler);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> {
                boolean previous = owner.get(); owner.set(false);
                try { call.getArgument(0, Runnable.class).run(); } finally { owner.set(previous); }
                return null;
            }).when(timer).execute(any());
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> { onOwner(call.getArgument(1)); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));
            when(player.getName()).thenReturn("StartupPlayer");
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenAnswer(call -> { assertTrue(owner.get(), "native online state requires its owner"); return true; });
            when(player.getMetadata(anyString())).thenAnswer(call -> { assertTrue(owner.get()); return new ArrayList<>(receipts); });
            doAnswer(call -> { assertTrue(owner.get()); receipts.clear(); receipts.add(call.getArgument(1)); return null; })
                    .when(player).setMetadata(anyString(), any(MetadataValue.class));
            when(user.getPlugin()).thenReturn(plugin);
            when(user.getUUID()).thenReturn(UUID.randomUUID().toString());
            when(user.getPlayer()).thenReturn(player);
            doAnswer(call -> { call.getArgument(1, Consumer.class).accept(user); return null; })
                    .when(users).getUserAsync(anyString(), any(), any());
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            eligibility = new RewardEligibility(plugin, handler);
            when(handler.getRewardEligibility()).thenReturn(eligibility);
        }
        void onOwner(Runnable callback) {
            boolean previous = owner.get(); owner.set(true);
            try { callback.run(); } finally { owner.set(previous); }
        }
        @Override public void close() { eligibility.close(); bukkit.close(); }
    }
}
