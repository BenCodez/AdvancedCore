package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.Date;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Data.Data;

public class RewardHandler {

	/** The instance. */
	static RewardHandler instance = new RewardHandler();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of RewardHandler.
	 *
	 * @return single instance of RewardHandler
	 */
	public static RewardHandler getInstance() {
		return instance;
	}

	/** The rewards. */
	private ArrayList<Reward> rewards;

	/**
	 * Instantiates a new RewardHandler.
	 */
	private RewardHandler() {
	}

	/**
	 * Check delayed timed rewards.
	 */
	public void checkDelayedTimedRewards() {
		for (User user : Data.getInstance().getUsers()) {
			for (Reward reward : getRewards()) {
				long time = user.getTimedReward(reward);
				if (time != 0) {
					Date timeDate = new Date(time);
					if (new Date().after(timeDate)) {
						reward.giveRewardReward(user, true);
						user.setTimedReward(reward, 0);
					}
				}
			}
		}
	}

	public Reward getReward(String reward) {
		reward = reward.replace(" ", "_");

		for (Reward rewardFile : getRewards()) {
			if (rewardFile.name.equals(reward)) {
				return rewardFile;
			}
		}

		if (reward.equals("")) {
			plugin.getLogger()
			.warning(
					"Tried to get any empty reward file name, renaming to EmptyName");
			reward = "EmptyName";
		}

		return new Reward(reward);
	}

	public ArrayList<Reward> getRewards() {
		return rewards;
	}

	public void giveReward(User user, Reward reward) {
		giveReward(user, reward, user.isOnline());
	}

	public void giveReward(User user, Reward reward, boolean online) {
		reward.giveReward(user, online);
	}

	public void giveReward(User user, String reward) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), user.isOnline());
		}
	}

	public void giveReward(User user, String reward, boolean online) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online);
		}
	}

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		ConfigRewards.getInstance().setupExample();
		rewards = new ArrayList<Reward>();
		for (String reward : ConfigRewards.getInstance().getRewardNames()) {
			if (!reward.equals("")) {
				rewards.add(new Reward(reward));
			}
		}
		plugin.debug("Loaded rewards");

	}

}
