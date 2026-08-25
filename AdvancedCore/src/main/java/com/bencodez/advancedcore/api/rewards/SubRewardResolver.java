package com.bencodez.advancedcore.api.rewards;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;

/**
 * Discovers sub rewards and prevents recursive discovery loops.
 */
public class SubRewardResolver {

	private final RewardHandler handler;
	private final AdvancedCorePlugin plugin;
	private final CopyOnWriteArrayList<SubDirectlyDefinedReward> fileBackedSubRewards = new CopyOnWriteArrayList<>();

	public SubRewardResolver(RewardHandler handler, AdvancedCorePlugin plugin) {
		this.handler = handler;
		this.plugin = plugin;
	}

	public void checkSubRewards() {
		plugin.extraDebug("Checking directlydefined rewards for sub rewards");
		handler.getRewardRegistry().resetSubDirectlyDefinedRewards();
		fileBackedSubRewards.clear();
		for (DirectlyDefinedReward direct : handler.getDirectlyDefinedRewards()) {
			checkSubRewards(direct, new HashSet<>(), true);
		}

		plugin.extraDebug("Checking reward files for internal sub rewards");
		for (Reward reward : handler.getRewards()) {
			checkSubRewards(new RewardFileDefinedReward(reward), new HashSet<>(), false);
		}
	}

	public void checkSubRewards(DefinedReward direct) {
		checkSubRewards(direct, new HashSet<>(), !(direct instanceof RewardFileDefinedReward));
	}

	public SubDirectlyDefinedReward getFileBackedSubReward(String path) {
		String normalized = RewardRegistry.normalizeDirectPath(path);
		for (SubDirectlyDefinedReward sub : fileBackedSubRewards) {
			if (RewardRegistry.normalizeDirectPath(sub.getFullPath()).equals(normalized)) {
				return sub;
			}
		}
		return null;
	}

	private void checkSubRewards(DefinedReward direct, Set<String> activePaths, boolean exposeStandalone) {
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
					if (exposeStandalone) {
						handler.addSubDirectlyDefined(sub);
					} else {
						addFileBackedSubReward(sub);
					}
					checkSubRewards(sub, activePaths, exposeStandalone);
				}
			}
		} finally {
			activePaths.remove(path);
		}
	}

	private void addFileBackedSubReward(SubDirectlyDefinedReward sub) {
		if (getFileBackedSubReward(sub.getFullPath()) == null) {
			fileBackedSubRewards.add(sub);
		}
	}
}
