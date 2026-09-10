package com.bencodez.advancedcore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

class CommandLoaderBulkPermissionTest {
	@Test
	void setDataBulkUsesTheSharedBaseAndAllAuthorization() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		UserManager userManager = mock(UserManager.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isMultiplePermissionChecks()).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(userManager);
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

		command.execute(sender, new String[] { "User", "all", "SetData", "rank", "trusted" });

		verify(userManager).forEachUserKeys(any(), any());
	}
}
