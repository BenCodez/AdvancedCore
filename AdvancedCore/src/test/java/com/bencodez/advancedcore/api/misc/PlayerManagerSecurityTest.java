package com.bencodez.advancedcore.api.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;

class PlayerManagerSecurityTest {

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
		PlayerManager.getInstance().plugin = plugin;

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
