package com.bencodez.advancedcore.listeners;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.command.TabCompleteHandler;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class PlayerJoinEvent implements Listener {

	/** The plugin. */
	private AdvancedCorePlugin plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin the plugin
	 */
	public PlayerJoinEvent(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(AdvancedCoreLoginEvent event) {
		if (event.isUserInStorage() && plugin.isLoadUserData()) {
			Player player = event.getPlayer();
			boolean userExist = plugin.getUserManager().userExist(event.getPlayer().getUniqueId());
			if (player.getName().startsWith(plugin.getOptions().getBedrockPlayerPrefix())
					|| plugin.getGeyserHandle().isGeyserPlayer(player.getUniqueId())) {
				userExist = true;
				plugin.getUuidNameCache().put(player.getUniqueId().toString(), player.getName());
				plugin.extraDebug("Detected Geyser Player, Forcing player data to load");
			}

			if (!plugin.getOptions().isOnlineMode()) {
				if (!player.getUniqueId().toString().equals(PlayerManager.getInstance().getUUID(player.getName()))) {
					plugin.debug("Detected UUID mismatch, please enabling OnlineMode in VotingPlugin "
							+ player.getUniqueId().toString() + ":"
							+ PlayerManager.getInstance().getUUID(player.getName()));
				}
			}

			plugin.getUserManager().getDataManager().cacheUser(player.getUniqueId());

			if (userExist) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(player);

				user.checkOfflineRewards();
				user.setLastOnline(System.currentTimeMillis());
				user.updateName(false);
			}
			plugin.getUuidNameCache().put(player.getUniqueId().toString(), player.getName());

		}

	}

	/**
	 * On player login.
	 *
	 * @param event the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerLogin(final PlayerLoginEvent event) {
		if (plugin != null && plugin.isEnabled() && plugin.isLoadUserData()) {
			plugin.debug("Login: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
			plugin.getLoginTimer().schedule(new Runnable() {

				@Override
				public void run() {
					try {
						if (plugin != null && plugin.isEnabled()) {
							TabCompleteHandler.getInstance().onLogin();
							if ((plugin.isAuthMeLoaded() || plugin.isLoginSecurityLoaded())
									&& plugin.getOptions().isWaitUntilLoggedIn()) {
								return;
							}

							Player player = event.getPlayer();

							if (player != null) {
								for (MetadataValue meta : player.getMetadata("vanished")) {
									if (meta.asBoolean()) {
										plugin.debug("Player " + player.getName() + " joined vanished");
										if (plugin.getOptions().isTreatVanishAsOffline()) {
											return;
										}
									}
								}

								try {
									if (plugin.getCmiHandle() != null) {
										if (plugin.getCmiHandle().isVanished(player)) {
											plugin.debug("Player " + player.getName() + " joined vanished");
											if (plugin.getOptions().isTreatVanishAsOffline()) {
												return;
											}
										}
									}
								} catch (Exception e) {
									plugin.debug(e);
								}

								plugin.debug("Login: " + event.getPlayer().getName() + " ("
										+ event.getPlayer().getUniqueId() + ")");
								if (plugin.getPermissionHandler() != null) {
									plugin.getPermissionHandler().login(player);
								}

								AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(player);
								Bukkit.getPluginManager().callEvent(login);

								if (login.isCancelled()) {
									return;
								}
							}

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, 1500 + plugin.getOptions().getDelayLoginEvent(), TimeUnit.MILLISECONDS);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (plugin != null && plugin.isEnabled()) {
			Player player = event.getPlayer();
			plugin.debug("Logout: " + event.getPlayer().getName() + " (" + player.getUniqueId() + ")");

			plugin.getLoginTimer().execute(new Runnable() {

				@Override
				public void run() {
					if (plugin != null && plugin.isEnabled()) {
						TabCompleteHandler.getInstance().onLogin();

						plugin.getUserManager().getDataManager().removeCache(player.getUniqueId());

					}
				}
			});
		}

	}

}