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

	/**
	 * Gets the handler list for this event.
	 * 
	 * @return the handler list
	 */
	public static HandlerList getHandlerList() {
		return handlers;
	}

	private boolean cancelled;

	/**
	 * Gets the AdvancedCoreUser associated with this login event.
	 * 
	 * @return the user
	 */
	@Getter
	private final AdvancedCoreUser user;

	/**
	 * Gets the Player associated with this login event.
	 * 
	 * @return the player
	 */
	@Getter
	private final Player player;

	/**
	 * UUID to use for storage/cache lookups. In offline-mode this should be the
	 * name-derived UUID (OfflinePlayer:Name).
	 * 
	 * @return the UUID string
	 */
	@Getter
	private final String uuid;

	/**
	 * Creates a new AdvancedCoreLoginEvent.
	 * 
	 * @param user the AdvancedCoreUser
	 * @param uuid the UUID string
	 * @param player the Player
	 */
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

	/**
	 * Gets whether this event is cancelled.
	 * 
	 * @return true if cancelled, false otherwise
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Sets whether this event is cancelled.
	 * 
	 * @param bln true to cancel, false otherwise
	 */
	public void setCancelled(boolean bln) {
		cancelled = bln;
	}

	/**
	 * Checks if the user is already in storage.
	 * 
	 * @return true if user exists in storage, false otherwise
	 */
	public boolean isUserInStorage() {
		if (uuid == null || uuid.isEmpty()) {
			return false;
		}
		return AdvancedCorePlugin.getInstance().getUserManager().getAllUUIDs().contains(uuid);
	}
}
