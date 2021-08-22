package com.bencodez.advancedcore.api.permissions;

import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;

public class PermissionHandler {
	private AdvancedCorePlugin plugin;

	@Getter
	private ConcurrentHashMap<UUID, PlayerPermissionHandler> perms = new ConcurrentHashMap<UUID, PlayerPermissionHandler>();

	@Getter
	private Timer timer = new Timer();

	public PermissionHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	public void addPermission(Player player, String permission) {
		if (permission.isEmpty()) {
			plugin.debug("Permission is empty");
			return;
		}
		if (player == null) {
			plugin.debug("Player is null, not adding perm");
			return;
		}

		for (String perm : permission.split(Pattern.quote("|"))) {
			if (!plugin.getPermissionHandler().getPerms().containsKey(player.getUniqueId())) {
				PermissionAttachment attachment = player.addAttachment(plugin);
				PlayerPermissionHandler handle = new PlayerPermissionHandler(player.getUniqueId(), attachment, this);
				handle.addPerm(perm);
				plugin.getPermissionHandler().getPerms().put(player.getUniqueId(), handle);
				plugin.debug("Giving temp permission " + perm + " to " + player.getName());
			} else {
				plugin.getPermissionHandler().getPerms().get(player.getUniqueId()).addPerm(perm);
				plugin.debug("Giving temp permission " + perm + " to " + player.getName());
			}
		}
	}

	public void addPermission(Player player, String permission, long expiration) {
		if (permission.isEmpty()) {
			plugin.debug("Permission is empty");
			return;
		}
		if (player == null) {
			plugin.debug("Player is null, not adding perm");
			return;
		}

		for (String perm : permission.split(Pattern.quote("|"))) {
			if (!plugin.getPermissionHandler().getPerms().containsKey(player.getUniqueId())) {
				PermissionAttachment attachment = player.addAttachment(plugin);
				PlayerPermissionHandler handle = new PlayerPermissionHandler(player.getUniqueId(), attachment, this);
				handle.addExpiration(perm, expiration);
				plugin.getPermissionHandler().getPerms().put(player.getUniqueId(), handle);
				plugin.debug("Giving temp permission " + perm + " to " + player.getName());
			} else {
				plugin.getPermissionHandler().getPerms().get(player.getUniqueId()).addExpiration(perm, expiration);
				plugin.debug("Giving temp permission " + perm + " to " + player.getName());
			}
		}
	}

	public void removePermission(UUID uuid, String playerName, String permission) {
		if (plugin.getPermissionHandler().getPerms().containsKey(uuid)) {
			for (String perm : permission.split(Pattern.quote("|"))) {
				plugin.getPermissionHandler().getPerms().get(uuid).removePermission(perm);
				plugin.debug("Removing temp permission " + perm + " from " + playerName);
			}
		}
	}

	public void removePermission(UUID uuid) {
		getPerms().remove(uuid);
	}
}
