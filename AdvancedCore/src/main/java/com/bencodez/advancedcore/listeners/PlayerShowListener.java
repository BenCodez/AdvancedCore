package com.bencodez.advancedcore.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import de.myzelyam.api.vanish.PlayerShowEvent;

public class PlayerShowListener implements Listener {

	private AdvancedCorePlugin plugin;

	public PlayerShowListener(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerShowEvent event) {
		if (plugin == null || !plugin.isEnabled()) {
			return;
		}

		plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (plugin == null || !plugin.isEnabled()) {
					return;
				}

				if ((plugin.isAuthMeLoaded() || plugin.isNLoginLoaded()) && plugin.getOptions().isWaitUntilLoggedIn()) {
					return;
				}

				Player player = event.getPlayer();
				if (player == null) {
					return;
				}

				String resolvedUuid = plugin.getOptions().isOnlineMode() ? player.getUniqueId().toString()
						: UuidLookup.getInstance().getUUID(player.getName()); // name-derived in offline mode

				if (resolvedUuid == null || resolvedUuid.isEmpty()) {
					return;
				}

				plugin.debug("Vanish Login: " + player.getName() + " (" + resolvedUuid + ")");

				if (plugin.getPermissionHandler() != null) {
					plugin.getPermissionHandler().login(player);
				}

				AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(resolvedUuid));
				AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(user, resolvedUuid, player);
				Bukkit.getPluginManager().callEvent(login);
			}
		}, 2);
	}
}
