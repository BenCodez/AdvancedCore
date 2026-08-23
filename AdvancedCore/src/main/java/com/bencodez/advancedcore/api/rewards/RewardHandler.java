package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.exceptions.FileDirectoryException;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.command.gui.RewardEditGUI;
import com.bencodez.simpleapi.array.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class RewardHandler.
 */
public class RewardHandler {

	/** The default folder. */
	private File defaultFolder;

	@Getter
	private final RewardRegistry rewardRegistry;

	@Getter
	private CopyOnWriteArrayList<RequirementInject> injectedRequirements = new CopyOnWriteArrayList<RequirementInject>();

	@Getter
	private CopyOnWriteArrayList<RewardInject> injectedRewards = new CopyOnWriteArrayList<RewardInject>();

	@Getter
	private CopyOnWriteArrayList<RewardPlaceholderHandle> placeholders = new CopyOnWriteArrayList<RewardPlaceholderHandle>();

	AdvancedCorePlugin plugin;

	/** The reward folders. */
	private ArrayList<File> rewardFolders;

	@Getter
	private ScheduledExecutorService delayedTimer = Executors.newSingleThreadScheduledExecutor();

	@Getter
	@Setter
	private Set<String> validPaths = new HashSet<>();

	public RewardHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		rewardRegistry = new RewardRegistry(plugin);
		rewardFolders = new ArrayList<>();
		setDefaultFolder(new File(plugin.getDataFolder(), "Rewards"));
	}

	public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
		rewardRegistry.addDirectlyDefined(directlyDefinedReward);
	}

	public void addInjectedRequirements(RequirementInject inject) {
		injectedRequirements.add(inject);
		sortInjectedRequirements();
	}

	public void addInjectedReward(RewardInject inject) {
		injectedRewards.add(inject);
		sortInjectedRewards();
	}

	public void addPlaceholder(RewardPlaceholderHandle handle) {
		placeholders.add(handle);
	}

	public void addRewardFolder(File file) {
		addRewardFolder(file, true, true);
	}

	public void addRewardFolder(File file, boolean load, boolean create) {
		if (create) {
			file.mkdirs();
		}
		if (!file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			if (!rewardFolders.contains(file)) {
				rewardFolders.add(file);
				if (load) {
					loadRewards();
				}
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

	public void addSubDirectlyDefined(SubDirectlyDefinedReward subDirectlyDefinedReward) {
		rewardRegistry.addSubDirectlyDefined(subDirectlyDefinedReward);
	}

	public void addValidPath(String path) {
		validPaths.add(path);
	}

	public void checkDirectlyDefinedRewardFiles() {
		ArrayList<String> directlyDefinedPaths = new ArrayList<String>();
		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			directlyDefinedPaths.add(direct.getPath().replace(".", "_"));
		}

		for (SubDirectlyDefinedReward direct : getSubDirectlyDefinedRewards()) {
			directlyDefinedPaths.add(direct.getFullPath().replace(".", "_"));
		}

		for (Reward rewardFile : getRewards()) {
			if (ArrayUtils.containsIgnoreCase(directlyDefinedPaths, rewardFile.getName())) {
				plugin.getLogger().warning("Found reward file conflict: " + rewardFile.getName()
						+ ", recommend deleting or renaming file to prevent issues");
			}
		}
	}

	public void checkDirectlyDefined() {
		for (Reward rewardFile : getRewards()) {
			File folder = rewardFile.getConfig().getRewardFolder();
			if (folder != null && folder.getName().equalsIgnoreCase("DirectlyDefined")) {
				if (hasDirectRewardHandle(rewardFile.getName())) {
					rewardFile.getFile().delete();
				}
			}
		}
		loadRewards();
	}

	public void checkSubRewards() {
		plugin.extraDebug("Checking directlydefined rewards for sub rewards");
		rewardRegistry.resetSubDirectlyDefinedRewards();
		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			checkSubRewards(direct);
		}

		plugin.extraDebug("Checking reward file for sub rewards");
		for (Reward reward : getRewards()) {
			checkSubRewards(new RewardFileDefinedReward(reward));
		}
	}

	public void checkSubRewards(DefinedReward direct) {
		for (RewardInject inject : getInjectedRewards()) {
			for (SubDirectlyDefinedReward sub : inject.subRewards(direct)) {
				addSubDirectlyDefined(sub);
				checkSubRewards(sub);
			}
		}
	}

	/**
	 * Copy file.
	 *
	 * @param fileName the file name
	 */
	private void copyFile(String fileName) {
		File file = new File(plugin.getDataFolder(), "Rewards" + File.separator + fileName);
		if (!file.exists()) {
			plugin.saveResource("Rewards" + File.separator + fileName, true);
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

	public DirectlyDefinedReward getDirectlyDefined(String path) {
		return rewardRegistry.getDirectlyDefined(path);
	}

	public CopyOnWriteArrayList<DirectlyDefinedReward> getDirectlyDefinedRewards() {
		return rewardRegistry.getDirectlyDefinedRewards();
	}

	private String getFileExtension(File file) {
		String name = file.getName();
		int lastIndexOf = name.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return ""; // empty extension
		}
		return name.substring(lastIndexOf);
	}

	public Reward getReward(ConfigurationSection data, String path, RewardOptions rewardOptions) {
		if (path == null) {
			plugin.getLogger().warning("Path is null, failing to give reward");
			return null;
		}
		if (data == null) {
			plugin.getLogger().warning("ConfigurationSection is null, failing to give reward: " + path);
			return null;
		}
		if (data.isConfigurationSection(path)) {
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
			return new Reward(rewardName, section);
		}
		return null;
	}

	/**
	 * Gets the reward.
	 *
	 * @param reward the reward
	 * @return the reward
	 */
	public Reward getReward(String reward) {
		return rewardRegistry.getReward(reward);
	}

	public Reward getRewardDirectlyDefined(String reward) {
		if (reward == null) {
			reward = "";
		}
		reward = reward.replace(" ", "_");

		for (Reward rewardFile : getRewards()) {
			File folder = rewardFile.getConfig().getRewardFolder();
			if (folder != null && folder.getName().equalsIgnoreCase("DirectlyDefined")) {
				if (rewardFile.getName().equalsIgnoreCase(reward)) {
					return rewardFile;
				}
			}
		}

		if (reward.equals("")) {
			plugin.getLogger().warning("Tried to get any empty reward file name, renaming to EmptyName");
			reward = "EmptyName";
		}

		if (reward.equalsIgnoreCase("examplebasic") || reward.equalsIgnoreCase("exampleadvanced")) {
			plugin.getLogger().warning("Using example rewards as a reward, becarefull");
		}

		File directFolder = new File(getDefaultFolder().getAbsolutePath() + File.separator + "DirectlyDefined");
		directFolder.mkdirs();
		return new Reward(directFolder, reward);
	}

	/**
	 * Gets the reward files.
	 *
	 * @param folder the folder
	 * @return the reward files
	 */
	public ArrayList<String> getRewardFiles(File folder) {
		ArrayList<String> fileNames = new ArrayList<String>();
		if (folder != null && folder.exists()) {
			File[] files = folder.listFiles();
			if (files == null) {
				return fileNames;
			}
			for (File file : files) {
				if (getFileExtension(file).equals(".yml")) {
					fileNames.add(file.getName());
				}
			}
		}
		return fileNames;
	}

	/**
	 * Gets the reward names.
	 *
	 * @param file the file
	 * @return the reward names
	 */
	public ArrayList<String> getRewardNames(File file) {
		ArrayList<String> rewardFiles = getRewardFiles(file);
		if (rewardFiles == null) {
			return new ArrayList<>();
		}
		for (int i = 0; i < rewardFiles.size(); i++) {
			if (rewardFiles.get(i).contains(".yml")) {
				rewardFiles.set(i, rewardFiles.get(i).replace(".yml", ""));
			} else {
				plugin.debug("Not a proper reward file: " + rewardFiles.get(i));
			}
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
		return rewardRegistry.getRewards();
	}

	public SubDirectlyDefinedReward getSubDirectlyDefined(String path) {
		return rewardRegistry.getSubDirectlyDefined(path);
	}

	public CopyOnWriteArrayList<SubDirectlyDefinedReward> getSubDirectlyDefinedRewards() {
		return rewardRegistry.getSubDirectlyDefinedRewards();
	}

	public void giveChoicesReward(Reward mainReward, AdvancedCoreUser user, String choice) {
		RewardBuilder reward = new RewardBuilder(mainReward.getConfig().getConfigData(),
				mainReward.getConfig().getChoicesRewardsPath(choice));
		reward.withPrefix(mainReward.getName());
		reward.withPlaceHolder("choice", choice);
		reward.send(user);
	}

	@SuppressWarnings("unchecked")
	public void giveReward(AdvancedCoreUser user, ConfigurationSection data, String path, RewardOptions rewardOptions) {
		if (!rewardOptions.isOnlineSet()) {
			rewardOptions.setOnline(user.isOnline());
		}
		if (path == null) {
			plugin.getLogger().warning("Path is null, failing to give reward");
			return;
		}
		if (data == null) {
			plugin.getLogger().warning("ConfigurationSection is null, failing to give reward: " + path);
			return;
		}
		if (plugin == null || !plugin.isEnabled()) {
			plugin.getLogger().severe("Not giving reward " + path + ", plugin is not enabled");
			return;
		}
		if (data.isList(path)) {
			ArrayList<String> rewards = (ArrayList<String>) data.getList(path, new ArrayList<>());
			if (rewards.isEmpty()) {
				plugin.debug(
						"Not giving empty list of rewards from " + path + ", Options: " + rewardOptions.toString());
			} else {
				plugin.debug("Giving list of rewards (" + ArrayUtils.makeStringList(rewards) + ") from " + path
						+ ", Options: " + rewardOptions.toString() + " to " + user.getPlayerName() + "/"
						+ user.getUUID());
				for (String reward : rewards) {
					giveReward(user, reward, rewardOptions);
				}
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
			DirectlyDefinedReward direct = getDirectlyDefined(path);
			SubDirectlyDefinedReward sub = getSubDirectlyDefined(rewardName);
			if (suffix != null && prefix != null && (direct != null || sub != null)) {
				if (direct != null) {
					Reward reward = direct.getReward();
					if (reward != null) {
						plugin.debug("Giving directlydefined reward " + path + ", Options: " + rewardOptions.toString()
								+ " to " + user.getPlayerName() + "/" + user.getUUID());
						giveReward(user, reward, rewardOptions);
					} else {
						plugin.debug("Failed to give directlydefined reward " + path + ", Options: "
								+ rewardOptions.toString() + ", Reward == null");
					}
				} else {
					Reward reward = sub.getReward();
					if (reward != null) {
						plugin.debug("Giving subdirectlydefined reward " + rewardName + ", Options: "
								+ rewardOptions.toString() + " to " + user.getPlayerName() + "/" + user.getUUID());
						giveReward(user, reward, rewardOptions);
					} else {
						plugin.debug("Failed to give subdirectlydefined reward " + path + ", Options: "
								+ rewardOptions.toString() + ", Reward == null");
					}
				}
			} else {
				ConfigurationSection section = data.getConfigurationSection(path);
				Reward reward = new Reward(rewardName, section);
				reward.checkRewardFile();
				plugin.debug("Giving reward " + path + ", Options: " + rewardOptions.toString() + " to "
						+ user.getPlayerName() + "/" + user.getUUID());
				giveReward(user, reward, rewardOptions);
			}
		} else {
			String reward = data.getString(path, "");
			if (!reward.isEmpty()) {
				plugin.debug("Giving reward " + reward + " from path " + path + ", Options: " + rewardOptions.toString()
						+ " to " + user.getPlayerName() + "/" + user.getUUID());
				giveReward(user, reward, rewardOptions);
			} else {
				plugin.debug("Not giving reward " + reward + " from path " + path + ", Options: "
						+ rewardOptions.toString());
			}
		}
	}

	public void giveReward(AdvancedCoreUser user, Reward reward, RewardOptions rewardOptions) {
		if (reward != null) {
			// make sure reward is async to avoid issues
			if (Bukkit.isPrimaryThread()) {
				plugin.getBukkitScheduler().runTaskAsynchronously(plugin, new Runnable() {
					@Override
					public void run() {
						reward.giveReward(user, rewardOptions);
					}
				});
			} else {
				reward.giveReward(user, rewardOptions);
			}
		} else {
			plugin.debug("Reward == null");
		}
	}

	public void giveReward(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
		if (!reward.equals("")) {
			if (reward.startsWith("/")) {
				MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), reward,
						rewardOptions.getPlaceholders());
				return;
			}
			giveReward(user, getReward(reward), rewardOptions);
		}
	}

	public boolean hasDirectRewardHandle(String reward) {
		return rewardRegistry.hasDirectRewardHandle(reward);
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

	public void loadInjectedRequirements() {
		injectedRequirements.clear();
		com.bencodez.advancedcore.api.rewards.builtin.requirements.BuiltinRequirements.register(this, plugin);
		for (RequirementInject requirement : injectedRequirements) {
			requirement.setInternalReward(true);
		}
		sortInjectedRequirements();
	}

	public void loadInjectedRewards() {
		injectedRewards.clear();
		com.bencodez.advancedcore.api.rewards.builtin.BuiltinRewards.register(this, plugin);
		for (RewardInject reward : injectedRewards) {
			reward.setInternalReward(true);
		}
		sortInjectedRewards();
	}

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		rewardRegistry.resetRewards();
		setupExample();
		addValidPath("DirectlyDefinedReward");
		addValidPath("Delayed");
		addValidPath("Timed");
		addValidPath("DisplayItem");
		addValidPath("ForceOffline");
		for (File file : rewardFolders) {
			loadRewards(file);
		}
		sortInjectedRewards();
		sortInjectedRequirements();
		plugin.debug("Loaded rewards");
	}

	private void loadRewards(File file) {
		for (String reward : getRewardNames(file)) {
			if (!reward.equals("")) {
				if (!rewardExist(reward)) {
					try {
						Reward reward1 = new Reward(file, reward);
						reward1.validate();
						if (!reward1.getConfig().isDirectlyDefinedReward()
								|| file.getName().equalsIgnoreCase("DirectlyDefined")) {
							getRewards().add(reward1);
							if (reward1.getConfig().getConfigData().getConfigurationSection("").getKeys(true).size() > 0) {
								plugin.extraDebug("Loaded Reward File: " + file.getAbsolutePath() + "/" + reward);
							} else {
								plugin.debug("Loaded empty reward file" + file.getAbsolutePath() + "/" + reward);
							}
						} else {
							plugin.extraDebug("Ignoring directly defined reward file " + file.getAbsolutePath() + "/" + reward);
						}
					} catch (Exception e) {
						plugin.getLogger().severe("Failed to load reward file " + reward + ".yml: " + e.getMessage());
						e.printStackTrace();
					}
				} else {
					plugin.debug("Detected that a reward file named " + reward
							+ " already exists, cannot load reward file " + file.getAbsolutePath() + "/" + reward);
				}
			} else {
				plugin.getLogger().warning(
						"Detected getting a reward file with an empty name! That means you either didn't type a name or didn't properly make an empty list");
			}
		}
	}

	public void openSubReward(Player player, String path, RewardEditData reward) {
		if (!reward.getData().contains(path)) {
			reward.createSection(path);
		}
		RewardEditGUI.getInstance().openRewardGUI(player, new RewardEditData(new DirectlyDefinedReward(path) {
			@Override
			public void createSection(String path) {
				reward.createSection(path);
			}

			@Override
			public ConfigurationSection getFileData() {
				return reward.getData();
			}

			@Override
			public void save() {
				reward.save();
			}

			@Override
			public void setData(String path, Object value) {
				reward.setValue(path, value);
			}
		}, reward), reward.getName() + "." + path);
	}

	/**
	 * Reward exist.
	 *
	 * @param reward the reward
	 * @return true, if successful
	 */
	public boolean rewardExist(String reward) {
		return rewardRegistry.rewardExist(reward);
	}

	/**
	 * Sets the default folder.
	 *
	 * @param defaultFolder the new default folder
	 */
	public void setDefaultFolder(File defaultFolder) {
		this.defaultFolder = defaultFolder;
	}

	/**
	 * Setup example.
	 */
	public void setupExample() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}
		if (AdvancedCorePlugin.getInstance().getOptions().isLoadDefaultRewards()) {
			copyFile("ExampleBasic.yml");
			copyFile("ExampleAdvanced.yml");
		}
	}

	public void shutdown() {
		delayedTimer.shutdown();
		try {
			delayedTimer.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		delayedTimer.shutdownNow();
	}

	public void sortInjectedRequirements() {
		Collections.sort(injectedRequirements, new Comparator<RequirementInject>() {
			@Override
			public int compare(RequirementInject o1, RequirementInject o2) {
				return Integer.compare(o2.getPriority(), o1.getPriority());
			}
		});
	}

	public void sortInjectedRewards() {
		Collections.sort(injectedRewards, new Comparator<RewardInject>() {
			@Override
			public int compare(RewardInject o1, RewardInject o2) {
				return Integer.compare(o2.getPriority(), o1.getPriority());
			}
		});
	}

	/*
	 * private void updateReward(Reward reward) { for (int i = getRewards().size() -
	 * 1; i >= 0; i--) { if
	 * (getRewards().get(i).getFile().getName().equals(reward.getFile().getName()))
	 * { getRewards().set(i, reward); return; } } getRewards().add(reward); }
	 */

	public void startup() {
		plugin.addUserStartup(new UserStartup() {
			@Override
			public void onFinish() {
			}

			@Override
			public void onStart() {
				plugin.debug("Checking timed/delayed rewards");
			}

			@Override
			public void onStartUp(AdvancedCoreUser user) {
				try {
					HashMap<String, Long> timed = user.getTimedRewards();
					for (Entry<String, Long> entry : timed.entrySet()) {
						user.loadTimedDelayedTimer(entry.getValue().longValue());
					}
				} catch (Exception ex) {
					plugin.debug("Failed to update delayed/timed for: " + user.getUUID());
					plugin.debug(ex);
				}
			}
		});
	}

	public void updateReward(Configuration data, String path, RewardOptions rewardOptions) {
		if (rewardOptions == null) {
			rewardOptions = new RewardOptions();
		}
		if (data.isConfigurationSection(path)) {
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
			reward.checkRewardFile();
		}
	}

	public void updateReward(Reward reward) {
		rewardRegistry.updateReward(reward);
	}
}
