package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

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
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditActionBar;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedPriority;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedRandomReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditBossBar;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditChoices;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXP;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXPLevels;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEffect;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditFirework;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditItems;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditJavascript;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditLocationDistance;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMessages;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMoney;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSound;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSpecialChance;
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

import lombok.Getter;

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

	@Getter
	private ArrayList<RewardInject> injectedRewards = new ArrayList<RewardInject>();

	@Getter
	private ArrayList<RequirementInject> injectedRequirements = new ArrayList<RequirementInject>();

	@Getter
	private ArrayList<RewardPlaceholderHandle> placeholders = new ArrayList<RewardPlaceholderHandle>();

	@Getter
	private ArrayList<DirectlyDefinedReward> directlyDefinedRewards = new ArrayList<DirectlyDefinedReward>();

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The rewards. */
	private List<Reward> rewards;

	/** The default folder. */
	private File defaultFolder;

	/** The reward folders. */
	private ArrayList<File> rewardFolders;

	public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
		plugin.debug("Adding directlydefined reward handle: " + directlyDefinedReward.getPath()
				+ ", isdirectlydefined: " + directlyDefinedReward.isDirectlyDefined());
		directlyDefinedRewards.add(directlyDefinedReward);
	}

	public DirectlyDefinedReward getDirectlyDefined(String path) {
		for (DirectlyDefinedReward direct : getDirectlyDefinedRewards()) {
			if (direct.getPath().equalsIgnoreCase(path)) {
				return direct;
			}
		}
		return null;
	}

	/**
	 * Instantiates a new reward handler.
	 */
	private RewardHandler() {
		rewardFolders = new ArrayList<File>();
		setDefaultFolder(new File(AdvancedCorePlugin.getInstance().getDataFolder(), "Rewards"));
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
		addRewardFolder(file, true);
	}

	public void addRewardFolder(File file, boolean load) {
		file.mkdirs();
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
			}
		}, 0);

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

		return new Reward(new File(getDefaultFolder().getAbsolutePath() + File.separator + "DirectlyDefined"), reward);
	}

	private String getFileExtension(File file) {
		String name = file.getName();
		int lastIndexOf = name.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return ""; // empty extension
		}
		return name.substring(lastIndexOf);
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
		if (data.isList(path)) {
			ArrayList<String> rewards = (ArrayList<String>) data.getList(path, new ArrayList<String>());
			if (rewards.isEmpty()) {
				plugin.debug(
						"Not giving empty list of rewards from " + path + ", Options: " + rewardOptions.toString());
			} else {
				plugin.debug("Giving list of rewards (" + ArrayUtils.getInstance().makeStringList(rewards) + ") from "
						+ path + ", Options: " + rewardOptions.toString());
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
			if (suffix != null && prefix != null && direct != null) {

				Reward reward = direct.getReward();
				plugin.debug("Giving directlydefined reward " + path + ", Options: " + rewardOptions.toString());
				giveReward(user, reward, rewardOptions);
			} else {
				ConfigurationSection section = data.getConfigurationSection(path);
				Reward reward = new Reward(rewardName, section);
				reward.checkRewardFile();
				plugin.debug("Giving reward " + path + ", Options: " + rewardOptions.toString());
				giveReward(user, reward, rewardOptions);
			}
		} else {
			String reward = data.getString(path, "");
			if (!reward.isEmpty()) {
				plugin.debug(
						"Giving reward " + reward + " from path " + path + ", Options: " + rewardOptions.toString());
				giveReward(user, reward, rewardOptions);
			} else {
				plugin.debug("Not giving reward " + reward + " from path " + path + ", Options: "
						+ rewardOptions.toString());
			}
		}
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

	public void giveReward(AdvancedCoreUser user, Reward reward, RewardOptions rewardOptions) {
		// make sure reward is async to avoid issues
		if (Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					reward.giveReward(user, rewardOptions);
				}
			});
		} else {
			reward.giveReward(user, rewardOptions);
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
				if (rewardOptions.getPlaceholders().containsKey("ExecDate") && num > 0) {
					long execDate = Long.parseLong(rewardOptions.getPlaceholders().get("ExecDate"));
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

				boolean perm = PlayerUtils.getInstance().hasServerPermission(user.getPlayerName(), str);
				if (!perm) {
					plugin.getLogger().info(user.getPlayerName() + " does not have permission " + str
							+ " to get reward " + reward.getName());
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
					}
				}.addLore("Set permission required to be given, set RequirePermission to true if using this")))
				.addEditButton(new EditGUIButton(new EditGUIValueBoolean("RequirePermission", null) {

					@Override
					public void setValue(Player player, boolean value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
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

		injectedRequirements.add(new RequirementInjectStringList("Worlds", new ArrayList<String>()) {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> worlds,
					RewardOptions rewardOptions) {
				if (worlds.isEmpty()) {
					return true;
				}

				Player player = user.getPlayer();
				if (player == null) {
					return false;
				}
				reward.checkRewardFile();
				String world = player.getWorld().getName();
				if (worlds.contains(world)) {
					return true;
				}

				user.setCheckWorld(true);
				return false;
			}
		}.priority(100).allowReattempt().addEditButton(
				new EditGUIButton(new ItemBuilder("END_PORTAL_FRAME"), new EditGUIValueList("Worlds", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
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

		injectedRequirements.add(new RequirementInjectString("RewardType", "BOTH") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String type,
					RewardOptions rewardOptions) {
				if (rewardOptions.isOnline()) {
					if (type.equalsIgnoreCase("offline")) {
						plugin.debug("Reward Type Don't match");
						return false;
					}
				} else {
					if (type.equalsIgnoreCase("online")) {
						plugin.debug("Reward Type Don't match");
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
					}
				}.addOptions("ONLINE", "OFFLINE", "BOTH").addLore("Define whether should execute if player was offline/online"))));

		injectedRequirements.add(new RequirementInjectString("JavascriptExpression", "") {

			@Override
			public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String expression,
					RewardOptions rewardOptions) {
				if (expression.equals("")) {
					return true;
				}
				if (new JavascriptEngine().addPlayer(user.getOfflinePlayer()).getBooleanValue(
						StringParser.getInstance().replacePlaceHolder(expression, rewardOptions.getPlaceholders()))) {
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
				user.giveMoney(value);
				DecimalFormat f = new DecimalFormat("##.00");
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
				for (String str : value) {
					MiscUtils.getInstance().broadcast(StringParser.getInstance().replacePlaceHolders(user.getPlayer(),
							StringParser.getInstance().replacePlaceHolder(str, placeholders)));
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
				MiscUtils.getInstance().broadcast(StringParser.getInstance().replacePlaceHolders(user.getPlayer(),
						StringParser.getInstance().replacePlaceHolder(value, placeholders)));
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
				MiscUtils.getInstance().executeConsoleCommands(user.getPlayer(), value, placeholders);
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueString("Command", null) {

			@Override
			public void setValue(Player player, String value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
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
				user.sendActionBar(
						StringParser.getInstance().replacePlaceHolder(section.getString("Message", ""), placeholders),
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
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), list, placeholders);
				}
				return null;
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueList("Commands", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value);
				plugin.reloadAdvancedCore(false);
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

				ArrayList<String> consoleCommands = (ArrayList<String>) section.getList("Console",
						new ArrayList<String>());
				ArrayList<String> userCommands = (ArrayList<String>) section.getList("Player", new ArrayList<String>());
				if (!consoleCommands.isEmpty()) {
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), consoleCommands, placeholders);
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
					}
				}.addLore("Old style for console commands"))).addEditButton(new EditGUIButton(
						new ItemBuilder(Material.PAPER), new EditGUIValueList("Commands.Player", null) {

							@Override
							public void setValue(Player player, ArrayList<String> value) {
								RewardEditData reward = (RewardEditData) getInv().getData("Reward");
								reward.setValue(getKey(), value);
								plugin.reloadAdvancedCore(false);
							}
						}.addLore("Execute commands as player"))));

		injectedRewards.add(new RewardInjectStringList("Javascripts") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (!list.isEmpty()) {
					JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getOfflinePlayer());
					for (String str : list) {
						engine.execute(StringParser.getInstance().replacePlaceHolder(str, placeholders));
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
			}
		}.addLore("Javascript expressions to run"))));

		injectedRewards.add(new RewardInjectConfigurationSection("Javascript") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					if (new JavascriptEngine().addPlayer(user.getOfflinePlayer()).getBooleanValue(StringParser
							.getInstance().replacePlaceHolder(section.getString("Expression"), placeholders))) {
						new RewardBuilder(section, "TrueRewards").withPrefix(reward.getName()).send(user);
					} else {
						new RewardBuilder(section, "FalseRewards").withPrefix(reward.getName()).send(user);
					}
				}
				return null;

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
				HashMap<Integer, String> luckyRewards = new HashMap<Integer, String>();

				for (String str : section.getKeys(false)) {
					if (StringParser.getInstance().isInt(str)) {
						int num = Integer.parseInt(str);
						if (num > 0) {
							String path = "Lucky." + num;
							luckyRewards.put(num, path);
						}
					}
				}
				HashMap<String, Integer> map = new LinkedHashMap<String, Integer>();
				for (Entry<Integer, String> entry : luckyRewards.entrySet()) {
					if (MiscUtils.getInstance().checkChance(1, entry.getKey())) {
						map.put(entry.getValue(), entry.getKey());
					}
				}

				map = ArrayUtils.getInstance().sortByValuesStr(map, false);
				if (map.size() > 0) {
					if (reward.getConfig().getConfigData().getBoolean("OnlyOneLucky", false)) {
						for (Entry<String, Integer> entry : map.entrySet()) {
							new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
									.withPlaceHolder(placeholders).send(user);
							return null;
						}
					} else {
						for (Entry<String, Integer> entry : map.entrySet()) {
							new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
									.withPlaceHolder(placeholders).send(user);
						}
					}
				}
				return null;

			}
		}.priority(10));

		injectedRewards.add(new RewardInjectConfigurationSection("Random") {

			@SuppressWarnings("unchecked")
			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (MiscUtils.getInstance().checkChance(section.getDouble("Chance", 100), 100)) {
					if (section.getBoolean("PickRandom", true)) {
						ArrayList<String> rewards = (ArrayList<String>) section.getList("Rewards",
								new ArrayList<String>());
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
		}.priority(10));

		injectedRewards.add(new RewardInjectConfigurationSection("Rewards") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				new RewardBuilder(reward.getConfig().getConfigData(), "Rewards").withPrefix(reward.getName())
						.withPlaceHolder(placeholders).send(user);
				return null;

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
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayer(),
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
		}.asPlaceholder("RandomReward").priority(90).addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomReward", null) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}.addLore("Execute random reward"))).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("AdvancedRandomReward") {

			@Override
			public String onRewardRequested(Reward r, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				Set<String> keys = section.getKeys(false);
				ArrayList<String> rewards = ArrayUtils.getInstance().convert(keys);
				if (rewards.size() > 0) {
					String reward = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
					giveReward(user, section, reward, new RewardOptions().setPlaceholders(placeholders)
							.setPrefix(r.getRewardName() + "_AdvancedRandomReward_"));
					return reward;
				}
				return null;

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

				}.addLore("Execute random reward"))).asPlaceholder("RandomReward").priority(90).postReward());

		injectedRewards.add(new RewardInjectStringList("Priority") {

			@Override
			public String onRewardRequest(Reward r, AdvancedCoreUser user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				for (String str : list) {
					Reward reward = RewardHandler.getInstance().getReward(str);
					if (reward.canGiveReward(user, new RewardOptions())) {
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
		});

		injectedRewards.add(new RewardInjectConfigurationSection("Title") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					user.sendTitle(
							StringParser.getInstance().replacePlaceHolder(section.getString("Title"), placeholders),

							StringParser.getInstance().replacePlaceHolder(section.getString("SubTitle"), placeholders),

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
							StringParser.getInstance().replacePlaceHolder(section.getString("Message", ""),
									placeholders),
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
							(ArrayList<String>) section.getList("Colors", new ArrayList<String>()),
							(ArrayList<String>) section.getList("FadeOutColor", new ArrayList<String>()),
							section.getBoolean("Trail"), section.getBoolean("Flicker"),
							(ArrayList<String>) section.getList("Types", new ArrayList<String>()));
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
					if (reward != null && reward.canGiveReward(user, new RewardOptions())) {
						plugin.extraDebug("AdvancedPriority: Giving reward " + reward.getName());
						reward.giveReward(user, new RewardOptions().withPlaceHolder(placeholders).setIgnoreChance(true)
								.setIgnoreRequirements(true).setPrefix(reward1.getName() + "_AdvancedPriority"));
						return reward.getName();
					} else {
						plugin.extraDebug("AdvancedPriority: Can't give reward " + reward.getName());
					}
				}
				return null;

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
				.priority(10).postReward());

		injectedRewards.add(new RewardInjectConfigurationSection("SpecialChance") {

			@Override
			public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				double totalChance = 0;
				LinkedHashMap<Double, String> map = new LinkedHashMap<Double, String>();
				for (String key : section.getKeys(false)) {
					String path = key;
					key = key.replaceAll("_", ".");
					if (StringParser.getInstance().isDouble(key)) {
						double chance = Double.valueOf(key);
						totalChance += chance;
						map.put(chance, path);
					}
				}

				Set<Entry<Double, String>> copy = new HashSet<Entry<Double, String>>(map.entrySet());
				double currentNum = 0;
				map.clear();
				for (Entry<Double, String> entry : copy) {
					currentNum += entry.getKey();
					map.put(currentNum, entry.getValue());
				}

				double randomNum = ThreadLocalRandom.current().nextDouble(totalChance);

				for (Entry<Double, String> entry : map.entrySet()) {
					if (randomNum <= entry.getKey()) {
						new RewardBuilder(section, entry.getValue()).withPrefix(reward.getName())
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
					String item = ArrayUtils.getInstance().pickRandom(ArrayUtils.getInstance().convert(section));
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
			}
		}));

		injectedRewards.add(new RewardInjectBoolean("EnableChoices") {

			@Override
			public String onRewardRequest(Reward reward, AdvancedCoreUser user, boolean value,
					HashMap<String, String> placeholders) {
				if (value) {
					debug("Checking choice rewards");
					reward.checkRewardFile();
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

		}.priority(10).synchronize().validator(new RewardInjectValidator() {

			@Override
			public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
				if (data.getBoolean("EnableChoices")) {
					if (data.getConfigurationSection("Choices").getKeys(false).size() <= 1) {
						warning(reward, inject, "Not enough choices for choice rewards, 1 or less is not a choice");
					}
				}
			}
		}).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Choices") {

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
							return null;
						}
					}
				}
				return null;
			}
		}.priority(90).validator(new RewardInjectValidator() {

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
		}).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Items") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				new RewardEditItems() {

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

	public void openSubReward(Player player, String path, RewardEditData reward) {
		if (!reward.getData().contains(path)) {
			reward.createSection(path);
		}
		RewardEditGUI.getInstance().openRewardGUI(player, new RewardEditData(new DirectlyDefinedReward(path) {

			@Override
			public void setData(String path, Object value) {
				reward.setValue(path, value);
			}

			@Override
			public void save() {
				reward.save();
			}

			@Override
			public ConfigurationSection getFileData() {
				return reward.getData();
			}

			@Override
			public void createSection(String path) {
				reward.createSection(path);
			}
		}, reward), reward.getName() + "." + path);
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
							reward1.getRepeatHandle().giveRepeatAll();
						}
						if (!reward1.getConfig().isDirectlyDefinedReward()
								|| file.getName().equalsIgnoreCase("DirectlyDefined")) {
							if (reward1.getConfig().getConfigData().getConfigurationSection("").getKeys(true)
									.size() > 0) {
								rewards.add(reward1);
								plugin.extraDebug("Loaded Reward File: " + file.getAbsolutePath() + "/" + reward);
							} else {
								plugin.debug("Ignoring empty reward file" + file.getAbsolutePath() + "/" + reward);
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

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		rewards = Collections.synchronizedList(new ArrayList<Reward>());
		setupExample();
		for (File file : rewardFolders) {
			loadRewards(file);
		}

		sortInjectedRewards();
		sortInjectedRequirements();
		plugin.debug("Loaded rewards");

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

	public boolean usesTimed() {
		for (Reward reward : getRewards()) {
			if (reward.isTimedEnabled() || reward.isDelayEnabled()) {
				return true;
			}
		}
		return false;
	}
}
