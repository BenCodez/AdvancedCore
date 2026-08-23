package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * Owns registered reward state and reward lookup behavior.
 * <p>
 * {@link RewardHandler} remains the compatibility facade for callers while
 * registry-specific state and lookup responsibilities live here.
 */
public class RewardRegistry {

	private final AdvancedCorePlugin plugin;
	private final CopyOnWriteArrayList<DirectlyDefinedReward> directlyDefinedRewards = new CopyOnWriteArrayList<>();
	private CopyOnWriteArrayList<SubDirectlyDefinedReward> subDirectlyDefinedRewards = new CopyOnWriteArrayList<>();
	private List<Reward> rewards;

	public RewardRegistry(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		resetRewards();
	}

	public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
		if (getDirectlyDefined(directlyDefinedReward.getPath()) != null) {
			plugin.extraDebug(
					"DirectlyDefinedReward with path already exists, skipping: " + directlyDefinedReward.getPath());
			return;
		}
		plugin.extraDebug("Adding directlydefined reward handle: " + directlyDefinedReward.getPath()
				+ ", isdirectlydefined: " + directlyDefinedReward.isDirectlyDefined());
		directlyDefinedRewards.add(directlyDefinedReward);
	}

	public void addSubDirectlyDefined(SubDirectlyDefinedReward subDirectlyDefinedReward) {
		plugin.extraDebug("Adding subdirectlydefined reward handle: " + subDirectlyDefinedReward.getFullPath()
				+ ", isdirectlydefined: " + subDirectlyDefinedReward.isDirectlyDefined());
		subDirectlyDefinedRewards.add(subDirectlyDefinedReward);
	}

	public DirectlyDefinedReward getDirectlyDefined(String path) {
		for (DirectlyDefinedReward direct : directlyDefinedRewards) {
			if (direct.getPath().equalsIgnoreCase(path)) {
				return direct;
			}
		}
		return null;
	}

	public CopyOnWriteArrayList<DirectlyDefinedReward> getDirectlyDefinedRewards() {
		return directlyDefinedRewards;
	}

	public Reward getReward(String reward) {
		if (reward == null) {
			reward = "";
		}
		reward = reward.replace(" ", "_");

		if (reward.equals("")) {
			plugin.getLogger().warning("Tried to get any empty reward file name, renaming to EmptyName");
			reward = "EmptyName";
		}

		if (reward.equalsIgnoreCase("examplebasic") || reward.equalsIgnoreCase("exampleadvanced")) {
			plugin.getLogger().warning("Using example rewards as a reward, be carefull");
		}

		for (DirectlyDefinedReward direct : directlyDefinedRewards) {
			if (direct.getPath().replace(".", "_").equals(reward)) {
				plugin.debug("Using directlydefined reward for: " + reward);
				return direct.getReward();
			}
		}

		for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
			if (matchesSubDirectlyDefined(direct, reward)) {
				plugin.debug("Using subdirectlydefined reward for: " + reward);
				return direct.getReward();
			}
		}

		for (Reward rewardFile : getRewards()) {
			if (rewardFile.getName().equalsIgnoreCase(reward)) {
				return rewardFile;
			}
		}

		return new Reward(reward);
	}

	public List<Reward> getRewards() {
		if (rewards == null) {
			resetRewards();
		}
		return rewards;
	}

	public SubDirectlyDefinedReward getSubDirectlyDefined(String path) {
		for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
			if (matchesSubDirectlyDefined(direct, path)) {
				return direct;
			}
		}
		return null;
	}

	public CopyOnWriteArrayList<SubDirectlyDefinedReward> getSubDirectlyDefinedRewards() {
		return subDirectlyDefinedRewards;
	}

	public boolean hasDirectRewardHandle(String reward) {
		for (DirectlyDefinedReward direct : directlyDefinedRewards) {
			if (direct.getPath().replace(".", "_").equals(reward)) {
				return true;
			}
		}
		for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
			if (matchesSubDirectlyDefined(direct, reward)) {
				return true;
			}
		}
		return false;
	}

	public boolean rewardExist(String reward) {
		if (reward.equals("")) {
			return false;
		}
		for (Reward rewardName : getRewards()) {
			if (rewardName.getName().equalsIgnoreCase(reward)) {
				return true;
			}
		}
		return false;
	}

	public void resetRewards() {
		rewards = Collections.synchronizedList(new ArrayList<Reward>());
	}

	public void resetSubDirectlyDefinedRewards() {
		subDirectlyDefinedRewards = new CopyOnWriteArrayList<>();
	}

	public void updateReward(Reward reward) {
		reward.validate();
		for (int i = getRewards().size() - 1; i >= 0; i--) {
			if (getRewards().get(i).getFile().getPath().equals(reward.getFile().getPath())) {
				getRewards().set(i, reward);
				return;
			}
		}
		getRewards().add(reward);
	}

	private boolean matchesSubDirectlyDefined(SubDirectlyDefinedReward direct, String reward) {
		return direct.getFullPath().equalsIgnoreCase(reward)
				|| direct.getFullPath().replace(".", "_").equalsIgnoreCase(reward)
				|| direct.getFullPath().equalsIgnoreCase(reward.replaceAll("_", "."));
	}
}
