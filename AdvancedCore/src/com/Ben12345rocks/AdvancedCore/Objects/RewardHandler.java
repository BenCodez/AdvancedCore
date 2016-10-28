package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
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
		setDefaultFolder(new File(plugin.getDataFolder(), "Rewards"));
	}

	private File defaultFolder;

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

		return new Reward(defaultFolder, reward);
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
		setupExample();
		for (File file : rewardFolders) {
			for (String reward : getRewardNames(file)) {
				if (!reward.equals("")) {
					if (!rewardExist(reward)) {
						rewards.add(new Reward(file, reward));
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

	/**
	 * Copy file.
	 *
	 * @param fileName
	 *            the file name
	 */
	private void copyFile(String fileName) {
		File file = new File(plugin.getDataFolder(), "Rewards" + File.separator
				+ fileName);
		if (!file.exists()) {
			plugin.saveResource("Rewards" + File.separator + fileName, true);
		}
	}

	/**
	 * Setup example.
	 */
	public void setupExample() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}

		copyFile("ExampleBasic.yml");
		copyFile("ExampleAdvanced.yml");
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

	/**
	 * Gets the reward files.
	 *
	 * @return the reward files
	 */
	public ArrayList<String> getRewardFiles(File folder) {
		String[] fileNames = folder.list();
		return Utils.getInstance().convertArray(fileNames);
	}

	/**
	 * Gets the reward names.
	 *
	 * @return the reward names
	 */
	public ArrayList<String> getRewardNames(File file) {
		ArrayList<String> rewardFiles = getRewardFiles(file);
		if (rewardFiles == null) {
			return new ArrayList<String>();
		}
		for (int i = 0; i < rewardFiles.size(); i++) {
			rewardFiles.set(i, rewardFiles.get(i).replace(".yml", ""));
		}

		Collections.sort(rewardFiles, String.CASE_INSENSITIVE_ORDER);

		return rewardFiles;
	}

	public File getDefaultFolder() {
		return defaultFolder;
	}

	public void setDefaultFolder(File defaultFolder) {
		this.defaultFolder = defaultFolder;
	}
}
