package com.bencodez.advancedcore.api.permissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;

public class PermissionHandler {
	@Getter
	private AdvancedCorePlugin plugin;

	@Getter
	private ConcurrentHashMap<UUID, PlayerPermissionHandler> perms = new ConcurrentHashMap<>();

	@Getter
	private ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);

	@Getter
	private HashMap<UUID, PlayerPermissionHandler> permsToAdd;

	public PermissionHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		permsToAdd = new HashMap<>();
		if (plugin.getServerDataFile().getData() != null) {
			if (plugin.getServerDataFile().getData().isConfigurationSection("TimedPermissions")) {
				for (String string : plugin.getServerDataFile().getData().getConfigurationSection("TimedPermissions")
						.getKeys(false)) {
					UUID uuid = UUID.fromString(string);
					List<String> list = plugin.getServerDataFile().getData()
							.getStringList("TimedPermissions." + string);
					for (String str : list) {
						String[] data = str.split(Pattern.quote("%line%"));
						if (data.length > 1) {
							String perm = data[0];
							String longStr = data[1];
							long delay = Long.valueOf(longStr).longValue() - System.currentTimeMillis();
							if (delay > 0) {
								plugin.debug("Adding permission " + perm + " to " + string);
								addPermission(uuid, perm, delay);

							}
						}
					}
				}
				plugin.getServerDataFile().getData().set("TimedPermissions", null);
			}
		}
	}

	public void addPermission(Player player, String permission) {
		addPermission(player.getUniqueId(), permission);
	}

	public void addPermission(Player player, String permission, long expiration) {
		addPermission(player.getUniqueId(), permission, expiration);
	}

	public void addPermission(UUID uuid, String permission) {
		if (permission.isEmpty()) {
			plugin.debug("Permission is empty");
			return;
		}
		for (String perm : permission.split(Pattern.quote("|"))) {
			if (getPerms().contains(uuid)) {
				getPerms().get(uuid).addPerm(perm);
			} else {
				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					PermissionAttachment attachment = p.addAttachment(plugin);
					PlayerPermissionHandler handle = new PlayerPermissionHandler(uuid, attachment, this);
					plugin.getPermissionHandler().getPerms().put(uuid, handle.addPerm(perm));
				} else {
					getPermsToAdd().put(uuid, new PlayerPermissionHandler(uuid, null, this).addOfflinePerm(perm, -1));
				}
			}
		}
	}

	public void addPermission(UUID uuid, String permission, long delay) {
		if (permission.isEmpty()) {
			plugin.debug("Permission is empty");
			return;
		}
		for (String perm : permission.split(Pattern.quote("|"))) {
			if (getPerms().contains(uuid)) {
				getPerms().get(uuid).addPerm(perm);
			} else {
				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					PermissionAttachment attachment = p.addAttachment(plugin);
					PlayerPermissionHandler handle = new PlayerPermissionHandler(uuid, attachment, this);
					plugin.getPermissionHandler().getPerms().put(uuid, handle.addExpiration(perm, delay));
				} else {
					getPermsToAdd().put(uuid,
							new PlayerPermissionHandler(uuid, null, this).addOfflinePerm(perm, delay));
				}
			}

		}
	}

	public void login(Player player) {
		if (permsToAdd.containsKey(player.getUniqueId())) {
			PlayerPermissionHandler handle = permsToAdd.get(player.getUniqueId());
			handle.setAttachment(player.addAttachment(plugin));
			handle.onLogin(player);
			getPerms().put(player.getUniqueId(), handle);
			permsToAdd.remove(player.getUniqueId());
		}
	}

	public void removePermission(UUID uuid) {
		getPerms().remove(uuid);
	}

	public void removePermission(UUID uuid, String playerName, String permission) {
		if (plugin.getPermissionHandler().getPerms().containsKey(uuid)) {
			for (String perm : permission.split(Pattern.quote("|"))) {
				plugin.getPermissionHandler().getPerms().get(uuid).removePermission(perm);
				plugin.debug("Removing temp permission " + perm + " from " + playerName);
			}
		}
	}

	public void shutDown() {
		for (PlayerPermissionHandler handle : getPerms().values()) {
			ArrayList<String> list = new ArrayList<>();
			for (Entry<String, Long> entry : handle.getTimedPermissions().entrySet()) {
				list.add(entry.getKey() + "%line%" + entry.getValue().longValue());
			}
			if (list.size() > 0) {
				plugin.getServerDataFile().getData().set("TimedPermissions." + handle.getUuid().toString(), list);
			}
		}
		plugin.getServerDataFile().saveData();
	}
}
