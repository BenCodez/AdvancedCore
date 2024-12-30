package com.bencodez.advancedcore.api.permissions;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import lombok.Getter;
import lombok.Setter;

public class PlayerPermissionHandler {
	@Getter
	private UUID uuid;
	@Getter
	@Setter
	private PermissionAttachment attachment;

	private PermissionHandler handler;

	@Getter
	private HashMap<String, Long> timedPermissions;

	private HashMap<String, Long> permsToAdd;

	public PlayerPermissionHandler(UUID uuid, PermissionAttachment attachment, PermissionHandler handler) {
		this.uuid = uuid;
		this.attachment = attachment;
		this.handler = handler;
	}

	public PlayerPermissionHandler addExpiration(String perm, long delay) {
		if (timedPermissions == null) {
			timedPermissions = new HashMap<>();
		}
		timedPermissions.put(perm, System.currentTimeMillis() + (delay * 1000));
		getAttachment().setPermission(perm, true);
		handler.getTimer().schedule(new Runnable() {

			@Override
			public void run() {
				removePermission(perm);
			}
		}, delay, TimeUnit.SECONDS);
		handler.getPlugin().debug("Giving temp permission " + perm + " to " + uuid.toString() + " for " + delay);
		return this;
	}

	public PlayerPermissionHandler addOfflinePerm(String perm, long delay) {
		if (permsToAdd == null) {
			permsToAdd = new HashMap<>();
		}
		permsToAdd.put(perm, delay);
		return this;
	}

	public PlayerPermissionHandler addPerm(String perm) {
		if (getAttachment() != null) {
			getAttachment().setPermission(perm, true);
			handler.getPlugin().debug("Giving permission " + perm + " to " + uuid.toString());
		}
		return this;
	}

	public void onLogin(Player player) {
		if ((player == null) || (permsToAdd == null)) {
			return;
		}
		for (Entry<String, Long> entry : permsToAdd.entrySet()) {
			if (entry.getValue().longValue() > 0) {
				addExpiration(entry.getKey(), entry.getValue().longValue() / 1000);
			} else {
				addPerm(entry.getKey());
			}
		}
	}

	public void remove() {
		getAttachment().remove();
		handler.removePermission(uuid);
	}

	public void removePermission(String perm) {
		getAttachment().setPermission(perm, false);
		getAttachment().getPermissions().remove(perm);
		if (timedPermissions != null) {
			timedPermissions.remove(perm);
		}
		handler.getPlugin().debug("Removing permission " + perm + " to " + uuid.toString());
		if (getAttachment().getPermissions().size() == 0) {
			remove();
		}
	}
}
