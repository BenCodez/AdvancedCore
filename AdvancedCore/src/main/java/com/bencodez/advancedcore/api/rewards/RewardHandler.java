package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.exceptions.FileDirectoryException;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditActionBar;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedPriority;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedRandomReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedWorld;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditBossBar;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditChoices;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditDate;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXP;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXPLevels;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEffect;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditFirework;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditItems;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditJavascript;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditLocationDistance;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditLucky;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMessages;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMoney;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditPotions;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSound;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSpecialChance;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditTempPermission;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditTitle;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectBoolean;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectDouble;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectKeys;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectDouble;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectInt;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectStringList;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.command.gui.RewardEditGUI;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class RewardHandler.
 */
public class RewardHandler {

	/** The instance. */
	static RewardHandler instance = new RewardHandler();

	@Deprecated
	public static RewardHandler getInstance() {
		return instance;
	}

	/** The default folder. */
	private File defaultFolder;

	@Getter
	private ArrayList<DirectlyDefinedReward> directlyDefinedRewards = new ArrayList<>();

	@Getter
	private ArrayList<SubDirectlyDefinedReward> subDirectlyDefinedRewards = new ArrayList<>();

	@Getter
	private ArrayList<RequirementInject> injectedRequirements = new ArrayList<>();

	@Getter
	private ArrayList<RewardInject> injectedRewards = new ArrayList<>();

	@Getter
	private ArrayList<RewardPlaceholderHandle> placeholders = new ArrayList<>();

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The reward folders. */
	private ArrayList<File> rewardFolders;

	/** The rewards. */
	private List<Reward> rewards;

	@Getter
	private Timer repeatTimer = new Timer();

	@Getter
	private ScheduledExecutorService delayedTimer = Executors.newSingleThreadScheduledExecutor();

	@Getter
	@Setter
	private Set<String> validPaths = new HashSet<>();

	/**
	 * Instantiates a new reward handler.
	 */
	private RewardHandler() {
		rewardFolders = new ArrayList<>();
		setDefaultFolder(new File(AdvancedCorePlugin.getInstance().getDataFolder(), "Rewards"));
	}

	public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
		plugin.extraDebug("Adding directlydefined reward handle: " + directlyDefinedReward.getPath()
				+ ", isdirectlydefined: " + directlyDefinedReward.isDirectlyDefined());
		directlyDefinedRewards.add(directlyDefinedReward);
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
		plugin.extraDebug("Adding subdirectlydefined reward handle: " + subDirectlyDefinedReward.getFullPath()
				+ ", isdirectlydefined: " + subDirectlyDefinedReward.isDirectlyDefined());
		subDirectlyDefinedRewards.add(subDirectlyDefinedReward);
	}

	public void addValidPath(String path) {
		validPaths.add(path);
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
		subDirectlyDefinedRewards = new ArrayList<>();
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
		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			if (direct.getPath().equalsIgnoreCase(path)) {
				return direct;
			}
		}
		return null;
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
		if (reward == null) {
			reward = "";
		}
		reward = reward.replace(" ", "_");

		/*
		 * if (rewardOptions != null) { String prefix = rewardOptions.getPrefix(); if
		 * (prefix != null && !prefix.equals("")) { String str = reward; reward = prefix
		 * + "_" + str; } String suffix = rewardOptions.getSuffix(); if (suffix != null
		 * && !suffix.equals("")) { reward += "_" + suffix; } }
		 */

		for (Reward rewardFile : getRewards()) {
			if (rewardFile.getName().equalsIgnoreCase(reward)) {
				return rewardFile;
			}
		}

		if (reward.equals("")) {
			plugin.getLogger().warning("Tried to get any empty reward file name, renaming to EmptyName");
			reward = "EmptyName";
		}

		if (reward.equalsIgnoreCase("examplebasic") || reward.equalsIgnoreCase("exampleadvanced")) {
			plugin.getLogger().warning("Using example rewards as a reward, becarefull");
		}

		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			if (direct.getPath().replace(".", "_").equals(reward)) {
				plugin.debug("Using directlydefined reward for: " + reward);
				return direct.getReward();
			}
		}

		for (SubDirectlyDefinedReward direct : getSubDirectlyDefinedRewards()) {
			if (direct.getFullPath().equalsIgnoreCase(reward)
					|| direct.getFullPath().equalsIgnoreCase(reward.replaceAll("_", "."))) {
				plugin.debug("Using subdirectlydefined reward for: " + reward);
				return direct.getReward();
			}
		}

		return new Reward(reward);
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
		ArrayList<String> fileNames = new ArrayList<>();
		if (folder != null && folder.exists()) {
			for (File file : folder.listFiles()) {
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
		if (rewards == null) {
			rewards = Collections.synchronizedList(new ArrayList<Reward>());
		}
		return rewards;
	}

	public SubDirectlyDefinedReward getSubDirectlyDefined(String path) {
		for (SubDirectlyDefinedReward direct : getSubDirectlyDefinedRewards()) {
			if (direct.getFullPath().equalsIgnoreCase(path)
					|| direct.getFullPath().equalsIgnoreCase(path.replaceAll("_", "."))) {
				return direct;
			}
		}
		return null;
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
		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			if (direct.getPath().replace(".", "_").equals(reward)) {
				return true;
			}
		}

		for (SubDirectlyDefinedReward direct : getSubDirectlyDefinedRewards()) {
			if (direct.getFullPath().equalsIgnoreCase(reward)
					|| direct.getFullPath().equalsIgnoreCase(reward.replaceAll("_", "."))) {
				return true;
			}
		}
		return false;
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
		injectedRequirements.add(new RequirementInjectDouble("Chance", 100) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, double num,
					RewardOptions rewardOptions) {
				if (rewardOptions.isIgnoreChance()) {
					return true;
				}
				return MiscUtils.getInstance().checkChance(num, 100);
			}
		}.priority(100)
				.addEditButton(new EditGUIButton(new ItemBuilder("DROPPER"), new EditGUIValueNumber("Chance", null) {

					@Override
					public void setValue(Player player, Number value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value.intValue());
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Set chance for reward to execute"))).validator(new RequirementInjectValidator() {

					@Override
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						if (data.getDouble(inject.getPath(), 0) == 100) {
							warning(reward, inject,
									"Chance is 100, if intended then remove the chance option, as it's unneeded");
						} else if (data.getDouble(inject.getPath(), 0) > 100) {
							warning(reward, inject, "Chance is greater than 100, this will always give the reward");
						} else if (data.getDouble(inject.getPath(), 1) == 0) {
							warning(reward, inject, "Chance can not be 0");
						} else if (data.getDouble(inject.getPath(), 1) < 0) {
							warning(reward, inject, "Chance can not be negative");
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectInt("RewardExpiration", -1) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, int num,
					RewardOptions rewardOptions) {
				if (rewardOptions.getOrginalTrigger() > 0) {
					long execDate = rewardOptions.getOrginalTrigger();
					debug("OrgTrigger: " + execDate + ", plus time: " + (execDate + num * 60 * 1000)
							+ ", current time: " + System.currentTimeMillis());
					if (execDate + num * 60 * 1000 > System.currentTimeMillis()) {
						return true;
					} else {
						return false;
					}
				}
				if (rewardOptions.getPlaceholders().containsKey("ExecDate") && num > 0) {
					long execDate = Long.parseLong(rewardOptions.getPlaceholders().get("ExecDate"));
					debug("ExecDate: " + execDate + ", plus time: " + (execDate + num * 60 * 1000) + ", current time: "
							+ System.currentTimeMillis());
					if (execDate + num * 60 * 1000 > System.currentTimeMillis()) {
						return true;
					} else {
						return false;
					}
				}
				return true;
			}
		}.priority(100).addEditButton(
				new EditGUIButton(new ItemBuilder("CLOCK"), new EditGUIValueNumber("RewardExpiration", null) {

					@Override
					public void setValue(Player player, Number value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value.intValue());
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Time before reward expires, if not executed").addLore("In minutes"))));

		injectedRequirements.add(new RequirementInjectString("Permission", "") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String str,
					RewardOptions rewardOptions) {
				if (!reward.getConfig().getRequirePermission()) {
					return true;
				}
				if (str.isEmpty()) {
					str = "AdvancedCore.Reward." + reward.getName();
				}

				boolean reverse = false;
				if (str.startsWith("!")) {
					reverse = true;
					str = str.substring(1);
					debug("Doing permission check in reverse");
				}

				boolean perm = PlayerManager.getInstance().hasServerPermission(UUID.fromString(user.getUUID()),
						user.getPlayerName(), str);
				if (reverse) {
					perm = !perm;
				}
				if (!perm) {
					debug(user.getPlayerName() + " does not have permission " + str + " to get reward "
							+ reward.getName() + ", reverse: " + reverse);
					return false;
				}
				return true;
			}
		}.priority(100).alwaysForce().addEditButton(
				new EditGUIButton(new ItemBuilder("IRON_DOOR"), new EditGUIValueString("Permission", null) {

					@Override
					public void setValue(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Set permission required to be given, set RequirePermission to true if using this")))
				.addEditButton(new EditGUIButton(new EditGUIValueBoolean("RequirePermission", null) {

					@Override
					public void setValue(Player player, boolean value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("If true, permission is required to run reward")))
				.validator(new RequirementInjectValidator() {

					@Override
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						if (!data.getBoolean("RequirePermission", false)) {
							if (!data.getString("Permission", "").isEmpty()) {
								warning(reward, inject, "Detected permission set but RequirePermission is false");
							}
						}
					}
				}.addPath("RequirePermission")));

		injectedRequirements.add(new RequirementInjectConfigurationSection("DayOfMonth") {
			@Override
			public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
					RewardOptions rewardOptions) {
				if (!data.getBoolean("Enabled", false)) {
					return true;
				}

				List<Integer> days = data.getIntegerList("Days");
				int currentDay = LocalDateTime.now().getDayOfMonth();

				return days.contains(currentDay);
			}
		}.priority(100)
				.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Days", null) {
					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Set the days of the month for the requirement")))
				.validator(new RequirementInjectValidator() {
					@Override
					@SuppressWarnings("unchecked")
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						List<Integer> days = (List<Integer>) data.getList("Days", null);
						if (days != null && days.isEmpty()) {
							warning(reward, inject, "No days specified for DayOfMonth requirement");
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectString("Server", "") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String str,
					RewardOptions rewardOptions) {
				// set server to match
				String serverToMatch = str;
				boolean hadPlaceholder = false;
				if (str.isEmpty()) {
					if (rewardOptions.getPlaceholders().containsKey("Server")) {
						serverToMatch = rewardOptions.getPlaceholders().get("Server");
						hadPlaceholder = true;
					} else if (!rewardOptions.getServer().isEmpty()) {
						serverToMatch = rewardOptions.getServer();
					}
				}
				String currentServer = AdvancedCorePlugin.getInstance().getOptions().getServer();

				if (!serverToMatch.isEmpty()) {
					debug("Current Server: " + currentServer + ", ServerToMatch: " + serverToMatch);
					boolean matched = serverToMatch.equalsIgnoreCase(currentServer);

					if (!matched && !hadPlaceholder) {
						// add server for offline reward
						rewardOptions.addPlaceholder("Server", serverToMatch);
					}

					return matched;

				}

				return true;
			}
		}.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueString("Server", null) {

					@Override
					public void setValue(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}.addOptions(Bukkit.getServer().getName()).addLore("Server to execute reward on"))));

		injectedRequirements.add(new RequirementInjectStringList("BlockedServers", new ArrayList<>()) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> servers,
					RewardOptions rewardOptions) {
				if (servers.isEmpty()) {
					return true;
				}

				String currentServer = AdvancedCorePlugin.getInstance().getOptions().getServer();

				if (ArrayUtils.containsIgnoreCase(servers, currentServer)) {
					debug("Current server is in blocekd servers list: " + currentServer + " " + servers.toString());
					return false;
				}

				return true;
			}
		}.priority(100).allowReattempt().addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("BlockedServers", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("List of servers for reward not to run on"))).validator(new RequirementInjectValidator() {

					@Override
					@SuppressWarnings("unchecked")
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						ArrayList<String> list = (ArrayList<String>) data.getList("BlockedServers", null);
						if (list != null) {
							if (list.isEmpty()) {
								warning(reward, inject, "No blocked servers were listed");
							}
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectStringList("Worlds", new ArrayList<>()) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> worlds,
					RewardOptions rewardOptions) {
				if (worlds.isEmpty()) {
					if (plugin.getOptions().getDefaultRewardWorlds().isEmpty() || !rewardOptions.isUseDefaultWorlds()) {
						debug("No whitelisted worlds specified");
						return true;
					}
					Player player = user.getPlayer();
					if (player == null) {
						debug("No player");
						return false;
					}
					reward.checkRewardFile();
					String world = player.getWorld().getName();
					if (plugin.getOptions().getDefaultRewardWorlds().contains(world)) {
						debug("Player in default whitelisted world: " + world);
						return true;
					}

					user.setCheckWorld(true);
					debug("Player not in default whitelisted world: " + world);
					return false;
				}

				Player player = user.getPlayer();
				if (player == null) {
					debug("No player");
					return false;
				}
				reward.checkRewardFile();
				String world = player.getWorld().getName();
				if (worlds.contains(world)) {
					debug("Player in whitelisted world: " + world);
					return true;
				}

				user.setCheckWorld(true);
				debug("Player not in whitelisted world: " + world);
				return false;
			}
		}.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
				new EditGUIButton(new ItemBuilder("END_PORTAL_FRAME"), new EditGUIValueList("Worlds", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Worlds to execute reward in, only executes into one reward")))
				.validator(new RequirementInjectValidator() {

					@Override
					@SuppressWarnings("unchecked")
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						ArrayList<String> list = (ArrayList<String>) data.getList("Worlds", null);
						if (list != null) {
							if (list.isEmpty()) {
								warning(reward, inject, "No worlds were listed");
							}
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectStringList("BlackListedWorlds", new ArrayList<>()) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> worlds,
					RewardOptions rewardOptions) {
				if (worlds.isEmpty()) {
					if (plugin.getOptions().getDefaultRewardBlackListedWorlds().isEmpty()
							|| !rewardOptions.isUseDefaultWorlds()) {
						debug("No blacklisted worlds specified");
						return true;
					}
					Player player = user.getPlayer();
					if (player == null) {
						debug("No player");
						return false;
					}
					reward.checkRewardFile();
					String world = player.getWorld().getName();
					if (plugin.getOptions().getDefaultRewardBlackListedWorlds().contains(world)) {
						user.setCheckWorld(true);
						debug("Player in default blacklisted world: " + world);
						return false;
					}
					debug("Player not in default blacklisted worlds");
					return true;
				}

				Player player = user.getPlayer();
				if (player == null) {
					debug("No player");
					return false;
				}
				reward.checkRewardFile();
				String world = player.getWorld().getName();
				if (worlds.contains(world)) {
					user.setCheckWorld(true);
					debug("Player in default blacklisted world: " + world);
					return false;
				}
				debug("Player not in blacklisted worlds");
				return true;
			}
		}.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
				new EditGUIButton(new ItemBuilder("END_PORTAL_FRAME"), new EditGUIValueList("BlackListedWorlds", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Worlds to never execute the reward in"))).validator(new RequirementInjectValidator() {

					@Override
					@SuppressWarnings("unchecked")
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						ArrayList<String> list = (ArrayList<String>) data.getList("BlackListedWorlds", null);
						if (list != null) {
							if (list.isEmpty()) {
								warning(reward, inject, "No worlds were listed");
							}
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectString("RewardType", "BOTH") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String type,
					RewardOptions rewardOptions) {
				if (rewardOptions.isOnline()) {
					if (type.equalsIgnoreCase("offline")) {
						debug("Reward Type Don't match");
						return false;
					}
				} else {
					if (type.equalsIgnoreCase("online")) {
						debug("Reward Type Don't match");
						return false;
					}
				}
				return true;
			}
		}.priority(100).addEditButton(
				new EditGUIButton(new ItemBuilder("REDSTONE_TORCH"), new EditGUIValueString("RewardType", null) {

					@Override
					public void setValue(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addOptions("ONLINE", "OFFLINE", "BOTH")
						.addLore("Define whether should execute if player was offline/online"))));

		injectedRequirements.add(new RequirementInjectString("JavascriptExpression", "") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String expression,
					RewardOptions rewardOptions) {
				if (expression.equals("") || new JavascriptEngine().addPlayer(user.getOfflinePlayer())
						.getBooleanValue(PlaceholderUtils.replacePlaceHolders(user.getOfflinePlayer(),
								PlaceholderUtils.replacePlaceHolder(expression, rewardOptions.getPlaceholders())))) {
					return true;
				}
				return false;
			}
		}.priority(90).addEditButton(new EditGUIButton(new ItemBuilder("DETECTOR_RAIL"),
				new EditGUIValueString("JavascriptExpression", null) {

					@Override
					public void setValue(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}.addLore("Javascript expression required to run reward"))).validator(new RequirementInjectValidator() {

					@Override
					public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
						String str = data.getString("JavascriptExpression", null);
						if (str != null) {
							if (str.isEmpty()) {
								warning(reward, inject, "No javascript expression set");
							}
						}
					}
				}));

		injectedRequirements.add(new RequirementInjectConfigurationSection("Date") {
			@Override
			public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					RewardOptions rewardOptions) {
				LocalDateTime now = LocalDateTime.now();

				// Validate WeekDay
				if (section.isString("WeekDay")) {
					String requiredWeekDay = section.getString("WeekDay").toUpperCase();
					if (!now.getDayOfWeek().name().equals(requiredWeekDay)) {
						debug("WeekDay does not match: " + requiredWeekDay);
						return false;
					}
				} else if (section.isInt("WeekDay")) {
					int requiredWeekDay = section.getInt("WeekDay");
					if (now.getDayOfWeek().getValue() != requiredWeekDay) {
						debug("WeekDay does not match: " + requiredWeekDay);
						return false;
					}
				}

				// Validate DayOfMonth
				if (section.isInt("DayOfMonth")) {
					int requiredDayOfMonth = section.getInt("DayOfMonth");
					if (now.getDayOfMonth() != requiredDayOfMonth) {
						debug("DayOfMonth does not match: " + requiredDayOfMonth);
						return false;
					}
				}

				// Validate Month
				if (section.isString("Month")) {
					String requiredMonth = section.getString("Month").toUpperCase();
					if (!now.getMonth().name().equals(requiredMonth)) {
						debug("Month does not match: " + requiredMonth);
						return false;
					}
				}

				return true;
			}
		}.priority(90).validator(new RequirementInjectValidator() {
			@Override
			public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
				if (!data.isConfigurationSection("Date")) {
					return;
				}

				ConfigurationSection section = data.getConfigurationSection("Date");

				// Validate WeekDay
				if (section.isString("WeekDay")) {
					try {
						DayOfWeek.valueOf(section.getString("WeekDay").toUpperCase());
					} catch (IllegalArgumentException e) {
						warning(reward, inject, "Invalid WeekDay: " + section.getString("WeekDay"));
					}
				} else if (section.isInt("WeekDay")) {
					int weekDay = section.getInt("WeekDay");
					if (weekDay < 1 || weekDay > 7) {
						warning(reward, inject, "Invalid WeekDay: " + weekDay);
					}
				}

				// Validate DayOfMonth
				if (section.isInt("DayOfMonth")) {
					int day = section.getInt("DayOfMonth");
					if (day < 1 || day > 31) {
						warning(reward, inject, "Invalid DayOfMonth: " + day);
					}
				}

				// Validate Month
				if (section.isString("Month")) {
					try {
						Month.valueOf(section.getString("Month").toUpperCase());
					} catch (IllegalArgumentException e) {
						warning(reward, inject, "Invalid Month: " + section.getString("Month"));
					}
				}
			}
		}).addEditButton(new EditGUIButton(new ItemBuilder("PAPER"), new EditGUIValueInventory("Date") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditDate() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Edit date-based requirements for the reward"))));

		injectedRequirements.add(new RequirementInjectConfigurationSection("LocationDistance") {
			@Override
			public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					RewardOptions rewardOptions) {
				if (!user.isOnline()) {
					plugin.debug("user not online");
					return false;
				}
				Location loc = new Location(Bukkit.getWorld(section.getString("World")), section.getInt("X"),
						section.getInt("Y"), section.getInt("Z"));
				Location pLoc = user.getPlayer().getLocation();
				if (!loc.getWorld().getName().equals(pLoc.getWorld().getName())) {
					plugin.debug("Worlds don't match");
					return false;
				}
				if (pLoc.distance(loc) < section.getInt("Distance")) {
					return true;
				}
				return false;
			}
		}.priority(90).validator(new RequirementInjectValidator() {

			@Override
			public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
				if (!data.isConfigurationSection("LocationDistance")) {
					return;

				}

				ConfigurationSection section = data.getConfigurationSection("LocationDistance");

				try {
					new Location(Bukkit.getWorld(section.getString("World")), section.getInt("X"), section.getInt("Y"),
							section.getInt("Z"));
				} catch (Exception e) {
					warning(reward, inject, "Failed to get location for LocationDistance");
					e.printStackTrace();
				}

				if (section.getInt("Distance") < 0) {
					warning(reward, inject, "Invalid distance for LocationDistance");
				}

			}
		}).addEditButton(new EditGUIButton(new ItemBuilder("MAP"), new EditGUIValueInventory("LocationDistance") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditLocationDistance() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Require player to be within a certain distance of locaction to get reward"))));

		for (RequirementInject reward : injectedRequirements) {
			reward.setInternalReward(true);
		}

		sortInjectedRequirements();
	}

	public void loadInjectedRewards() {
		injectedRewards.add(new RewardInjectDouble("Money", 0) {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, double num,
					HashMap<String, String> placeholders) {
				user.giveMoney(num);
				return "" + (int) num;
			}
		}.asPlaceholder("Money").priority(100)
				.addEditButton(new EditGUIButton(new ItemBuilder(Material.DIAMOND), new EditGUIValueInventory("Money") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditMoney() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}
				}.addLore("Money to execute, may not work on some economy plugins").addLore("Supports random amounts")))
				.validator(new RewardInjectValidator() {

					@Override
					public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
						if (data.getDouble(inject.getPath(), -1) == 0) {
							warning(reward, inject, "Money can not be 0");
						}
					}
				}));

		injectedRewards.add(new RewardInjectConfigurationSection("Money") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				double minMoney = section.getDouble("Min", 0);
				double maxMoney = section.getDouble("Max", 0);
				double value = ThreadLocalRandom.current().nextDouble(minMoney, maxMoney);

				if (section.getBoolean("Round")) {
					value = Math.round(value);
					user.giveMoney(value);
					return "" + value;
				}
				DecimalFormat f = new DecimalFormat("##.00");
				user.giveMoney(value);
				return "" + f.format(value);
			}
		}.asPlaceholder("Money").priority(100).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getDouble("Money.Max", -1) == 0) {
					warning(reward, inject, "Maxium money can not be 0");
				}
			}
		}));

		injectedRewards.add(new RewardInjectConfigurationSection("NumberCommand") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				int min = section.getInt("Min", 0);
				int max = section.getInt("Max", 100);
				int number = ThreadLocalRandom.current().nextInt(min, max + 1);
				String command = section.getString("Command", "").replace("%number%", String.valueOf(number));
				MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), command, placeholders);
				return String.valueOf(number);
			}
		}.asPlaceholder("Number").priority(100).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (!data.isInt("Min") || !data.isInt("Max") || !data.isString("Command")) {
					warning(reward, inject, "NumberCommand requires Min, Max, and Command to be set");
				}
			}
		}));

		injectedRewards.add(new RewardInjectInt("EXP", 0) {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, int num,
					HashMap<String, String> placeholders) {
				user.giveExp(num);
				return null;
			}
		}.asPlaceholder("EXP").priority(100).addEditButton(
				new EditGUIButton(new ItemBuilder("EXPERIENCE_BOTTLE"), new EditGUIValueInventory("EXP") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditEXP() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}
				}.addLore("EXP to give"))).validator(new RewardInjectValidator() {

					@Override
					public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
						if (data.getDouble(inject.getPath(), -1) == 0) {
							warning(reward, inject, "EXP can not be 0");
						}
					}
				}));

		injectedRewards.add(new RewardInjectInt("EXPLevels", 0) {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, int num,
					HashMap<String, String> placeholders) {
				user.giveExpLevels(num);
				return null;
			}
		}.asPlaceholder("EXP").priority(100).addEditButton(
				new EditGUIButton(new ItemBuilder("EXPERIENCE_BOTTLE"), new EditGUIValueInventory("EXPLevels") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditEXPLevels() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}
				}.addLore("EXPLevels to give"))).validator(new RewardInjectValidator() {

					@Override
					public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
						if (data.getDouble(inject.getPath(), -1) == 0) {
							warning(reward, inject, "EXP can not be 0");
						}
					}
				}));

		injectedRewards.add(new RewardInjectConfigurationSection("EXP") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				int minEXP = section.getInt("Min", 0);
				int maxEXP = section.getInt("Max", 0);
				int value = ThreadLocalRandom.current().nextInt(minEXP, maxEXP);
				user.giveExp(value);
				return "" + value;
			}
		}.asPlaceholder("EXP").priority(100).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getDouble("EXP.Max", -1) == 0) {
					warning(reward, inject, "Max EXP can not be 0");
				}
			}
		}));

		injectedRewards.add(new RewardInjectConfigurationSection("EXPLevels") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				int minEXP = section.getInt("Min", 0);
				int maxEXP = section.getInt("Max", 0);
				int value = ThreadLocalRandom.current().nextInt(minEXP, maxEXP);
				user.giveExpLevels(value);
				return "" + value;
			}
		}.asPlaceholder("EXP").priority(100).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getDouble("EXP.Max", -1) == 0) {
					warning(reward, inject, "Max EXP can not be 0");
				}
			}
		}));

		injectedRewards.add(new RewardInjectString("Message") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
					HashMap<String, String> placeholders) {
				user.sendMessage(value, placeholders);
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("OAK_SIGN"), new EditGUIValueInventory("Messages") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditMessages() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addCheckKey("Message"))).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getString(inject.getPath()).isEmpty()) {
					warning(reward, inject, "No player message set");
				}

			}
		}));

		injectedRewards.add(new RewardInjectStringList("Messages.Player") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
					HashMap<String, String> placeholders) {
				user.sendMessage(value, placeholders);
				return null;
			}
		});

		injectedRewards.add(new RewardInjectStringList("Message") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
					HashMap<String, String> placeholders) {
				user.sendMessage(value, placeholders);
				return null;
			}
		});

		injectedRewards.add(new RewardInjectString("Messages.Player") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
					HashMap<String, String> placeholders) {
				user.sendMessage(value, placeholders);
				return null;
			}
		}.validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.isString(inject.getPath()) && data.getString(inject.getPath()).isEmpty()) {
					warning(reward, inject, "No player message set");
				}

			}
		}));

		injectedRewards.add(new RewardInjectStringList("Messages.Broadcast") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
					HashMap<String, String> placeholders) {
				String playerName = user.getPlayerName();
				if (plugin.getOptions().getBroadcastBlacklist().contains(playerName)) {
					debug("Not broadcasting for " + playerName + ", in blacklist");
					return null;
				}
				for (String str : value) {
					MiscUtils.getInstance().broadcast(PlaceholderUtils.replacePlaceHolders(user.getPlayer(),
							PlaceholderUtils.replacePlaceHolder(str, placeholders)));
				}
				return null;
			}
		}.validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
			}
		}));

		injectedRewards.add(new RewardInjectString("Messages.Broadcast") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
					HashMap<String, String> placeholders) {
				String playerName = user.getPlayerName();
				if (plugin.getOptions().getBroadcastBlacklist().contains(playerName)) {
					debug("Not broadcasting for " + playerName + ", in blacklist");
					return null;
				}
				MiscUtils.getInstance().broadcast(PlaceholderUtils.replacePlaceHolders(user.getPlayer(),
						PlaceholderUtils.replacePlaceHolder(value, placeholders)));
				return null;
			}
		}.validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (!data.isList(inject.getPath())) {
					if (data.getString(inject.getPath(), "Empty").isEmpty()) {
						warning(reward, inject, "No broadcast was set");
					}
				}

			}
		}));

		injectedRewards.add(new RewardInjectString("Command") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
					HashMap<String, String> placeholders) {
				MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), value, placeholders);
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueString("Command", null) {

			@Override
			public void setValue(Player player, String value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
				reward.reOpenEditGUI(player);
			}
		}.addLore("Execute single console command"))).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getString(inject.getPath()).startsWith("/")) {
					warning(reward, inject, "Can't start command with /");
				}
			}
		}));

		injectedRewards.add(new RewardInjectConfigurationSection("ActionBar") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				user.sendActionBar(PlaceholderUtils.replacePlaceHolder(section.getString("Message", ""), placeholders),
						section.getInt("Delay", 30));
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("ActionBar") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditActionBar() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}

		}.addLore("Actionbar configuration"))).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				String str = data.getString("ActionBar.Message");
				int delay = data.getInt("ActionBar.Delay", -1);
				if (str != null && str.isEmpty()) {
					warning(reward, inject, "No actionbar message set");
				}
				if (delay == -1) {
					warning(reward, inject, "No actionbar delay set");
				} else if (delay == 0) {
					warning(reward, inject, "Actionbar delay can not be 0");
				}
			}
		}));

		injectedRewards.add(new RewardInjectStringList("Commands") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (!list.isEmpty()) {
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), list, placeholders, true);
				}
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueList("Commands", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
				reward.reOpenEditGUI(player);
			}
		}.addLore("List of console commands"))).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.isList(inject.getPath()) && !data.isConfigurationSection(inject.getPath())) {
					List<String> list = data.getStringList(inject.getPath());
					if (list != null) {
						if (list.isEmpty()) {
							warning(reward, inject, "No commands listed");
						}
						for (String str : list) {
							if (str.startsWith("/")) {
								warning(reward, inject, "Commands can not start with /");
							}
						}
					}
				}
			}
		}));

		injectedRewards.add(new RewardInjectConfigurationSection("Commands") {

			@SuppressWarnings("unchecked")
			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				ArrayList<String> consoleCommands = (ArrayList<String>) section.getList("Console", new ArrayList<>());
				ArrayList<String> userCommands = (ArrayList<String>) section.getList("Player", new ArrayList<>());
				if (!consoleCommands.isEmpty()) {
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), consoleCommands, placeholders,
							section.getBoolean("Stagger", true));
				}
				if (!userCommands.isEmpty()) {
					user.preformCommand(userCommands, placeholders);
				}
				return null;

			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Commands.Console", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Old style for console commands"))).addEditButton(new EditGUIButton(
						new ItemBuilder(Material.PAPER), new EditGUIValueList("Commands.Player", null) {

							@Override
							public void setValue(Player player, ArrayList<String> value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(getKey(), value);
								plugin.reloadAdvancedCore(false);
								reward.reOpenEditGUI(player);
							}
						}.addLore("Execute commands as player"))));

		injectedRewards.add(new RewardInjectStringList("Javascripts") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (!list.isEmpty()) {
					JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getOfflinePlayer());

					for (String str : list) {
						String expression = PlaceholderUtils.replacePlaceHolders(user.getOfflinePlayer(), str);
						engine.execute(PlaceholderUtils.replacePlaceHolder(expression, placeholders));
					}
				}
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Javascripts", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
				reward.reOpenEditGUI(player);
			}
		}.addLore("Javascript expressions to run"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Javascript") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					String expression = section.getString("Expression");
					expression = PlaceholderUtils.replacePlaceHolders(user.getOfflinePlayer(), expression);
					if (new JavascriptEngine().addPlayer(user.getOfflinePlayer())
							.getBooleanValue(PlaceholderUtils.replacePlaceHolder(expression, placeholders))) {
						new RewardBuilder(section, "TrueRewards").withPrefix(reward.getName() + ".Javascript")
								.send(user);
					} else {
						new RewardBuilder(section, "FalseRewards").withPrefix(reward.getName() + ".Javascript")
								.send(user);
					}
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "Javascript.TrueRewards")) {
					subs.add(new SubDirectlyDefinedReward(direct, "Javascript.TrueRewards"));
				}
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "Javascript.FalseRewards")) {
					subs.add(new SubDirectlyDefinedReward(direct, "Javascript.FalseRewards"));
				}
				return subs;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Javascript") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditJavascript() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}

		}.addLore("Run javascript to run rewards based on expression return value of true/false"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Lucky") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				HashMap<Integer, String> luckyRewards = new HashMap<>();

				for (String str : section.getKeys(false)) {
					if (MessageAPI.isInt(str)) {
						int num = Integer.parseInt(str);
						if (num > 0) {
							String path = "Lucky." + num;
							luckyRewards.put(num, path);
						}
					}
				}
				HashMap<String, Integer> map = new LinkedHashMap<>();
				for (Entry<Integer, String> entry : luckyRewards.entrySet()) {
					if (MiscUtils.getInstance().checkChance(1, entry.getKey())) {
						map.put(entry.getValue(), entry.getKey());
					}
				}

				map = ArrayUtils.sortByValuesStr(map, false);
				if (map.size() > 0) {
					if (reward.getConfig().getConfigData().getBoolean("OnlyOneLucky", false)) {
						for (Entry<String, Integer> entry : map.entrySet()) {
							new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
									.withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
							return null;
						}
					} else {
						for (Entry<String, Integer> entry : map.entrySet()) {
							new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
									.withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
						}
					}
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Lucky")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "Lucky").getKeys(false)) {
						if (MessageAPI.isInt(str)) {
							int num = Integer.parseInt(str);
							if (num > 0) {
								String path = "Lucky." + num;
								if (direct.getFileData()
										.isConfigurationSection(direct.getPath() + direct.needsDot() + path)) {
									subs.add(new SubDirectlyDefinedReward(direct, path));
								}
							}
						}
					}
				}
				return subs;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Lucky") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditLucky() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}

		})).priority(10).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {

			}
		}.addPath("OnlyOneLucky")));

		injectedRewards.add(new RewardInjectConfigurationSection("Random") {

			@SuppressWarnings("unchecked")
			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (MiscUtils.getInstance().checkChance(section.getDouble("Chance", 100), 100)) {
					if (section.getBoolean("PickRandom", true)) {
						ArrayList<String> rewards = (ArrayList<String>) section.getList("Rewards", new ArrayList<>());
						if (rewards != null) {
							if (rewards.size() > 0) {
								String reward1 = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
								if (!reward1.equals("")) {
									RewardHandler.getInstance().giveReward(user, reward1,
											new RewardOptions().setPlaceholders(placeholders));
								}
							}
						}
					} else {
						new RewardBuilder(reward.getConfig().getConfigData(), "Random.Rewards")
								.withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
					}
				} else {
					new RewardBuilder(reward.getConfig().getConfigData(), "Random.FallBack")
							.withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.Rewards")) {
					subs.add(new SubDirectlyDefinedReward(direct, "Random.Rewards"));
				}
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.FallBack")) {
					subs.add(new SubDirectlyDefinedReward(direct, "Random.FallBack"));
				}
				return subs;
			}
		}.priority(10));

		injectedRewards.add(new RewardInjectConfigurationSection("Rewards") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				plugin.debug("12345c" + reward.getName());
				new RewardBuilder(reward.getConfig().getConfigData(), "Rewards").withPrefix(reward.getName())
						.withPlaceHolder(placeholders).send(user);
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Rewards")) {
					subs.add(new SubDirectlyDefinedReward(direct, "Rewards"));
				}
				return subs;
			}

		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.DISPENSER), new EditGUIValueInventory("Rewards") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				openSubReward(clickEvent.getPlayer(), "Rewards", reward);
			}

		}.addLore("Sub rewards"))).priority(5).alwaysForce().postReward());

		injectedRewards.add(new RewardInjectStringList("RandomCommand") {

			@Override
			public String onRewardRequest(Reward r, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (list.size() > 0) {
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(),
							list.get(ThreadLocalRandom.current().nextInt(list.size())), placeholders);
				}
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomCommand", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
				reward.reOpenEditGUI(player);
			}
		}.addLore("Execute random command"))).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				List<String> list = data.getStringList(inject.getPath());
				if (list.size() == 0) {
					warning(reward, inject, "No rewards listed for random reward");
				} else if (list.size() == 1) {
					warning(reward, inject, "Only one reward listed for random reward");
				}
			}
		}));

		injectedRewards.add(new RewardInjectStringList("RandomReward") {

			@Override
			public String onRewardRequest(Reward r, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (list.size() > 0) {
					String reward = list.get(ThreadLocalRandom.current().nextInt(list.size()));
					giveReward(user, reward, new RewardOptions().setPlaceholders(placeholders));
					return reward;
				}
				return null;
			}
		}.asPlaceholder("RandomReward").priority(20).addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomReward", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}.addLore("Execute random reward"))).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("TempPermission") {

			@Override
			public String onRewardRequested(Reward r, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				String perm = section.getString("Permission", "");
				int time = section.getInt("Expiration");
				if (!perm.isEmpty()) {
					if (time > 0) {
						user.addPermission(perm, time);
					} else {
						extraDebug("Time is 0");
					}
				} else {
					extraDebug("Permission is empty");
				}

				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("TempPermission") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditTempPermission() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}

		}.addLore("Give temporary permission"))).priority(90).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("AdvancedRewards") {

			@Override
			public String onRewardRequested(Reward r, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				Set<String> keys = section.getKeys(false);
				ArrayList<String> rewards = ArrayUtils.convert(keys);
				if (rewards.size() > 0) {
					for (String reward : rewards) {
						giveReward(user, section, reward, new RewardOptions().setPlaceholders(placeholders)
								.setPrefix(r.getRewardName() + "_AdvancedRewards_" + reward));
					}
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedRewards")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedRewards")
							.getKeys(false)) {
						if (direct.getFileData().isConfigurationSection(
								direct.getPath() + direct.needsDot() + "AdvancedRewards." + str)) {
							subs.add(new SubDirectlyDefinedReward(direct, "AdvancedRewards." + str));
						}
					}
				}
				return subs;
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedRewards") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditAdvancedRandomReward() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}

				}.addLore("Execute rewards"))).synchronize().priority(20).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("AdvancedRandomReward") {

			@Override
			public String onRewardRequested(Reward r, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				Set<String> keys = section.getKeys(false);
				ArrayList<String> rewards = ArrayUtils.convert(keys);
				if (rewards.size() > 0) {
					String reward = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
					giveReward(user, section, reward, new RewardOptions().setPlaceholders(placeholders)
							.setPrefix(r.getRewardName() + "_AdvancedRandomReward"));
					return reward;
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedRandomReward")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedRandomReward")
							.getKeys(false)) {
						if (direct.getFileData().isConfigurationSection(
								direct.getPath() + direct.needsDot() + "AdvancedRandomReward." + str)) {
							subs.add(new SubDirectlyDefinedReward(direct, "AdvancedRandomReward." + str));
						}
					}
				}
				return subs;
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedRandomReward") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditAdvancedRandomReward() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}

				}.addLore("Execute random reward"))).asPlaceholder("RandomReward").synchronize().priority(20)
				.postReward());

		injectedRewards.add(new RewardInjectStringList("Priority") {

			@Override
			public String onRewardRequest(Reward r, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				for (String str : list) {
					Reward reward = RewardHandler.getInstance().getReward(str);
					if (reward.canGiveReward(user, new RewardOptions().withPlaceHolder(placeholders))) {
						new RewardBuilder(reward).withPlaceHolder(placeholders).setIgnoreChance(true)
								.setIgnoreRequirements(true).send(user);
						return reward.getName();
					}
				}
				return null;
			}
		}.asPlaceholder("Priority").addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Priority", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
						reward.reOpenEditGUI(player);
					}
				}.addLore("Execute first reward file that can be executed"))).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("Potions") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				for (String potion : section.getKeys(false)) {
					user.givePotionEffect(potion, section.getInt(potion + ".Duration", 1),
							section.getInt(potion + ".Amplifier", 1));
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAINTING), new EditGUIValueInventory("Potions") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditPotions() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure Potion Effects"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Title") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					user.sendTitle(PlaceholderUtils.replacePlaceHolder(section.getString("Title"), placeholders),

							PlaceholderUtils.replacePlaceHolder(section.getString("SubTitle"), placeholders),

							section.getInt("FadeIn", 10), section.getInt("ShowTime", 50),
							section.getInt("FadeOut", 10));
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAINTING), new EditGUIValueInventory("Title") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditTitle() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure Title & SubTitle"))));

		injectedRewards.add(new RewardInjectConfigurationSection("BossBar") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					user.sendBossBar(
							PlaceholderUtils.replacePlaceHolder(section.getString("Message", ""), placeholders),
							section.getString("Color", "BLUE"), section.getString("Style", "SOLID"),
							section.getDouble("Progress", .5), section.getInt("Delay", 30));
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("DRAGON_HEAD"), new EditGUIValueInventory("BossBar") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditBossBar() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure bossbar"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Sound") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					try {
						user.playSound(section.getString("Sound"), (float) section.getDouble("Volume", 1.0),
								(float) section.getDouble("Pitch", 1.0));
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.NOTE_BLOCK), new EditGUIValueInventory("Sound") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditSound() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure sound"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Effect") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (section.getBoolean("Enabled")) {
					user.playParticle(section.getString("Effect"), section.getInt("Data", 1),
							section.getInt("Particles", 1), section.getInt("Radius", 5));
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.DIAMOND), new EditGUIValueInventory("Effect") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditEffect() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure particle effect"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Firework") {

			@SuppressWarnings("unchecked")
			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (section.getBoolean("Enabled")) {
					FireworkHandler.getInstance().launchFirework(user.getPlayer().getLocation(),
							section.getInt("Power", 1),
							(ArrayList<String>) section.getList("Colors", new ArrayList<>()),
							(ArrayList<String>) section.getList("FadeOutColor", new ArrayList<>()),
							section.getBoolean("Trail"), section.getBoolean("Flicker"),
							(ArrayList<String>) section.getList("Types", new ArrayList<>()),
							section.getBoolean("Detonate", false));
				}
				return null;

			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("FIREWORK_ROCKET"), new EditGUIValueInventory("Firework") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditFirework() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}
		}.addLore("Configure firework effect"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Item") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				ItemBuilder builder = new ItemBuilder(section);
				builder.setCheckLoreLength(false);
				user.giveItem(builder);
				return null;

			}
		}.validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				String material = data.getString("Item.Material", "");
				if (material.isEmpty()) {
					warning(reward, inject, "No material is set on item");
				} else {
					try {
						Material m = Material.matchMaterial(material.toUpperCase());

						// change legacy item
						if (m == null) {
							m = Material.matchMaterial(material, true);
							if (material != null) {
								warning(reward, inject,
										"Found legacy material: " + material + ", please update material");
							}
						}
					} catch (NoSuchMethodError e) {
					}
				}

			}
		}));

		injectedRewards.add(new RewardInjectConfigurationSection("AdvancedPriority") {

			@Override
			public String onRewardRequested(Reward reward1, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				for (String keys : section.getKeys(false)) {
					Reward reward = RewardHandler.getInstance().getReward(section, keys, new RewardOptions());
					if (reward != null
							&& reward.canGiveReward(user, new RewardOptions().withPlaceHolder(placeholders))) {
						plugin.extraDebug("AdvancedPriority: Giving reward " + reward.getName());
						reward.giveReward(user, new RewardOptions().setIgnoreChance(true).setIgnoreRequirements(true)
								.setPrefix(reward1.getName() + "_AdvancedPriority").withPlaceHolder(placeholders));
						return reward.getName();
					}
					plugin.extraDebug("AdvancedPriority: Can't give reward " + keys);
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedPriority")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedPriority")
							.getKeys(false)) {
						if (direct.getFileData().isConfigurationSection(
								direct.getPath() + direct.needsDot() + "AdvancedPriority." + str)) {
							subs.add(new SubDirectlyDefinedReward(direct, "AdvancedPriority." + str));
						}
					}
				}
				return subs;
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedPriority") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditAdvancedPriority() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}

				}.addLore(
						"AdvancedPriority will run first sub reward that it can, then ignore the rest of the sub rewards")
						.addLore("Can be used for permission based rewards or chance based rewards")))
				.priority(10).alwaysValid().postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("AdvancedWorld") {

			@Override
			public String onRewardRequested(Reward reward1, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				for (String keys : section.getKeys(false)) {
					plugin.extraDebug("AdvancedWorld: Giving reward " + reward1.getName() + "_AdvancedWorld");
					section.set(keys + ".Worlds", ArrayUtils.convert(new String[] { keys }));
					giveReward(user, section, keys, new RewardOptions().withPlaceHolder(placeholders)
							.setPrefix(reward1.getName() + "_AdvancedWorld"));
				}
				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedWorld")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "AdvancedWorld")
							.getKeys(false)) {
						if (direct.getFileData().isConfigurationSection(
								direct.getPath() + direct.needsDot() + "AdvancedWorld." + str)) {
							subs.add(new SubDirectlyDefinedReward(direct, "AdvancedWorld." + str));
						}
					}
				}
				return subs;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedWorld") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");

				new RewardEditAdvancedWorld() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);

			}

		}.addLore("AdvancedReward will run rewards based worlds specified"))).priority(10).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("SpecialChance") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				double totalChance = 0;
				LinkedHashMap<Double, String> map = new LinkedHashMap<>();
				for (String key : section.getKeys(false)) {
					String path = key;
					key = key.replaceAll("_", ".");
					if (MessageAPI.isDouble(key)) {
						double chance = Double.valueOf(key);
						totalChance += chance;
						map.put(chance, path);
					}
				}

				Set<Entry<Double, String>> copy = new HashSet<>(map.entrySet());
				double currentNum = 0;
				map.clear();
				for (Entry<Double, String> entry : copy) {
					currentNum += entry.getKey();
					map.put(currentNum, entry.getValue());
				}

				double randomNum = ThreadLocalRandom.current().nextDouble(totalChance);

				for (Entry<Double, String> entry : map.entrySet()) {
					if (randomNum <= entry.getKey()) {
						new RewardBuilder(section, entry.getValue()).withPrefix(reward.getName() + "_SpecialChance")
								.withPlaceHolder(placeholders).withPlaceHolder("chance", "" + entry.getKey())
								.send(user);

						AdvancedCorePlugin.getInstance().debug("Giving special chance: " + entry.getValue()
								+ ", Random numuber: " + randomNum + ", Total chance: " + totalChance);
						return null;
					}
				}

				AdvancedCorePlugin.getInstance().debug("Failed to give special chance");

				return null;

			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData()
						.isConfigurationSection(direct.getPath() + direct.needsDot() + "SpecialChance")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "SpecialChance")
							.getKeys(false)) {
						String key = str.replaceAll("_", ".");
						if (MessageAPI.isDouble(key)) {
							String path = "SpecialChance." + str;
							if (direct.getFileData()
									.isConfigurationSection(direct.getPath() + direct.needsDot() + path)) {
								subs.add(new SubDirectlyDefinedReward(direct, path));
							}
						}
					}
				}
				return subs;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("SpecialChance") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditSpecialChance() {

					@Override
					public void setVal(String key, Object value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(key, value);
						plugin.reloadAdvancedCore(false);
					}
				}.open(clickEvent.getPlayer(), reward);
			}

		}.addLore("Rewards based on chance"))).priority(10).postReward());

		injectedRewards.add(new RewardInjectKeys("RandomItem") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				if (section.size() > 0) {
					String item = ArrayUtils.pickRandom(ArrayUtils.convert(section));
					ItemBuilder builder = new ItemBuilder(data.getConfigurationSection(item));

					builder.setCheckLoreLength(false);
					user.giveItem(builder);
					return item;
				}
				return null;
			}
		}.asPlaceholder("RandomItem").priority(90).validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				for (String item : data.getConfigurationSection("RandomItem").getKeys(false)) {
					String material = data.getString("RandomItem." + item + ".Material", "");
					if (material.isEmpty()) {
						warning(reward, inject, "No material is set on item: " + item);
					} else {
						try {
							Material m = Material.matchMaterial(material.toUpperCase());

							// change legacy item
							if (m == null) {
								m = Material.matchMaterial(material.toUpperCase(), true);
								if (material != null) {
									warning(reward, inject, "Found legacy material: " + material
											+ ", please update material on RandomItem." + item);
								}
							}
						} catch (NoSuchMethodError e) {
						}
					}

				}
			}
		}));

		injectedRewards.add(new RewardInjectBoolean("EnableChoices") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, boolean value,
					HashMap<String, String> placeholders) {
				if (value) {
					debug("Checking choice rewards");
					// reward.checkRewardFile();
					String choice = user.getChoicePreference(reward.getName());
					if (choice.isEmpty() || choice.equalsIgnoreCase("none")) {
						debug("No choice specified");
						user.addUnClaimedChoiceReward(reward.getName());
					} else {
						giveChoicesReward(reward, user, choice);
					}
				}
				return null;
			}

			@Override
			public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
				ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
				if (direct.getFileData().getBoolean(direct.getPath() + direct.needsDot() + "EnableChoices") && direct
						.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Choices")) {
					for (String str : direct.getFileData()
							.getConfigurationSection(direct.getPath() + direct.needsDot() + "Choices").getKeys(false)) {

						String path = "Choices." + str + ".Rewards";
						if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + path)) {
							subs.add(new SubDirectlyDefinedReward(direct, path));
						}

					}
				}
				return subs;
			}
		}.priority(10).synchronize().validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getBoolean("EnableChoices")) {
					if (data.getConfigurationSection("Choices").getKeys(false).size() <= 1) {
						warning(reward, inject, "Not enough choices for choice rewards, 1 or less is not a choice");
					}
				}
			}
		}.addPath("Choices"))
				.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Choices") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditChoices() {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}

				}.addCheckKey("EnableChoices").addLore("Give users a choice on the reward"))));

		injectedRewards.add(new RewardInjectKeys("Items") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				boolean oneChance = reward.getConfig().getConfigData().getBoolean("OnlyOneItemChance", false);
				if (section.size() > 0) {
					for (String str : section) {
						ItemBuilder builder = new ItemBuilder(data.getConfigurationSection(str));
						builder.setCheckLoreLength(false);
						user.giveItem(builder.setPlaceholders(placeholders));
						debug("Giving item " + str + ":" + builder.toString());
						if (builder.isChancePass() && oneChance) {
							return str;
						}
					}
				}
				return "";
			}
		}.priority(90).asPlaceholder("Item").validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.isConfigurationSection("Items")) {
					for (String item : data.getConfigurationSection("Items").getKeys(false)) {
						String material = data.getString("Items." + item + ".Material", "");
						if (material.isEmpty()) {
							try {
								Material.valueOf(item);
							} catch (Exception e) {
								warning(reward, inject, "No material is set on item: " + item);
							}

						} else {
							try {
								Material m = Material.matchMaterial(material.toUpperCase());

								// check legacy
								if (m == null) {
									m = Material.matchMaterial(material, true);
									if (m != null) {
										warning(reward, inject,
												"Found legacy material: " + material + ", please update material");
									} else {
										warning(reward, inject, "Invalid material set: " + material);
									}
								}
							} catch (NoSuchMethodError e) {
							}
						}

						if (data.getInt("Items." + item + ".Amount", 0) == 0) {
							if (data.getInt("Items." + item + ".MinAmount", 0) == 0
									&& data.getInt("Items." + item + ".MaxAmount") == 0) {
								warning(reward, inject, "No amount on item: " + item);
							}
						}

					}
				} else {
					warning(reward, inject, "Invalid item section");
				}
			}
		}.addPath("OnlyOneItemChance"))
				.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Items") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						new RewardEditItems(plugin) {

							@Override
							public void setVal(String key, Object value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(key, value);
								plugin.reloadAdvancedCore(false);
							}
						}.open(clickEvent.getPlayer(), reward);
					}

				}.addLore("Edit items"))));

		for (RewardInject reward : injectedRewards) {
			reward.setInternalReward(true);
		}

		sortInjectedRewards();
	}

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		rewards = Collections.synchronizedList(new ArrayList<Reward>());
		setupExample();
		addValidPath("DirectlyDefinedReward");
		addValidPath("Delayed");
		addValidPath("Timed");
		addValidPath("DisplayItem");
		addValidPath("Repeat");
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
						if (reward1.getRepeatHandle().isEnabled() && reward1.getRepeatHandle().isRepeatOnStartup()
								&& !reward1.getConfig().isDirectlyDefinedReward()) {
							reward1.getRepeatHandle().giveRepeatAll(plugin);
						}
						if (!reward1.getConfig().isDirectlyDefinedReward()
								|| file.getName().equalsIgnoreCase("DirectlyDefined")) {
							rewards.add(reward1);
							if (reward1.getConfig().getConfigData().getConfigurationSection("").getKeys(true)
									.size() > 0) {

								plugin.extraDebug("Loaded Reward File: " + file.getAbsolutePath() + "/" + reward);
							} else {
								plugin.debug("Loaded empty reward file" + file.getAbsolutePath() + "/" + reward);
							}
						} else {
							plugin.extraDebug(
									"Ignoring directly defined reward file " + file.getAbsolutePath() + "/" + reward);
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
		repeatTimer.cancel();
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
		reward.validate();
		for (int i = getRewards().size() - 1; i >= 0; i--) {
			if (getRewards().get(i).getFile().getPath().equals(reward.getFile().getPath())) {
				getRewards().set(i, reward);
				return;
			}
		}
		getRewards().add(reward);
	}

}
