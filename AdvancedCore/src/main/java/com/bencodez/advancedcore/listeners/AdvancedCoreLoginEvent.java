package com.bencodez.advancedcore.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;

/**
 * Fired when AdvancedCore considers a player "logged in" (after optional auth
 * delays).
 *
 * Identity is AdvancedCoreUser + a resolved UUID string chosen by the listener.
 * (Listener decides online/offline mode rules and passes the UUID in.)
 */
public class AdvancedCoreLoginEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	public static HandlerList getHandlerList() {
		return handlers;
	}

	private boolean cancelled;

	@Getter
	private final AdvancedCoreUser user;

	@Getter
	private final Player player;

	/**
	 * UUID to use for storage/cache lookups. In offline-mode this should be the
	 * name-derived UUID (OfflinePlayer:Name).
	 */
	@Getter
	private final String uuid;

	public AdvancedCoreLoginEvent(AdvancedCoreUser user, String uuid, Player player) {
		super(true);
		this.user = user;
		this.uuid = uuid;
		this.player = player;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean bln) {
		cancelled = bln;
	}

	public boolean isUserInStorage() {
		if (uuid == null || uuid.isEmpty()) {
			return false;
		}
		return AdvancedCorePlugin.getInstance().getUserManager().getAllUUIDs().contains(uuid);
	}
}
