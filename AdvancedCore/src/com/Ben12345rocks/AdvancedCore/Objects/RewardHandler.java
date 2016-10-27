package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;

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

	private ArrayList<File> rewardFolders;

	public void addRewardFolder(File file) {
		if (file.isDirectory()) {
			rewardFolders.add(file);
			loadRewards();
		}
	}

	/**
	 * Check delayed timed rewards.
	 */
	public synchronized void checkDelayedTimedRewards() {
		com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().run(
				new Runnable() {

					@Override
					public void run() {
						for (User user : UserManager.getInstance().getUsers()) {
							for (Reward reward : getRewards()) {
								ArrayList<Long> times = user
										.getTimedReward(reward);
								for (Long t : times) {
									long time = t.longValue();
									if (time != 0) {
										Date timeDate = new Date(time);
										if (new Date().after(timeDate)) {
											reward.giveRewardReward(user, true);
											user.removeTimedReward(reward, time);
										}
									}
								}
							}
						}
					}
				});

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

	public void giveReward(User user, Reward reward, boolean online,
			boolean giveOffline) {
		reward.giveReward(user, online, giveOffline);
	}

	public void giveReward(User user, String reward, boolean online,
			boolean giveOffline) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online, giveOffline);
		}
	}

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		rewards = new ArrayList<Reward>();
		ConfigRewards.getInstance().setupExample();
		for (File file : rewardFolders) {
			for (String reward : ConfigRewards.getInstance().getRewardNames(
					file)) {
				if (!reward.equals("")) {
					if (!rewardExist(reward)) {
						rewards.add(new Reward(reward));
					} else {
						plugin.getLogger().warning(
								"Detected that " + reward
										+ " already exists, cannot load file "
										+ file.getName() + "/" + reward);
					}
				} else {
					plugin.getLogger()
							.warning(
									"Detected getting a reward file with an empty name! That means you either didn't type a name or didn't properly make an empty list");
				}
			}
		}
		plugin.debug("Loaded rewards");

	}

	public boolean rewardExist(String reward) {
		if (reward.equals("")) {
			return false;
		}
		for (Reward rewardName : getRewards()) {
			if (rewardName.getRewardName().equals(reward)) {
				return true;
			}
		}
		return false;
	}
}
