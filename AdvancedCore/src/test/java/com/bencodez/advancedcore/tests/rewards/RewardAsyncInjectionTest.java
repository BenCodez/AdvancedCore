package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.MockedConstruction;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.VaultHandler;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.builtin.RewardExp;
import com.bencodez.advancedcore.api.rewards.builtin.RewardItems;
import com.bencodez.advancedcore.api.rewards.builtin.RewardPotions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.rewards.builtin.RewardSubRewards;
import com.bencodez.advancedcore.api.rewards.builtin.RewardRandomReward;
import com.bencodez.advancedcore.api.rewards.builtin.RewardJavascript;
import com.bencodez.advancedcore.api.rewards.builtin.RewardSpecialChance;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.thread.FileThread;

import net.milkbowl.vault.economy.Economy;

class RewardAsyncInjectionTest {

	@TempDir
	File tempDir;

	private AdvancedCorePlugin plugin;
	private RewardHandler handler;
	private Reward reward;
	private ConfigurationSection data;
	private AdvancedCoreUser user;
	private AtomicBoolean enabled;
	private com.bencodez.simpleapi.scheduler.BukkitScheduler scheduler;

	@BeforeEach
	void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		enabled = new AtomicBoolean(true);
		when(plugin.isEnabled()).thenAnswer(ignored -> enabled.get());
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		doAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AdvancedCorePlugin.setInstance(plugin);
		try {
			java.lang.reflect.Field pluginField = FileThread.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(FileThread.getInstance(), plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		handler = new RewardHandler(plugin);
		when(plugin.getRewardHandler()).thenReturn(handler);

		YamlConfiguration configuration = new YamlConfiguration();
		data = configuration.createSection("Reward");
		reward = new Reward("AsyncReward", data);
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(reward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		user = mock(AdvancedCoreUser.class);
	}

	@AfterEach
	void tearDown() {
		handler.getDelayedTimer().shutdownNow();
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	void reusedTopLevelOptionsDoNotShareCompletedReplayState() {
		AtomicInteger invocations = new AtomicInteger();
		Player player = mock(Player.class);
		when(user.getPlayer()).thenReturn(player);
		doAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));
		handler.getInjectedRewards().add(new RewardInject("Async") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invocations.incrementAndGet();
				return CompletableFuture.completedFuture(null);
			}
		});
		RewardOptions options = new RewardOptions().forceOffline();

		Reward.ReplayState firstDispatch = Reward.replayStateFor(options);
		Reward.ReplayState secondDispatch = Reward.replayStateFor(options);
		reward.giveRewardUserAsync(user, new HashMap<>(), options).toCompletableFuture().join();
		reward.giveRewardUserAsync(user, new HashMap<>(), options).toCompletableFuture().join();

		assertNotSame(firstDispatch, secondDispatch);
		assertEquals(2, invocations.get());
		assertNull(options.getAsyncReplayState());
		assertNull(options.getAsyncReplayKey());
		assertNull(options.getAsyncReplayOccurrenceId());
	}

	@Test
	void legacyScheduledRewardsWaitForQueuedBukkitActions() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		FullInventoryHandler inventory = mock(FullInventoryHandler.class);
		when(plugin.getFullInventoryHandler()).thenReturn(inventory);
		CompletableFuture<Void> itemDelivery = new CompletableFuture<>();
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, uuid, false, false);
		realUser.setPlayerName("Legacy");
		Player player = mock(Player.class);
		when(inventory.giveItemAsync(eq(player), any(ItemStack.class))).thenReturn(itemDelivery);
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
		doAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return null;
		})
				.when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));

		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				realUser.giveMoney(2);
				realUser.giveItem(new ItemStack(Material.STONE));
				return null;
			}
		});

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> result = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			assertFalse(result.toCompletableFuture().isDone());
			assertEquals(1, queued.size());
			queued.remove(0).run();
			assertTrue(queued.isEmpty(), "replay hands the item directly to the awaited inventory task");
			assertFalse(result.toCompletableFuture().isDone(), "the inner item delivery still owns completion");
			itemDelivery.complete(null);
			result.toCompletableFuture().join();
		}
		verify(economy).depositPlayer(offlinePlayer, 2);
		verify(inventory).giveItemAsync(eq(player), any(ItemStack.class));
	}

	@Test
	void ordinaryItemGrantUsesTheVoidInventoryApiWithoutAnAsyncTimeout() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		FullInventoryHandler inventory = mock(FullInventoryHandler.class);
		when(plugin.getFullInventoryHandler()).thenReturn(inventory);
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, uuid, false, false);
		Player player = mock(Player.class);
		ItemStack item = new ItemStack(Material.STONE);

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			realUser.giveItem(item);
		}

		verify(inventory).giveItem(player, item);
		verify(inventory, never()).giveItemAsync(any(Player.class), any(ItemStack.class));
	}

	@Test
	void replayItemsRemainPendingWhenThePlayerDisconnectsBeforeTheirInjectionRuns() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		FullInventoryHandler inventory = mock(FullInventoryHandler.class);
		when(plugin.getFullInventoryHandler()).thenReturn(inventory);
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, uuid, false, false);
		realUser.setPlayerName("Dispatch");
		Player player = mock(Player.class);
		when(player.getDisplayName()).thenReturn("Dispatch");
		AtomicReference<Player> availablePlayer = new AtomicReference<>(player);
		AtomicReference<Material> itemType = new AtomicReference<>(Material.STONE);
		AtomicReference<Runnable> queuedInjection = new AtomicReference<>();
		doAnswer(invocation -> {
			queuedInjection.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
		data.createSection("Items").createSection("Stone").set("Material", "STONE");
		RewardItems.registerItems(handler, plugin);

		try (MockedConstruction<ItemBuilder> builders = mockConstruction(ItemBuilder.class, (builder, context) -> {
			when(builder.setPlaceholders(any(HashMap.class))).thenReturn(builder);
			when(builder.toItemStack(any(Player.class))).thenAnswer(ignored -> new ItemStack(itemType.get()));
		}); org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenAnswer(ignored -> availablePlayer.get());
			CompletionStage<Void> delivery = reward.giveRewardUserAsync(realUser, new HashMap<>(), new RewardOptions());
			assertFalse(delivery.toCompletableFuture().isDone(), "the initial online check only queues injection dispatch");
			assertNotNull(queuedInjection.get());

			availablePlayer.set(null);
			Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
					() -> {
						queuedInjection.get().run();
						delivery.toCompletableFuture().join();
					});
			Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
			assertEquals(0, checkpoint.getCompletedInjectionCount());
			assertEquals(0, checkpoint.getReplayProgress().getOrDefault("AsyncReward", 0));
			assertTrue(checkpoint.getReplayPlaceholders().entrySet().stream().noneMatch(entry ->
					entry.getKey().startsWith("__advancedcore_replay_legacy_actions_")
							&& !entry.getKey().endsWith("_snapshot")),
					"an undelivered item must not receive an action-completion checkpoint");
			verify(inventory, never()).giveItemAsync(any(Player.class), any(ItemStack.class));

			availablePlayer.set(player);
			itemType.set(Material.DIRT);
			when(inventory.giveItemAsync(eq(player), any(ItemStack.class)))
					.thenReturn(CompletableFuture.completedFuture(null));
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			resumed.toCompletableFuture().join();

			availablePlayer.set(null);
			AdvancedCoreUser.AsyncActionCollection placeholderContext = realUser.beginAsyncActionCollection();
			realUser.giveItem(new ItemStack(Material.STONE), new HashMap<>());
			assertThrows(java.util.concurrent.CompletionException.class,
					() -> realUser.endAsyncActionCollection(placeholderContext).toCompletableFuture().join(),
					"placeholder-expanded items use the same replay-only unavailable-player failure");
		}
		verify(inventory).giveItemAsync(eq(player), any(ItemStack.class));
	}

	@Test
	void replayPotionsRemainPendingWhenThePlayerDisconnectsBeforeTheirInjectionRuns() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, uuid, false, false);
		realUser.setPlayerName("PotionDispatch");
		Player player = mock(Player.class);
		when(player.getDisplayName()).thenReturn("PotionDispatch");
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		AtomicReference<Player> availablePlayer = new AtomicReference<>(player);
		AtomicReference<Runnable> queuedInjection = new AtomicReference<>();
		doAnswer(invocation -> {
			queuedInjection.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
		data.createSection("Potions").createSection("SPEED").set("Duration", 1);
		RewardPotions.register(handler, plugin);

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenAnswer(ignored -> availablePlayer.get());
			CompletionStage<Void> delivery = reward.giveRewardUserAsync(realUser, new HashMap<>(), new RewardOptions());
			assertFalse(delivery.toCompletableFuture().isDone());
			availablePlayer.set(null);
			Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
					() -> {
						queuedInjection.get().run();
						delivery.toCompletableFuture().join();
					});
			Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
			assertEquals(0, checkpoint.getCompletedInjectionCount());
			assertEquals(0, checkpoint.getReplayProgress().getOrDefault("AsyncReward", 0));
			verify(player, never()).addPotionEffect(any());

			availablePlayer.set(player);
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));
			AtomicReference<Runnable> queuedPotionDelivery = new AtomicReference<>();
			doAnswer(invocation -> {
				queuedPotionDelivery.set(invocation.getArgument(1, Runnable.class));
				return null;
			}).when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			assertFalse(resumed.toCompletableFuture().isDone(), "recovery must requeue the unfinished potion action");
			assertNotNull(queuedPotionDelivery.get());
			availablePlayer.set(null);
			queuedPotionDelivery.get().run();
			assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		}
		verify(player, never()).addPotionEffect(any());
	}

	@Test
	void replayExperienceRemainsPendingForNullAndDisconnectedPlayers() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, uuid, false, false);
		realUser.setPlayerName("ExperienceDispatch");
		Player player = mock(Player.class);
		when(player.getDisplayName()).thenReturn("ExperienceDispatch");
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		AtomicReference<Player> availablePlayer = new AtomicReference<>(player);
		AtomicReference<Runnable> queuedInjection = new AtomicReference<>();
		doAnswer(invocation -> {
			queuedInjection.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
		data.set("EXP", 5);
		RewardExp.register(handler, plugin);

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenAnswer(ignored -> availablePlayer.get());
			CompletionStage<Void> delivery = reward.giveRewardUserAsync(realUser, new HashMap<>(), new RewardOptions());
			assertFalse(delivery.toCompletableFuture().isDone());
			availablePlayer.set(null);
			Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
					() -> {
						queuedInjection.get().run();
						delivery.toCompletableFuture().join();
					});
			Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
			assertEquals(0, checkpoint.getCompletedInjectionCount());
			verify(player, never()).giveExp(5);

			availablePlayer.set(player);
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), eq(player));
			AtomicReference<Runnable> queuedExperience = new AtomicReference<>();
			doAnswer(invocation -> {
				queuedExperience.set(invocation.getArgument(1, Runnable.class));
				return null;
			}).when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			assertFalse(resumed.toCompletableFuture().isDone());
			assertNotNull(queuedExperience.get());
			availablePlayer.set(null);
			queuedExperience.get().run();
			Throwable scheduledFailure = assertThrows(java.util.concurrent.CompletionException.class,
					() -> resumed.toCompletableFuture().join());
			Reward.RewardReplayFailure retryCheckpoint = findCheckpoint(scheduledFailure);

			// The disconnected owner task never mutated the player, so its reservation
			// must be released and a changed retry payload must be accepted.
			data.set("EXP", 7);
			availablePlayer.set(player);
			doAnswer(invocation -> {
				invocation.getArgument(1, Runnable.class).run();
				return null;
			}).when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
			CompletionStage<Void> changedRetry = (CompletionStage<Void>) replay.invoke(reward, realUser,
					retryCheckpoint.getReplayPlaceholders(), 0,
					state.newInstance(retryCheckpoint.getReplayProgress(),
							retryCheckpoint.getReplayRegistryFingerprints(), false), "AsyncReward");
			changedRetry.toCompletableFuture().join();
			verify(player).giveExp(7);
		}
		verify(player, never()).giveExp(5);
	}

	@Test
	void offlineRequeueRetainsReplayStateAndLegacyActionMarkers() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isProcessRewards()).thenReturn(true);
		when(config.isPauseRewards()).thenReturn(false);
		when(config.isTreatVanishAsOffline()).thenReturn(false);
		when(config.getFormatRewardTimeFormat()).thenReturn("yyyy-MM-dd");
		when(plugin.getOptions()).thenReturn(config);
		when(user.isOnline()).thenReturn(false);
		when(user.getPlayerName()).thenReturn("Offline");
		RewardOptions options = new RewardOptions().setCheckTimed(false).setIgnoreRequirements(true).setGiveOffline(true);
		options.setOnline(false);
		options.setAsyncReplayProgress(Map.of("AsyncReward", 1));
		options.setAsyncReplayRegistryFingerprints(Map.of("AsyncReward", "registry-fingerprint"));
		options.getPlaceholders().put("__advancedcore_replay_legacy_actions_marker", "v2:completed");
		org.bukkit.plugin.PluginManager pluginManager = mock(org.bukkit.plugin.PluginManager.class);

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			reward.giveReward(user, options);
		}

		ArgumentCaptor<RewardOptions> queuedOptions = ArgumentCaptor.forClass(RewardOptions.class);
		verify(user).addOfflineRewards(eq(reward), eq(options.getPlaceholders()), queuedOptions.capture());
		assertEquals(options, queuedOptions.getValue());
		assertEquals(1, queuedOptions.getValue().getAsyncReplayProgress().get("AsyncReward"));
		assertEquals("registry-fingerprint", queuedOptions.getValue().getAsyncReplayRegistryFingerprints().get("AsyncReward"));
		assertEquals("v2:completed", queuedOptions.getValue().getPlaceholders()
				.get("__advancedcore_replay_legacy_actions_marker"));
	}

	@Test
	void legacySchedulerRejectionFailsAsyncRewardClosed() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		when(vault.getEcon()).thenReturn(mock(Economy.class));
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Legacy");
		doThrow(new IllegalStateException("scheduler stopped")).when(scheduler)
				.runTask(eq(plugin), any(Runnable.class));
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				realUser.giveMoney(2);
				return null;
			}
		});
		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			assertThrows(java.util.concurrent.CompletionException.class,
					() -> reward.giveInjectedRewardsAsync(realUser, new HashMap<>()).toCompletableFuture().join());
		}
	}

	@Test
	void schedulerRejectionReleasesUnstartedRandomActionForReplay() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("RandomReplay");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		AtomicReference<Double> amount = new AtomicReference<>(1D);
		AtomicBoolean reject = new AtomicBoolean(true);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			if (reject.get()) throw new IllegalStateException("scheduler stopped");
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				realUser.giveMoney(amount.get());
				return CompletableFuture.completedFuture(null);
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			Reward.RewardReplayFailure checkpoint = findCheckpoint(assertThrows(
					java.util.concurrent.CompletionException.class,
					() -> reward.giveInjectedRewardsAsync(realUser, new HashMap<>()).toCompletableFuture().join()));

			amount.set(2D);
			reject.set(false);
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			assertEquals(1, queued.size(), "the safely unstarted action is regenerated on retry");
			queued.remove(0).run();
			resumed.toCompletableFuture().join();
		}
		verify(economy, never()).depositPlayer(offlinePlayer, 1D);
		verify(economy).depositPlayer(offlinePlayer, 2D);
	}

	@Test
	void capturedAsyncContinuationActionCheckpointsRetryOnlyTheUnfinishedSuffix() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Checkpointed");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		CompletableFuture<Void> continuation = new CompletableFuture<>();
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				realUser.giveMoney(1);
				realUser.giveMoney(2);
				return null;
			}
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				AdvancedCoreUser.AsyncActionContext context = ignoredUser.captureAsyncActionContext();
				return continuation.thenApply(context.wrap(nothing -> {
					realUser.giveMoney(1);
					realUser.giveMoney(2);
					return null;
				}));
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> first = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			continuation.complete(null);
			assertEquals(1, queued.size());
			queued.remove(0).run();
			assertEquals(1, queued.size());
			enabled.set(false);
			queued.remove(0).run();
			Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
					() -> first.toCompletableFuture().join());
			Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
			assertTrue(checkpoint.getReplayPlaceholders().entrySet().stream().anyMatch(entry ->
					entry.getKey().startsWith("__advancedcore_replay_legacy_actions_") && entry.getValue().startsWith("v2:")));
			assertTrue(checkpoint.getReplayPlaceholders().keySet().stream()
					.anyMatch(key -> key.startsWith("__advancedcore_replay_legacy_actions_") && key.endsWith("_snapshot")));

			enabled.set(true);
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			assertEquals(1, queued.size(), "the completed first action is never scheduled again");
			queued.remove(0).run();
			resumed.toCompletableFuture().join();
		}
		verify(economy).depositPlayer(offlinePlayer, 1);
		verify(economy).depositPlayer(offlinePlayer, 2);
	}

	@Test
	void legacyActionSnapshotsSurviveReorderRemovalAndExtension() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("ActionSnapshot");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		AtomicReference<List<Double>> amounts = new AtomicReference<>(new ArrayList<>(List.of(1D, 2D)));
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				for (double amount : amounts.get()) realUser.giveMoney(amount);
				return CompletableFuture.completedFuture(null);
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> first = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			queued.remove(0).run();
			enabled.set(false);
			queued.remove(0).run();
			Reward.RewardReplayFailure checkpoint = findCheckpoint(assertThrows(
					java.util.concurrent.CompletionException.class, () -> first.toCompletableFuture().join()));

			enabled.set(true);
			amounts.set(new ArrayList<>(List.of(2D, 3D)));
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			assertEquals(1, queued.size(), "the completed, removed action is not rescheduled");
			queued.remove(0).run();
			assertEquals(1, queued.size(), "the extended action runs after the unfinished stable action");
			queued.remove(0).run();
			resumed.toCompletableFuture().join();
		}
		verify(economy).depositPlayer(offlinePlayer, 1D);
		verify(economy).depositPlayer(offlinePlayer, 2D);
		verify(economy).depositPlayer(offlinePlayer, 3D);
	}

	@Test
	void legacyActionReplayAllowsRemovalOfAConclusiveUnstartedAction() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("RemovedUnfinishedAction");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		AtomicReference<List<Double>> amounts = new AtomicReference<>(new ArrayList<>(List.of(1D, 2D)));
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				for (double amount : amounts.get()) realUser.giveMoney(amount);
				return CompletableFuture.completedFuture(null);
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> first = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			queued.remove(0).run();
			enabled.set(false);
			queued.remove(0).run();
			Reward.RewardReplayFailure checkpoint = findCheckpoint(assertThrows(
					java.util.concurrent.CompletionException.class, () -> first.toCompletableFuture().join()));

			enabled.set(true);
			amounts.set(new ArrayList<>(List.of(1D)));
			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, realUser,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			resumed.toCompletableFuture().join();
			assertTrue(queued.isEmpty(), "an action proven unstarted may disappear before retry");
		}
		verify(economy).depositPlayer(offlinePlayer, 1D);
		verify(economy, never()).depositPlayer(offlinePlayer, 2D);
	}

	@Test
	void itemActionFingerprintIsCanonicalAndIncludesMetadata() throws Exception {
		LinkedHashMap<String, Object> first = new LinkedHashMap<>();
		first.put("type", "DIAMOND");
		first.put("amount", 1);
		first.put("meta", Map.of("display-name", "Original", "lore", List.of("one", "two")));
		LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
		reordered.put("meta", Map.of("lore", List.of("one", "two"), "display-name", "Original"));
		reordered.put("amount", 1);
		reordered.put("type", "DIAMOND");
		LinkedHashMap<String, Object> changedMetadata = new LinkedHashMap<>(reordered);
		changedMetadata.put("meta", Map.of("display-name", "Changed", "lore", List.of("one", "two")));
		java.lang.reflect.Method descriptor = AdvancedCoreUser.class.getDeclaredMethod("canonicalActionDescriptor",
				Object.class);
		descriptor.setAccessible(true);
		String firstDescriptor = (String) descriptor.invoke(null, first);
		String reorderedDescriptor = (String) descriptor.invoke(null, reordered);
		String changedDescriptor = (String) descriptor.invoke(null, changedMetadata);

		assertEquals(Reward.legacyActionFingerprint(firstDescriptor), Reward.legacyActionFingerprint(reorderedDescriptor));
		assertNotEquals(Reward.legacyActionFingerprint(firstDescriptor), Reward.legacyActionFingerprint(changedDescriptor));
	}

	@Test
	void legacyQueuedActionFailsWhenPluginShutsDownBeforeExecution() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		when(vault.getEcon()).thenReturn(mock(Economy.class));
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Legacy");
		AtomicReference<Runnable> queued = new AtomicReference<>();
		doAnswer(invocation -> {
			queued.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				realUser.giveMoney(2);
				return null;
			}
		});
		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			CompletionStage<Void> result = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			assertFalse(result.toCompletableFuture().isDone());
			enabled.set(false);
			queued.get().run();
			assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		}
	}

	@Test
	void legacyActionsCapturedByAsyncContextAreAwaited() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Legacy");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queued = new ArrayList<>();
		doAnswer(invocation -> {
			queued.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		CompletableFuture<Void> continuation = new CompletableFuture<>();
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				AdvancedCoreUser.AsyncActionContext context = ignoredUser.captureAsyncActionContext();
				return continuation.thenApply(context.wrap(nothing -> {
					realUser.giveMoney(2);
					return null;
				}));
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> result = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			continuation.complete(null);
			assertEquals(1, queued.size());
			assertFalse(result.toCompletableFuture().isDone());
			queued.get(0).run();
			result.toCompletableFuture().join();
		}
		verify(economy).depositPlayer(offlinePlayer, 2);
	}

	@Test
	void serializedPersistedReplaysDoNotClaimUnscopedContinuationActions() throws Exception {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Serialized");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		ArrayList<Runnable> queuedActions = new ArrayList<>();
		doAnswer(invocation -> {
			queuedActions.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		ArrayList<CompletableFuture<Void>> continuations = new ArrayList<>();
		continuations.add(new CompletableFuture<>());
		continuations.add(new CompletableFuture<>());
		AtomicInteger invocations = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				int index = invocations.getAndIncrement();
				return continuations.get(index).thenApply(nothing -> {
					realUser.giveMoney(index + 1);
					return null;
				});
			}
		});

		java.lang.reflect.Method enqueue = AdvancedCoreUser.class.getDeclaredMethod("enqueuePersistedReplay",
				java.util.function.Supplier.class);
		enqueue.setAccessible(true);
		java.util.function.Supplier<CompletionStage<Void>> replay =
				() -> reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			enqueue.invoke(realUser, replay);
			enqueue.invoke(realUser, replay);
			assertEquals(1, invocations.get(), "the second replay must remain behind the first continuation");

			continuations.get(0).complete(null);
			assertEquals(1, queuedActions.size());
			assertEquals(2, invocations.get(), "the second replay starts after the first async stage completes");
			queuedActions.get(0).run();

			continuations.get(1).complete(null);
			assertEquals(2, queuedActions.size());
			queuedActions.get(1).run();
		}
		verify(economy).depositPlayer(offlinePlayer, 1);
		verify(economy).depositPlayer(offlinePlayer, 2);
	}

	@Test
	void unscopedActionsAreNotClaimedByAnArbitraryCollection() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		when(vault.getEcon()).thenReturn(mock(Economy.class));
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Nested");
		ArrayList<Runnable> queuedActions = new ArrayList<>();
		doAnswer(invocation -> {
			queuedActions.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(mock(OfflinePlayer.class));
			AdvancedCoreUser.AsyncActionCollection outer = realUser.beginAsyncActionCollection();
			AdvancedCoreUser.AsyncActionCollection inner = realUser.beginAsyncActionCollection();
			realUser.restoreAsyncActionCollectionScope(inner);
				realUser.restoreAsyncActionCollectionScope(outer);
				realUser.giveMoney(1);
				realUser.claimAsyncContinuationActions(inner);

				CompletionStage<Void> outerCompletion = realUser.endAsyncActionCollection(outer);
				CompletionStage<Void> innerCompletion = realUser.endAsyncActionCollection(inner);
				assertEquals(1, queuedActions.size());
				assertTrue(outerCompletion.toCompletableFuture().isDone());
				assertTrue(innerCompletion.toCompletableFuture().isDone());
			queuedActions.get(0).run();
			outerCompletion.toCompletableFuture().join();
			innerCompletion.toCompletableFuture().join();
		}
	}

	@Test
	void synchronousRewardActionIsNotClaimedByPendingAsyncInjection() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		when(vault.getEcon()).thenReturn(mock(Economy.class));
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Independent");
		ArrayList<Runnable> queuedActions = new ArrayList<>();
		doAnswer(invocation -> {
			queuedActions.add(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		CompletableFuture<Object> pending = new CompletableFuture<>();
		handler.getInjectedRewards().add(new RewardInject("Async") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return pending; }
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			CompletionStage<Void> result = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			realUser.giveMoney(4);
			assertEquals(1, queuedActions.size());
			pending.complete(null);
			assertTrue(result.toCompletableFuture().isDone(),
					"an unrelated queued action must not become this async reward's dependency");
		}
	}

	@Test
	void failedLegacyActionCreatedByAnAsyncContinuationDoesNotFailAnUnrelatedReward() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		when(vault.getEcon()).thenReturn(mock(Economy.class));
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Legacy");
		AtomicReference<Runnable> queued = new AtomicReference<>();
		doAnswer(invocation -> {
			queued.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		CompletableFuture<Void> continuation = new CompletableFuture<>();
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return continuation.thenApply(nothing -> {
					realUser.giveMoney(2);
					return null;
				});
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			CompletionStage<Void> result = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			continuation.complete(null);
			enabled.set(false);
			queued.get().run();
			result.toCompletableFuture().join();
		}
	}

	@Test
	void concurrentContinuationsDoNotClaimOneAnotherActions() {
		AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
		when(config.isOnlineMode()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(config);
		VaultHandler vault = mock(VaultHandler.class);
		Economy economy = mock(Economy.class);
		when(vault.getEcon()).thenReturn(economy);
		when(plugin.getVaultHandler()).thenReturn(vault);
		AdvancedCoreUser realUser = new AdvancedCoreUser(plugin, UUID.randomUUID(), false, false);
		realUser.setPlayerName("Concurrent");
		OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
		AtomicReference<Runnable> successfulAction = new AtomicReference<>();
		doThrow(new IllegalStateException("first scheduler failure")).doAnswer(invocation -> {
			successfulAction.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(eq(plugin), any(Runnable.class));
		ArrayList<CompletableFuture<Void>> continuations = new ArrayList<>();
		continuations.add(new CompletableFuture<>());
		continuations.add(new CompletableFuture<>());
		AtomicInteger invocation = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Legacy") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				int index = invocation.getAndIncrement();
				return continuations.get(index).thenApply(nothing -> {
					realUser.giveMoney(index + 1);
					return null;
				});
			}
		});

		UUID uuid = UUID.fromString(realUser.getUUID());
		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
			CompletionStage<Void> first = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());
			CompletionStage<Void> second = reward.giveInjectedRewardsAsync(realUser, new HashMap<>());

			continuations.get(0).complete(null);
			first.toCompletableFuture().join();
			assertFalse(second.toCompletableFuture().isDone());

			continuations.get(1).complete(null);
			successfulAction.get().run();
			second.toCompletableFuture().join();
		}
		verify(economy).depositPlayer(offlinePlayer, 2);
	}

	@Test
	void commandSnapshotIsCheckpointedBeforeItsFirstDispatch() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		List<Reward.ReplayCheckpoint> checkpoints = new ArrayList<>();
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(new HashMap<>(), new HashMap<>(), false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoints::add);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("replayCommandSequence",
				AdvancedCorePlugin.class, HashMap.class, String.class, List.class, List.class, stateType, String.class,
				java.util.function.BiFunction.class);
		replay.setAccessible(true);
		List<String> dispatched = new ArrayList<>();
		@SuppressWarnings("unchecked")
		CompletionStage<Void> result = (CompletionStage<Void>) replay.invoke(null, plugin, new HashMap<>(), "console",
				List.of("say %value%"), List.of("say fixed"), replayState, "AsyncReward/0",
					(java.util.function.BiFunction<String, Integer, CompletionStage<Void>>) (command, index) -> {
					dispatched.add(command);
					return CompletableFuture.completedFuture(null);
				});

		ArgumentCaptor<Runnable> writes = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(writes.capture());
		assertTrue(dispatched.isEmpty());
		assertFalse(result.toCompletableFuture().isDone());
		writes.getValue().run();
		assertEquals(List.of("say fixed"), dispatched);
		assertTrue(checkpoints.get(0).getReplayProgress().isEmpty());
		assertTrue(checkpoints.get(0).getPlaceholders().entrySet().stream()
				.anyMatch(entry -> entry.getKey().startsWith("__advancedcore_replay_commands_")
						&& entry.getKey().endsWith("_snapshot") && entry.getValue().contains("c2F5IGZpeGVk")));
		ArgumentCaptor<Runnable> progressWrite = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor, times(2)).execute(progressWrite.capture());
		progressWrite.getAllValues().get(1).run();
		result.toCompletableFuture().join();
	}

	@Test
	void commandSnapshotDistinguishesAnEmptyElementFromAnEmptyList() throws Exception {
		java.lang.reflect.Method encode = Reward.class.getDeclaredMethod("encodeCommandSnapshot", List.class);
		encode.setAccessible(true);
		String emptyList = (String) encode.invoke(null, List.of());
		String emptyElement = (String) encode.invoke(null, List.of(""));
		assertNotEquals(emptyList, emptyElement);
		java.lang.reflect.Method decode = Reward.class.getDeclaredMethod("decodeCommandSnapshot", String.class, int.class);
		decode.setAccessible(true);
		assertEquals(List.of(), decode.invoke(null, emptyList, 0));
		assertEquals(List.of(""), decode.invoke(null, emptyElement, 1));
	}

	@Test
	void asyncInjectionsAreSequentialAndPostRewardsWaitForThem() {
		List<String> events = new ArrayList<>();
		HashMap<String, String> placeholders = new HashMap<>();
		CompletableFuture<Object> deferred = new CompletableFuture<>();

		RewardInject first = new RewardInject("First") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous hook should be used");
			}

			@Override
			public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("first-start");
				return deferred;
			}
		};
		first.asPlaceholder("first");
		RewardInject second = new RewardInject("Second") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous hook should be used");
			}

			@Override
			public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("second:" + ignoredPlaceholders.get("first"));
				return CompletableFuture.completedFuture("second-value");
			}
		};
		second.asPlaceholder("second");
		RewardInject post = new RewardInject("Post") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				events.add("post:" + ignoredPlaceholders.get("second"));
				return null;
			}
		};
		post.postReward();
		handler.getInjectedRewards().add(first);
		handler.getInjectedRewards().add(second);
		handler.getInjectedRewards().add(post);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, placeholders);
		assertFalse(result.toCompletableFuture().isDone());
		assertEquals(List.of("first-start"), events);

		deferred.complete("first-value");
		result.toCompletableFuture().join();
		assertEquals(List.of("first-start", "second:first-value", "post:second-value"), events);
		assertEquals("first-value", placeholders.get("first"));
		assertEquals("second-value", placeholders.get("second"));
	}

	@Test
	void checkpointWriteRunsOffServerThreadAndGatesTheNextInjection() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		List<String> events = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("First") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("first");
				return null;
			}
		});
		handler.getInjectedRewards().add(new RewardInject("Second") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("second");
				return null;
			}
		});

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(null, null, false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint ->
				events.add("checkpoint:" + checkpoint.getReplayProgress().get("AsyncReward")));
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		@SuppressWarnings("unchecked")
		CompletionStage<Void> result = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence");

		ArgumentCaptor<Runnable> writes = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(writes.capture());
		assertEquals(List.of("first"), events);
		assertFalse(result.toCompletableFuture().isDone());

		writes.getValue().run();
		verify(storageExecutor, times(2)).execute(writes.capture());
		assertEquals(List.of("first", "checkpoint:1", "second"), events);
		writes.getAllValues().get(writes.getAllValues().size() - 1).run();
		result.toCompletableFuture().join();
		assertEquals(List.of("first", "checkpoint:1", "second", "checkpoint:2"), events);
	}

	@Test
	void injectionIsNotifiedOnlyAfterItsReplayCheckpointIsPersisted() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		List<String> events = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("Points") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("injection");
				return null;
			}
			@Override public CompletionStage<Void> onReplayCheckpointPersisted(Reward ignored, AdvancedCoreUser ignoredUser,
					String occurrenceId, String injectionKey) {
				events.add("ack:" + occurrenceId + ":" + injectionKey);
				return CompletableFuture.completedFuture(null);
			}
		});

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(null, null, false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint ->
				events.add("checkpoint"));
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		@SuppressWarnings("unchecked")
		CompletionStage<Void> result = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence-1");

		ArgumentCaptor<Runnable> writes = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(writes.capture());
		assertEquals(List.of("injection"), events);
		assertFalse(result.toCompletableFuture().isDone());

		writes.getValue().run();
		result.toCompletableFuture().join();
		assertEquals(List.of("injection", "checkpoint", "ack:occurrence-1:AsyncReward/0"), events);
	}

	@Test
	void timedOutQueuedCheckpointCannotPersistLater() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		AtomicInteger writes = new AtomicInteger();
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(null, null, false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState,
				(java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint -> writes.incrementAndGet());
		java.lang.reflect.Method persist = stateType.getDeclaredMethod("persistCheckpointAsync",
				AdvancedCorePlugin.class, HashMap.class, long.class, TimeUnit.class);
		persist.setAccessible(true);
		@SuppressWarnings("unchecked")
		CompletionStage<Void> result = (CompletionStage<Void>) persist.invoke(replayState, plugin, new HashMap<>(), 0L,
				TimeUnit.MILLISECONDS);

		ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(queued.capture());
		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		queued.getValue().run();
		assertEquals(0, writes.get());
	}

	@Test
	void replayRetriesFailedCheckpointNotificationWithoutRepeatingInjection() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			invocation.getArgument(0, Runnable.class).run();
			return null;
		}).when(storageExecutor).execute(any(Runnable.class));
		when(plugin.getTimer()).thenReturn(storageExecutor);
		AtomicInteger injections = new AtomicInteger();
		AtomicInteger notifications = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Points") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				injections.incrementAndGet();
				return null;
			}
			@Override public CompletionStage<Void> onReplayCheckpointPersisted(Reward ignored,
					AdvancedCoreUser ignoredUser, String occurrenceId, String injectionKey) {
				return notifications.incrementAndGet() == 1
						? CompletableFuture.failedFuture(new IllegalStateException("ack unavailable"))
						: CompletableFuture.completedFuture(null);
			}
		});

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(null, null, false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint -> { });
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);

		@SuppressWarnings("unchecked")
		CompletionStage<Void> first = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence-1");
		assertThrows(java.util.concurrent.CompletionException.class, () -> first.toCompletableFuture().join());
		@SuppressWarnings("unchecked")
		CompletionStage<Void> retry = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence-1");
		retry.toCompletableFuture().join();

		assertEquals(1, injections.get());
		assertEquals(2, notifications.get());
	}

	@Test
	void typedIntegerAsyncHookUsesParsedValue() {
		data.set("Amount", 7);
		HashMap<String, String> placeholders = new HashMap<>();
		List<Integer> received = new ArrayList<>();

		RewardInjectInt amount = new RewardInjectInt("Amount") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public String onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, int ignoredValue,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous typed hook should be used");
			}

			@Override
			public CompletionStage<String> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser, int value,
					HashMap<String, String> ignoredPlaceholders) {
				received.add(value);
				return CompletableFuture.completedFuture("async-" + value);
			}
		};
		amount.asPlaceholder("amount");
		handler.getInjectedRewards().add(amount);

		reward.giveInjectedRewardsAsync(user, placeholders).toCompletableFuture().join();
		assertTrue(received.contains(7));
		assertEquals("async-7", placeholders.get("amount"));
	}

	@Test
	void typedStringAsyncHookPreservesConfiguredValueWhenCallbackReturnsNull() {
		data.set("Message", "configured-value");
		HashMap<String, String> placeholders = new HashMap<>();
		RewardInjectString message = new RewardInjectString("Message") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public String onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, String value,
					HashMap<String, String> ignoredPlaceholders) { return null; }
		};
		message.asPlaceholder("message");
		handler.getInjectedRewards().add(message);

		reward.giveInjectedRewardsAsync(user, placeholders).toCompletableFuture().join();

		assertEquals("configured-value", placeholders.get("message"));
	}

	@Test
	void failedAsyncInjectionStopsDependentRewardsAndPropagates() {
		AtomicBoolean dependentRan = new AtomicBoolean();
		RewardInject failing = asyncInjection("Failure", CompletableFuture.failedFuture(
				new IllegalStateException("durable update failed")), null);
		RewardInject dependent = asyncInjection("Dependent", CompletableFuture.completedFuture(null), dependentRan);
		handler.getInjectedRewards().add(failing);
		handler.getInjectedRewards().add(dependent);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		assertFalse(dependentRan.get());
	}

	@Test
	void failingLegacyInjectionDoesNotStopLaterAsyncChainSteps() {
		AtomicBoolean laterRan = new AtomicBoolean();
		handler.getInjectedRewards().add(new RewardInject("BrokenLegacy") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				throw new IllegalStateException("bad legacy configuration");
			}
		});
		handler.getInjectedRewards().add(asyncInjection("Later", CompletableFuture.completedFuture(null), laterRan));

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		assertTrue(laterRan.get());
	}

	@Test
	void synchronizedAsyncInjectionSerializesThroughCompletion() {
		List<CompletableFuture<Object>> completions = new ArrayList<>();
		List<Integer> starts = new ArrayList<>();
		RewardInject serialized = new RewardInject("Serialized") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				starts.add(starts.size() + 1);
				CompletableFuture<Object> completion = new CompletableFuture<>();
				completions.add(completion);
				return completion;
			}
		};
		serialized.synchronize();
		handler.getInjectedRewards().add(serialized);

		CompletionStage<Void> first = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		CompletionStage<Void> second = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertEquals(List.of(1), starts);

		completions.get(0).complete(null);
		assertEquals(List.of(1, 2), starts);
		completions.get(1).complete(null);
		first.toCompletableFuture().join();
		second.toCompletableFuture().join();
	}

	@Test
	void shutdownStopsContinuationsAfterPendingInjection() {
		CompletableFuture<Object> completion = new CompletableFuture<>();
		AtomicBoolean dependentRan = new AtomicBoolean();
		handler.getInjectedRewards().add(asyncInjection("Pending", completion, null));
		handler.getInjectedRewards().add(
				asyncInjection("Dependent", CompletableFuture.completedFuture(null), dependentRan));
		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());

		enabled.set(false);
		completion.complete(null);

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		assertFalse(dependentRan.get());
	}

	@Test
	void schedulerCancellationCompletesStageExceptionally() {
		Reward shortTimeoutReward = new Reward("AsyncReward", data) {
			@Override
			protected long getServerThreadDispatchTimeoutMillis() {
				return 25;
			}
		};
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(shortTimeoutReward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		AtomicBoolean invoked = new AtomicBoolean();
		handler.getInjectedRewards().add(
				asyncInjection("Dropped", CompletableFuture.completedFuture(null), invoked));
		org.mockito.Mockito.reset(scheduler);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AtomicReference<Runnable> droppedTask = new AtomicReference<>();
		doAnswer(invocation -> {
			droppedTask.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));

		CompletionStage<Void> result = shortTimeoutReward.giveInjectedRewardsAsync(user, new HashMap<>());

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		droppedTask.get().run();
		assertFalse(invoked.get());
	}

	@Test
	void injectionCompletionIsNotLimitedBySchedulerHandoffTimeout() throws Exception {
		Reward shortTimeoutReward = new Reward("AsyncReward", data) {
			@Override
			protected long getServerThreadDispatchTimeoutMillis() {
				return 25;
			}
		};
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(shortTimeoutReward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		CompletableFuture<Object> completion = new CompletableFuture<>();
		handler.getInjectedRewards().add(asyncInjection("SlowStorage", completion, null));

		CompletionStage<Void> result = shortTimeoutReward.giveInjectedRewardsAsync(user, new HashMap<>());
		Thread.sleep(75);
		assertFalse(result.toCompletableFuture().isDone());
		completion.complete(null);
		result.toCompletableFuture().join();
	}

	@Test
	void nestedPostRewardWaitsForChildRewardBeforeAdvancing() {
		handler = org.mockito.Mockito.spy(new RewardHandler(plugin));
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(user.getPlugin()).thenReturn(plugin);
		data.createSection("Rewards");
		CompletableFuture<Void> child = new CompletableFuture<>();
		doReturn(child).when(handler).giveRewardAsync(eq(user), any(ConfigurationSection.class), eq("Rewards"),
				any());
		List<String> events = new ArrayList<>();
		RewardSubRewards.register(handler, plugin);
		RewardInject after = new RewardInject("After") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("after-child");
				return null;
			}
		};
		after.postReward();
		handler.getInjectedRewards().add(after);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertFalse(result.toCompletableFuture().isDone());
		assertTrue(events.isEmpty());

		child.complete(null);
		result.toCompletableFuture().join();
		assertEquals(List.of("after-child"), events);
	}

	@Test
	void removedSubRewardsSectionCannotDropItsPendingNestedSnapshot() throws Exception {
		RewardSubRewards.register(handler, plugin);
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class,
				boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(new HashMap<>(), new HashMap<>(), false);
		@SuppressWarnings("unchecked")
		CompletionStage<List<String>> snapshot = Reward.replayNestedRewardSnapshot(plugin, new HashMap<>(),
				"nested-list:Rewards", List.of("First", "Second"), (Reward.ReplayState) replayState,
				"AsyncReward/0");
		snapshot.toCompletableFuture().join();
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
	}

	@Test
	void everyNestedRewardInjectorWaitsForItsSelectedChild() {
		handler = org.mockito.Mockito.spy(new RewardHandler(plugin));
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(user.getPlugin()).thenReturn(plugin);
		data.set("RandomReward", new ArrayList<>(List.of("child")));
		CompletableFuture<Void> child = new CompletableFuture<>();
		ArgumentCaptor<RewardOptions> childOptions = ArgumentCaptor.forClass(RewardOptions.class);
		doReturn(child).when(handler).giveRewardAsync(eq(user), eq("child"), childOptions.capture());
		List<String> events = new ArrayList<>();
		RewardRandomReward.register(handler, plugin);
		handler.getInjectedRewards().add(new RewardInject("After") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("after-child");
				return null;
			}
		}.postReward());

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertFalse(result.toCompletableFuture().isDone());
		assertTrue(events.isEmpty());
		assertNotNull(childOptions.getValue().getAsyncReplayState());
		assertTrue(childOptions.getValue().getAsyncReplayKey().endsWith("/selected:child"));
		child.complete(null);
		result.toCompletableFuture().join();
		assertEquals(List.of("after-child"), events);
	}

	@Test
	void randomRewardResumesItsSelectedChildAfterConfigurationRemoval() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		handler = org.mockito.Mockito.spy(new RewardHandler(plugin));
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(user.getPlugin()).thenReturn(plugin);
		data.set("RandomReward", new ArrayList<>(List.of("child")));
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")))
				.when(handler).giveRewardAsync(eq(user), eq("child"), any(RewardOptions.class));
		RewardRandomReward.register(handler, plugin);
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		Object initialState = state.newInstance(new HashMap<>(), new HashMap<>(), false);
		List<Reward.ReplayCheckpoint> checkpoints = new ArrayList<>();
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(initialState,
				(java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoints::add);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> first = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				initialState, "AsyncReward", "occurrence");
		ArgumentCaptor<Runnable> writes = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(writes.capture());
		verify(handler, never()).giveRewardAsync(eq(user), eq("child"), any(RewardOptions.class));

		writes.getValue().run();
		assertThrows(java.util.concurrent.CompletionException.class, () -> first.toCompletableFuture().join());
		assertEquals(1, checkpoints.size());
		assertTrue(checkpoints.get(0).getPlaceholders().keySet().stream()
				.anyMatch(key -> key.startsWith("__advancedcore_replay_selection_")));
		data.set("RandomReward", null);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user,
				checkpoints.get(0).getPlaceholders(), 0,
				state.newInstance(checkpoints.get(0).getReplayProgress(),
						checkpoints.get(0).getReplayRegistryFingerprints(), false),
				"AsyncReward", "occurrence");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		verify(handler, times(2)).giveRewardAsync(eq(user), eq("child"), any(RewardOptions.class));
	}

	@Test
	void javascriptChildCarriesSelectionAndReplayStateIntoItsCheckpoint() {
		ConfigurationSection javascript = data.createSection("Javascript");
		javascript.set("Enabled", true);
		javascript.set("Expression", "true");
		javascript.createSection("TrueRewards");
		RewardJavascript.register(handler, plugin);
		RewardOptions nestedOptions = new RewardOptions();

		try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
				org.mockito.Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
				(engine, context) -> when(engine.getBooleanValue("true")).thenReturn(true));
				MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
						org.mockito.Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
						(builder, context) -> {
							when(builder.getRewardOptions()).thenReturn(nestedOptions);
							when(builder.withPlaceHolder(any())).thenAnswer(invocation -> {
								nestedOptions.withPlaceHolder(invocation.getArgument(0));
								return builder;
							});
							when(builder.sendAsync(user)).thenReturn(CompletableFuture.completedFuture(null));
						})) {
			reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		}

		assertNotNull(nestedOptions.getAsyncReplayState());
		assertTrue(nestedOptions.getAsyncReplayKey().endsWith("/path:TrueRewards"));
		assertFalse(nestedOptions.getPlaceholders().isEmpty());
	}

	@Test
	void disabledJavascriptResumesItsPersistedSelectedChild() throws Exception {
		ConfigurationSection javascript = data.createSection("Javascript");
		javascript.set("Enabled", true);
		javascript.set("Expression", "true");
		javascript.createSection("TrueRewards");
		RewardJavascript.register(handler, plugin);
		AtomicInteger sends = new AtomicInteger();

		try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
				org.mockito.Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
				(engine, context) -> when(engine.getBooleanValue("true")).thenReturn(true));
				MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
						org.mockito.Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
						(builder, context) -> {
							RewardOptions nested = new RewardOptions();
							when(builder.getRewardOptions()).thenReturn(nested);
							when(builder.withPlaceHolder(any())).thenAnswer(invocation -> {
								nested.withPlaceHolder(invocation.getArgument(0));
								return builder;
							});
							when(builder.sendAsync(user)).thenAnswer(ignored -> sends.getAndIncrement() == 0
									? CompletableFuture.failedFuture(new IllegalStateException("temporary child failure"))
									: CompletableFuture.completedFuture(null));
						})) {
			Reward.RewardReplayFailure checkpoint = findCheckpoint(assertThrows(CompletionException.class,
					() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join()));
			javascript.set("Enabled", false);

			Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
			java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
			state.setAccessible(true);
			java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
					AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
			replay.setAccessible(true);
			CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user,
					checkpoint.getReplayPlaceholders(), 0,
					state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
					"AsyncReward");
			resumed.toCompletableFuture().join();

			assertEquals(2, sends.get());
			assertEquals(1, engines.constructed().size(), "a persisted selection must not rerun JavaScript");
		}
	}

	@Test
	void specialChanceChildCarriesSelectionAndReplayStateIntoItsCheckpoint() {
		ConfigurationSection specialChance = data.createSection("SpecialChance");
		specialChance.createSection("1");
		RewardSpecialChance.register(handler, plugin);
		RewardOptions nestedOptions = new RewardOptions();

		try (MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
				org.mockito.Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
				(builder, context) -> {
					when(builder.getRewardOptions()).thenReturn(nestedOptions);
					when(builder.withPlaceHolder(any())).thenAnswer(invocation -> {
						nestedOptions.withPlaceHolder(invocation.getArgument(0));
						return builder;
					});
					when(builder.sendAsync(user)).thenReturn(CompletableFuture.completedFuture(null));
				})) {
			reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		}

		assertNotNull(nestedOptions.getAsyncReplayState());
		assertTrue(nestedOptions.getAsyncReplayKey().endsWith("/path:1"));
		assertFalse(nestedOptions.getPlaceholders().isEmpty());
	}

	@Test
	void persistedReplayFailsWhenItsPlayerDisappearsBeforePreparation() {
		when(user.getPlayerName()).thenReturn("Departed");
		RewardOptions options = new RewardOptions();
		options.setAsyncReplayCheckpointConsumer(ignored -> { });

		try (org.mockito.MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer("Departed")).thenReturn(null);
			CompletionStage<Void> result = reward.giveRewardUserAsync(user, new HashMap<>(), options);
			CompletionException failure = assertThrows(CompletionException.class,
					() -> result.toCompletableFuture().join());
			assertTrue(failure.getCause().getMessage().contains("Player became unavailable"));
		}
	}

	@Test
	void replayCheckpointSkipsAlreadyAppliedInjectionAfterLaterFailure() throws Exception {
		AtomicInteger alreadyApplied = new AtomicInteger();
		AtomicBoolean fail = new AtomicBoolean(true);
		RewardInject applied = new RewardInject("Money") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				alreadyApplied.incrementAndGet();
				return "receipt-1";
			}
		};
		applied.asPlaceholder("receipt");
		handler.getInjectedRewards().add(applied);
		handler.getInjectedRewards().add(new RewardInject("Durable") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return fail.get() ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
						: CompletableFuture.completedFuture(null);
			}
		});
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class);
		replay.setAccessible(true);
		CompletionStage<Void> first = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0);
		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> first.toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertEquals(1, checkpoint.getCompletedInjectionCount());
		assertEquals("receipt-1", checkpoint.getReplayPlaceholders().get("receipt"));
		fail.set(false);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(),
				checkpoint.getCompletedInjectionCount());
		resumed.toCompletableFuture().join();
		assertEquals(1, alreadyApplied.get());
	}

	@Test
	void commandReplayRetriesOnlyTheFailedSuffix() throws Exception {
		ArrayList<String> dispatched = new ArrayList<>();
		AtomicBoolean failSecond = new AtomicBoolean(true);
		AtomicInteger expansion = new AtomicInteger();
		AtomicReference<List<String>> configuredTemplates = new AtomicReference<>(
				List.of("first %value%", "second %value%"));
		handler.getInjectedRewards().add(new RewardInject("Commands") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> commandPlaceholders) {
				List<String> templates = configuredTemplates.get();
				int currentExpansion = expansion.incrementAndGet();
				List<String> expanded = templates.stream()
						.map(template -> template.substring(0, template.indexOf(' ')) + "-" + currentExpansion).toList();
				return Reward.replayCommandSequence(plugin, commandPlaceholders, "console", templates, expanded,
						(command, ignoredIndex) -> {
							dispatched.add(command);
							return command.startsWith("second-") && failSecond.get()
									? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
									: CompletableFuture.completedFuture(null);
						}).thenApply(nothing -> null);
			}
		});

		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		failSecond.set(false);
		configuredTemplates.set(List.of("inserted %value%", "second %value%", "first %value%"));
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward,
				user, checkpoint.getReplayPlaceholders(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");
		resumed.toCompletableFuture().join();

		assertEquals(List.of("first-1", "second-1", "second-1"), dispatched);
	}

	@Test
	void replayProgressBeyondTheInjectorRegistryFailsClosed() throws Exception {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Only") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class);
		replay.setAccessible(true);
		CompletionStage<Void> result = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 2);

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		assertEquals(0, invoked.get());
	}

	@Test
	void persistedReplayRejectsAChangedInjectorRegistryBeforeAnyStageRuns() throws Exception {
		AtomicInteger applied = new AtomicInteger();
		AtomicInteger inserted = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Applied") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				applied.incrementAndGet();
				return null;
			}
		});
		handler.getInjectedRewards().add(new RewardInject("Fails") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
			}
		});

		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertFalse(checkpoint.getReplayRegistryFingerprints().isEmpty());

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> matchingRegistry = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");
		assertThrows(java.util.concurrent.CompletionException.class, () -> matchingRegistry.toCompletableFuture().join());
		assertEquals(1, applied.get());

		handler.getInjectedRewards().add(0, new RewardInject("Inserted") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				inserted.incrementAndGet();
				return null;
			}
		});
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(1, applied.get());
		assertEquals(0, inserted.get());
	}

	@Test
	void completedInjectorIsNotReplayedWhenItsConfigurationChangesDuringRecovery() throws Exception {
		AtomicInteger applied = new AtomicInteger();
		data.set("Applied", "before");
		handler.getInjectedRewards().add(new RewardInject("Applied") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				applied.incrementAndGet();
				return null;
			}
		});
		handler.getInjectedRewards().add(new RewardInject("Fails") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
			}
		});

		Reward.RewardReplayFailure checkpoint = findCheckpoint(assertThrows(
				java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join()));
		data.set("Applied", "after");
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");
		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(1, applied.get());
	}

	@Test
	void legacyCountOnlyReplayCheckpointDoesNotResumeAgainstAnUnknownRegistry() throws Exception {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Applied") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 1,
				state.newInstance(Map.of(), Map.of(), true), "AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(0, invoked.get());
	}

	@Test
	void persistedReplayDoesNotFallBackToSynchronousDispatchWhenAsyncInjectorsAreGone() {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Synchronous") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		RewardOptions options = new RewardOptions();
		options.setAsyncReplayProgress(Map.of("AsyncReward", 1));
		options.setAsyncReplayRegistryFingerprints(Map.of("AsyncReward", "removed-async-injector"));

		reward.giveRewardUser(user, new HashMap<>(), options);

		assertEquals(0, invoked.get());
	}

	@Test
	void publicRewardUserApiRetainsNullOptionsCompatibilityWithAsyncInjectors() {
		AtomicReference<RewardOptions> receivedOptions = new AtomicReference<>();
		handler.getInjectedRewards().add(new RewardInject("Async") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public boolean requiresConfiguredDataForAsync() { return false; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return CompletableFuture.completedFuture(null);
			}
		});
		Reward spyReward = org.mockito.Mockito.spy(reward);
		doAnswer(invocation -> {
			receivedOptions.set(invocation.getArgument(2));
			return CompletableFuture.completedFuture(null);
		}).when(spyReward).giveRewardUserAsync(eq(user), any(HashMap.class), any(RewardOptions.class));

		spyReward.giveRewardUser(user, new HashMap<>(), null);

		assertNotNull(receivedOptions.get());
	}

	@Test
	void sharedNestedReplayStatePreventsSynchronousFallbackWhenOptionMapsAreEmpty() throws Exception {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Synchronous") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		RewardOptions options = new RewardOptions();
		options.setAsyncReplayKey("AsyncReward/0/child");
		options.setAsyncReplayState((Reward.ReplayState) state.newInstance(Map.of("AsyncReward/0/child", 1),
				Map.of("AsyncReward/0/child", "removed-async-injector"), false));
		assertTrue(options.getAsyncReplayProgress().isEmpty());
		assertTrue(options.getAsyncReplayRegistryFingerprints().isEmpty());

		reward.giveRewardUser(user, new HashMap<>(), options);

		assertEquals(0, invoked.get());
	}

	@Test
	void childCheckpointBindsItsUncompletedParentRegistryBeforeNestedDispatch() throws Exception {
		AtomicBoolean dispatchingChild = new AtomicBoolean();
		AtomicInteger parentInvocations = new AtomicInteger();
		AtomicInteger insertedInvocations = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Nested") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward current, AdvancedCoreUser currentUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				if (dispatchingChild.get()) return CompletableFuture.completedFuture(null);
				parentInvocations.incrementAndGet();
				dispatchingChild.set(true);
				try {
					Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
					java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
							AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
					replay.setAccessible(true);
					CompletionStage<Void> child = (CompletionStage<Void>) replay.invoke(current, currentUser, new HashMap<>(), 0,
							Reward.currentReplayState(), Reward.currentReplayKey() + "/child");
					return child.whenComplete((ignored, failure) -> dispatchingChild.set(false)).thenApply(ignored -> null);
				} catch (ReflectiveOperationException failure) {
					return CompletableFuture.failedFuture(failure);
				}
			}
		});
		handler.getInjectedRewards().add(new RewardInject("ChildFailure") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return dispatchingChild.get() ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
						: CompletableFuture.completedFuture(null);
			}
		});

		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertTrue(checkpoint.getReplayRegistryFingerprints().containsKey("AsyncReward"));
		assertTrue(checkpoint.getReplayRegistryFingerprints().containsKey("AsyncReward/0/child"));

		handler.getInjectedRewards().add(0, new RewardInject("Inserted") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				insertedInvocations.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(1, parentInvocations.get());
		assertEquals(0, insertedInvocations.get());
	}

	@Test
	void replayProgressDoesNotSuppressASecondSameNamedChildOccurrence() throws Exception {
		AtomicInteger deliveries = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Deliver") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				deliveries.incrementAndGet();
				return CompletableFuture.completedFuture(null);
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(java.util.Map.class);
		constructor.setAccessible(true);
		Object state = constructor.newInstance((Object) null);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state,
				"root/Rewards/child:0")).toCompletableFuture().join();
		((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state,
				"root/Rewards/child:1")).toCompletableFuture().join();
		assertEquals(2, deliveries.get());
	}

	@Test
	void replaySelectionKeepsTheFirstNondeterministicChoice() {
		HashMap<String, String> placeholders = new HashMap<>();
		AtomicInteger selections = new AtomicInteger();
		assertEquals("first", Reward.replaySelection(placeholders,
				() -> selections.incrementAndGet() == 1 ? "first" : "second"));
		assertEquals("first", Reward.replaySelection(placeholders,
				() -> selections.incrementAndGet() == 1 ? "first" : "second"));
		assertEquals(1, selections.get());
	}

	@Test
	void independentAsyncRewardOccurrencesReceiveDifferentDurableIds() {
		List<String> occurrences = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("Capture") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				occurrences.add(Reward.currentReplayOccurrenceId());
				return CompletableFuture.completedFuture(null);
			}
		});

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();

		assertEquals(2, occurrences.size());
		assertNotEquals(occurrences.get(0), occurrences.get(1));
	}

	@Test
	void retryOfTheSameOccurrenceRetainsItsId() throws Exception {
		List<String> occurrences = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("Capture") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				occurrences.add(Reward.currentReplayOccurrenceId());
				return CompletableFuture.completedFuture(null);
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		for (int attempt = 0; attempt < 2; attempt++) {
			((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state.newInstance((Object) null),
					"AsyncReward", "occurrence-1")).toCompletableFuture().join();
		}

		assertEquals(List.of("occurrence-1", "occurrence-1"), occurrences);
	}

	@Test
	void nestedAsyncInjectorCanAwaitTheSameSharedInjectorWithoutDeadlocking() {
		AtomicInteger invocations = new AtomicInteger();
		RewardInject nested = new RewardInject("Nested") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public boolean supportsAsyncSynchronization() { return false; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward current, AdvancedCoreUser currentUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				if (invocations.incrementAndGet() == 1) {
					return current.giveInjectedRewardsAsync(currentUser, new HashMap<>()).thenApply(ignored -> null);
				}
				return CompletableFuture.completedFuture(null);
			}
		};
		nested.synchronize();
		handler.getInjectedRewards().add(nested);

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		assertEquals(2, invocations.get());
	}

	private RewardInject asyncInjection(String path, CompletionStage<Object> completion, AtomicBoolean invoked) {
		return new RewardInject(path) {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				if (invoked != null) invoked.set(true);
				return completion;
			}
		};
	}

	private Reward.RewardReplayFailure findCheckpoint(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof Reward.RewardReplayFailure) return (Reward.RewardReplayFailure) current;
		}
		throw new AssertionError("missing replay checkpoint", failure);
	}
}
