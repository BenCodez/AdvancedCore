package com.bencodez.advancedcore.api.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;

class PlayerCommandHandlerTest {

	@Test
	void forceConsoleConstructorForwardsFlag() {
		TestHandler handler = handler(true);

		assertTrue(handler.isForceConsole());
	}

	@Test
	void bulkTargetRequiresDedicatedPermission() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("example.command")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));

		when(sender.hasPermission("example.command.All")).thenReturn(true);
		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void administratorAlternativeStillAuthorizesBulkTarget() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("example.admin")).thenReturn(true);

		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void exposesBulkPermissionForPermissionListings() {
		assertEquals(java.util.Collections.singletonList("example.command.All"),
				handler(false).getAdditionalPermissions());
	}

	private TestHandler handler(boolean forceConsole) {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isMultiplePermissionChecks()).thenReturn(true);
		return new TestHandler(plugin, forceConsole);
	}

	private static final class TestHandler extends PlayerCommandHandler {
		private TestHandler(AdvancedCorePlugin plugin, boolean forceConsole) {
			super(plugin, new String[] { "user", "(player)" }, "example.command|example.admin", "help", true,
					forceConsole);
		}

		@Override
		public void executeAll(CommandSender sender, String[] args) {
		}

		@Override
		public void executeSinglePlayer(CommandSender sender, String[] args) {
		}
	}
}
