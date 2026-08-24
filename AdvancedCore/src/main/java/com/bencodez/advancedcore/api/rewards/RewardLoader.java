package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.exceptions.FileDirectoryException;

/**
 * Owns reward folder discovery and reward-file loading.
 */
public class RewardLoader {

	private File defaultFolder;
	private final RewardHandler handler;
	private final AdvancedCorePlugin plugin;
	private final ArrayList<File> rewardFolders = new ArrayList<>();
	private final Set<String> suppressedDirectlyDefinedRewards = new HashSet<>();

	public RewardLoader(RewardHandler handler, AdvancedCorePlugin plugin) {
		this.handler = handler;
		this.plugin = plugin;
		defaultFolder = new File(plugin.getDataFolder(), "Rewards");
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

	public void checkDirectlyDefined() {
		for (Reward rewardFile : handler.getRewards()) {
			File folder = rewardFile.getConfig().getRewardFolder();
			if (folder != null && folder.getName().equalsIgnoreCase("DirectlyDefined")) {
				if (handler.hasDirectRewardHandle(rewardFile.getName())) {
					rewardFile.getFile().delete();
				}
			}
		}
		loadRewards();
	}

	private void quarantineGeneratedDirectlyDefinedFiles() {
		suppressedDirectlyDefinedRewards.clear();
		for (File folder : rewardFolders) {
			if (folder == null || !folder.getName().equalsIgnoreCase("DirectlyDefined")) {
				continue;
			}
			for (String fileName : getRewardFiles(folder)) {
				File staleFile = new File(folder, fileName);
				YamlConfiguration data = YamlConfiguration.loadConfiguration(staleFile);
				if (!data.getBoolean("DirectlyDefinedReward", false)) {
					continue;
				}

				String rewardName = fileName.substring(0, fileName.length() - ".yml".length());
				suppressStaleGeneratedReward(staleFile, rewardName);
			}
		}
	}

	private void suppressStaleGeneratedReward(File staleFile, String rewardName) {
		suppressedDirectlyDefinedRewards.add(RewardRegistry.normalizeDirectPath(rewardName));
		if (staleFile == null || !staleFile.exists()) {
			return;
		}

		File disabledFile = nextDisabledFile(staleFile);
		if (staleFile.renameTo(disabledFile)) {
			plugin.getLogger().warning("Disabled stale generated directly-defined reward file " + staleFile.getName()
					+ " because generated reward files are no longer exposed as standalone rewards. Preserved as "
					+ disabledFile.getName());
		} else {
			plugin.getLogger().warning("Failed to quarantine stale generated directly-defined reward file "
					+ staleFile.getName() + "; it will remain suppressed from standalone reward lookup for this runtime");
		}
	}

	static File nextDisabledFile(File staleFile) {
		File parent = staleFile.getParentFile();
		File disabled = new File(parent, staleFile.getName() + ".disabled");
		int suffix = 1;
		while (disabled.exists()) {
			disabled = new File(parent, staleFile.getName() + ".disabled." + suffix++);
		}
		return disabled;
	}

	private void copyFile(String fileName) {
		File file = new File(plugin.getDataFolder(), "Rewards" + File.separator + fileName);
		if (!file.exists()) {
			plugin.saveResource("Rewards" + File.separator + fileName, true);
		}
	}

	public File getDefaultFolder() {
		return defaultFolder;
	}

	private String getFileExtension(File file) {
		String name = file.getName();
		int lastIndexOf = name.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return "";
		}
		return name.substring(lastIndexOf);
	}

	public Reward getRewardDirectlyDefined(String reward) {
		if (reward == null) {
			reward = "";
		}
		reward = reward.replace(" ", "_");

		for (Reward rewardFile : handler.getRewards()) {
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

	public ArrayList<String> getRewardFiles(File folder) {
		ArrayList<String> fileNames = new ArrayList<>();
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

	public ArrayList<String> getRewardNames(File file) {
		ArrayList<String> rewardFiles = getRewardFiles(file);
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

	public void loadRewards() {
		quarantineGeneratedDirectlyDefinedFiles();
		handler.getRewardRegistry().resetRewards();
		setupExample();
		handler.addValidPath("DirectlyDefinedReward");
		handler.addValidPath("Delayed");
		handler.addValidPath("Timed");
		handler.addValidPath("DisplayItem");
		handler.addValidPath("ForceOffline");
		for (File file : rewardFolders) {
			loadRewards(file);
		}
		handler.sortInjectedRewards();
		handler.sortInjectedRequirements();
		plugin.debug("Loaded rewards");
	}

	private void loadRewards(File file) {
		for (String reward : getRewardNames(file)) {
			if (!reward.equals("")) {
				if (file.getName().equalsIgnoreCase("DirectlyDefined")
						&& suppressedDirectlyDefinedRewards.contains(RewardRegistry.normalizeDirectPath(reward))) {
					plugin.getLogger().warning("Suppressing stale generated directly-defined reward from standalone lookup: "
							+ reward);
					continue;
				}
				if (!handler.rewardExist(reward)) {
					try {
						Reward reward1 = new Reward(file, reward);
						reward1.validate();
						if (!reward1.getConfig().isDirectlyDefinedReward()
								|| file.getName().equalsIgnoreCase("DirectlyDefined")) {
							handler.getRewards().add(reward1);
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

	public void setDefaultFolder(File defaultFolder) {
		this.defaultFolder = defaultFolder;
	}

	public void setupExample() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}
		if (AdvancedCorePlugin.getInstance().getOptions().isLoadDefaultRewards()) {
			copyFile("ExampleBasic.yml");
			copyFile("ExampleAdvanced.yml");
		}
	}
}
