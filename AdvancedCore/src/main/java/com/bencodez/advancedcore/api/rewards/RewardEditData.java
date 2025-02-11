package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.command.gui.RewardEditGUI;

import lombok.Getter;

public class RewardEditData {
	private DirectlyDefinedReward directlyDefinedReward;
	@Getter
	private RewardEditData parent;

	private Reward reward;

	public RewardEditData(DirectlyDefinedReward directlyDefinedReward) {
		this.directlyDefinedReward = directlyDefinedReward;
	}

	public RewardEditData(DirectlyDefinedReward directlyDefinedReward, RewardEditData parent) {
		this.directlyDefinedReward = directlyDefinedReward;
		this.parent = parent;
	}

	public RewardEditData(Reward reward) {
		this.reward = reward;
	}

	public void createSection(String path) {
		if (reward != null) {
			reward.getConfig().createSection(path);
		} else if (directlyDefinedReward != null) {
			directlyDefinedReward.createSectionLocal(path);
		}
	}

	public ConfigurationSection getData() {
		if (reward != null) {
			return reward.getConfig().getConfigData();
		}
		if (directlyDefinedReward != null) {
			return directlyDefinedReward.getFileData().getConfigurationSection(directlyDefinedReward.getPath());
		}
		return null;
	}

	public String getName() {
		if (reward != null) {
			return reward.getName();
		}
		return directlyDefinedReward.getPath();
	}

	public Object getValue(String key) {
		if (reward != null) {
			return reward.getConfig().getConfigData().get(key);
		}
		if (directlyDefinedReward != null) {
			return directlyDefinedReward.getValue(key);
		}
		return null;
	}

	public boolean hasPath(String path) {
		if (reward != null) {
			return reward.getConfig().getConfigData().contains(path, false);
		}
		return directlyDefinedReward.getFileData().contains(directlyDefinedReward.getPath() + "." + path, false);
	}

	public void reOpenEditGUI(Player player) {
		RewardEditGUI.getInstance().openRewardGUI(player, this, getName());
	}

	public void save() {
		if (reward != null) {
			reward.getConfig().save(reward.getConfig().getFileData());
		} else if (directlyDefinedReward != null) {
			directlyDefinedReward.save();
		}
	}

	public void setValue(String path, Object value) {
		if (reward != null) {
			reward.getConfig().set(path, value);
		} else if (directlyDefinedReward != null) {
			directlyDefinedReward.setValue(path, value);
		}
	}
}
