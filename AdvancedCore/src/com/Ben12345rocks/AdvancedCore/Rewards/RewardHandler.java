package com.Ben12345rocks.AdvancedCore.Rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Exceptions.FileDirectoryException;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInject;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInjectConfigurationSection;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStartup;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIValueType;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.MiscUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import lombok.Getter;

/**
 * The Class RewardHandler.
 */
public class RewardHandler {

	@Getter
	private ArrayList<RewardInject> injectedRewards = new ArrayList<RewardInject>();

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
		plugin.getTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				if (usesTimed()) {
					plugin.addUserStartup(new UserStartup() {

						@Override
						public void onFinish() {

						}

						@Override
						public void onStart() {
							plugin.debug("Checking timed/delayed rewards");
						}

						@Override
						public void onStartUp(User user) {
							try {
								HashMap<String, ArrayList<Long>> timed = user.getTimedRewards();
								for (Entry<String, ArrayList<Long>> entry : timed.entrySet()) {
									for (Long time : entry.getValue()) {
										user.loadTimedDelayedTimer(time.longValue());
									}
								}
							} catch (Exception ex) {
								plugin.debug("Failed to update delayed/timed for: " + user.getUUID());
								plugin.debug(ex);
							}
						}
					});
				}
			}
		}, 0);

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

		return new Reward(reward);
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

	@SuppressWarnings("unchecked")
	public void giveReward(User user, ConfigurationSection data, String path, RewardOptions rewardOptions) {
		if (data == null) {
			plugin.getPlugin().getLogger().warning("ConfigurationSection is null, failing to give reward");
		}
		if (path == null) {
			plugin.getPlugin().getLogger().warning("Path is null, failing to give reward");
		}
		if (data.isList(path)) {
			for (String reward : (ArrayList<String>) data.getList(path, new ArrayList<String>())) {
				giveReward(user, reward, rewardOptions);
			}
		} else if (data.isConfigurationSection(path)) {
			String rewardName = "";
			String prefix = rewardOptions.getPrefix();
			if (prefix != null && !prefix.equals("")) {
				rewardName += prefix + "_";
			}
			rewardName += path.replace(".", "_");

			String suffix = rewardOptions.getSuffix();
			if (suffix != null && !suffix.equals("")) {
				rewardName += "_" + suffix;
			}
			ConfigurationSection section = data.getConfigurationSection(path);
			Reward reward = new Reward(rewardName, section);
			giveReward(user, reward, rewardOptions);

		} else {
			giveReward(user, data.getString(path, ""), rewardOptions);
		}
	}

	public void giveReward(User user, Reward reward, RewardOptions rewardOptions) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				reward.giveReward(user, rewardOptions);
			}
		});

	}

	public void giveReward(User user, String reward, RewardOptions rewardOptions) {
		if (!reward.equals("")) {
			if (reward.startsWith("/")) {
				MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), reward,
						rewardOptions.getPlaceholders());
				return;
			}
			giveReward(user, getReward(reward), rewardOptions);
		}
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
			if (rewardName.getName().equalsIgnoreCase(reward)) {
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

		if (AdvancedCoreHook.getInstance().getOptions().isLoadDefaultRewards()) {
			copyFile("ExampleBasic.yml");
			copyFile("ExampleAdvanced.yml");
		}
	}

	public void updateReward(Reward reward) {
		for (int i = getRewards().size() - 1; i >= 0; i--) {
			if (getRewards().get(i).getFile().getName().equals(reward.getFile().getName())) {
				getRewards().set(i, reward);
				return;
			}
		}
		getRewards().add(reward);
	}

	/*
	 * private void updateReward(Reward reward) { for (int i = getRewards().size() -
	 * 1; i >= 0; i--) { if
	 * (getRewards().get(i).getFile().getName().equals(reward.getFile().getName()))
	 * { getRewards().set(i, reward); return; } } getRewards().add(reward); }
	 */

	public boolean usesTimed() {
		for (Reward reward : getRewards()) {
			if (reward.isTimedEnabled() || reward.isDelayEnabled()) {
				return true;
			}
		}
		return false;
	}

	public void loadInjectedRewards() {
		injectedRewards.add(new RewardInjectConfigurationSection("ActionBar") {

			@Override
			public void onRewardRequested(User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				user.sendActionBar(
						StringUtils.getInstance().replacePlaceHolder(section.getString("Message", ""), placeholders),
						section.getInt("Delay", 30));
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), "ActionBar.Delay", null, EditGUIValueType.INT) {

					@Override
					public void setValue(Player player, Object value) {
						Reward reward = (Reward) getInv().getData("Reward");
						reward.getConfig().setActionBarDelay((int) value);
						plugin.reload();
					}
				}).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "ActionBar.Message", null,
						EditGUIValueType.STRING) {

					@Override
					public void setValue(Player player, Object value) {
						Reward reward = (Reward) getInv().getData("Reward");
						reward.getConfig().setActionBarMsg((String) value);
						plugin.reload();
					}
				}));
	}
}
