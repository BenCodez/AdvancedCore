package com.bencodez.advancedcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.scheduler.BukkitScheduler;
import com.lenis0012.bukkit.loginsecurity.events.AuthActionEvent;
import com.lenis0012.bukkit.loginsecurity.session.AuthActionType;

public class LoginSecurityLogin implements Listener {
	private AdvancedCorePlugin plugin;

	public LoginSecurityLogin(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void loginSecurityLogin(AuthActionEvent event) {
		if (event.getType().equals(AuthActionType.LOGIN)) {
			BukkitScheduler.runTaskLaterAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					if (event.getPlayer() != null) {
						if (plugin.getOptions().isWaitUntilLoggedIn()) {
							plugin.debug("LoginSecurity Login: " + event.getPlayer().getName() + " ("
									+ event.getPlayer().getUniqueId() + ")");
							AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
							Bukkit.getPluginManager().callEvent(login);
						}
					}
				}
			}, 40l);
		}
	}
}
