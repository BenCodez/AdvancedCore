package com.bencodez.advancedcore.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.lenis0012.bukkit.loginsecurity.events.AuthActionEvent;
import com.lenis0012.bukkit.loginsecurity.session.AuthActionType;

public class LoginSecurityLogin implements Listener {
	private AdvancedCorePlugin plugin;

	public LoginSecurityLogin(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void loginSecurityLogin(AuthActionEvent event) {
		if (!event.getType().equals(AuthActionType.LOGIN)) {
			return;
		}

		plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
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

				plugin.debug("LoginSecurity Login: " + player.getName() + " (" + resolvedUuid + ")");

				AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(resolvedUuid));
				AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(user, resolvedUuid,player);
				Bukkit.getPluginManager().callEvent(login);
			}
		}, 2);
	}
}
