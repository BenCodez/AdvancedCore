package com.bencodez.advancedcore.api.time.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.time.TimeChangeTransition;

// TODO: Auto-generated Javadoc
/**
 * The Class DayChangeEvent.
 */
public class PreDateChangedEvent extends Event {

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

	private TimeType timeType;
	private final TimeChangeTransition transition;

	public PreDateChangedEvent(TimeType time) {
		this(time, null);
	}

	public PreDateChangedEvent(TimeType time, TimeChangeTransition transition) {
		super(true);
		this.timeType = time;
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
	 * @return the timeType
	 */
	public TimeType getTimeType() {
		return timeType;
	}

	/**
	 * Returns the durable transition for locally detected changes. It is null for
	 * legacy/manual dispatches that do not own a persistent time marker.
	 */
	public TimeChangeTransition getTransition() {
		return transition;
	}

	public boolean isFake() {
		return fake;
	}

	public void setFake(boolean fake) {
		this.fake = fake;
	}

	/**
	 * @param timeType the timeType to set
	 */
	public void setTimeType(TimeType timeType) {
		this.timeType = timeType;
	}

}
