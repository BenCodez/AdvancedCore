package com.bencodez.advancedcore.tests.api.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;

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
