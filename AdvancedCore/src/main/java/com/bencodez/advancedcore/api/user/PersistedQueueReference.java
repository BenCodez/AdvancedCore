package com.bencodez.advancedcore.api.user;

/**
 * Capability wrapper for a reward reference read from this user's persisted
 * offline/timed queue. The constructor is package-private so ordinary reward
 * callers cannot manufacture queue provenance through RewardOptions.
 */
public final class PersistedQueueReference {

	private final String reference;

	PersistedQueueReference(String reference) {
		this.reference = reference;
	}

	public String getReference() {
		return reference;
	}
}
