package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardOptionsTest {

	@Test
	void nestedDispatchPreservesCapturedLivePlayerState() {
		RewardOptions nested = new RewardOptions().captureLivePlayerState(false, true)
				.copyForNestedDispatch("parent/child:0");

		assertTrue(nested.isLivePlayerStateSet());
		assertFalse(nested.isOnline());
		assertTrue(nested.isLivePlayerVanished());
	}

	@Test
	void builtInNestedDispatchInheritsCapturedLivePlayerStateFromReplay() {
		RewardOptions parent = new RewardOptions().captureLivePlayerState(false, true);
		RewardOptions child = Reward.withReplayState(new RewardOptions(), Reward.replayStateFor(parent),
				"parent", "child:0", "occurrence");

		assertTrue(child.isLivePlayerStateSet());
		assertFalse(child.isOnline());
		assertTrue(child.isLivePlayerVanished());
	}

	@Test
	void deferredChildHelperInheritsCapturedLivePlayerStateFromReplay() {
		RewardOptions parent = new RewardOptions().captureLivePlayerState(false, true);
		RewardOptions child = Reward.withReplayState(new RewardOptions(), Reward.replayStateFor(parent));

		assertTrue(child.isLivePlayerStateSet());
		assertFalse(child.isOnline());
		assertTrue(child.isLivePlayerVanished());
	}
}
