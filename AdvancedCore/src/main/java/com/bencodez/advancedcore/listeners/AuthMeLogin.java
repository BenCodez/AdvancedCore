package com.bencodez.advancedcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.PlayerManager;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;

public class AuthMeLogin implements Listener {
	private AdvancedCorePlugin plugin;

	public AuthMeLogin(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void authmeLogin(AuthMeAsyncPreLoginEvent event) {
		plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (event.getPlayer() != null) {
					if (AuthMeApi.getInstance().isAuthenticated(event.getPlayer())
							&& plugin.getOptions().isWaitUntilLoggedIn()) {
						plugin.debug("Authme Login: " + event.getPlayer().getName() + " ("
								+ PlayerManager.getInstance().getUUID(event.getPlayer().getName()) + ")");
						AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
						Bukkit.getPluginManager().callEvent(login);
					}
				}
			}
		}, 2 + (plugin.getOptions().getDelayLoginEvent() / 1000));

	}
}
