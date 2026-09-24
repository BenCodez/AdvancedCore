package com.bencodez.advancedcore.api.permissions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import com.bencodez.simpleapi.time.ParsedDuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Stores permission state for a single player UUID.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Persistent permissions are re-applied on every login.</li>
 *   <li>Timed permissions are tracked as an absolute expiration timestamp (epoch millis).</li>
 *   <li>Offline queued permissions are applied on next login.</li>
 * </ul>
 */
public class PlayerPermissionHandler {

	@Getter
	private final UUID uuid;

	/**
	 * Current session attachment (null when offline).
	 */
	@Getter
	@Setter
	private PermissionAttachment attachment;

	private final PermissionHandler handler;

	/**
	 * perm -> expireAtMillis (absolute epoch millis)
	 */
	@Getter
	private HashMap<String, Long> timedPermissions;

	/**
	 * Offline queue:
	 * perm -> durationMillis (<=0 means permanent)
	 */
	private HashMap<String, Long> permsToAdd;

	/**
	 * Permanent permissions to re-apply every login.
	 */
	@Getter
	private final Set<String> persistentPermissions = new HashSet<>();

	public PlayerPermissionHandler(UUID uuid, PermissionAttachment attachment, PermissionHandler handler) {
		this.uuid = uuid;
		this.attachment = attachment;
		this.handler = handler;
	}

	/**
	 * Adds a timed permission using ParsedDuration.
	 *
	 * @param perm Permission node
	 * @param duration How long the permission should last
	 * @return this
	 */
	public synchronized PlayerPermissionHandler addExpiration(String perm, ParsedDuration duration) {
		if (duration == null || duration.isEmpty()) {
			return addPerm(perm);
		}

		if (timedPermissions == null) {
			timedPermissions = new HashMap<>();
		}

		long expireAt = System.currentTimeMillis() + duration.getMillis();
		timedPermissions.put(perm, expireAt);

		if (attachment != null) {
			attachment.setPermission(perm, true);
		}

		long delayMillis = duration.delayMillisFromNow();
		handler.scheduleExpiration(this, perm, expireAt, delayMillis);

		return this;
	}

	/**
	 * Backwards-compatible: seconds-based timed permission.
	 */
	public PlayerPermissionHandler addExpiration(String perm, long seconds) {
		return addExpiration(perm, ParsedDuration.ofMillis(seconds * 1000L));
	}

	/**
	 * Queues a permission to apply on next login.
	 *
	 * @param perm Permission node
	 * @param duration Duration; empty means permanent
	 * @return this
	 */
	public synchronized PlayerPermissionHandler addOfflinePerm(String perm, ParsedDuration duration) {
		if (permsToAdd == null) {
			permsToAdd = new HashMap<>();
		}
		long millis = (duration == null || duration.isEmpty()) ? -1L : duration.getMillis();
		permsToAdd.put(perm, millis);
		return this;
	}

	synchronized void mergeOfflinePermissions(java.util.Map<String, Long> queued) {
		if (queued.isEmpty()) return;
		if (permsToAdd == null) permsToAdd = new HashMap<>();
		permsToAdd.putAll(queued);
	}

	synchronized java.util.Map<String, Long> offlinePermissionSnapshot() {
		return permsToAdd == null ? java.util.Map.of() : new HashMap<>(permsToAdd);
	}

	/**
	 * Backwards-compatible offline queue.
	 */
	public PlayerPermissionHandler addOfflinePerm(String perm, long seconds) {
		return addOfflinePerm(perm, seconds > 0 ? ParsedDuration.ofMillis(seconds * 1000L) : ParsedDuration.empty());
	}

	/**
	 * Adds a permanent permission (persists across logouts).
	 *
	 * @param perm Permission node
	 * @return this
	 */
	public synchronized PlayerPermissionHandler addPerm(String perm) {
		persistentPermissions.add(perm);

		if (attachment != null) {
			attachment.setPermission(perm, true);
		}

		return this;
	}

	/**
	 * Re-applies stored permissions after an attachment is created on login.
	 */
	public synchronized void onLogin(Player player) {
		if (attachment == null) {
			return;
		}

		// Re-apply permanent permissions
		for (String perm : persistentPermissions) {
			attachment.setPermission(perm, true);
		}

		// Re-apply timed permissions that are still valid
		if (timedPermissions != null && !timedPermissions.isEmpty()) {
			long now = System.currentTimeMillis();
			for (Entry<String, Long> e : new HashMap<>(timedPermissions).entrySet()) {
				if (e.getValue() > now) {
					attachment.setPermission(e.getKey(), true);
				} else {
					timedPermissions.remove(e.getKey());
				}
			}
		}

		// Apply offline queued permissions
		if (permsToAdd != null && !permsToAdd.isEmpty()) {
			for (Entry<String, Long> e : permsToAdd.entrySet()) {
				long millis = e.getValue() == null ? -1L : e.getValue().longValue();
				if (millis > 0) {
					addExpiration(e.getKey(), ParsedDuration.ofMillis(millis));
				} else {
					addPerm(e.getKey());
				}
			}
			permsToAdd.clear();
		}
	}

	/**
	 * Logout hook; state is intentionally preserved.
	 */
	public void onLogout(Player player) {
		// intentionally empty
	}


	/** True only while this exact scheduled expiration still owns the timed grant. */
	synchronized java.util.Map<String, Long> timedPermissionSnapshot() {
		return timedPermissions == null ? java.util.Map.of() : new java.util.HashMap<>(timedPermissions);
	}

	synchronized boolean isExpirationCurrent(String perm, long expectedExpireAt) {
		if (timedPermissions == null) return false;
		Long current = timedPermissions.get(perm);
		return current != null && current.longValue() == expectedExpireAt;
	}

	/**
	 * Expire only the scheduled grant that still owns this permission. The
	 * attachment flag is true only from the player's owning platform scheduler.
	 */
	void expirePermission(String perm, long expectedExpireAt, boolean updateAttachment) {
		long remaining;
		boolean removeHandler;
		synchronized (this) {
			if (timedPermissions == null) return;
			Long current = timedPermissions.get(perm);
			if (current == null || current.longValue() != expectedExpireAt) return;
			remaining = current.longValue() - System.currentTimeMillis();
			if (remaining > 0L) {
				removeHandler = false;
			} else {
				if (!timedPermissions.remove(perm, current)) return;
				persistentPermissions.remove(perm);
				if (updateAttachment && attachment != null) attachment.unsetPermission(perm);
				removeHandler = isHandlerEmpty(!updateAttachment);
			}
		}
		if (remaining > 0L) handler.scheduleExpiration(this, perm, expectedExpireAt, remaining);
		else if (removeHandler) handler.removePermissionIfEmpty(uuid, this, !updateAttachment);
	}

	synchronized boolean isHandlerEmpty(boolean attachmentIsOffline) {
		boolean noTracked = persistentPermissions.isEmpty()
				&& (timedPermissions == null || timedPermissions.isEmpty())
				&& (permsToAdd == null || permsToAdd.isEmpty());
		return noTracked && (attachmentIsOffline || attachment == null || attachment.getPermissions().isEmpty());
	}

	/**
	 * Removes a permission from both internal tracking and the live attachment (if online).
	 */
	public void removePermission(String perm) {
		boolean removeHandler;
		synchronized (this) {
			persistentPermissions.remove(perm);
			if (timedPermissions != null) timedPermissions.remove(perm);
			if (permsToAdd != null) permsToAdd.remove(perm);

			if (attachment != null) {
				attachment.setPermission(perm, false);
				attachment.getPermissions().remove(perm);
			}

			removeHandler = isHandlerEmpty(false);
		}
		if (removeHandler) handler.removePermissionIfEmpty(uuid, this, false);
	}
}
