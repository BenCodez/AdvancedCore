package com.bencodez.advancedcore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class CommandLoaderBulkPermissionTest {
	@Test void bulkSetDataAcknowledgesOnlyAfterSynchronousStorageWritesFinish() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserData data = mock(UserData.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getData()).thenReturn(data);
		doAnswer(call -> {
			@SuppressWarnings("unchecked") BiConsumer<UUID, ArrayList<Column>> each = call.getArgument(0);
			each.accept(uuid, new ArrayList<>());
			return null;
		}).when(users).forEachUserKeys(any(), any());
		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
				.when(scheduler).runTask(any(), any());
		PlayerCommandHandler command = (PlayerCommandHandler) find(new CommandLoader(plugin),
				"User", "(player)", "SetData", "(text)", "(text)");
		String[] args = {"User", "all", "SetData", "rank", "trusted"};
		command.executeAll(sender, args);
		assertEquals(1, workers.size());
		assertTrue(callbacks.isEmpty());
		workers.remove(0).run();
		verify(data).setString("rank", "trusted", false);
		assertEquals(1, callbacks.size());
		verify(sender, never()).sendMessage(any(String.class));
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Set all users rank"));
	}

	@Test
	void clearCacheReportsOnlyAfterCompletion() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		CompletableFuture<Void> completion = new CompletableFuture<>();
		when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
		when(users.getDataManager()).thenReturn(dataManager);
		when(dataManager.clearCacheAsyncCompletion()).thenReturn(completion);
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { callbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());
		CommandHandler command = find(new CommandLoader(plugin), "ClearCache");

		command.execute(sender, new String[] { "ClearCache" });
		verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.contains("Cache cleared"));
		assertTrue(callbacks.isEmpty());
		completion.complete(null);
		assertEquals(1, callbacks.size());
		verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.contains("Cache cleared"));
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Cache cleared"));
	}

	@Test
	void forceCacheReportsOnlyTheDeferredPopulationOutcome() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		CommandSender sender = mock(CommandSender.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
		when(users.getDataManager()).thenReturn(dataManager);
		ArrayList<java.util.function.Supplier<Boolean>> workers = new ArrayList<>();
		ArrayList<Consumer<Boolean>> successes = new ArrayList<>();
		ArrayList<Consumer<Throwable>> failures = new ArrayList<>();
		doAnswer(call -> {
			workers.add(call.getArgument(0));
			successes.add(call.getArgument(1));
			failures.add(call.getArgument(2));
			return true;
		}).when(dataManager).deferSharedStorageResult(any(), any(), any(),
				org.mockito.ArgumentMatchers.isNull());
		CommandLoader loader = new CommandLoader(plugin);

		loader.cacheUserAndReport(sender, "voter", user);
		verify(user, never()).cache();
		verify(sender, never()).sendMessage(any(String.class));

		Boolean populated = workers.remove(0).get();
		verify(user).cache();
		verify(sender, never()).sendMessage(any(String.class));
		successes.remove(0).accept(populated);
		failures.remove(0);
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Forced cached voter"));

		loader.cacheUserAndReport(sender, "broken", user);
		workers.remove(0);
		failures.remove(0).accept(new IllegalStateException("storage unavailable"));
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Unable to cache broken"));

		when(dataManager.deferSharedStorageResult(any(), any(), any(),
				org.mockito.ArgumentMatchers.isNull()))
				.thenThrow(new java.util.concurrent.RejectedExecutionException("manager stopped"));
		loader.cacheUserAndReport(sender, "stopped", user);
		verify(user, org.mockito.Mockito.times(1)).cache();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Unable to cache stopped"));
	}

	@Test
	void deferredCommandFailureDoesNotCallPlayerFromWorkerWhenEntitySchedulerStops() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = new UserDataManager(plugin);
		dataManager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		java.lang.reflect.Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(dataManager, worker);
		dataManager.bindSharedSqlBackend(mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class),
				(user, operation) -> operation.run());
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		Player sender = mock(Player.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		RejectedExecutionException rejected = new RejectedExecutionException("scheduler stopped");
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getDataManager()).thenReturn(dataManager);
		doAnswer(call -> { throw rejected; }).when(scheduler).runTask(any(), any(Runnable.class),
				org.mockito.ArgumentMatchers.same(sender));

		try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
			bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true, false);
			new CommandLoader(plugin).cacheUserAndReport(sender, "voter", user);
			ArgumentCaptor<Runnable> storage = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storage.capture());
			storage.getValue().run();
		}

		verify(user).cache();
		verify(sender, never()).sendMessage(any(String.class));
		assertSame(rejected, dataManager.getLastDeferredStorageFailure());
		dataManager.getTimer().shutdownNow();
	}

	@Test
	void totalUsersDefersStorageAndReturnsResultOrErrorOnTheMainScheduler() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getAllUUIDs()).thenReturn(new ArrayList<>(List.of("one", "two")));
		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> { callbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());
		CommandHandler command = find(new CommandLoader(plugin), "TotalNumberOfUsers");

		command.execute(sender, new String[] { "TotalNumberOfUsers" });
		verify(users, never()).getAllUUIDs();
		assertEquals(1, workers.size());
		workers.remove(0).run();
		verify(users).getAllUUIDs();
		assertEquals(1, callbacks.size());
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Total number of users: 2"));

		when(users.getAllUUIDs()).thenThrow(new IllegalStateException("storage unavailable"));
		command.execute(sender, new String[] { "TotalNumberOfUsers" });
		workers.remove(0).run();
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Unable to process user storage"));
	}

	@Test
	void runCmdAllAndGiveAllDeferEnumerationBeforeSchedulingBukkitActions() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		RewardHandler rewards = mock(RewardHandler.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getRewardHandler()).thenReturn(rewards);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		ArrayList<Runnable> workers = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		CommandLoader loader = new CommandLoader(plugin);

		find(loader, "RunCMD", "All", "(List)").execute(sender, new String[] { "RunCMD", "All", "say", "hi" });
		find(loader, "GiveAll", "(reward)").execute(sender, new String[] { "GiveAll", "daily" });
		verify(users, never()).forEachUserKeys(any(), any());
		assertEquals(2, workers.size());
		for (Runnable worker : List.copyOf(workers)) worker.run();
		verify(users, org.mockito.Mockito.times(2)).forEachUserKeys(any(), any());
	}

	@Test
	void giveAllUsesTheRecipientEntitySchedulerForOnlineUsers() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		RewardHandler rewards = mock(RewardHandler.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		Player recipient = mock(Player.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getRewardHandler()).thenReturn(rewards);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getUser(uuid, false)).thenReturn(user);
		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> globalCallbacks = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> { globalCallbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());
		doAnswer(call -> {
			@SuppressWarnings("unchecked") BiConsumer<UUID, ArrayList<Column>> perUser = call.getArgument(0, BiConsumer.class);
			perUser.accept(uuid, new ArrayList<>());
			return null;
		}).when(users).forEachUserKeys(any(), any());
		find(new CommandLoader(plugin), "GiveAll", "(reward)").execute(sender, new String[] { "GiveAll", "daily" });
		workers.remove(0).run();
		assertEquals(2, globalCallbacks.size());
		try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(recipient);
			globalCallbacks.remove(0).run();
		}
		verify(scheduler).runTask(any(), any(Runnable.class), org.mockito.ArgumentMatchers.same(recipient));
	}

	@Test
	void runCmdAllUsesTheGlobalSchedulerEvenForAPlayerIssuer() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		Player sender = mock(Player.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlayerName()).thenReturn("voter");
		ArrayList<Runnable> workers = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> {
			@SuppressWarnings("unchecked") BiConsumer<UUID, ArrayList<Column>> perUser = call.getArgument(0, BiConsumer.class);
			perUser.accept(uuid, new ArrayList<>());
			return null;
		}).when(users).forEachUserKeys(any(), any());

		find(new CommandLoader(plugin), "RunCMD", "All", "(List)")
				.execute(sender, new String[] { "RunCMD", "All", "say", "hi" });
		workers.remove(0).run();

		verify(scheduler).runTask(any(), any(Runnable.class));
		verify(scheduler, never()).runTask(any(), any(Runnable.class), org.mockito.ArgumentMatchers.same(sender));
	}

	@Test
	void remainingBulkCommandsDoNotTouchWorkerOnlyStorageBeforeScheduling() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		RewardHandler rewards = mock(RewardHandler.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		AdvancedCoreUser offlineUser = mock(AdvancedCoreUser.class);
		UUID offlineUuid = UUID.randomUUID();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getRewardHandler()).thenReturn(rewards);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getOfflineRewardsPath()).thenReturn("OfflineRewards");
		when(users.getUser(offlineUuid, false)).thenReturn(offlineUser);
		when(offlineUser.forceRunOfflineRewardsAsync()).thenReturn(CompletableFuture.completedFuture(null));
		ArrayList<Runnable> workers = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> {
			@SuppressWarnings("unchecked") BiConsumer<UUID, ArrayList<Column>> perUser = call.getArgument(0, BiConsumer.class);
			perUser.accept(offlineUuid, new ArrayList<>());
			return null;
		}).when(users).forEachUserKeys(any(), any());
		CommandLoader loader = new CommandLoader(plugin);
		find(loader, "ClearOfflineRewards").execute(sender, new String[] { "ClearOfflineRewards" });
		find(loader, "ForceRunOfflineRewards").execute(sender, new String[] { "ForceRunOfflineRewards" });
		((PlayerCommandHandler) find(loader, "User", "(Player)", "ForceReward", "(Reward)"))
				.executeAll(sender, new String[] { "User", "all", "ForceReward", "daily" });
		((PlayerCommandHandler) find(loader, "User", "(player)", "SetData", "(text)", "(text)"))
				.executeAll(sender, new String[] { "User", "all", "SetData", "rank", "trusted" });
		verify(users, never()).removeAllKeyValues(any(), any());
		verify(users, never()).forEachUserKeys(any(), any());
		assertEquals(4, workers.size());
		workers.get(1).run();
		verify(offlineUser).forceRunOfflineRewardsAsync();
	}

	@Test
	void purgeRunsOnTheStorageWorkerAndReportsCompletionOnTheCommandScheduler() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> { callbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());

		find(new CommandLoader(plugin), "Purge").execute(sender, new String[] { "Purge" });
		verify(users, never()).purgeOldPlayersNow();
		workers.remove(0).run();
		verify(users).purgeOldPlayersNow();
		verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.contains("Purged data"));
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Purged data"));
	}

	@Test
	void temporaryPermissionCommandsResolveAsynchronouslyAndRouteMessagesBack() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		PermissionHandler permissions = mock(PermissionHandler.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getPermissionHandler()).thenReturn(permissions);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(user.getUUID()).thenReturn(uuid.toString());
		doAnswer(call -> {
			@SuppressWarnings("unchecked") Consumer<AdvancedCoreUser> success = call.getArgument(1, Consumer.class);
			success.accept(user);
			return null;
		}).when(users).getUserAsync(org.mockito.ArgumentMatchers.eq("voter"), any(), any());
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { callbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());
		CommandLoader loader = new CommandLoader(plugin);

		find(loader, "User", "(Player)", "RemoveTempPermissions")
				.execute(sender, new String[] { "User", "voter", "RemoveTempPermissions" });
		find(loader, "User", "(Player)", "AddTempPermissions", "(Text)")
				.execute(sender, new String[] { "User", "voter", "AddTempPermissions", "example.use" });
		find(loader, "User", "(Player)", "AddTempPermissions", "(Text)", "(Number)")
				.execute(sender, new String[] { "User", "voter", "AddTempPermissions", "example.use", "60" });
		verify(permissions, never()).removePermission(any());
		verify(permissions, never()).addPermission(any(UUID.class), any(String.class));

		try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(null);
			for (int index = 0; index < 3; index++) callbacks.remove(0).run();
		}
		verify(permissions).removePermission(uuid);
		verify(permissions).addPermission(uuid, "example.use");
		verify(permissions).addPermission(uuid, "example.use", 60);
		assertEquals(3, callbacks.size());
		for (Runnable callback : List.copyOf(callbacks)) callback.run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Removed temporary permissions"));
		verify(sender, org.mockito.Mockito.times(2))
				.sendMessage(org.mockito.ArgumentMatchers.contains("Added temporary permission"));
	}

	@Test
	void forcedReplayReportsCompletionOnlyAfterEveryReplayFinishes() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		CommandSender sender = mock(CommandSender.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UUID uuid = UUID.randomUUID();
		CompletableFuture<Void> replay = new CompletableFuture<>();
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getUser(uuid, false)).thenReturn(user);
		when(user.forceRunOfflineRewardsAsync()).thenReturn(replay);
		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		doAnswer(call -> { workers.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> { callbacks.add(call.getArgument(1, Runnable.class)); return null; })
				.when(scheduler).runTask(any(), any());
		doAnswer(call -> {
			@SuppressWarnings("unchecked") BiConsumer<UUID, ArrayList<Column>> perUser = call.getArgument(0, BiConsumer.class);
			perUser.accept(uuid, new ArrayList<>());
			@SuppressWarnings("unchecked") Consumer<Integer> onFinished = call.getArgument(1, Consumer.class);
			onFinished.accept(1);
			return null;
		}).when(users).forEachUserKeys(any(), any());

		find(new CommandLoader(plugin), "ForceRunOfflineRewards")
				.execute(sender, new String[] { "ForceRunOfflineRewards" });
		workers.remove(0).run();
		verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.contains("Finished running offline rewards"));
		assertTrue(callbacks.isEmpty());

		replay.complete(null);
		assertEquals(1, callbacks.size());
		callbacks.remove(0).run();
		verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Finished running offline rewards"));
	}

	private static CommandHandler find(CommandLoader loader, String... args) {
		return loader.getBasicAdminCommands("Example").stream()
				.filter(handler -> Arrays.equals(handler.getArgs(), args)).findFirst().orElseThrow();
	}

	@Test
	void setDataBulkUsesTheSharedBaseAndAllAuthorization() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager userManager = mock(UserManager.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isMultiplePermissionChecks()).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(userManager);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(1).run();
			return null;
		}).when(scheduler).runTaskAsynchronously(any(), any());
		Player sender = mock(Player.class);
		when(sender.hasPermission("Example.SetData")).thenReturn(true);
		when(sender.hasPermission("Example.SetData.All")).thenReturn(true);

		java.util.List<CommandHandler> commands = new CommandLoader(plugin).getBasicAdminCommands("Example");
		assertFalse(commands.stream().anyMatch(handler -> Arrays.equals(handler.getArgs(),
				new String[] { "User", "All", "SetData", "(text)", "(text)" })),
				"a literal all handler would bypass the shared PlayerCommandHandler gate");
		CommandHandler command = commands.stream()
				.filter(handler -> Arrays.equals(handler.getArgs(),
						new String[] { "User", "(player)", "SetData", "(text)", "(text)" }))
				.findFirst().orElseThrow();
		assertTrue(command instanceof PlayerCommandHandler);
		com.bencodez.simpleapi.command.TabCompleteHandler.getInstance()
				.addTabCompleteOption("(player)");
		com.bencodez.simpleapi.command.TabCompleteHandler.getInstance().addTabCompleteOption("(text)");
		assertTrue(command.argsMatch("__advancedcore_all_selector__", 1));

		try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.Collections.emptyList());
			assertTrue(command.runCommand(sender,
					new String[] { "User", "all", "SetData", "rank", "trusted" }));
		}

		verify(userManager).forEachUserKeys(any(), any());
	}

	@Test
	void legacySetAllDataPermissionStillAuthorizesOnlyTheBulkTarget() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager userManager = mock(UserManager.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isMultiplePermissionChecks()).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(userManager);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(1).run();
			return null;
		}).when(scheduler).runTaskAsynchronously(any(), any());
		Player sender = mock(Player.class);
		when(sender.hasPermission("Example.SetAllData")).thenReturn(true);
		PlayerCommandHandler command = (PlayerCommandHandler) new CommandLoader(plugin).getBasicAdminCommands("Example")
				.stream().filter(handler -> Arrays.equals(handler.getArgs(),
						new String[] { "User", "(player)", "SetData", "(text)", "(text)" }))
				.findFirst().orElseThrow();
		com.bencodez.simpleapi.command.TabCompleteHandler.getInstance()
				.addTabCompleteOption("(player)");
		com.bencodez.simpleapi.command.TabCompleteHandler.getInstance().addTabCompleteOption("(text)");
		assertTrue(command.argsMatch("__advancedcore_all_selector__", 1));

		try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.Collections.emptyList());
			assertTrue(command.runCommand(sender,
					new String[] { "User", "all", "SetData", "rank", "trusted" }));
		}

		verify(userManager).forEachUserKeys(any(), any());
		assertFalse(command.hasPerm(sender));
	}
}
