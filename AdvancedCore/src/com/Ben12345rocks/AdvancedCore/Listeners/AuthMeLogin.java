package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;

public class AuthMeLogin implements Listener {

	@EventHandler
	public void authmeLogin(AuthMeAsyncPreLoginEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				if (event.getPlayer() != null) {
					if (AuthMeApi.getInstance().isAuthenticated(event.getPlayer())
							&& AdvancedCorePlugin.getInstance().getOptions().isWaitUntilLoggedIn()) {
						AdvancedCorePlugin.getInstance().debug("Authme Login: " + event.getPlayer().getName() + " ("
								+ event.getPlayer().getUniqueId() + ")");
						AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
						Bukkit.getPluginManager().callEvent(login);
					}
				}
			}
		}, 40l);

	}
}
