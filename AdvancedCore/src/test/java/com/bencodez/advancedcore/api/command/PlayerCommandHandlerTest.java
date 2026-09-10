package com.bencodez.advancedcore.api.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.simpleapi.command.TabCompleteHandler;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class PlayerCommandHandlerTest {
	@BeforeAll
	static void registerPlayerArgument() {
		TabCompleteHandler.getInstance().addTabCompleteOption("(player)", "all", "Alex");
	}

	@Test
	void forceConsoleConstructorForwardsFlag() {
		TestHandler handler = handler(true);

		assertTrue(handler.isForceConsole());
	}

	@Test
	void bulkTargetRequiresDedicatedPermission() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));

		when(sender.hasPermission("example.command.All")).thenReturn(true);
		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void administratorAlternativeStillAuthorizesBulkTarget() {
		TestHandler handler = handler(false).withOverrides("example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.admin")).thenReturn(true);

		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void administratorAlternativeHonorsMultiplePermissionSetting() {
		TestHandler handler = handler(false, false).withOverrides("example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.admin")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));
	}

	@Test
	void primaryAdministratorOverrideStillAuthorizesWhenMultipleChecksDisabled() {
		TestHandler handler = handler(false, false, "example.admin|example.command").withOverrides("example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.admin")).thenReturn(true);

		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void exposesBulkPermissionForPermissionListings() {
		assertEquals(java.util.Collections.singletonList("example.command.All"),
				handler(false).withOverrides("example.admin").getAdditionalPermissions());
	}

	@Test
	void alternativeGranularPermissionNeedsItsOwnBulkPermission() {
		TestHandler handler = handler(false, true, "example.command|example.alternate");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.alternate")).thenReturn(true);

		assertEquals(java.util.List.of("example.command.All", "example.alternate.All"),
				handler.getAdditionalPermissions());
		assertFalse(handler.hasAllPermission(sender));

		when(sender.hasPermission("example.alternate.All")).thenReturn(true);
		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void bulkPermissionCannotCombineDifferentPermissionAlternatives() {
		TestHandler handler = handler(false, true, "example.command|example.alternate");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.alternate")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));

		when(sender.hasPermission("example.alternate.All")).thenReturn(true);
		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void disabledMultipleChecksIgnoreSecondaryGranularBulkPermission() {
		TestHandler handler = handler(false, false, "example.command|example.alternate");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.alternate.All")).thenReturn(true);

		assertEquals(java.util.List.of("example.command.All"), handler.getAdditionalPermissions());
		assertFalse(handler.hasAllPermission(sender));
	}

	@Test
	void soleConfiguredOverrideCanAuthorizeBulkWhenMultipleChecksEnabled() {
		TestHandler handler = handler(false, true, "example.admin").withOverrides("example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.admin")).thenReturn(true);

		assertEquals(java.util.Collections.emptyList(), handler.getAdditionalPermissions());
		assertTrue(handler.hasAllPermission(sender));
	}

	@Test
	void allPermissionWithoutBasePermissionDoesNotAuthorizeBulk() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));
	}

	@Test
	void legacyCombinedBulkAliasDoesNotGrantNamedPlayerAccess() {
		TestHandler handler = handler(false).withLegacyAliases("example.legacyAll");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.legacyAll")).thenReturn(true);

		assertTrue(handler.hasAllPermission(sender));
		assertFalse(handler.hasPerm(sender));
		assertEquals(java.util.List.of("example.command.All", "example.admin.All", "example.legacyAll"),
				handler.getAdditionalPermissions());
	}

	@Test
	void dispatchRejectsAllPermissionWithoutBasePermission() {
		TestContext context = context(true);
		TestHandler handler = new TestHandler(context.plugin, false, "example.command|example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer("all")).thenReturn(null);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Collections.emptySet());
			assertTrue(handler.runCommand(sender, new String[] { "user", "all" }));
		}

		verify(context.scheduler, never()).runTaskAsynchronously(eq(context.plugin), org.mockito.ArgumentMatchers.any());
		verify(sender).sendMessage("§cDenied");
		assertEquals(0, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void dispatchExecutesAuthorizedBulkExactlyOnce() {
		TestContext context = context(true);
		TestHandler handler = new TestHandler(context.plugin, false, "example.command|example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer("all")).thenReturn(null);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Collections.emptySet());
			assertTrue(handler.runCommand(sender, new String[] { "user", "all" }));
		}
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(context.scheduler).runTaskAsynchronously(eq(context.plugin), task.capture());
		task.getValue().run();

		assertEquals(1, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
		assertEquals("all", handler.lastBulkTarget);
	}

	@Test
	void dispatchPreservesAllBeforePartialPlayerNameNormalization() {
		TestContext context = context(true);
		TestHandler handler = new TestHandler(context.plugin, false, "example.command|example.admin");
		CommandSender sender = mock(Player.class);
		Player partialMatch = mock(Player.class);
		when(partialMatch.getName()).thenReturn("Sally");
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(anyString())).thenReturn(null);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(partialMatch));
			assertTrue(handler.runCommand(sender, new String[] { "user", "all" }));
		}
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(context.scheduler).runTaskAsynchronously(eq(context.plugin), task.capture());
		task.getValue().run();

		assertEquals(1, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void wrongCommandsAllPermissionDoesNotAuthorizeBulk() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("other.command.All")).thenReturn(true);

		assertFalse(handler.hasAllPermission(sender));
	}

	@Test
	void mixedCaseAllExecutesBulkExactlyOnce() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		handler.execute(sender, new String[] { "user", "ALL" });

		assertEquals(1, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void deniedBulkUsesConfiguredNoPermissionMessageWithoutSideEffects() {
		TestContext context = context(true);
		TestHandler handler = new TestHandler(context.plugin, false, "example.command|example.admin");
		CommandSender sender = mock(Player.class);
		when(sender.hasPermission("example.command")).thenReturn(true);

		handler.execute(sender, new String[] { "user", "all" });

		assertEquals(0, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
		verify(sender).sendMessage("§cDenied");
	}

	@Test
	void namedPlayerExecutionIsUnchanged() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(Player.class);

		handler.execute(sender, new String[] { "user", "Alex" });

		assertEquals(0, handler.allExecutions);
		assertEquals(1, handler.singleExecutions);
		verify(sender, never()).sendMessage(anyString());
	}

	@Test
	void shortArgumentsAndMissingPlayerSchemaFailClosed() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(CommandSender.class);

		handler.execute(sender, new String[] { "user" });
		handler.execute(sender, new String[] { "user", null });
		handler.setArgs(new String[] { "user" });
		handler.execute(sender, new String[] { "user", "all" });

		assertEquals(0, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void directBulkExecutionRejectsArgumentsShorterThanSchema() {
		TestContext context = context(true);
		TestHandler handler = new TestHandler(context.plugin, false, "example.command",
				new String[] { "user", "(player)", "mode", "(number)" });
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		handler.execute(sender, new String[] { "user", "all" });

		assertEquals(0, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void setArgsRecomputesPlayerIndexForNewSchema() {
		TestHandler handler = handler(false);
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("example.command")).thenReturn(true);
		when(sender.hasPermission("example.command.All")).thenReturn(true);

		handler.setArgs(new String[] { "user", "mode", "(player)" });
		handler.execute(sender, new String[] { "user", "mode", "all" });

		assertEquals(1, handler.allExecutions);
		assertEquals(0, handler.singleExecutions);
	}

	@Test
	void invalidPermissionMetadataFailsClosed() {
		TestHandler handler = handler(false, true, "example.command||example.admin")
				.withOverrides("example.admin");
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(anyString())).thenReturn(true);

		assertEquals(java.util.Collections.emptyList(), handler.getAdditionalPermissions());
		assertEquals(java.util.Collections.emptyList(), handler.getAllPermissionOverrides());
		assertFalse(handler.hasAllPermission(sender));
	}

	@Test
	void nullPermissionMetadataFailsClosedBeforeBaseAuthorization() {
		TestHandler handler = handler(false);
		handler.setPerm(null);
		CommandSender sender = mock(CommandSender.class);

		assertFalse(handler.hasAllPermission(sender));
		verify(sender, never()).hasPermission(anyString());
	}

	private TestHandler handler(boolean forceConsole) {
		return handler(forceConsole, true);
	}

	private TestHandler handler(boolean forceConsole, boolean multiplePermissions) {
		return handler(forceConsole, multiplePermissions, "example.command|example.admin");
	}

	private TestHandler handler(boolean forceConsole, boolean multiplePermissions, String permission) {
		TestContext context = context(multiplePermissions);
		return new TestHandler(context.plugin, forceConsole, permission);
	}

	private TestContext context(boolean multiplePermissions) {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("PlayerCommandHandlerTest"));
		when(options.isMultiplePermissionChecks()).thenReturn(multiplePermissions);
		when(options.getFormatNoPerms()).thenReturn("&cDenied");
		return new TestContext(plugin, scheduler);
	}

	private record TestContext(AdvancedCorePlugin plugin, BukkitScheduler scheduler) {
	}

	private static final class TestHandler extends PlayerCommandHandler {
		private int allExecutions;
		private int singleExecutions;
		private String lastBulkTarget;

		private TestHandler(AdvancedCorePlugin plugin, boolean forceConsole, String permission) {
			this(plugin, forceConsole, permission, new String[] { "user", "(player)" });
		}

		private TestHandler(AdvancedCorePlugin plugin, boolean forceConsole, String permission, String[] args) {
			super(plugin, args, permission, "help", true, forceConsole);
		}

		private TestHandler withOverrides(String... permissions) {
			withAllPermissionOverrides(permissions);
			return this;
		}

		private TestHandler withLegacyAliases(String... permissions) {
			withLegacyAllPermissionAliases(permissions);
			return this;
		}

		@Override
		public void executeAll(CommandSender sender, String[] args) {
			allExecutions++;
			lastBulkTarget = args[1];
		}

		@Override
		public void executeSinglePlayer(CommandSender sender, String[] args) {
			singleExecutions++;
		}
	}
}
