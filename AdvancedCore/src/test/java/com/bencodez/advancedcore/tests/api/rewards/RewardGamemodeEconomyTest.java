package com.bencodez.advancedcore.tests.api.rewards;

import com.bencodez.advancedcore.api.rewards.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.VaultHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;

import net.milkbowl.vault.economy.Economy;

class RewardGamemodeEconomyTest {
    @TempDir File directory;

    @Test void depositDefersAndKeepsCompletedNativePrefix() throws Exception { economyScenario(false, "policy"); }
    @Test void withdrawalDefersAndKeepsCompletedNativePrefix() throws Exception { economyScenario(true, "policy"); }
    @Test void absentPolicyPreservesDeposit() throws Exception { economyScenario(false, "none"); }
    @Test void absentPolicyPreservesWithdrawal() throws Exception { economyScenario(true, "none"); }
    @Test void configuredForceOfflinePreservesDeposit() throws Exception { economyScenario(false, "configured-force"); }
    @Test void configuredForceOfflinePreservesWithdrawal() throws Exception { economyScenario(true, "configured-force"); }
    @Test void optionForceOfflinePreservesDeposit() throws Exception { economyScenario(false, "option-force"); }
    @Test void optionForceOfflinePreservesWithdrawal() throws Exception { economyScenario(true, "option-force"); }

    @Test void disconnectedDepositRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(false, "policy", "disconnect");
    }
    @Test void disconnectedWithdrawalRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(true, "policy", "disconnect");
    }
    @Test void reconnectedDepositRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(false, "policy", "replacement");
    }
    @Test void reconnectedWithdrawalRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(true, "policy", "replacement");
    }
    @Test void absentPolicyKeepsDisconnectedDeposit() throws Exception {
        economyScenario(false, "none", "disconnect");
    }
    @Test void absentPolicyKeepsDisconnectedWithdrawal() throws Exception {
        economyScenario(true, "none", "disconnect");
    }
    @Test void absentPolicyKeepsReconnectedDeposit() throws Exception {
        economyScenario(false, "none", "replacement");
    }
    @Test void absentPolicyKeepsReconnectedWithdrawal() throws Exception {
        economyScenario(true, "none", "replacement");
    }

    @Test void immediatelyRetiredDepositRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(false, "policy", "retired-immediate");
    }
    @Test void immediatelyRetiredWithdrawalRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(true, "policy", "retired-immediate");
    }
    @Test void laterRetiredDepositRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(false, "policy", "retired-later");
    }
    @Test void laterRetiredWithdrawalRemainsQueuedAndResumesOnce() throws Exception {
        economyScenario(true, "policy", "retired-later");
    }
    @Test void retirementDuringShutdownRemainsDistinctFromEligibilityDeferral() throws Exception {
        economyScenario(false, "policy", "retired-shutdown");
    }
    @Test void rejectedOwnerTaskRemainsDistinctFromEligibilityDeferral() throws Exception {
        economyScenario(true, "policy", "retired-rejection");
    }
    @Test void absentPolicyDepositNeedsNoRetiringOwnerTask() throws Exception {
        economyScenario(false, "none", "retired-immediate");
    }
    @Test void absentPolicyWithdrawalNeedsNoRetiringOwnerTask() throws Exception {
        economyScenario(true, "none", "retired-later");
    }

    @Test void repeatedRetiredDepositReplayRetainsItsWakeHint() throws Exception {
        economyScenario(false, "policy", "retired-repeat");
    }
    @Test void repeatedRetiredWithdrawalReplayRetainsItsWakeHint() throws Exception {
        economyScenario(true, "policy", "retired-repeat");
    }

    private void economyScenario(boolean withdrawal, String policy) throws Exception {
        economyScenario(withdrawal, policy, "mode");
    }

    private void economyScenario(boolean withdrawal, String policy, String transition) throws Exception {
        // The legacy preparation path admits an absent player through configured ForceOffline only.
        // Options.forceOffline remains an online control for bypassing the live policy.
        boolean forcedOffline = policy.equals("configured-force");
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AtomicBoolean enabled = new AtomicBoolean(true);
        when(plugin.isEnabled()).thenAnswer(call -> enabled.get());
        when(plugin.getDataFolder()).thenReturn(directory);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
        when(config.isProcessRewards()).thenReturn(true);
        when(config.isOnlineMode()).thenReturn(true);
        when(config.getFormatRewardTimeFormat()).thenReturn("yyyy-MM-dd");
        when(plugin.getOptions()).thenReturn(config);
        UserManager users = mock(UserManager.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(mock(UserDataManager.class));
        when(users.getOfflineRewardsPath()).thenReturn("OfflineRewards");
        UserData data = mock(UserData.class);
        AtomicReference<ArrayList<String>> persisted = new AtomicReference<>(new ArrayList<>());
        when(data.getStringList("OfflineRewards", UserDataFetchMode.DEFAULT))
                .thenAnswer(call -> new ArrayList<>(persisted.get()));
        doAnswer(call -> { persisted.set(new ArrayList<>(call.getArgument(1))); return null; })
                .when(data).setStringList(eq("OfflineRewards"), any());
        doAnswer(call -> { persisted.set(new ArrayList<>(call.getArgument(1))); return null; })
                .when(data).setStringList(eq("OfflineRewards"), any(), eq(false));
        var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        ThreadLocal<Boolean> playerOwner = ThreadLocal.withInitial(() -> false);
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        doAnswer(call -> { onScheduler(playerOwner, false, call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { onScheduler(playerOwner, true, call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        doAnswer(call -> {
            Runnable task = call.getArgument(1);
            callbacks.add(() -> onScheduler(playerOwner, false, task));
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        doAnswer(call -> {
            Runnable task = call.getArgument(1);
            callbacks.add(() -> onScheduler(playerOwner, true, task));
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
        RetiringOwnerScheduler retiringScheduler = transition.startsWith("retired-")
                ? new RetiringOwnerScheduler(plugin, scheduler,
                        task -> callbacks.add(() -> onScheduler(playerOwner, true, task)),
                        task -> callbacks.add(() -> onScheduler(playerOwner, false, task)))
                : null;
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        doAnswer(call -> {
            Runnable task = call.getArgument(0);
            // A real worker executes the final ownership observation after the
            // completed replay has released its serial tail, rather than reentrantly.
            if (transition.equals("retired-repeat")) callbacks.add(() -> onScheduler(playerOwner, false, task));
            else task.run();
            return null;
        }).when(timer).execute(any());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        AtomicReference<Player> currentPlayer = new AtomicReference<>(forcedOffline ? null : player);
        when(player.isOnline()).thenAnswer(call -> currentPlayer.get() == player);
        when(player.getMetadata("AdvancedCoreRewardEligibilityLogin")).thenReturn(
                List.of(new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE)));
        AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        when(player.getGameMode()).thenAnswer(call -> {
            assertTrue(playerOwner.get(), "gamemode must only be read on the player owner scheduler");
            return mode.get();
        });
        Player replacement = mock(Player.class);
        when(replacement.getUniqueId()).thenReturn(id);
        when(replacement.isOnline()).thenAnswer(call -> currentPlayer.get() == replacement);
        when(replacement.getGameMode()).thenAnswer(call -> {
            assertTrue(playerOwner.get(), "gamemode must only be read on the replacement owner scheduler");
            return mode.get();
        });
        OfflinePlayer account = mock(OfflinePlayer.class);
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, id, "EconomyPlayer"));
        user.setData(data);
        doAnswer(call -> currentPlayer.get()).when(user).getPlayer();
        doAnswer(call -> currentPlayer.get() != null).when(user).isOnline();
        doReturn(account).when(user).getOfflinePlayer();
        Economy economy = mock(Economy.class);
        VaultHandler vault = mock(VaultHandler.class);
        when(plugin.getVaultHandler()).thenReturn(vault);
        when(vault.getEcon()).thenReturn(economy);
        List<String> mutations = new ArrayList<>();
        when(economy.depositPlayer(eq(account), anyDouble())).thenAnswer(call -> {
            assertFalse(playerOwner.get(), "economy provider must retain the global scheduler boundary");
            mutations.add("deposit:" + call.getArgument(1));
            return null;
        });
        when(economy.withdrawPlayer(eq(account), anyDouble())).thenAnswer(call -> {
            assertFalse(playerOwner.get(), "economy provider must retain the global scheduler boundary");
            mutations.add("withdraw:" + call.getArgument(1));
            return null;
        });
        AdvancedCorePlugin.setInstance(plugin);
        RewardHandler handler = new RewardHandler(plugin);
        when(plugin.getRewardHandler()).thenReturn(handler);
        AtomicInteger prefix = new AtomicInteger();
        AtomicInteger nativeRequests = new AtomicInteger();
        AtomicBoolean retireSuffixOnReplay = new AtomicBoolean();
        AtomicReference<Double> amount = new AtomicReference<>(withdrawal ? -5.0 : 5.0);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenAnswer(call -> currentPlayer.get());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            YamlConfiguration definition = new YamlConfiguration();
            if (!policy.equals("none")) definition.set("GameModes", List.of("SURVIVAL"));
            if (policy.equals("configured-force")) definition.set("ForceOffline", true);
            Reward reward = new Reward("GamemodeEconomy", definition).needsRewardFile(false);
            // Bind both real objects explicitly; legacy BaseTest retains a static singleton mock.
            EligibilityAccess.bind(reward, plugin);
            EligibilityAccess.bind(reward.getConfig(), plugin);
            handler.getRewards().add(reward);
            handler.addInjectedReward(new RewardInject("Prefix") {
                @Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    prefix.incrementAndGet();
                    return null;
                }
            });
            handler.addInjectedReward(new RewardInject("EconomyActions") {
                @Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    nativeRequests.incrementAndGet();
                    target.giveMoney(2.0);
                    target.giveMoney(amount.get());
                    // Arm only after replay reached the real injector. Its completed
                    // deposit is checkpointed, so the next owner task belongs to the suffix.
                    if (retireSuffixOnReplay.compareAndSet(true, false)) retiringScheduler.retireNext(true);
                    return null;
                }
            });
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            if (policy.equals("option-force")) options.forceOffline();
            options.setAsyncReplayOccurrenceId("economy-occurrence");
            var delivery = reward.giveRewardAsync(user, options).toCompletableFuture();
            for (int attempts = 0; mutations.isEmpty() && attempts < 15; attempts++) runNext(callbacks);
            assertEquals(List.of("deposit:2.0"), mutations, "native prefix must complete before the mode transition");
            assertFalse(delivery.isDone());
            if (retiringScheduler != null) {
                currentPlayer.set(replacement);
                if (transition.equals("retired-rejection")) retiringScheduler.rejectNext();
                else {
                    if (transition.equals("retired-shutdown")) retiringScheduler.beforeRetirement(() -> enabled.set(false));
                    retiringScheduler.retireNext(transition.equals("retired-immediate"));
                }
            } else if (transition.equals("mode")) mode.set(GameMode.CREATIVE);
            else currentPlayer.set(transition.equals("disconnect") ? null : replacement);
            if (transition.equals("retired-shutdown") || transition.equals("retired-rejection")) {
                assertThrows(ExecutionException.class, () -> drainUntilDone(callbacks, delivery));
                assertEquals(List.of("deposit:2.0"), mutations);
                assertTrue(persisted.get().isEmpty(), "shutdown and rejection must retain their existing failure behavior");
                retiringScheduler.enqueueStaleOwnerCallback();
                runNext(callbacks);
                retiringScheduler.repeatRetirementSignal();
                assertEquals(List.of("deposit:2.0"), mutations);
                assertTrue(callbacks.isEmpty());
                return;
            }
            drainUntilDone(callbacks, delivery);
            if (policy.equals("policy")) {
                assertEquals(List.of("deposit:2.0"), mutations, "ineligible economy action must not reach Vault");
                assertEquals(1, persisted.get().size());
                // An action that never began must not pin its old payload or repeat the completed prefix.
                amount.set(withdrawal ? -7.0 : 7.0);
                mode.set(GameMode.SURVIVAL);
                if (!transition.equals("mode")) currentPlayer.set(replacement);
                if (transition.equals("retired-repeat")) {
                    // This is the durable replay phase used by a fallback sweep: retire again
                    // while the player mode remains allowed, so no mode-denial check renews its hint.
                    retireSuffixOnReplay.set(true);
                    var retiredReplay = handler.getRewardEligibility()
                            .signalLogin(user, replacement).toCompletableFuture();
                    drainUntilDone(callbacks, retiredReplay);
                    assertEquals(2, nativeRequests.get(), "the durable replay must reach its real native injector");
                    assertFalse(retireSuffixOnReplay.get(), "retirement must be armed by the suffix injector");
                    assertEquals(List.of("deposit:2.0"), mutations);
                    assertEquals(1, persisted.get().size());
                    assertFalse(user.hasPendingRewardReplay(), "the failed replay must release its storage claim");
                    java.lang.reflect.Field pending = RewardEligibility.class.getDeclaredField("pending");
                    pending.setAccessible(true);
                    assertTrue(((java.util.Map<?, ?>) pending.get(handler.getRewardEligibility())).containsKey(user.getUUID()),
                            "a restored durable occurrence must retain its cached eligibility wake hint");
                }
                // A replacement connection becomes eligible through the accepted
                // login event, not merely by appearing in the player lookup.
                var replay = transition.equals("mode") || transition.equals("retired-repeat")
                        ? handler.getRewardEligibility().signal(user).toCompletableFuture()
                        : handler.getRewardEligibility().signalLogin(user, replacement).toCompletableFuture();
                drainUntilDone(callbacks, replay);
            }
            String action = withdrawal ? "withdraw:" : "deposit:";
            String finalAmount = policy.equals("policy") ? "7.0" : "5.0";
            assertEquals(List.of("deposit:2.0", action + finalAmount), mutations);
            assertEquals(1, prefix.get());
            assertTrue(persisted.get().isEmpty());
            var repeatedSignal = handler.getRewardEligibility().signal(user).toCompletableFuture();
            drainUntilDone(callbacks, repeatedSignal);
            assertEquals(List.of("deposit:2.0", action + finalAmount), mutations);
            assertTrue(callbacks.isEmpty());
            if (retiringScheduler != null && policy.equals("policy")) {
                retiringScheduler.enqueueStaleOwnerCallback();
                runNext(callbacks);
                retiringScheduler.repeatRetirementSignal();
                assertEquals(List.of("deposit:2.0", action + finalAmount), mutations);
                assertTrue(persisted.get().isEmpty());
                assertTrue(callbacks.isEmpty());
            }
            if (policy.endsWith("force") || policy.equals("none")) {
                verify(player, never()).getGameMode();
                verify(replacement, never()).getGameMode();
            }
        } finally {
            handler.getRewardEligibility().close();
            handler.getDelayedTimer().shutdownNow();
            AdvancedCorePlugin.setInstance(null);
        }
    }

    private void drainUntilDone(BlockingQueue<Runnable> callbacks, CompletableFuture<?> completion) throws Exception {
        for (int attempts = 0; !completion.isDone() && attempts < 40; attempts++) runNext(callbacks);
        completion.get(2, TimeUnit.SECONDS);
    }

    private void runNext(BlockingQueue<Runnable> callbacks) throws Exception {
        Runnable task = callbacks.poll(2, TimeUnit.SECONDS);
        assertNotNull(task, "economy callback was not scheduled");
        task.run();
    }

    private void onScheduler(ThreadLocal<Boolean> owner, boolean playerOwner, Runnable task) {
        boolean previous = owner.get();
        owner.set(playerOwner);
        try { task.run(); } finally { owner.set(previous); }
    }
}
