package com.Ben12345rocks.AdvancedCore.Rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
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
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInjectString;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInjectStringList;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStartup;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueList;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueNumber;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueString;
import com.Ben12345rocks.AdvancedCore.Util.Effects.FireworkHandler;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptEngine;
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

		injectedRewards.add(new RewardInjectString("Messages.Player") {

			@Override
			public void onRewardRequest(Reward reward, User user, String value, HashMap<String, String> placeholders) {
				user.sendMessage(value, placeholders);
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueString("Messages.Player", null) {

					@Override
					public void setValue(Player player, String value) {
						Reward reward = (Reward) getInv().getData("Reward");
						reward.getConfig().set(getKey(), value);
						plugin.reload();
					}
				})));

		injectedRewards.add(new RewardInjectString("Messages.Broadcast") {

			@Override
			public void onRewardRequest(Reward reward, User user, String value, HashMap<String, String> placeholders) {
				MiscUtils.getInstance().broadcast(StringUtils.getInstance().replacePlaceHolders(user.getPlayer(),
						StringUtils.getInstance().replacePlaceHolder(value, placeholders)));
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueString("Messages.Broadcast", null) {

					@Override
					public void setValue(Player player, String value) {
						Reward reward = (Reward) getInv().getData("Reward");
						reward.getConfig().set(getKey(), value);
						plugin.reload();
					}
				})));

		injectedRewards.add(new RewardInjectConfigurationSection("ActionBar") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				user.sendActionBar(
						StringUtils.getInstance().replacePlaceHolder(section.getString("Message", ""), placeholders),
						section.getInt("Delay", 30));
			}
		}.addEditButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueNumber("ActionBar.Delay", null) {

					@Override
					public void setValue(Player player, Number num) {
						Reward reward = (Reward) getInv().getData("Reward");
						reward.getConfig().set(getKey(), num.intValue());
						plugin.reload();
					}
				})).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
						new EditGUIValueString("ActionBar.Message", null) {

							@Override
							public void setValue(Player player, String value) {
								Reward reward = (Reward) getInv().getData("Reward");
								reward.getConfig().set(getKey(), value);
								plugin.reload();
							}
						})));

		injectedRewards.add(new RewardInjectStringList("Javascripts") {

			@Override
			public void onRewardRequest(Reward reward, User user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (!list.isEmpty()) {
					JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getPlayer());
					for (String str : list) {
						engine.execute(StringUtils.getInstance().replacePlaceHolder(str, placeholders));
					}
				}
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Javascripts", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				Reward reward = (Reward) getInv().getData("Reward");
				reward.getConfig().set(getKey(), value);
				plugin.reload();
			}
		})));

		injectedRewards.add(new RewardInjectConfigurationSection("Javascript") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					if (new JavascriptEngine().addPlayer(user.getPlayer()).getBooleanValue(StringUtils.getInstance()
							.replacePlaceHolder(section.getString("Expression"), placeholders))) {
						new RewardBuilder(section, "TrueRewards").withPrefix(reward.getName()).send(user);
					} else {
						new RewardBuilder(section, "FalseRewards").withPrefix(reward.getName()).send(user);
					}
				}

			}
		});

		injectedRewards.add(new RewardInjectStringList("RandomCommand") {

			@Override
			public void onRewardRequest(Reward r, User user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				if (list.size() > 0) {
					MiscUtils.getInstance().executeConsoleCommands(user.getPlayer(),
							list.get(ThreadLocalRandom.current().nextInt(list.size())), placeholders);
				}
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomCommand", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				Reward reward = (Reward) getInv().getData("Reward");
				reward.getConfig().set(getKey(), value);
				plugin.reload();
			}
		})));

		injectedRewards.add(new RewardInjectStringList("Priority") {

			@Override
			public void onRewardRequest(Reward r, User user, ArrayList<String> list,
					HashMap<String, String> placeholders) {
				for (String str : list) {
					Reward reward = RewardHandler.getInstance().getReward(str);
					if (reward.canGiveReward(user)) {
						new RewardBuilder(reward).withPlaceHolder(placeholders).setIgnoreChance(true).send(user);
						return;
					}
				}
			}
		}.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Priority", null) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				Reward reward = (Reward) getInv().getData("Reward");
				reward.getConfig().set(getKey(), value);
				plugin.reload();
			}
		})));

		injectedRewards.add(new RewardInjectConfigurationSection("Potions") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				for (String potion : section.getKeys(false)) {
					user.givePotionEffect(potion, section.getInt(potion + ".Duration", 1),
							section.getInt(potion + ".Amplifier", 1));

				}

			}
		});

		injectedRewards.add(new RewardInjectConfigurationSection("Title") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					user.sendTitle(
							StringUtils.getInstance().replacePlaceHolder(section.getString("Title"), placeholders),

							StringUtils.getInstance().replacePlaceHolder(section.getString("SubTitle"), placeholders),

							section.getInt("FadeIn", 10), section.getInt("ShowTime", 50),
							section.getInt("FadeOut", 10));
				}

			}
		});

		injectedRewards.add(new RewardInjectConfigurationSection("BossBar") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					user.sendBossBar(
							StringUtils.getInstance().replacePlaceHolder(section.getString("Message", ""),
									placeholders),
							section.getString("Color", "BLUE"), section.getString("Style", "SOLID"),
							section.getDouble("Progress", .5), section.getInt("Delay", 30));
				}

			}
		});

		injectedRewards.add(new RewardInjectConfigurationSection("Sound") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {

				if (section.getBoolean("Enabled")) {
					try {
						user.playSound(section.getString("Sound"), (float) section.getDouble("Volume", 1.0),
								(float) section.getDouble("Pitch", 1.0));
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

			}
		});

		injectedRewards.add(new RewardInjectConfigurationSection("Effect") {

			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (section.getBoolean("Enabled")) {
					user.playParticle(section.getString("Effect"), section.getInt("Data", 1),
							section.getInt("Particles", 1), section.getInt("Radius", 5));
				}

			}
		});

		injectedRewards.add(new RewardInjectConfigurationSection("Firework") {

			@SuppressWarnings("unchecked")
			@Override
			public void onRewardRequested(Reward reward, User user, ConfigurationSection section,
					HashMap<String, String> placeholders) {
				if (section.getBoolean("Enabled")) {
					FireworkHandler.getInstance().launchFirework(user.getPlayer().getLocation(),
							section.getInt("Power", 1),
							(ArrayList<String>) section.getList("Colors", new ArrayList<String>()),
							(ArrayList<String>) section.getList("FadeOutColor", new ArrayList<String>()),
							section.getBoolean("Trail"), section.getBoolean("Flicker"),
							(ArrayList<String>) section.getList("Types", new ArrayList<String>()));
				}

			}
		});
	}
}
