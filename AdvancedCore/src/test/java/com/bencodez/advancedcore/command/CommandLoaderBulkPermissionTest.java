package com.bencodez.advancedcore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class CommandLoaderBulkPermissionTest {
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
