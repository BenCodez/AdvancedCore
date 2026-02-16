package com.bencodez.advancedcore.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;

/**
 * Listens for AuthMe login events and triggers AdvancedCore login events.
 */
public class AuthMeLogin implements Listener {
	private AdvancedCorePlugin plugin;

	/**
	 * Creates a new AuthMe login listener.
	 * 
	 * @param plugin the plugin instance
	 */
	public AuthMeLogin(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Handles AuthMe pre-login events.
	 * 
	 * @param event the AuthMe pre-login event
	 */
	@EventHandler
	public void authmeLogin(AuthMeAsyncPreLoginEvent event) {
		plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				Player player = event.getPlayer();
				if (player == null) {
					return;
				}

				if (AuthMeApi.getInstance().isAuthenticated(player) && plugin.getOptions().isWaitUntilLoggedIn()) {
					String resolvedUuid = plugin.getOptions().isOnlineMode() ? player.getUniqueId().toString()
							: UuidLookup.getInstance().getUUID(player.getName()); // name-derived in offline mode

					if (resolvedUuid == null || resolvedUuid.isEmpty()) {
						return;
					}

					plugin.debug("Authme Login: " + player.getName() + " (" + resolvedUuid + ")");

					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(resolvedUuid));
					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(user, resolvedUuid, player);
					Bukkit.getPluginManager().callEvent(login);
				}
			}
		}, 2 + (plugin.getOptions().getDelayLoginEventMs() / 1000));
	}
}
