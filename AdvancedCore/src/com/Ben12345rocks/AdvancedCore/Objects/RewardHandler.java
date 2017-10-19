package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Exceptions.FileDirectoryException;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

/**
 * The Class RewardHandler.
 */
public class RewardHandler {

	/** The instance. */
	static RewardHandler instance = new RewardHandler();

	/**
	 * Gets the single instance of RewardHandler.
	 *
	 * @return single instance of RewardHandler
	 */
	public static RewardHandler getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/** The rewards. */
	private List<Reward> rewards;

	/** The default folder. */
	private File defaultFolder;

	/** The reward folders. */
	private ArrayList<File> rewardFolders;

	/**
	 * Instantiates a new reward handler.
	 */
	private RewardHandler() {
		rewardFolders = new ArrayList<File>();
		setDefaultFolder(new File(AdvancedCoreHook.getInstance().getPlugin().getDataFolder(), "Rewards"));
	}

	/**
	 * Adds the reward folder.
	 *
	 * @param file
	 *            the file
	 */
	public void addRewardFolder(File file) {
		file.mkdirs();
		if (file.isDirectory()) {
			if (!rewardFolders.contains(file)) {
				rewardFolders.add(file);
				loadRewards();
			}
		} else {
			plugin.debug(file.getAbsolutePath());
			try {
				throw new FileDirectoryException("File is not a directory");
			} catch (FileDirectoryException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Check delayed timed rewards.
	 */
	public synchronized void checkDelayedTimedRewards() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				if (usesTimed()) {
					for (String uuid : UserManager.getInstance().getAllUUIDs()) {
						try {
							User user = UserManager.getInstance().getUser(new UUID(uuid));
							HashMap<Reward, ArrayList<Long>> timed = user.getTimedRewards();
							for (Entry<Reward, ArrayList<Long>> entry : timed.entrySet()) {
								for (Long time : entry.getValue()) {
									user.loadTimedDelayedTimer(time.longValue());
								}
							}
						} catch (Exception ex) {
							plugin.debug("Failed to update delayed/timed for: " + uuid);
							plugin.debug(ex);
						}
					}
				}
			}
		});

	}

	/**
	 * Copy file.
	 *
	 * @param fileName
	 *            the file name
	 */
	private void copyFile(String fileName) {
		File file = new File(plugin.getPlugin().getDataFolder(), "Rewards" + File.separator + fileName);
		if (!file.exists()) {
			plugin.getPlugin().saveResource("Rewards" + File.separator + fileName, true);
		}
	}

	/**
	 * Gets the default folder.
	 *
	 * @return the default folder
	 */
	public File getDefaultFolder() {
		return defaultFolder;
	}

	/**
	 * Gets the reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the reward
	 */
	public Reward getReward(String reward) {
		reward = reward.replace(" ", "_");

		for (Reward rewardFile : getRewards()) {
			if (rewardFile.getName().equalsIgnoreCase(reward)) {
				return rewardFile;
			}
		}

		if (reward.equals("")) {
			plugin.getPlugin().getLogger().warning("Tried to get any empty reward file name, renaming to EmptyName");
			reward = "EmptyName";
		}

		return new Reward(defaultFolder, reward);
	}

	/**
	 * Gets the reward files.
	 *
	 * @param folder
	 *            the folder
	 * @return the reward files
	 */
	public ArrayList<String> getRewardFiles(File folder) {
		String[] fileNames = folder.list();
		return ArrayUtils.getInstance().convert(fileNames);
	}

	/**
	 * Gets the reward names.
	 *
	 * @param file
	 *            the file
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

	/**
	 * Gets the rewards.
	 *
	 * @return the rewards
	 */
	public List<Reward> getRewards() {
		if (rewards == null) {
			rewards = Collections.synchronizedList(new ArrayList<Reward>());
		}
		return rewards;
	}

	public void giveReward(User user, FileConfiguration data, String path) {
		giveReward(user, data, path, user.isOnline(), true);
	}

	public void giveReward(User user, FileConfiguration data, String path, boolean online) {
		giveReward(user, data, path, online, true);
	}

	public void giveReward(User user, FileConfiguration data, String path, boolean online, boolean giveOffline) {
		giveReward(user, "", data, path, online, giveOffline);
	}

	/**
	 * Give reward
	 *
	 * @param user
	 *            the user
	 * @param reward
	 *            the reward
	 * @param online
	 *            the online
	 */
	@Deprecated
	public void giveReward(User user, Reward reward, boolean online) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, online);
			}
		});

	}

	/**
	 * Give reward.
	 *
	 * @param user
	 *            the user
	 * @param reward
	 *            the reward
	 * @param online
	 *            the online
	 * @param giveOffline
	 *            the give offline
	 */
	@Deprecated
	public void giveReward(User user, Reward reward, boolean online, boolean giveOffline) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, online, giveOffline);
			}
		});
	}

	@Deprecated
	public void giveReward(User user, Reward reward, boolean online, boolean giveOffline, boolean checkTimed) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, online, giveOffline, checkTimed);
			}
		});
	}

	@Deprecated
	public void giveReward(User user, Reward reward, boolean online, boolean giveOffline, boolean checkTimed,
			boolean ignoreChance, HashMap<String, String> placeholders) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, online, giveOffline, checkTimed, ignoreChance, placeholders);
			}
		});
	}

	@Deprecated
	public void giveReward(User user, Reward reward, boolean online, boolean giveOffline, boolean checkTimed,
			HashMap<String, String> placeholders) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, online, giveOffline, checkTimed, placeholders);
			}
		});
	}

	@Deprecated
	public void giveReward(User user, String reward) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), user.isOnline());
		}
	}

	/**
	 * Give reward.
	 *
	 * @param user
	 *            the user
	 * @param reward
	 *            the reward
	 * @param online
	 *            the online
	 */
	@Deprecated
	public void giveReward(User user, String reward, boolean online) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online);
		}
	}

	/**
	 * Give reward.
	 *
	 * @param user
	 *            the user
	 * @param reward
	 *            the reward
	 * @param online
	 *            the online
	 * @param giveOffline
	 *            the give offline
	 */
	@Deprecated
	public void giveReward(User user, String reward, boolean online, boolean giveOffline) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online, giveOffline);
		}
	}

	@Deprecated
	public void giveReward(User user, String reward, boolean online, boolean giveOffline, boolean checkTimed) {
		giveReward(user, reward, online, giveOffline, checkTimed, null);
	}

	@Deprecated
	public void giveReward(User user, String reward, boolean online, boolean giveOffline, boolean checkTimed,
			boolean ignoreChance, HashMap<String, String> placeholders) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online, giveOffline, checkTimed, ignoreChance, placeholders);
		}
	}

	@Deprecated
	public void giveReward(User user, String reward, boolean online, boolean giveOffline, boolean checkTimed,
			HashMap<String, String> placeholders) {
		if (!reward.equals("")) {
			giveReward(user, getReward(reward), online, giveOffline, checkTimed, placeholders);
		}
	}

	public void giveReward(User user, String prefix, FileConfiguration data, String path) {
		giveReward(user, prefix, data, path, user.isOnline(), true);
	}

	public void giveReward(User user, String prefix, FileConfiguration data, String path, boolean online) {
		giveReward(user, prefix, data, path, online, true);
	}

	public void giveReward(User user, String prefix, FileConfiguration data, String path, boolean online,
			boolean giveOffline) {
		giveReward(user, prefix, data, path, online, giveOffline, null);
	}

	@SuppressWarnings("unchecked")
	public void giveReward(User user, String prefix, FileConfiguration data, String path, boolean online,
			boolean giveOffline, boolean checkTimed, boolean ignoreChance, HashMap<String, String> placeholders) {
		if (data.isList(path)) {
			for (String reward : (ArrayList<String>) data.getList(path, new ArrayList<String>())) {
				giveReward(user, reward, online, giveOffline, checkTimed, ignoreChance, placeholders);
			}
		} else if (data.isConfigurationSection(path)) {
			String rewardName = "";
			if (prefix != null && !prefix.equals("")) {
				rewardName += prefix + "_";
			}
			rewardName = path.replace(".", "_");
			ConfigurationSection section = data.getConfigurationSection(path);
			Reward reward = new Reward(rewardName, section);
			/*Reward reward;
			if (!rewardExist(rewardName)) {
				reward = new Reward(rewardName);
			} else {
				reward = getReward(rewardName);
			}
			if (reward.getConfig().getData().getConfigurationSection("").getKeys(true).size() != 0) {
				if (!reward.getConfig().getData().getConfigurationSection("").getKeys(true)
						.equals(section.getKeys(true))) {
					plugin.getPlugin().getLogger().warning(
							"Detected a reward file edited when it should be edited where directly defined, overriding");
				}
			}
			reward.getConfig().setData(section);
			reward.getConfig().getData().options().header(
					"Directly defined reward file from path: '" + path + "' ANY EDITS HERE CAN GET OVERRIDDEN!");
			updateReward(reward);*/
			giveReward(user, reward, online, giveOffline, checkTimed, ignoreChance, placeholders);

		} else {
			giveReward(user, data.getString(path, ""), online, giveOffline, checkTimed, ignoreChance, placeholders);
		}
	}

	public void giveReward(User user, String prefix, FileConfiguration data, String path, boolean online,
			boolean giveOffline, boolean checkTimed, HashMap<String, String> placeholders) {
		giveReward(user, prefix, data, path, online, giveOffline, checkTimed, false, placeholders);
	}

	public void giveReward(User user, String prefix, FileConfiguration data, String path, boolean online,
			boolean giveOffline, HashMap<String, String> placeholders) {
		giveReward(user, prefix, data, path, online, giveOffline, true, placeholders);
	}

	public boolean hasRewards(FileConfiguration data, String path) {
		if (data.isList(path)) {
			if (data.getList(path, new ArrayList<String>()).size() != 0) {
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

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		rewards = Collections.synchronizedList(new ArrayList<Reward>());
		setupExample();
		for (File file : rewardFolders) {
			for (String reward : getRewardNames(file)) {
				if (!reward.equals("")) {
					if (!rewardExist(reward)) {
						try {
							rewards.add(new Reward(file, reward));
							plugin.extraDebug("Loaded Reward File: " + file.getAbsolutePath() + "/" + reward);
						} catch (Exception e) {
							plugin.getPlugin().getLogger()
									.severe("Failed to load reward file " + reward + ".yml: " + e.getMessage());
							e.printStackTrace();
						}
					} else {
						plugin.getPlugin().getLogger().warning("Detected that a reward file named " + reward
								+ " already exists, cannot load reward file " + file.getAbsolutePath() + "/" + reward);
					}
				} else {
					plugin.getPlugin().getLogger().warning(
							"Detected getting a reward file with an empty name! That means you either didn't type a name or didn't properly make an empty list");
				}
			}
		}
		plugin.debug("Loaded rewards");

	}

	/**
	 * Reward exist.
	 *
	 * @param reward
	 *            the reward
	 * @return true, if successful
	 */
	public boolean rewardExist(String reward) {
		if (reward.equals("")) {
			return false;
		}
		for (Reward rewardName : getRewards()) {
			if (rewardName.getRewardName().equalsIgnoreCase(reward)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the default folder.
	 *
	 * @param defaultFolder
	 *            the new default folder
	 */
	public void setDefaultFolder(File defaultFolder) {
		this.defaultFolder = defaultFolder;
	}

	/**
	 * Setup example.
	 */
	public void setupExample() {
		if (!plugin.getPlugin().getDataFolder().exists()) {
			plugin.getPlugin().getDataFolder().mkdir();
		}

		copyFile("ExampleBasic.yml");
		copyFile("ExampleAdvanced.yml");
	}

	/*private void updateReward(Reward reward) {
		for (int i = getRewards().size() - 1; i >= 0; i--) {
			if (getRewards().get(i).getFile().getName().equals(reward.getFile().getName())) {
				getRewards().set(i, reward);
				return;
			}
		}
		getRewards().add(reward);
	}*/

	public synchronized boolean usesTimed() {
		for (Reward reward : getRewards()) {
			if (reward.isTimedEnabled() || reward.isDelayEnabled()) {
				return true;
			}
		}
		return false;
	}
}
