package com.bencodez.advancedcore.api.permissions;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private final AtomicBoolean acceptingExpirations = new AtomicBoolean(true);
	private final Object[] stateLocks = new Object[64];

	public PermissionHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		for (int i = 0; i < stateLocks.length; i++) stateLocks[i] = new Object();

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


	void scheduleExpiration(PlayerPermissionHandler handle, String permission, long expectedExpireAt, long delayMillis) {
		if (!acceptingExpirations.get()) return;
		try {
			timer.schedule(() -> dispatchExpiration(handle, permission, expectedExpireAt),
					Math.max(0L, delayMillis), java.util.concurrent.TimeUnit.MILLISECONDS);
		} catch (RuntimeException failure) {
			plugin.debug(failure);
			throw failure;
		}
	}

	void dispatchExpiration(PlayerPermissionHandler handle, String permission, long expectedExpireAt) {
		if (!acceptingExpirations.get() || !handle.isExpirationCurrent(permission, expectedExpireAt)) return;
		try {
			plugin.getBukkitScheduler().runTask(plugin, () -> {
				if (!acceptingExpirations.get() || !handle.isExpirationCurrent(permission, expectedExpireAt)) return;
				Player player = Bukkit.getPlayer(handle.getUuid());
				if (player == null) {
					expireOfflineOrRetry(handle, permission, expectedExpireAt);
					return;
				}
				try {
					plugin.getBukkitScheduler().runTask(plugin, () -> {
						if (!acceptingExpirations.get()) return;
						handle.expirePermission(permission, expectedExpireAt, true);
					}, player);
				} catch (RuntimeException failure) {
					plugin.debug(failure);
					retryExpiration(handle, permission, expectedExpireAt);
				}
			});
		} catch (RuntimeException failure) {
			plugin.debug(failure);
			retryExpiration(handle, permission, expectedExpireAt);
		}
	}

	private void expireOfflineOrRetry(PlayerPermissionHandler handle, String permission, long expectedExpireAt) {
		boolean active;
		UUID uuid = handle.getUuid();
		synchronized (stateLock(uuid)) {
			if (permsToAdd.get(uuid) == handle) {
				handle.expirePermission(permission, expectedExpireAt, false);
				return;
			}
			active = perms.get(uuid) == handle;
		}
		if (active) retryExpiration(handle, permission, expectedExpireAt);
	}

	private void retryExpiration(PlayerPermissionHandler handle, String permission, long expectedExpireAt) {
		if (!acceptingExpirations.get() || !handle.isExpirationCurrent(permission, expectedExpireAt)) return;
		try { scheduleExpiration(handle, permission, expectedExpireAt, 1_000L); }
		catch (RuntimeException ignored) { /* scheduleExpiration already reported the rejection */ }
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

		synchronized (stateLock(uuid)) {
			for (String perm : permission.split(Pattern.quote("|"))) {
				PlayerPermissionHandler handle = perms.get(uuid);

				if (handle != null) {
					handle.addPerm(perm);
					continue;
				}
				PlayerPermissionHandler pending = permsToAdd.get(uuid);
				if (pending != null) {
					pending.addOfflinePerm(perm, ParsedDuration.empty());
					continue;
				}

				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					PermissionAttachment attachment = p.addAttachment(plugin);
					PlayerPermissionHandler newHandle = new PlayerPermissionHandler(uuid, attachment, this).addPerm(perm);
					perms.put(uuid, newHandle);
				} else {
					permsToAdd.compute(uuid, (ignored, existing) -> {
						PlayerPermissionHandler target = existing == null
								? new PlayerPermissionHandler(uuid, null, this) : existing;
						return target.addOfflinePerm(perm, ParsedDuration.empty());
					});
				}
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

		synchronized (stateLock(uuid)) {
			for (String perm : permission.split(Pattern.quote("|"))) {
				PlayerPermissionHandler handle = perms.get(uuid);

				if (handle != null) {
					handle.addExpiration(perm, duration);
					continue;
				}
				PlayerPermissionHandler pending = permsToAdd.get(uuid);
				if (pending != null) {
					pending.addOfflinePerm(perm, duration);
					continue;
				}

				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					PermissionAttachment attachment = p.addAttachment(plugin);
					PlayerPermissionHandler newHandle = new PlayerPermissionHandler(uuid, attachment, this)
							.addExpiration(perm, duration);
					perms.put(uuid, newHandle);
				} else {
					permsToAdd.compute(uuid, (ignored, existing) -> {
						PlayerPermissionHandler target = existing == null
								? new PlayerPermissionHandler(uuid, null, this) : existing;
						return target.addOfflinePerm(perm, duration);
					});
				}
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
		synchronized (stateLock(uuid)) {
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
		UUID uuid = player.getUniqueId();
		synchronized (stateLock(uuid)) {
			PlayerPermissionHandler handle = perms.remove(uuid);
			if (handle == null) return;

			try {
				if (handle.getAttachment() != null) player.removeAttachment(handle.getAttachment());
			} catch (Throwable ignored) {
			}

			handle.setAttachment(null);
			handle.onLogout(player);
			permsToAdd.merge(uuid, handle, (pending, moved) -> {
				java.util.Map<String, Long> queued = pending.offlinePermissionSnapshot();
				moved.mergeOfflinePermissions(queued);
				return moved;
			});
		}
	}

	public void removePermission(UUID uuid) {
		synchronized (stateLock(uuid)) {
			perms.remove(uuid);
			permsToAdd.remove(uuid);
		}
	}

	void removePermission(UUID uuid, PlayerPermissionHandler expected) {
		synchronized (stateLock(uuid)) {
			perms.remove(uuid, expected);
			permsToAdd.remove(uuid, expected);
		}
	}

	void removePermissionIfEmpty(UUID uuid, PlayerPermissionHandler expected, boolean attachmentIsOffline) {
		synchronized (stateLock(uuid)) {
			if (!expected.isHandlerEmpty(attachmentIsOffline)) return;
			perms.remove(uuid, expected);
			permsToAdd.remove(uuid, expected);
		}
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

		synchronized (stateLock(uuid)) {
			PlayerPermissionHandler handle = perms.get(uuid);
			if (handle == null) handle = permsToAdd.get(uuid);
			if (handle == null) return;

			for (String perm : permission.split(Pattern.quote("|"))) {
				handle.removePermission(perm);
				if (playerName != null && !playerName.isEmpty()) {
					plugin.debug("Removing temp permission " + perm + " from " + playerName);
				} else {
					plugin.debug("Removing temp permission " + perm + " from " + uuid);
				}
			}
		}
	}

	private Object stateLock(UUID uuid) {
		return stateLocks[(uuid.hashCode() & Integer.MAX_VALUE) % stateLocks.length];
	}

	/**
	 * Persists timed permissions for both online + offline handlers.
	 */
	public void shutDown() {
		// Fence every timer/global/entity callback before taking persistence snapshots.
		// A callback already inside a handler monitor finishes before timedPermissionSnapshot().
		acceptingExpirations.set(false);
		timer.shutdownNow();
		saveTimedPerms(perms);
		saveTimedPerms(permsToAdd);
		plugin.getServerDataFile().saveData();
	}

	private void saveTimedPerms(ConcurrentHashMap<UUID, PlayerPermissionHandler> map) {
		for (PlayerPermissionHandler handle : map.values()) {
			java.util.Map<String, Long> snapshot = handle.timedPermissionSnapshot();
			if (snapshot.isEmpty()) continue;

			ArrayList<String> list = new ArrayList<>();
			for (Entry<String, Long> entry : snapshot.entrySet()) {
				list.add(entry.getKey() + "%line%" + entry.getValue());
			}
			plugin.getServerDataFile().getData().set("TimedPermissions." + handle.getUuid(), list);
		}
	}
}
