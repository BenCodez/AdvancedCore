package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.array.ArrayUtils;

/**
 * Owns reward validation helper state and conflict/content checks.
 */
public class RewardValidator {

	private final RewardHandler handler;
	private final AdvancedCorePlugin plugin;
	private Set<String> validPaths = new HashSet<>();

	public RewardValidator(RewardHandler handler, AdvancedCorePlugin plugin) {
		this.handler = handler;
		this.plugin = plugin;
	}

	public void addValidPath(String path) {
		validPaths.add(path);
	}

	public void checkDirectlyDefinedRewardFiles() {
		ArrayList<String> directlyDefinedPaths = new ArrayList<>();
		for (DirectlyDefinedReward direct : handler.getDirectlyDefinedRewards()) {
			directlyDefinedPaths.add(direct.getPath().replace(".", "_"));
		}

		for (SubDirectlyDefinedReward direct : handler.getSubDirectlyDefinedRewards()) {
			directlyDefinedPaths.add(direct.getFullPath().replace(".", "_"));
		}

		for (Reward rewardFile : handler.getRewards()) {
			if (ArrayUtils.containsIgnoreCase(directlyDefinedPaths, rewardFile.getName())) {
				plugin.getLogger().warning("Found reward file conflict: " + rewardFile.getName()
						+ ", recommend deleting or renaming file to prevent issues");
			}
		}
	}

	public Set<String> getValidPaths() {
		return validPaths;
	}

	public boolean hasRewards(FileConfiguration data, String path) {
		if (data.isList(path)) {
			if (data.getList(path, new ArrayList<>()).size() != 0) {
				return true;
			}
		}
		if (data.isConfigurationSection(path)) {
			if (data.getConfigurationSection(path).getKeys(false).size() != 0) {
				return true;
			}
		}
		if (data.isString(path)) {
			if (!data.getString(path, "").equals("")) {
				return true;
			}
		}
		return false;
	}

	public void setValidPaths(Set<String> validPaths) {
		this.validPaths = validPaths;
	}
}
