package com.bencodez.advancedcore.api.time;

import java.util.Objects;

/**
 * Identifies one local DAY, WEEK, or MONTH transition.
 *
 * <p>The identifier remains the same while the transition is pending. A
 * listener that starts work after the event returns must retain a lease before
 * returning, persist its own idempotency receipt using {@link #getId()}, then
 * complete or fail that lease. The checker advances its legacy time marker
 * only when every lease succeeds.</p>
 */
public final class TimeChangeTransition {
	interface Owner {
		Lease retain(TimeChangeTransition transition);
		boolean isCancellationRequested(TimeChangeTransition transition);
	}

	private final Owner owner;
	private final String id;
	private final String periodKey;
	private final TimeType type;

	TimeChangeTransition(Owner owner, String id, String periodKey, TimeType type) {
		this.owner = Objects.requireNonNull(owner, "owner");
		this.id = Objects.requireNonNull(id, "id");
		this.periodKey = Objects.requireNonNull(periodKey, "periodKey");
		this.type = Objects.requireNonNull(type, "type");
	}

	public String getId() {
		return id;
	}

	public String getPeriodKey() {
		return periodKey;
	}

	public TimeType getType() {
		return type;
	}

	/**
	 * Retains this transition for asynchronous listener work. It may be called
	 * only while the transition is active, including after shutdown admission has
	 * closed for a transition that was already dispatched.
	 */
	public Lease retain() {
		return owner.retain(this);
	}

	/** True once shutdown or the lifecycle watchdog has cancelled this attempt. */
	public boolean isCancellationRequested() {
		return owner.isCancellationRequested(this);
	}

	/** One asynchronous participant in a transition. */
	public interface Lease {
		/** Records successful completion of this participant's work. */
		void complete();

		/** Records failure and keeps the transition pending for recovery. */
		void fail(Throwable failure);
	}
}
