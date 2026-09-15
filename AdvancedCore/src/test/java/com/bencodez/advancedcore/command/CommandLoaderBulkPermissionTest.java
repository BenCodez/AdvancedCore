package com.bencodez.advancedcore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class CommandLoaderBulkPermissionTest {
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
		verify(offlineUser).forceRunOfflineRewards();
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
