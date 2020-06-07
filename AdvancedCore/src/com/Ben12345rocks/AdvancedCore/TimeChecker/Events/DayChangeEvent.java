package com.Ben12345rocks.AdvancedCore.TimeChecker.Events;

import org.bukkit.event.HandlerList;

// TODO: Auto-generated Javadoc
/**
 * The Class DayChangeEvent.
 */
public class DayChangeEvent extends Event {

	/** The Constant handlers. */
	private static final HandlerList handlers = new HandlerList();

	/**
	 * Gets the handler list.
	 *
	 * @return the handler list
	 */
	public static HandlerList getHandlerList() {
		return handlers;
	}

	private boolean fake = false;

	/**
	 * Instantiates a new day change event.
	 */
	public DayChangeEvent() {
		super(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.bukkit.event.Event#getHandlers()
	 */
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public boolean isFake() {
		return fake;
	}

	public void setFake(boolean fake) {
		this.fake = fake;
	}

}
