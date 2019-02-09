package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;

public class AuthMeLogin implements Listener {

	@EventHandler
	public void authmeLogin(AuthMeAsyncPreLoginEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

			@Override
			public void run() {
				if (AuthMeApi.getInstance().isAuthenticated(event.getPlayer())
						&& AdvancedCoreHook.getInstance().getOptions().isWaitUntilLoggedIn()) {
					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
					Bukkit.getPluginManager().callEvent(login);
				}
			}
		}, 20l);

	}
}
