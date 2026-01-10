package com.bencodez.advancedcore.listeners;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.command.TabCompleteHandler;

public class PlayerJoinEvent implements Listener {

	private final AdvancedCorePlugin plugin;

	public PlayerJoinEvent(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(AdvancedCoreLoginEvent event) {
		if (!plugin.isLoadUserData()) {
			return;
		}
		if (!event.isUserInStorage()) {
			return;
		}

		AdvancedCoreUser user = event.getUser();
		if (user == null) {
			return;
		}

		Player player = user.getPlayer();
		if (player == null) {
			return;
		}
		
		plugin.getUserManager().getDataManager().cacheUser(player.getUniqueId(), player.getName());

		plugin.getBedrockHandle().learn(user);

		

		user.checkOfflineRewards();
		user.setLastOnline(System.currentTimeMillis());
		user.updateName(false);

		// Keep UUID<->name cache correct (offline-mode uses name-derived UUID)
		String uuid = event.getUuid();
		if (uuid != null && !uuid.isEmpty()) {
			UuidLookup.getInstance().cacheMapping(uuid, player.getName());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerLogin(final org.bukkit.event.player.PlayerJoinEvent event) {
		if (plugin == null || !plugin.isEnabled() || !plugin.isLoadUserData()) {
			return;
		}

		plugin.getLogger().info("Login: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");

		plugin.getLoginTimer().schedule(new Runnable() {

			@Override
			public void run() {
				try {
					if (plugin == null || !plugin.isEnabled()) {
						return;
					}

					TabCompleteHandler.getInstance().onLogin();

					if ((plugin.isAuthMeLoaded() || plugin.isLoginSecurityLoaded() || plugin.isNLoginLoaded())
							&& plugin.getOptions().isWaitUntilLoggedIn()) {
						return;
					}

					Player player = event.getPlayer();
					if (player == null) {
						return;
					}

					// Vanish metadata support
					for (MetadataValue meta : player.getMetadata("vanished")) {
						if (meta.asBoolean()) {
							plugin.debug("Player " + player.getName() + " joined vanished");
							if (plugin.getOptions().isTreatVanishAsOffline()) {
								return;
							}
						}
					}

					// CMI vanish support
					try {
						if (plugin.getCmiHandle() != null && plugin.getCmiHandle().isVanished(player)) {
							plugin.debug("Player " + player.getName() + " joined vanished");
							if (plugin.getOptions().isTreatVanishAsOffline()) {
								return;
							}
						}
					} catch (Exception e) {
						plugin.debug(e);
					}

					plugin.debug("Login: " + player.getName() + " (" + player.getUniqueId() + ")");

					if (plugin.getPermissionHandler() != null) {
						plugin.getPermissionHandler().login(player);
					}

					// Resolve UUID BEFORE constructing/getting the user
					final String resolvedUuid = plugin.getOptions().isOnlineMode() ? player.getUniqueId().toString()
							: UuidLookup.getInstance().getUUID(player.getName()); // name-derived in offline mode

					if (resolvedUuid == null || resolvedUuid.isEmpty()) {
						return;
					}

					// Cache mapping using resolved UUID (offline-mode friendly).
					UuidLookup.getInstance().cacheMapping(resolvedUuid, player.getName());

					// IMPORTANT: create/get AdvancedCoreUser by resolved UUID, not Bukkit UUID
					// (offline-mode correctness)
					AdvancedCoreUser user = plugin.getUserManager().getUser(java.util.UUID.fromString(resolvedUuid),
							player.getName());

					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(user, resolvedUuid);
					Bukkit.getPluginManager().callEvent(login);

					if (login.isCancelled()) {
						return;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 1500 + plugin.getOptions().getDelayLoginEvent(), TimeUnit.MILLISECONDS);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (plugin == null || !plugin.isEnabled()) {
			return;
		}

		Player player = event.getPlayer();
		plugin.debug("Logout: " + player.getName() + " (" + player.getUniqueId() + ")");

		plugin.getLoginTimer().execute(new Runnable() {

			@Override
			public void run() {
				if (plugin != null && plugin.isEnabled()) {
					TabCompleteHandler.getInstance().onLogin();
					plugin.getUserManager().getDataManager().removeCache(player.getUniqueId(), player.getName());
				}
			}
		});
	}
}
