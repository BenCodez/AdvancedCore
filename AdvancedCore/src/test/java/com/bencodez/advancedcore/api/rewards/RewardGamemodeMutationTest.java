package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.data.ServerData;

class RewardGamemodeMutationTest {
    @TempDir File directory;

    @Test void experienceCallbackDefersAndKeepsCompletedLegacyAction() throws Exception {
        mutationScenario(false, "GameModes", true);
    }

    @Test void itemCallbackDefersAndKeepsCompletedLegacyAction() throws Exception {
        mutationScenario(true, "GameModes", true);
    }

    @Test void blacklistAlsoRechecksAtTheExperienceMutation() throws Exception {
        mutationScenario(false, "BlackListedGameModes", true);
    }

    @Test void allowedItemCallbackStillCompletes() throws Exception {
        mutationScenario(true, "GameModes", false);
    }

    @Test void absentPolicyKeepsExistingItemDeliveryAfterModeChange() throws Exception {
        mutationScenario(true, null, true);
    }

    @Test void disconnectedExperienceCallbackRetainsUnstartedSuffix() throws Exception {
        mutationScenario(false, "GameModes", false, true);
    }

    @Test void disconnectedItemCallbackRetainsUnstartedSuffix() throws Exception {
        mutationScenario(true, "GameModes", false, true);
    }

    private void mutationScenario(boolean itemAction, String rule, boolean changeMode) throws Exception {
        mutationScenario(itemAction, rule, changeMode, false);
    }

    @Test void immediatelyRetiredExperienceRetainsUnstartedSuffix() throws Exception {
        mutationScenario(false, "GameModes", false, false, 1);
    }
    @Test void laterRetiredExperienceRetainsUnstartedSuffix() throws Exception {
        mutationScenario(false, "GameModes", false, false, 2);
    }
    @Test void immediatelyRetiredItemsRetainUnstartedSuffix() throws Exception {
        mutationScenario(true, "GameModes", false, false, 1);
    }
    @Test void laterRetiredItemsRetainUnstartedSuffix() throws Exception {
        mutationScenario(true, "GameModes", false, false, 2);
    }
    private void mutationScenario(boolean itemAction, String rule, boolean changeMode, boolean disconnect) throws Exception {
        mutationScenario(itemAction, rule, changeMode, disconnect, 0);
    }
    private void mutationScenario(boolean itemAction, String rule, boolean changeMode, boolean disconnect,
            int retirement) throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
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
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
        RetiringOwnerScheduler retiringScheduler = retirement == 0 ? null
                : new RetiringOwnerScheduler(plugin, scheduler, callbacks::add, callbacks::add);
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(timer).execute(any());
        ServerData serverData = mock(ServerData.class);
        when(plugin.getServerDataFile()).thenReturn(serverData);
        when(serverData.getData()).thenReturn(new YamlConfiguration());
        FullInventoryHandler inventoryHandler = itemAction ? new FullInventoryHandler(plugin) : null;
        when(plugin.getFullInventoryHandler()).thenReturn(inventoryHandler);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        HashMap<org.bukkit.plugin.Plugin, MetadataValue> loginReceipt = new HashMap<>();
        loginReceipt.put(plugin, new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE));
        when(player.getMetadata("AdvancedCoreRewardEligibilityLogin"))
                .thenAnswer(call -> loginReceipt.isEmpty() ? List.of() : List.copyOf(loginReceipt.values()));
        doAnswer(call -> { MetadataValue value = call.getArgument(1);
            loginReceipt.put(value.getOwningPlugin(), value); return null; })
                .when(player).setMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any(MetadataValue.class));
        doAnswer(call -> { loginReceipt.remove(call.getArgument(1)); return null; })
                .when(player).removeMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any());
        if (retiringScheduler != null) doAnswer(call -> {
            retiringScheduler.retireNext(retirement == 1);
            return null;
        }).when(player).giveExp(2);
        AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        when(player.getGameMode()).thenAnswer(call -> mode.get());
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack item = new ItemStack(Material.STONE);
        when(inventory.addItem(item)).thenReturn(new HashMap<>());
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, id, "MutationPlayer"));
        user.setData(data);
        doReturn(player).when(user).getPlayer();
        doReturn(true).when(user).isOnline();
        AdvancedCorePlugin.setInstance(plugin);
        RewardHandler handler = new RewardHandler(plugin);
        when(plugin.getRewardHandler()).thenReturn(handler);
        AtomicInteger prefix = new AtomicInteger();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            YamlConfiguration definition = new YamlConfiguration();
            if (rule != null) definition.set(rule,
                    List.of(rule.equals("GameModes") ? "SURVIVAL" : "CREATIVE"));
            Reward reward = new Reward("GamemodeMutation", definition).needsRewardFile(false);
            // Legacy BaseTest can retain a static singleton mock across classes. Bind this
            // real reward and its configuration to the fixture, as runtime construction does.
            reward.plugin = plugin;
            reward.getConfig().plugin = plugin;
            handler.getRewards().add(reward);
            handler.addInjectedReward(new RewardInject("Prefix") {
                @Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    prefix.incrementAndGet();
                    return null;
                }
            });
            handler.addInjectedReward(new RewardInject("NativeActions") {
                @Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    target.giveExp(2);
                    if (itemAction) target.giveItem(item);
                    else target.giveExp(3);
                    return null;
                }
            });
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId("mutation-occurrence");
            var delivery = reward.giveRewardAsync(user, options).toCompletableFuture();
            assertFalse(callbacks.isEmpty(), () -> "Native mutation was not scheduled; plugin calls: "
                    + mockingDetails(plugin).getInvocations());
            runNext(callbacks); // Commit the first legacy action while still eligible.
            verify(player).giveExp(2);
            if (retiringScheduler == null) {
                Runnable finalMutation = callbacks.poll(2, TimeUnit.SECONDS);
                assertNotNull(finalMutation, "native mutation was not scheduled");
                assertFalse(delivery.isDone());
                if (changeMode) mode.set(GameMode.CREATIVE);
                if (disconnect) when(player.isOnline()).thenReturn(false);
                finalMutation.run();
            } else {
                for (int attempts = 0; !delivery.isDone() && attempts < 10; attempts++) runNext(callbacks);
            }
            delivery.get(2, TimeUnit.SECONDS);
            boolean deferred = rule != null && (changeMode || disconnect || retirement != 0);
            if (deferred) {
                assertEquals(1, persisted.get().size());
                verify(player, never()).giveExp(3);
                verify(inventory, never()).addItem(any(ItemStack[].class));
                if (inventoryHandler != null) assertTrue(inventoryHandler.getItems().isEmpty());
                mode.set(GameMode.SURVIVAL);
                when(player.isOnline()).thenReturn(true);
                var replay = (disconnect || retirement != 0
                        ? handler.getRewardEligibility().signalLogin(user, player)
                        : handler.getRewardEligibility().signal(user)).toCompletableFuture();
                runNext(callbacks);
                replay.get(2, TimeUnit.SECONDS);
            }
            if (retiringScheduler != null) {
                retiringScheduler.enqueueStaleOwnerCallback();
                runNext(callbacks);
                retiringScheduler.repeatRetirementSignal();
            }
            assertTrue(persisted.get().isEmpty());
            assertEquals(1, prefix.get());
            verify(player, times(1)).giveExp(2);
            if (itemAction) verify(inventory, times(1)).addItem(item);
            else verify(player, times(1)).giveExp(3);
            assertTrue(callbacks.isEmpty());
        } finally {
            handler.getRewardEligibility().close();
            handler.getDelayedTimer().shutdownNow();
            if (inventoryHandler != null) inventoryHandler.shutdown();
            AdvancedCorePlugin.setInstance(null);
        }
    }

    private void runNext(BlockingQueue<Runnable> callbacks) throws InterruptedException {
        Runnable callback = callbacks.poll(2, TimeUnit.SECONDS);
        assertNotNull(callback, "native mutation was not scheduled");
        callback.run();
    }
}
