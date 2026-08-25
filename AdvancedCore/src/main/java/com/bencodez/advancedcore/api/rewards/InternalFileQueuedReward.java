package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Marker reward used when a file-backed nested reward must be persisted for
 * offline or timed replay. These rewards are resolved only through the internal
 * file-backed sub-reward registry and are never treated as standalone rewards.
 */
public final class InternalFileQueuedReward extends Reward {

	InternalFileQueuedReward(String name, ConfigurationSection section) {
		super(name, section);
	}
}
