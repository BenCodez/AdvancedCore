package com.bencodez.advancedcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;

public class AuthMeLogin implements Listener {
	private AdvancedCorePlugin plugin;
	
	public AuthMeLogin(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void authmeLogin(AuthMeAsyncPreLoginEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (event.getPlayer() != null) {
					if (AuthMeApi.getInstance().isAuthenticated(event.getPlayer())
							&& plugin.getOptions().isWaitUntilLoggedIn()) {
						plugin.debug("Authme Login: " + event.getPlayer().getName() + " ("
								+ event.getPlayer().getUniqueId() + ")");
						AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
						Bukkit.getPluginManager().callEvent(login);
					}
				}
			}
		}, 40l);

	}
}
