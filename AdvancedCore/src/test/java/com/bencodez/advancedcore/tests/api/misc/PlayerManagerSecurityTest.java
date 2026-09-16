package com.bencodez.advancedcore.tests.api.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class PlayerManagerSecurityTest {
	@Test
	void asynchronousValidationUsesPersistedBedrockResolution() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		PlayerManager.getInstance().setPlugin(plugin);
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result(".StoredBedrock", true, "db-bedrock-prefixed-variant"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("StoredBedrock"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		AtomicReference<Boolean> valid = new AtomicReference<>();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("StoredBedrock")).thenReturn(null);
			PlayerManager.getInstance().isValidUserAsync("StoredBedrock", false, valid::set,
					failure -> org.junit.jupiter.api.Assertions.fail(failure));
		}

		assertTrue(valid.get());
		org.mockito.Mockito.verify(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("StoredBedrock"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void legacyValidationKeepsPersistedResolutionOffTheSharedPrimaryPath() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		when(users.userExist("StoredBedrock")).thenReturn(false);
		when(dataManager.mustDeferSharedStorageAccess()).thenReturn(false);
		when(resolver.isBedrock("StoredBedrock")).thenReturn(true);
		PlayerManager.getInstance().setPlugin(plugin);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("StoredBedrock")).thenReturn(null);
			assertTrue(PlayerManager.getInstance().isValidUser("StoredBedrock", false));
		}

		org.mockito.Mockito.verify(resolver).isBedrock("StoredBedrock");
		org.mockito.Mockito.verify(resolver, org.mockito.Mockito.never()).resolveWithoutDb("StoredBedrock");
	}

	@Test
	void legacyValidationUsesOnlyExactCacheEvidenceOnSharedPrimaryThread() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		when(dataManager.mustDeferSharedStorageAccess()).thenReturn(true);
		when(resolver.resolveWithoutDb("CachedJava"))
				.thenReturn(new BedrockNameResolver.Result("CachedJava", false, "cache-java"));
		PlayerManager.getInstance().setPlugin(plugin);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("CachedJava")).thenReturn(null);
			assertTrue(PlayerManager.getInstance().isValidUser("CachedJava", false));
		}

		verify(users, never()).userExist(org.mockito.ArgumentMatchers.anyString());
		verify(resolver).resolveWithoutDb("CachedJava");
	}

	@Test
	void asynchronousServerHistoryUsesProfileUpdateAndUuidLookup() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		PlayerProfile resolvedProfile = mock(PlayerProfile.class);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		UUID uuid = UUID.randomUUID();
		CompletableFuture<PlayerProfile> update = new CompletableFuture<>();
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(users.getDataManager()).thenReturn(dataManager);
		when(options.getBedrockPlayerPrefix()).thenReturn(".");
		when(pendingProfile.update()).thenReturn(update);
		when(resolvedProfile.getUniqueId()).thenReturn(uuid);
		when(offline.hasPlayedBefore()).thenReturn(true);
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result("NewJava", false, "none"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("NewJava"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		ArrayList<Runnable> callbacks = new ArrayList<>();
		org.mockito.Mockito.doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
				.when(scheduler).runTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		PlayerManager.getInstance().setPlugin(plugin);
		AtomicReference<Boolean> valid = new AtomicReference<>();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("NewJava")).thenReturn(null);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(() -> Bukkit.createPlayerProfile("NewJava")).thenReturn(pendingProfile);
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);
			PlayerManager.getInstance().isValidUserAsync("NewJava", true, valid::set,
					failure -> org.junit.jupiter.api.Assertions.fail(failure));
			assertTrue(valid.get() == null);
			bukkit.verify(() -> Bukkit.getOfflinePlayer("NewJava"), never());
			update.complete(resolvedProfile);
			assertTrue(valid.get() == null);
			callbacks.remove(0).run();
			assertTrue(valid.get());
			bukkit.verify(() -> Bukkit.getOfflinePlayer(uuid));
		}
	}

	@Test
	void asynchronousServerHistoryUsesOfflineUuidWithoutProfileLookup() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		ArrayList<Runnable> callbacks = new ArrayList<>();
		UUID offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:OfflineUser".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(options.isOnlineMode()).thenReturn(false);
		when(options.getBedrockPlayerPrefix()).thenReturn(".");
		when(offline.hasPlayedBefore()).thenReturn(true);
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result("OfflineUser", false, "none"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("OfflineUser"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
				.when(scheduler).runTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		PlayerManager.getInstance().setPlugin(plugin);
		AtomicReference<Boolean> valid = new AtomicReference<>();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("OfflineUser")).thenReturn(null);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(() -> Bukkit.getOfflinePlayer(offlineUuid)).thenReturn(offline);
			PlayerManager.getInstance().isValidUserAsync("OfflineUser", true, valid::set,
					failure -> org.junit.jupiter.api.Assertions.fail(failure));
			assertEquals(1, callbacks.size());
			bukkit.verify(() -> Bukkit.createPlayerProfile(org.mockito.ArgumentMatchers.anyString()), never());
			callbacks.remove(0).run();
			assertTrue(valid.get());
			bukkit.verify(() -> Bukkit.getOfflinePlayer(offlineUuid));
		}
	}

	@Test
	void asynchronousServerHistoryDoesNotTouchBukkitAfterShutdown() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		PlayerProfile resolvedProfile = mock(PlayerProfile.class);
		CompletableFuture<PlayerProfile> update = new CompletableFuture<>();
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(options.isOnlineMode()).thenReturn(true);
		when(options.getBedrockPlayerPrefix()).thenReturn(".");
		when(pendingProfile.update()).thenReturn(update);
		when(resolvedProfile.getUniqueId()).thenReturn(UUID.randomUUID());
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result("NewJava", false, "none"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("NewJava"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		PlayerManager.getInstance().setPlugin(plugin);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("NewJava")).thenReturn(null);
			bukkit.when(() -> Bukkit.createPlayerProfile("NewJava")).thenReturn(pendingProfile);
			bukkit.when(Bukkit::getServer).thenReturn(null);
			PlayerManager.getInstance().isValidUserAsync("NewJava", true,
					ignored -> org.junit.jupiter.api.Assertions.fail("unexpected success"), failure::set);
			update.complete(resolvedProfile);
			assertTrue(failure.get() instanceof IllegalStateException);
			bukkit.verify(() -> Bukkit.getOfflinePlayer(org.mockito.ArgumentMatchers.any(UUID.class)), never());
			verify(scheduler, never()).runTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		}
	}

	@Test
	void queuedServerHistoryRechecksShutdownBeforeBukkitLookup() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		PlayerProfile resolvedProfile = mock(PlayerProfile.class);
		CompletableFuture<PlayerProfile> update = new CompletableFuture<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(options.isOnlineMode()).thenReturn(true);
		when(options.getBedrockPlayerPrefix()).thenReturn(".");
		when(pendingProfile.update()).thenReturn(update);
		when(resolvedProfile.getUniqueId()).thenReturn(UUID.randomUUID());
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result("NewJava", false, "none"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("NewJava"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
				.when(scheduler).runTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		PlayerManager.getInstance().setPlugin(plugin);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("NewJava")).thenReturn(null);
			bukkit.when(() -> Bukkit.createPlayerProfile("NewJava")).thenReturn(pendingProfile);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class), (Server) null);
			PlayerManager.getInstance().isValidUserAsync("NewJava", true,
					ignored -> org.junit.jupiter.api.Assertions.fail("unexpected success"), failure::set);
			update.complete(resolvedProfile);
			assertEquals(1, callbacks.size());
			callbacks.remove(0).run();
			assertTrue(failure.get() instanceof IllegalStateException);
			bukkit.verify(() -> Bukkit.getOfflinePlayer(org.mockito.ArgumentMatchers.any(UUID.class)), never());
		}
	}

	@Test
	void asynchronousServerHistoryRoutesBukkitFailure() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BedrockNameResolver resolver = mock(BedrockNameResolver.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		PlayerProfile resolvedProfile = mock(PlayerProfile.class);
		CompletableFuture<PlayerProfile> update = new CompletableFuture<>();
		ArrayList<Runnable> callbacks = new ArrayList<>();
		UUID uuid = UUID.randomUUID();
		when(plugin.getBedrockHandle()).thenReturn(resolver);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(options.isOnlineMode()).thenReturn(true);
		when(options.getBedrockPlayerPrefix()).thenReturn(".");
		when(pendingProfile.update()).thenReturn(update);
		when(resolvedProfile.getUniqueId()).thenReturn(uuid);
		org.mockito.Mockito.doAnswer(call -> {
			@SuppressWarnings("unchecked") java.util.function.Consumer<BedrockNameResolver.Result> success =
					call.getArgument(1);
			success.accept(new BedrockNameResolver.Result("NewJava", false, "none"));
			return null;
		}).when(resolver).resolveAsync(org.mockito.ArgumentMatchers.eq("NewJava"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
				.when(scheduler).runTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		PlayerManager.getInstance().setPlugin(plugin);
		AtomicReference<Boolean> success = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		IllegalStateException historyFailure = new IllegalStateException("history unavailable");

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("NewJava")).thenReturn(null);
			bukkit.when(() -> Bukkit.createPlayerProfile("NewJava")).thenReturn(pendingProfile);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenThrow(historyFailure);
			PlayerManager.getInstance().isValidUserAsync("NewJava", true, success::set, failure::set);
			update.complete(resolvedProfile);
			callbacks.remove(0).run();
			assertTrue(success.get() == null);
			assertTrue(failure.get() == historyFailure);
		}
	}

	@AfterEach
	void tearDown() {
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	void nameFallbackRequiresMatchingUuid() {
		UUID requestedUuid = UUID.randomUUID();
		Player differentPlayer = mock(Player.class);
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);

		when(differentPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
		when(differentPlayer.hasPermission("reward.permission")).thenReturn(true);
		when(plugin.getOptions()).thenReturn(options);
		AdvancedCorePlugin.setInstance(plugin);
		PlayerManager.getInstance().setPlugin(plugin);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(requestedUuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getPlayer("stored-name")).thenReturn(differentPlayer);

			assertFalse(PlayerManager.getInstance().hasServerPermission(requestedUuid, "stored-name",
					"reward.permission"));
		}
	}

	@Test
	void nameFallbackAllowsMatchingUuid() {
		UUID requestedUuid = UUID.randomUUID();
		Player requestedPlayer = mock(Player.class);

		when(requestedPlayer.getUniqueId()).thenReturn(requestedUuid);
		when(requestedPlayer.hasPermission("reward.permission")).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(requestedUuid)).thenReturn(null);
			bukkit.when(() -> Bukkit.getPlayer("stored-name")).thenReturn(requestedPlayer);

			assertTrue(PlayerManager.getInstance().hasServerPermission(requestedUuid, "stored-name",
					"reward.permission"));
		}
	}
}
