package com.bencodez.advancedcore.api.permissions;

import java.util.TimerTask;
import java.util.UUID;

import org.bukkit.permissions.PermissionAttachment;

import lombok.Getter;

public class PlayerPermissionHandler {
	@Getter
	private UUID uuid;
	@Getter
	private PermissionAttachment attachment;

	private PermissionHandler handler;

	public PlayerPermissionHandler(UUID uuid, PermissionAttachment attachment, PermissionHandler handler) {
		this.uuid = uuid;
		this.attachment = attachment;
		this.handler = handler;
	}

	public void addExpiration(String perm, long delay) {
		getAttachment().setPermission(perm, true);
		handler.getTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				removePermission(perm);
			}
		}, delay * 1000);
	}

	public void addPerm(String perm) {
		getAttachment().setPermission(perm, true);
	}
	
	public void removePermission(String perm) {
		getAttachment().setPermission(perm, false);
		getAttachment().getPermissions().remove(perm);
		if (getAttachment().getPermissions().size() == 0) {
			remove();
		}
	}
	
	public void remove() {
		getAttachment().remove();
		handler.removePermission(uuid);
	}
}
