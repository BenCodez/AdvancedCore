package com.bencodez.advancedcore.api.time.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.api.time.TimeChangeTransition;

// TODO: Auto-generated Javadoc
/**
 * The Class MonthChangeEvent.
 */
public class MonthChangeEvent extends Event {

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
	 * Instantiates a new month change event.
	 */
	public MonthChangeEvent() {
		this(null);
	}

	public MonthChangeEvent(TimeChangeTransition transition) {
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
