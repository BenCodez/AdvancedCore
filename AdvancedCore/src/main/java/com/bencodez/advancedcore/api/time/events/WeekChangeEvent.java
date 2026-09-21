package com.bencodez.advancedcore.api.time.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.api.time.TimeChangeTransition;

// TODO: Auto-generated Javadoc
/**
 * The Class WeekChangeEvent.
 */
public class WeekChangeEvent extends Event {

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
	 * Instantiates a new week change event.
	 */
	public WeekChangeEvent() {
		this(null);
	}

	public WeekChangeEvent(TimeChangeTransition transition) {
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

	public boolean isFake() {
		return fake;
	}

	/** See {@link TimeChangeTransition} for durable listener acknowledgement. */
	public TimeChangeTransition getTransition() {
		return transition;
	}

	public void setFake(boolean fake) {
		this.fake = fake;
	}

}
