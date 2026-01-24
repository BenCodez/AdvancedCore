package com.bencodez.advancedcore.api.permissions;

import java.util.ArrayList;
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
import com.bencodez.simpleapi.time.ParsedDuration;

import lombok.Getter;

/**
 * Central permission manager handling temporary and persistent permissions.
 *
 * <p>
 * Permissions are tracked by UUID and re-applied on login to avoid Bukkit
 * {@link PermissionAttachment} loss on logout.
 * </p>
 *
 * <p>
 * Timed permissions are stored on disk as an absolute expiration timestamp
 * (epoch millis).
 * </p>
 */
public class PermissionHandler {

	@Getter
	private final AdvancedCorePlugin plugin;

	/**
	 * Active handlers (attachment may be null while offline after logout).
	 */
	@Getter
	private final ConcurrentHashMap<UUID, PlayerPermissionHandler> perms = new ConcurrentHashMap<>();

	/**
	 * Offline handlers waiting for attachment on next login.
	 */
	@Getter
	private final ConcurrentHashMap<UUID, PlayerPermissionHandler> permsToAdd = new ConcurrentHashMap<>();

	@Getter
	private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);

	public PermissionHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;

		// Restore timed permissions from previous shutdown (stored as expireAtMillis)
		if (plugin.getServerDataFile().getData() != null
				&& plugin.getServerDataFile().getData().isConfigurationSection("TimedPermissions")) {

			for (String key : plugin.getServerDataFile().getData().getConfigurationSection("TimedPermissions")
					.getKeys(false)) {

				UUID uuid = UUID.fromString(key);

				for (String entry : plugin.getServerDataFile().getData().getStringList("TimedPermissions." + key)) {

					String[] data = entry.split(Pattern.quote("%line%"));
					if (data.length != 2) {
						continue;
					}

					String perm = data[0];
					long expireAtMillis;
					try {
						expireAtMillis = Long.parseLong(data[1]);
					} catch (Exception e) {
						continue;
					}

					long remainingMillis = expireAtMillis - System.currentTimeMillis();
					if (remainingMillis > 0) {
						addPermission(uuid, perm, ParsedDuration.ofMillis(remainingMillis));
					}
				}
			}

			plugin.getServerDataFile().getData().set("TimedPermissions", null);
		}
	}

	public void addPermission(Player player, String permission) {
		addPermission(player.getUniqueId(), permission);
	}

	/**
	 * Adds a timed permission for a duration.
	 *
	 * @param player     Player
	 * @param permission Permission node(s), split by "|"
	 * @param duration   Duration to keep the permission for
	 */
	public void addPermission(Player player, String permission, ParsedDuration duration) {
		addPermission(player.getUniqueId(), permission, duration);
	}

	/**
	 * Backwards-compatible: seconds-based API.
	 */
	public void addPermission(Player player, String permission, long seconds) {
		addPermission(player.getUniqueId(), permission, ParsedDuration.ofMillis(seconds * 1000L));
	}

	public void addPermission(UUID uuid, String permission) {
		if (permission == null || permission.isEmpty()) {
			return;
		}

		for (String perm : permission.split(Pattern.quote("|"))) {
			PlayerPermissionHandler handle = perms.get(uuid);

			if (handle != null) {
				handle.addPerm(perm);
				continue;
			}

			Player p = Bukkit.getPlayer(uuid);
			if (p != null) {
				PermissionAttachment attachment = p.addAttachment(plugin);
				PlayerPermissionHandler newHandle = new PlayerPermissionHandler(uuid, attachment, this).addPerm(perm);
				perms.put(uuid, newHandle);
			} else {
				permsToAdd.put(uuid,
						new PlayerPermissionHandler(uuid, null, this).addOfflinePerm(perm, ParsedDuration.empty()));
			}
		}
	}

	/**
	 * Adds a timed permission for a duration.
	 *
	 * @param uuid       Player UUID
	 * @param permission Permission node(s), split by "|"
	 * @param duration   Duration to keep the permission for
	 */
	public void addPermission(UUID uuid, String permission, ParsedDuration duration) {
		if (permission == null || permission.isEmpty()) {
			return;
		}
		if (duration == null || duration.isEmpty()) {
			// Treat empty as "not timed" (caller probably wanted permanent)
			addPermission(uuid, permission);
			return;
		}

		for (String perm : permission.split(Pattern.quote("|"))) {
			PlayerPermissionHandler handle = perms.get(uuid);

			if (handle != null) {
				handle.addExpiration(perm, duration);
				continue;
			}

			Player p = Bukkit.getPlayer(uuid);
			if (p != null) {
				PermissionAttachment attachment = p.addAttachment(plugin);
				PlayerPermissionHandler newHandle = new PlayerPermissionHandler(uuid, attachment, this)
						.addExpiration(perm, duration);
				perms.put(uuid, newHandle);
			} else {
				permsToAdd.put(uuid, new PlayerPermissionHandler(uuid, null, this).addOfflinePerm(perm, duration));
			}
		}
	}

	/**
	 * Backwards-compatible: seconds-based API.
	 */
	public void addPermission(UUID uuid, String permission, long seconds) {
		addPermission(uuid, permission, ParsedDuration.ofMillis(seconds * 1000L));
	}

	/**
	 * Call on PlayerJoinEvent.
	 *
	 * <p>
	 * Ensures the player has a fresh attachment and re-applies all stored perms.
	 * </p>
	 */
	public void login(Player player) {
		UUID uuid = player.getUniqueId();

		PlayerPermissionHandler handle = perms.get(uuid);
		if (handle != null) {
			handle.setAttachment(player.addAttachment(plugin));
			handle.onLogin(player);
			return;
		}

		PlayerPermissionHandler pending = permsToAdd.remove(uuid);
		if (pending != null) {
			pending.setAttachment(player.addAttachment(plugin));
			pending.onLogin(player);
			perms.put(uuid, pending);
		}
	}

	/**
	 * Call on PlayerQuitEvent and PlayerKickEvent.
	 *
	 * <p>
	 * Detaches the Bukkit attachment but preserves state so permissions re-apply
	 * next login.
	 * </p>
	 */
	public void logout(Player player) {
		PlayerPermissionHandler handle = perms.remove(player.getUniqueId());
		if (handle == null) {
			return;
		}

		try {
			if (handle.getAttachment() != null) {
				player.removeAttachment(handle.getAttachment());
			}
		} catch (Throwable ignored) {
		}

		handle.setAttachment(null);
		handle.onLogout(player);
		permsToAdd.put(player.getUniqueId(), handle);
	}

	public void removePermission(UUID uuid) {
		perms.remove(uuid);
		permsToAdd.remove(uuid);
	}

	/**
	 * Removes one or more permissions (split by "|") from a specific player.
	 *
	 * <p>
	 * This works for both online and offline cached handlers.
	 * </p>
	 *
	 * @param uuid       Player UUID
	 * @param playerName Player name (debug/logging only; may be null)
	 * @param permission Permission node(s), split by "|"
	 */
	public void removePermission(UUID uuid, String playerName, String permission) {
		if (permission == null || permission.isEmpty()) {
			return;
		}

		PlayerPermissionHandler handle = perms.get(uuid);
		if (handle == null) {
			handle = permsToAdd.get(uuid);
		}
		if (handle == null) {
			return;
		}

		for (String perm : permission.split(Pattern.quote("|"))) {
			handle.removePermission(perm);
			if (playerName != null && !playerName.isEmpty()) {
				plugin.debug("Removing temp permission " + perm + " from " + playerName);
			} else {
				plugin.debug("Removing temp permission " + perm + " from " + uuid);
			}
		}
	}

	/**
	 * Persists timed permissions for both online + offline handlers.
	 */
	public void shutDown() {
		saveTimedPerms(perms);
		saveTimedPerms(permsToAdd);
		plugin.getServerDataFile().saveData();
	}

	private void saveTimedPerms(ConcurrentHashMap<UUID, PlayerPermissionHandler> map) {
		for (PlayerPermissionHandler handle : map.values()) {
			if (handle.getTimedPermissions() == null || handle.getTimedPermissions().isEmpty()) {
				continue;
			}

			ArrayList<String> list = new ArrayList<>();
			for (Entry<String, Long> entry : handle.getTimedPermissions().entrySet()) {
				// Store absolute expireAtMillis
				list.add(entry.getKey() + "%line%" + entry.getValue());
			}

			plugin.getServerDataFile().getData().set("TimedPermissions." + handle.getUuid(), list);
		}
	}
}
