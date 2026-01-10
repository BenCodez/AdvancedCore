package com.bencodez.advancedcore.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent;

public class NLoginAuthenticate implements Listener {
	private AdvancedCorePlugin plugin;

	public NLoginAuthenticate(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void nLoginLogin(AuthenticateEvent event) {
		plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, () -> {
			Player player = event.getPlayer();
			if (player == null) {
				return;
			}

			if (!plugin.getOptions().isWaitUntilLoggedIn()) {
				return;
			}

			String resolvedUuid = plugin.getOptions().isOnlineMode()
					? player.getUniqueId().toString()
					: UuidLookup.getInstance().getUUID(player.getName()); // name-derived in offline mode

			if (resolvedUuid == null || resolvedUuid.isEmpty()) {
				return;
			}

			plugin.debug("nLogin Login: " + player.getName() + " (" + resolvedUuid + ")");

			AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(resolvedUuid));
			AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(user, resolvedUuid);
			Bukkit.getPluginManager().callEvent(login);

		}, 2 + (plugin.getOptions().getDelayLoginEvent() / 1000));
	}
}
