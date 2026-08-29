package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/**
 * A generated directly-defined reward that is still referenced by a persisted
 * offline or timed queue. These rewards are intentionally restricted to the
 * users whose persisted queue references the generated reward name.
 */
public final class QueuedGeneratedReward extends Reward {

	private final Set<String> allowedUserUuids;

	QueuedGeneratedReward(File folder, String reward, Set<String> allowedUserUuids) {
		super(folder, reward);
		this.allowedUserUuids = Collections.unmodifiableSet(new HashSet<>(allowedUserUuids));
	}

	@Override
	public void giveReward(AdvancedCoreUser user, RewardOptions rewardOptions) {
		if (user == null || user.getUUID() == null || !allowedUserUuids.contains(user.getUUID())) {
			plugin.getLogger().warning("Blocked generated queued reward " + getRewardName()
					+ " for a user without a matching persisted queue entry");
			return;
		}
		super.giveReward(user, rewardOptions);
	}

	@Override
	public boolean isGeneratedSnapshotCreated() {
		// A loaded generated snapshot must retain snapshot provenance if execution is
		// deferred again (paused rewards, vanish-as-offline, or another offline retry).
		return true;
	}

	@Override
	public void validate() {
		// This file is a persisted snapshot created from a reward that was already
		// validated before it was queued. Revalidating requires the live injected
		// registries and adds no security boundary; execution is instead restricted
		// to UUIDs with a matching persisted queue reference.
	}

	public Set<String> getAllowedUserUuids() {
		return allowedUserUuids;
	}
}
