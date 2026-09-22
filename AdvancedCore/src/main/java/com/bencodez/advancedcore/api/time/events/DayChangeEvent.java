package com.bencodez.advancedcore.api.time.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.api.time.TimeChangeTransition;

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
	private final TimeChangeTransition transition;

	/**
	 * Instantiates a new day change event.
	 */
	public DayChangeEvent() {
		this(null);
	}

	public DayChangeEvent(TimeChangeTransition transition) {
		super(true);
		this.transition = transition;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.bukkit.event.Event#getHandlers()
	 */
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	/**
	 * Checks if fake.
	 *
	 * @return true if fake
	 */
	public boolean isFake() {
		return fake;
	}

	/** See {@link TimeChangeTransition} for durable listener acknowledgement. */
	public TimeChangeTransition getTransition() {
		return transition;
	}

	/**
	 * Sets fake.
	 *
	 * @param fake the fake flag
	 */
	public void setFake(boolean fake) {
		this.fake = fake;
	}

}
