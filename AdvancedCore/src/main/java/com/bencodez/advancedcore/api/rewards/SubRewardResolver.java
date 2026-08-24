package com.bencodez.advancedcore.api.rewards;

import java.util.HashSet;
import java.util.Set;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;

/**
 * Discovers directly-defined sub rewards and prevents recursive discovery loops.
 */
public class SubRewardResolver {

	private final RewardHandler handler;
	private final AdvancedCorePlugin plugin;

	public SubRewardResolver(RewardHandler handler, AdvancedCorePlugin plugin) {
		this.handler = handler;
		this.plugin = plugin;
	}

	public void checkSubRewards() {
		plugin.extraDebug("Checking directlydefined rewards for sub rewards");
		handler.getRewardRegistry().resetSubDirectlyDefinedRewards();
		for (DirectlyDefinedReward direct : handler.getDirectlyDefinedRewards()) {
			checkSubRewards(direct, new HashSet<>());
		}
	}

	public void checkSubRewards(DefinedReward direct) {
		checkSubRewards(direct, new HashSet<>());
	}

	private void checkSubRewards(DefinedReward direct, Set<String> activePaths) {
		String path = direct.getFullPath();
		if (!activePaths.add(path)) {
			plugin.getLogger().warning("Detected recursive sub reward path, skipping: " + path);
			return;
		}
		try {
			for (RewardInject inject : handler.getInjectedRewards()) {
				for (SubDirectlyDefinedReward sub : inject.subRewards(direct)) {
					String subPath = sub.getFullPath();
					if (activePaths.contains(subPath)) {
						plugin.getLogger().warning("Detected recursive sub reward path, skipping: " + subPath);
						continue;
					}
					handler.addSubDirectlyDefined(sub);
					checkSubRewards(sub, activePaths);
				}
			}
		} finally {
			activePaths.remove(path);
		}
	}
}
