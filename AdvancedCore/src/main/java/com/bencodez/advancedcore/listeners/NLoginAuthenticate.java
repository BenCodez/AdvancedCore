package com.bencodez.advancedcore.listeners;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NLoginAuthenticate implements Listener {
    private AdvancedCorePlugin plugin;

    public NLoginAuthenticate(AdvancedCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void nLoginLogin(AuthenticateEvent event) {

        plugin.getBukkitScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (event.getPlayer() != null) {
                if (plugin.getOptions().isWaitUntilLoggedIn()) {
                    plugin.debug("nLogin Login: " + event.getPlayer().getName() + " ("
                            + PlayerManager.getInstance().getUUID(event.getPlayer().getName()) + ")");
                    AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(event.getPlayer());
                    Bukkit.getPluginManager().callEvent(login);
                }
            }
        }, 2);
    }
}
