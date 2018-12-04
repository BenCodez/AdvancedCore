package com.Ben12345rocks.AdvancedCore.Rewards;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerRewardEvent;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInject;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.Util.Annotation.AnnotationHandler;
import com.Ben12345rocks.AdvancedCore.Util.Effects.FireworkHandler;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptEngine;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.MiscUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

/**
 * The Class Reward.
 */
public class Reward {

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/** The name. */
	private String name;

	/** The file data. */
	private RewardFileData fileData;

	private String rewardType;

	private boolean forceOffline;

	/** The delay enabled. */
	private boolean delayEnabled;

	/** The delay hours. */
	private int delayHours;

	/** The delay minutes. */
	private int delayMinutes;

	private int delaySeconds;

	/** The timed enabled. */
	private boolean timedEnabled;

	/** The timed hour. */
	private int timedHour;

	/** The timed minute. */
	private int timedMinute;

	/** The chance. */
	private double chance;

	/** The random chance. */
	private double randomChance;

	/** The require permission. */
	private boolean requirePermission;

	/** The worlds. */
	private ArrayList<String> worlds;

	/** The items. */
	private Set<String> items;

	/** The money. */
	private int money;

	/** The Min money. */
	private int MinMoney;

	/** The Max money. */
	private int MaxMoney;

	/** The exp. */
	private int exp;

	/** The min exp. */
	private int minExp;

	/** The max exp. */
	private int maxExp;

	/** The console commands. */
	private ArrayList<String> consoleCommands;

	private ArrayList<String> randomCommand;

	/** The player commands. */
	private ArrayList<String> playerCommands;

	/** The potions. */
	private Set<String> potions;

	/** The potions duration. */
	private HashMap<String, Integer> potionsDuration;

	/** The potions amplifier. */
	private HashMap<String, Integer> potionsAmplifier;

	/** The reward msg. */
	private String rewardMsg;

	/** The action bar msg. */
	private String actionBarMsg;

	/** The action bar delay. */
	private int actionBarDelay;

	/** The boss bar enabled. */
	private boolean bossBarEnabled;

	/** The boss bar message. */
	private String bossBarMessage;

	/** The boss bar color. */
	private String bossBarColor;

	/** The boss bar style. */
	private String bossBarStyle;

	/** The boss bar delay. */
	private int bossBarDelay;

	/** The boss bar progress. */
	private double bossBarProgress;

	/** The broadcast msg. */
	private String broadcastMsg;

	/** The permission. */
	private String permission;

	/** The javascript enabled. */
	private boolean javascriptEnabled;

	/** The javascript expression. */
	private String javascriptExpression;

	/** The items and amounts given. */
	private HashMap<String, Integer> itemsAndAmountsGiven;

	/** The choice rewards enabled. */
	private boolean enableChoices;

	private Set<String> choices;

	/** The firework enabled. */
	private boolean fireworkEnabled;

	/** The firework flicker. */
	private boolean fireworkFlicker;

	/** The firework trail. */
	private boolean fireworkTrail;

	/** The firework power. */
	private int fireworkPower;

	/** The firework colors. */
	private ArrayList<String> fireworkColors;

	/** The firework fade out colors. */
	private ArrayList<String> fireworkFadeOutColors;

	/** The firework types. */
	private ArrayList<String> fireworkTypes;

	/** The uses worlds. */
	private boolean usesWorlds;

	/** The title enabled. */
	private boolean titleEnabled;

	/** The title title. */
	private String titleTitle;

	/** The title sub title. */
	private String titleSubTitle;

	/** The title fade in. */
	private int titleFadeIn;

	/** The title show time. */
	private int titleShowTime;

	/** The title fade out. */
	private int titleFadeOut;

	/** The sound enabled. */
	private boolean soundEnabled;

	/** The sound sound. */
	private String soundSound;

	/** The sound volume. */
	private float soundVolume;

	/** The sound pitch. */
	private float soundPitch;

	/** The effect enabled. */
	private boolean effectEnabled;

	/** The effect effect. */
	private String effectEffect;

	/** The effect data. */
	private int effectData;

	/** The effect particles. */
	private int effectParticles;

	/** The effect radius. */
	private int effectRadius;

	private ArrayList<String> javascripts;

	private ArrayList<String> priority;

	private HashMap<Integer, String> luckyRewards;

	private boolean onlyOneLucky;

	private String server;

	private File file;

	private boolean randomPickRandom;

	/**
	 * Instantiates a new reward.
	 *
	 * @param file
	 *            the file
	 * @param reward
	 *            the reward
	 */
	public Reward(File file, String reward) {
		load(file, reward);
	}

	/**
	 * Instantiates a new reward.
	 *
	 * @param reward
	 *            the reward
	 */
	public Reward(String reward) {
		load(RewardHandler.getInstance().getDefaultFolder(), reward);
	}

	public Reward(String name, ConfigurationSection section) {
		load(name, section);
	}

	public boolean canGiveReward(User user) {
		if (hasPermission(user) && checkChance()) {
			return true;
		}
		return false;
	}

	/**
	 * Check chance.
	 *
	 * @return true, if successful
	 */
	public boolean checkChance() {
		return MiscUtils.getInstance().checkChance(getChance(), 100);
	}

	/**
	 * Check choice rewards.
	 *
	 * @param user
	 *            the user
	 */
	public void checkChoiceRewards(User user) {
		if (isEnableChoices()) {
			checkRewardFile();
			String choice = user.getChoicePreference(getName());
			if (choice.isEmpty() || choice.equalsIgnoreCase("none")) {
				user.addUnClaimedChoiceReward(getName());
			} else {
				giveChoicesReward(user, choice);
			}
		}
	}

	/**
	 * Check delayed.
	 *
	 * @param user
	 *            the user
	 * @return true, if successful
	 */
	public boolean checkDelayed(User user) {
		if (!isDelayEnabled()) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		time = time.plus(getDelayHours(), ChronoUnit.HOURS);
		time = time.plus(getDelayMinutes(), ChronoUnit.MINUTES);
		time = time.plus(getDelaySeconds(), ChronoUnit.SECONDS);
		checkRewardFile();
		user.addTimedReward(this, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		plugin.debug("Giving reward " + name + " in " + getDelayHours() + " hours, " + getDelayMinutes() + " minutes, "
				+ getDelaySeconds() + " seconds (" + time.toString() + ")");
		return true;
	}

	/**
	 * Check random chance.
	 *
	 * @return true, if successful
	 */
	public boolean checkRandomChance() {
		return MiscUtils.getInstance().checkChance(getRandomChance(), 100);
	}

	private void checkRewardFile() {
		if (!getConfig().hasRewardFile()) {
			Reward reward = RewardHandler.getInstance().getReward(name);
			ConfigurationSection section = getConfig().getConfigData();

			if (reward.getConfig().getConfigData().getConfigurationSection("").getKeys(true).size() != 0) {
				if (reward.getConfig().getConfigData().getConfigurationSection("").getKeys(true)
						.size() != section.getKeys(true).size() + 1) {
					plugin.getPlugin().getLogger().warning(
							"Detected a reward file edited when it should be edited where directly defined, overriding");
				}
			}
			reward.getConfig().setData(section);
			reward.getConfig().getFileData().options()
					.header("Directly defined reward file. ANY EDITS HERE CAN GET OVERRIDDEN!");
			reward.getConfig().setDirectlyDefinedReward(true);
			reward.getConfig().save(reward.getConfig().getFileData());
			RewardHandler.getInstance().updateReward(reward);
		}
	}

	public boolean checkServer() {
		if (server != null && !server.isEmpty()) {
			return Bukkit.getServer().getName().equals(server);
		}
		return true;
	}

	/**
	 * Check timed.
	 *
	 * @param user
	 *            the user
	 * @return true, if successful
	 */
	public boolean checkTimed(User user) {
		if (!isTimedEnabled()) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		time = time.withHour(getTimedHour());
		time = time.withMinute(getTimedMinute());

		if (LocalDateTime.now().isAfter(time)) {
			time = time.plusDays(1);
		}
		checkRewardFile();
		user.addTimedReward(this, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		plugin.debug("Giving reward " + name + " at " + time.toString());
		return true;
	}

	public boolean checkWorld(User user) {
		if (!isUsesWorlds()) {
			return true;
		}

		Player player = user.getPlayer();
		if (player == null) {
			return false;
		}
		checkRewardFile();
		String world = player.getWorld().getName();
		if (getWorlds().contains(world)) {
			return true;
		}

		return false;
	}

	/**
	 * Gets the action bar delay.
	 *
	 * @return the action bar delay
	 */
	public int getActionBarDelay() {
		return actionBarDelay;
	}

	/**
	 * Gets the action bar msg.
	 *
	 * @return the action bar msg
	 */
	public String getActionBarMsg() {
		return actionBarMsg;
	}

	/**
	 * Gets the boss bar color.
	 *
	 * @return the boss bar color
	 */
	public String getBossBarColor() {
		return bossBarColor;
	}

	/**
	 * Gets the boss bar delay.
	 *
	 * @return the boss bar delay
	 */
	public int getBossBarDelay() {
		return bossBarDelay;
	}

	/**
	 * Gets the boss bar message.
	 *
	 * @return the boss bar message
	 */
	public String getBossBarMessage() {
		return bossBarMessage;
	}

	/**
	 * Gets the boss bar progress.
	 *
	 * @return the boss bar progress
	 */
	public double getBossBarProgress() {
		return bossBarProgress;
	}

	/**
	 * Gets the boss bar style.
	 *
	 * @return the boss bar style
	 */
	public String getBossBarStyle() {
		return bossBarStyle;
	}

	/**
	 * Gets the broadcast msg.
	 *
	 * @return the broadcast msg
	 */
	public String getBroadcastMsg() {
		return broadcastMsg;
	}

	/**
	 * Gets the chance.
	 *
	 * @return the chance
	 */
	public double getChance() {
		return chance;
	}

	/**
	 * @return the choices
	 */
	public Set<String> getChoices() {
		return choices;
	}

	/**
	 * Gets the config.
	 *
	 * @return the config
	 */
	public RewardFileData getConfig() {
		return fileData;
	}

	/**
	 * Gets the console commands.
	 *
	 * @return the console commands
	 */
	public ArrayList<String> getConsoleCommands() {
		return consoleCommands;
	}

	/**
	 * Gets the delay hours.
	 *
	 * @return the delay hours
	 */
	public int getDelayHours() {
		return delayHours;
	}

	/**
	 * Gets the delay minutes.
	 *
	 * @return the delay minutes
	 */
	public int getDelayMinutes() {
		return delayMinutes;
	}

	/**
	 * @return the delaySeconds
	 */
	public int getDelaySeconds() {
		return delaySeconds;
	}

	/**
	 * Gets the effect data.
	 *
	 * @return the effect data
	 */
	public int getEffectData() {
		return effectData;
	}

	/**
	 * Gets the effect effect.
	 *
	 * @return the effect effect
	 */
	public String getEffectEffect() {
		return effectEffect;
	}

	/**
	 * Gets the effect particles.
	 *
	 * @return the effect particles
	 */
	public int getEffectParticles() {
		return effectParticles;
	}

	/**
	 * Gets the effect radius.
	 *
	 * @return the effect radius
	 */
	public int getEffectRadius() {
		return effectRadius;
	}

	/**
	 * Gets the .
	 *
	 * @return the exp
	 */
	public int getExp() {
		return exp;
	}

	/**
	 * Gets the exp to give.
	 *
	 * @return the exp to give
	 */
	public int getExpToGive() {
		int amount = getExp();
		int maxAmount = getMaxExp();
		int minAmount = getMinExp();
		if ((maxAmount == 0) && (minAmount == 0)) {
			return amount;
		} else {
			return ThreadLocalRandom.current().nextInt(minAmount, maxAmount);
		}
	}

	/**
	 * @return the file
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Gets the firework colors.
	 *
	 * @return the firework colors
	 */
	public ArrayList<String> getFireworkColors() {
		return fireworkColors;
	}

	/**
	 * Gets the firework fade out colors.
	 *
	 * @return the firework fade out colors
	 */
	public ArrayList<String> getFireworkFadeOutColors() {
		return fireworkFadeOutColors;
	}

	/**
	 * Gets the firework power.
	 *
	 * @return the firework power
	 */
	public int getFireworkPower() {
		return fireworkPower;
	}

	/**
	 * Gets the firework types.
	 *
	 * @return the firework types
	 */
	public ArrayList<String> getFireworkTypes() {
		return fireworkTypes;
	}

	public ItemStack getItem() {
		return new ItemStack(Material.STONE);
	}

	/**
	 * Gets the items.
	 *
	 * @return the items
	 */
	public Set<String> getItems() {
		return items;
	}

	/**
	 * Gets the items and amounts given.
	 *
	 * @return the items and amounts given
	 */
	public HashMap<String, Integer> getItemsAndAmountsGiven() {
		return itemsAndAmountsGiven;
	}

	public ItemStack getItemStack(User user, String item) {
		return new ItemBuilder(getConfig().getItemSection(item)).setSkullOwner(user.getOfflinePlayer())
				.toItemStack(user.getPlayer());
	}

	/**
	 * Gets the javascript expression.
	 *
	 * @return the javascript expression
	 */
	public String getJavascriptExpression() {
		return javascriptExpression;
	}

	/**
	 * @return the javascripts
	 */
	public ArrayList<String> getJavascripts() {
		return javascripts;
	}

	/**
	 * @return the luckyRewards
	 */
	public HashMap<Integer, String> getLuckyRewards() {
		return luckyRewards;
	}

	/**
	 * Gets the max exp.
	 *
	 * @return the max exp
	 */
	public int getMaxExp() {
		return maxExp;
	}

	/**
	 * Gets the max money.
	 *
	 * @return the max money
	 */
	public int getMaxMoney() {
		return MaxMoney;
	}

	/**
	 * Gets the min exp.
	 *
	 * @return the min exp
	 */
	public int getMinExp() {
		return minExp;
	}

	/**
	 * Gets the min money.
	 *
	 * @return the min money
	 */
	public int getMinMoney() {
		return MinMoney;
	}

	/**
	 * Gets the money.
	 *
	 * @return the money
	 */
	public int getMoney() {
		return money;
	}

	/**
	 * Gets the money to give.
	 *
	 * @return the money to give
	 */
	public int getMoneyToGive() {
		int amount = getMoney();
		int maxAmount = getMaxMoney();
		int minAmount = getMinMoney();
		if ((maxAmount == 0) && (minAmount == 0)) {
			return amount;
		} else {
			return ThreadLocalRandom.current().nextInt(minAmount, maxAmount);
		}
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the permission.
	 *
	 * @return the permission
	 */
	public String getPermission() {
		return permission;
	}

	/**
	 * Gets the player commands.
	 *
	 * @return the player commands
	 */
	public ArrayList<String> getPlayerCommands() {
		return playerCommands;
	}

	/**
	 * Gets the potions.
	 *
	 * @return the potions
	 */
	public Set<String> getPotions() {
		return potions;
	}

	/**
	 * Gets the potions amplifier.
	 *
	 * @return the potions amplifier
	 */
	public HashMap<String, Integer> getPotionsAmplifier() {
		return potionsAmplifier;
	}

	/**
	 * Gets the potions duration.
	 *
	 * @return the potions duration
	 */
	public HashMap<String, Integer> getPotionsDuration() {
		return potionsDuration;
	}

	/**
	 * @return the priority
	 */
	public ArrayList<String> getPriority() {
		return priority;
	}

	/**
	 * Gets the random chance.
	 *
	 * @return the random chance
	 */
	public double getRandomChance() {
		return randomChance;
	}

	public ArrayList<String> getRandomCommand() {
		return randomCommand;
	}

	/**
	 * Gets the reward msg.
	 *
	 * @return the reward msg
	 */
	public String getRewardMsg() {
		return rewardMsg;
	}

	/**
	 * Gets the reward name.
	 *
	 * @return the reward name
	 */
	public String getRewardName() {
		return name;
	}

	/**
	 * Gets the reward type.
	 *
	 * @return the reward type
	 */
	public String getRewardType() {
		return rewardType;
	}

	public String getServer() {
		return server;
	}

	/**
	 * Gets the sound pitch.
	 *
	 * @return the sound pitch
	 */
	public double getSoundPitch() {
		return soundPitch;
	}

	/**
	 * Gets the sound sound.
	 *
	 * @return the sound sound
	 */
	public String getSoundSound() {
		return soundSound;
	}

	/**
	 * Gets the sound volume.
	 *
	 * @return the sound volume
	 */
	public double getSoundVolume() {
		return soundVolume;
	}

	/**
	 * Gets the timed hour.
	 *
	 * @return the timed hour
	 */
	public int getTimedHour() {
		return timedHour;
	}

	/**
	 * Gets the timed minute.
	 *
	 * @return the timed minute
	 */
	public int getTimedMinute() {
		return timedMinute;
	}

	/**
	 * Gets the title fade in.
	 *
	 * @return the title fade in
	 */
	public int getTitleFadeIn() {
		return titleFadeIn;
	}

	/**
	 * Gets the title fade out.
	 *
	 * @return the title fade out
	 */
	public int getTitleFadeOut() {
		return titleFadeOut;
	}

	/**
	 * Gets the title show time.
	 *
	 * @return the title show time
	 */
	public int getTitleShowTime() {
		return titleShowTime;
	}

	/**
	 * Gets the title sub title.
	 *
	 * @return the title sub title
	 */
	public String getTitleSubTitle() {
		return titleSubTitle;
	}

	/**
	 * Gets the title title.
	 *
	 * @return the title title
	 */
	public String getTitleTitle() {
		return titleTitle;
	}

	/**
	 * Gets the worlds.
	 *
	 * @return the worlds
	 */
	public ArrayList<String> getWorlds() {
		return worlds;
	}

	public void giveChoicesReward(User user, String choice) {
		RewardBuilder reward = new RewardBuilder(getConfig().getConfigData(),
				getConfig().getChoicesRewardsPath(choice));
		reward.withPrefix(getName());
		reward.withPlaceHolder("choice", choice);
		reward.send(user);
	}

	/**
	 * Give exp.
	 *
	 * @param user
	 *            the user
	 * @param exp
	 *            the exp
	 */
	public void giveExp(User user, int exp) {
		user.giveExp(exp);
	}

	/**
	 * Give items.
	 *
	 * @param user
	 *            the user
	 * @param placeholders
	 *            placeholders
	 */
	public void giveItems(User user, HashMap<String, String> placeholders) {
		itemsAndAmountsGiven = new HashMap<String, Integer>();
		for (String item : getItems()) {
			user.giveItem(getItemStack(user, item), placeholders);
		}
	}

	public void giveLucky(User user, HashMap<String, String> placeholders) {
		HashMap<String, Integer> map = new LinkedHashMap<String, Integer>();
		for (Entry<Integer, String> entry : luckyRewards.entrySet()) {
			if (MiscUtils.getInstance().checkChance(1, entry.getKey())) {
				map.put(entry.getValue(), entry.getKey());
			}
		}

		map = ArrayUtils.getInstance().sortByValuesStr(map, false);
		if (map.size() > 0) {
			if (isOnlyOneLucky()) {
				for (Entry<String, Integer> entry : map.entrySet()) {
					new RewardBuilder(getConfig().getConfigData(), entry.getKey()).withPlaceHolder(placeholders)
							.send(user);
					return;
				}
			} else {
				for (Entry<String, Integer> entry : map.entrySet()) {
					new RewardBuilder(getConfig().getConfigData(), entry.getKey()).withPlaceHolder(placeholders)
							.send(user);
				}
			}
		}
	}

	/**
	 * Give money.
	 *
	 * @param user
	 *            the user
	 * @param money
	 *            the money
	 */
	public void giveMoney(User user, int money) {
		user.giveMoney(money);
	}

	/**
	 * Give potions.
	 *
	 * @param user
	 *            the user
	 */
	public void givePotions(User user) {
		for (String potionName : getPotions()) {
			user.givePotionEffect(potionName, getPotionsDuration().get(potionName),
					getPotionsAmplifier().get(potionName));
		}
	}

	public void givePriorityReward(User user, final HashMap<String, String> placeholders) {
		for (String str : getPriority()) {
			Reward reward = RewardHandler.getInstance().getReward(str);
			if (reward.canGiveReward(user)) {
				new RewardBuilder(reward).withPlaceHolder(placeholders).setIgnoreChance(true).send(user);
				return;
			}
		}
	}

	/**
	 * Give random.
	 *
	 * @param user
	 *            the user
	 * @param online
	 *            the online
	 * @param placeholders
	 *            placeholders
	 */
	public void giveRandom(User user, boolean online, HashMap<String, String> placeholders) {
		if (checkRandomChance()) {
			if (isRandomPickRandom()) {
				ArrayList<String> rewards = getConfig().getRandomRewards();
				if (rewards != null) {
					if (rewards.size() > 0) {
						String reward = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
						if (!reward.equals("")) {
							RewardHandler.getInstance().giveReward(user, reward,
									new RewardOptions().setOnline(online).setPlaceholders(placeholders));
						}
					}
				}
			} else {
				new RewardBuilder(getConfig().getConfigData(), getConfig().getRandomRewardsPath()).withPrefix(name)
						.withPlaceHolder(placeholders).send(user);
			}
		} else {
			new RewardBuilder(getConfig().getConfigData(), getConfig().getRandomFallBackRewardsPath()).withPrefix(name)
					.withPlaceHolder(placeholders).send(user);
		}
	}

	public void giveReward(User user, RewardOptions rewardOptions) {
		if (!AdvancedCoreHook.getInstance().getOptions().isProcessRewards()) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Processing rewards is disabled");
			return;
		}

		if (rewardOptions == null) {
			rewardOptions = new RewardOptions();
		}

		PlayerRewardEvent event = new PlayerRewardEvent(this, user, rewardOptions);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			plugin.debug("Reward " + name + " was cancelled for " + user.getPlayerName());
			return;
		}

		if (rewardOptions.isCheckTimed()) {
			if (checkDelayed(user)) {
				return;
			}

			if (checkTimed(user)) {
				return;
			}
		}

		boolean checkServer = checkServer();
		boolean checkWorld = checkWorld(user);

		if (((!rewardOptions.isOnline() && !user.isOnline()) || !checkWorld || !checkServer) && !isForceOffline()) {
			if (rewardOptions.isGiveOffline()) {
				checkRewardFile();
				user.addOfflineRewards(this, rewardOptions.getPlaceholders());
				if (!checkWorld) {
					user.setCheckWorld(true);
				}
			}
			return;
		}

		giveRewardReward(user, rewardOptions);
	}

	public void giveRewardReward(User user, RewardOptions rewardOptions) {
		plugin.debug("Attempting to give " + user.getPlayerName() + " reward " + name);

		String type = getRewardType();
		if (rewardOptions.isOnline()) {
			if (type.equalsIgnoreCase("offline")) {
				plugin.debug("Reward Type Don't match");
				return;
			}
		} else {
			if (type.equalsIgnoreCase("online")) {
				plugin.debug("Reward Type Don't match");
				return;
			}
		}

		if (!hasPermission(user)) {
			plugin.debug(
					user.getPlayerName() + " does not have permission " + getPermission() + " to get reward " + name);
			return;
		}

		if (rewardOptions.isIgnoreChance() || checkChance()) {
			giveRewardUser(user, rewardOptions.getPlaceholders());
		}
	}

	private void giveRewardsRewards(User user, HashMap<String, String> placeholders) {
		new RewardBuilder(getConfig().getConfigData(), "Rewards").withPrefix(name).withPlaceHolder(placeholders)
				.send(user);
	}

	/**
	 * Give reward user.
	 *
	 * @param user
	 *            the user
	 * @param phs
	 *            placeholders
	 */
	public void giveRewardUser(User user, HashMap<String, String> phs) {
		Player player = user.getPlayer();
		if (player != null || isForceOffline()) {

			// placeholders
			if (phs == null) {
				phs = new HashMap<String, String>();
			}
			final String playerName = user.getPlayerName();
			phs.put("player", playerName);
			LocalDateTime ldt = LocalDateTime.now();
			Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
			phs.put("CurrentDate", "" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
			int exp = getExpToGive();
			int money = getMoneyToGive();
			phs.put("money", "" + money);
			phs.put("exp", "" + exp);
			phs.put("uuid", user.getUUID());

			final HashMap<String, String> placeholders = new HashMap<String, String>(phs);

			givePriorityReward(user, placeholders);
			giveRandom(user, true, placeholders);
			runJavascript(user, true, placeholders);
			giveMoney(user, money);
			giveItems(user, placeholders);
			giveExp(user, exp);
			runCommands(user, placeholders);
			givePotions(user);
			sendTitle(user, placeholders);
			sendActionBar(user, placeholders);
			playSound(user);
			playEffect(user);
			sendBossBar(user, placeholders);
			sendMessage(user, money, exp, placeholders);
			checkChoiceRewards(user);
			sendFirework(user);
			giveLucky(user, placeholders);

			giveRewardsRewards(user, placeholders);

			giveInjectedRewards(user, placeholders);

			plugin.debug("Gave " + user.getPlayerName() + " reward " + name);
		}
	}

	public void giveInjectedRewards(User user, HashMap<String, String> placeholders) {
		for (RewardInject inject : RewardHandler.getInstance().getInjectedRewards()) {
			inject.onRewardRequest(user, getConfig().getConfigData(), placeholders);
		}
	}

	/**
	 * Checks for permission.
	 *
	 * @param user
	 *            the user
	 * @return true, if successful
	 */
	public boolean hasPermission(User user) {
		if (!isRequirePermission()) {
			return true;
		}
		return PlayerUtils.getInstance().hasServerPermission(user.getPlayerName(), permission);
	}

	/**
	 * Checks if is boss bar enabled.
	 *
	 * @return true, if is boss bar enabled
	 */
	public boolean isBossBarEnabled() {
		return bossBarEnabled;
	}

	/**
	 * Checks if is delay enabled.
	 *
	 * @return true, if is delay enabled
	 */
	public boolean isDelayEnabled() {
		return delayEnabled;
	}

	/**
	 * Checks if is effect enabled.
	 *
	 * @return true, if is effect enabled
	 */
	public boolean isEffectEnabled() {
		return effectEnabled;
	}

	/**
	 * @return the enableChoices
	 */
	public boolean isEnableChoices() {
		return enableChoices;
	}

	/**
	 * Checks if is firework enabled.
	 *
	 * @return true, if is firework enabled
	 */
	public boolean isFireworkEnabled() {
		return fireworkEnabled;
	}

	/**
	 * Checks if is firework flicker.
	 *
	 * @return true, if is firework flicker
	 */
	public boolean isFireworkFlicker() {
		return fireworkFlicker;
	}

	/**
	 * Checks if is firework trail.
	 *
	 * @return true, if is firework trail
	 */
	public boolean isFireworkTrail() {
		return fireworkTrail;
	}

	/**
	 * @return the forceOffline
	 */
	public boolean isForceOffline() {
		return forceOffline;
	}

	/**
	 * Checks if is javascript enabled.
	 *
	 * @return true, if is javascript enabled
	 */
	public boolean isJavascriptEnabled() {
		return javascriptEnabled;
	}

	/**
	 * @return the onlyOneLucky
	 */
	public boolean isOnlyOneLucky() {
		return onlyOneLucky;
	}

	/**
	 * @return the randomPickRandom
	 */
	public boolean isRandomPickRandom() {
		return randomPickRandom;
	}

	/**
	 * Checks if is require permission.
	 *
	 * @return true, if is require permission
	 */
	public boolean isRequirePermission() {
		return requirePermission;
	}

	/**
	 * Checks if is sound enabled.
	 *
	 * @return true, if is sound enabled
	 */
	public boolean isSoundEnabled() {
		return soundEnabled;
	}

	/**
	 * Checks if is timed enabled.
	 *
	 * @return true, if is timed enabled
	 */
	public boolean isTimedEnabled() {
		return timedEnabled;
	}

	/**
	 * Checks if is title enabled.
	 *
	 * @return true, if is title enabled
	 */
	public boolean isTitleEnabled() {
		return titleEnabled;
	}

	/**
	 * Checks if is uses worlds.
	 *
	 * @return true, if is uses worlds
	 */
	public boolean isUsesWorlds() {
		return usesWorlds;
	}

	/**
	 * Load.
	 *
	 * @param folder
	 *            the folder
	 * @param reward
	 *            the reward
	 */
	public void load(File folder, String reward) {
		name = reward;
		if (folder.isDirectory()) {
			file = new File(folder, reward + ".yml");
		} else {
			file = folder;
		}
		fileData = new RewardFileData(this, folder);
		loadValues();
	}

	public void load(String name, ConfigurationSection section) {
		this.name = name;
		fileData = new RewardFileData(section);
		loadValues();
	}

	public void loadValues() {
		setRewardType(getConfig().getRewardType());

		forceOffline = getConfig().getForceOffline();

		setDelayEnabled(getConfig().getDelayedEnabled());
		if (delayEnabled) {
			setDelayHours(getConfig().getDelayedHours());
			setDelayMinutes(getConfig().getDelayedMinutes());
			setDelaySeconds(getConfig().getDelayedSeconds());
		}

		setTimedEnabled(getConfig().getTimedEnabled());
		if (timedEnabled) {
			setTimedHour(getConfig().getTimedHour());
			setTimedMinute(getConfig().getTimedMinute());
		}

		javascripts = getConfig().getJavascripts();

		setChance(getConfig().getChance());
		setRandomChance(getConfig().getRandomChance());
		randomPickRandom = getConfig().getRandomPickRandom();

		setRequirePermission(getConfig().getRequirePermission());
		setWorlds(getConfig().getWorlds());

		setItems(getConfig().getItems());

		setMoney(getConfig().getMoney());
		setMinMoney(getConfig().getMinMoney());
		setMaxMoney(getConfig().getMaxMoney());

		setExp(getConfig().getEXP());
		setMinExp(getConfig().getMinExp());
		setMaxExp(getConfig().getMaxExp());

		setConsoleCommands(getConfig().getCommandsConsole());
		setPlayerCommands(getConfig().getCommandsPlayer());

		potions = getConfig().getPotions();
		potionsDuration = new HashMap<String, Integer>();
		potionsAmplifier = new HashMap<String, Integer>();
		for (String potion : potions) {
			potionsDuration.put(potion, getConfig().getPotionsDuration(potion));
			potionsAmplifier.put(potion, getConfig().getPotionsAmplifier(potion));
		}

		setRewardMsg(getConfig().getMessagesPlayer());
		setActionBarMsg(getConfig().getActionBarMessage());
		setActionBarDelay(getConfig().getActionBarDelay());

		setBossBarEnabled(getConfig().getBossBarEnabled());
		if (bossBarEnabled) {
			setBossBarMessage(getConfig().getBossBarMessage());
			setBossBarColor(getConfig().getBossBarColor());
			setBossBarStyle(getConfig().getBossBarStyle());
			setBossBarProgress(getConfig().getBossBarProgress());
			setBossBarDelay(getConfig().getBossBarDelay());
		}

		broadcastMsg = getConfig().getMessagesBroadcast();

		permission = getConfig().getPermission();

		setJavascriptEnabled(getConfig().getJavascriptEnabled());
		setJavascriptExpression(getConfig().getJavascriptExpression());

		enableChoices = getConfig().getEnableChoices();
		if (enableChoices) {
			choices = getConfig().getChoices();
		}

		fireworkEnabled = getConfig().getFireworkEnabled();
		if (fireworkEnabled) {
			fireworkColors = getConfig().getFireworkColors();
			fireworkFadeOutColors = getConfig().getFireworkColorsFadeOut();
			fireworkPower = getConfig().getFireworkPower();
			fireworkTypes = getConfig().getFireworkTypes();
			fireworkTrail = getConfig().getFireworkTrail();
			fireworkFlicker = getConfig().getFireworkFlicker();
		}

		if (getWorlds().size() == 0) {
			usesWorlds = false;
		} else {
			usesWorlds = true;
		}

		titleEnabled = getConfig().getTitleEnabled();
		if (titleEnabled) {
			titleTitle = getConfig().getTitleTitle();
			titleSubTitle = getConfig().getTitleSubTitle();
			titleFadeIn = getConfig().getTitleFadeIn();
			titleShowTime = getConfig().getTitleShowTime();
			titleFadeOut = getConfig().getTitleFadeOut();
		}

		soundEnabled = getConfig().getSoundEnabled();
		if (soundEnabled) {
			soundSound = getConfig().getSoundSound();
			soundPitch = getConfig().getSoundPitch();
			soundVolume = getConfig().getSoundVolume();
		}

		effectEnabled = getConfig().getEffectEnabled();
		if (effectEnabled) {
			effectEffect = getConfig().getEffectEffect();
			effectData = getConfig().getEffectData();
			effectParticles = getConfig().getEffectParticles();
			effectRadius = getConfig().getEffectRadius();
		}

		priority = getConfig().getPriority();

		luckyRewards = new HashMap<Integer, String>();

		for (String str : getConfig().getLuckyRewards()) {
			if (StringUtils.getInstance().isInt(str)) {
				int num = Integer.parseInt(str);
				if (num > 0) {
					String path = getConfig().getLuckyRewardsPath(num);
					luckyRewards.put(num, path);
				}
			}
		}

		onlyOneLucky = getConfig().getOnlyOneLucky();

		randomCommand = getConfig().getRandomCommand();

		server = getConfig().getServer();

		new AnnotationHandler().load(getConfig().getConfigData(), this);
	}

	/**
	 * Play effect.
	 *
	 * @param user
	 *            the user
	 */
	public void playEffect(User user) {
		if (effectEnabled) {
			user.playParticle(effectEffect, effectData, effectParticles, effectRadius);
		}
	}

	/**
	 * Play sound.
	 *
	 * @param user
	 *            the user
	 */
	public void playSound(User user) {
		if (soundEnabled) {
			try {
				user.playSound(soundSound, soundVolume, soundPitch);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Run commands.
	 *
	 * @param user
	 *            the user
	 * @param placeholders
	 *            placeholders
	 */
	public void runCommands(User user, HashMap<String, String> placeholders) {
		MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), getConsoleCommands(), placeholders);

		user.preformCommand(getPlayerCommands(), placeholders);

		if (randomCommand.size() > 0) {
			MiscUtils.getInstance().executeConsoleCommands(user.getPlayer(),
					randomCommand.get(ThreadLocalRandom.current().nextInt(randomCommand.size())), placeholders);
		}
	}

	/**
	 * Run javascript.
	 *
	 * @param user
	 *            the user
	 * @param online
	 *            the online
	 * @param placeholders
	 *            placeholders
	 */
	public void runJavascript(User user, boolean online, HashMap<String, String> placeholders) {
		if (isJavascriptEnabled()) {
			if (new JavascriptEngine().addPlayer(user.getPlayer()).getBooleanValue(
					StringUtils.getInstance().replacePlaceHolder(getJavascriptExpression(), placeholders))) {
				new RewardBuilder(getConfig().getConfigData(), getConfig().getJavascriptTrueRewardsPath())
						.withPrefix(name).send(user);
			} else {
				new RewardBuilder(getConfig().getConfigData(), getConfig().getJavascriptFalseRewardsPath())
						.withPrefix(name).send(user);
			}
		}

		if (!getJavascripts().isEmpty()) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getPlayer());
			for (String str : getJavascripts()) {
				engine.execute(StringUtils.getInstance().replacePlaceHolder(str, placeholders));
			}
		}
	}

	/**
	 * Send action bar.
	 *
	 * @param user
	 *            the user
	 * @param placeholders
	 *            placeholders
	 */
	public void sendActionBar(User user, HashMap<String, String> placeholders) {
		user.sendActionBar(StringUtils.getInstance().replacePlaceHolder(getActionBarMsg(), placeholders),
				getActionBarDelay());
	}

	/**
	 * Send boss bar.
	 *
	 * @param user
	 *            the user
	 * @param placeholders
	 *            placeholders
	 */
	public void sendBossBar(User user, HashMap<String, String> placeholders) {
		if (isBossBarEnabled()) {
			user.sendBossBar(StringUtils.getInstance().replacePlaceHolder(getBossBarMessage(), placeholders),
					getBossBarColor(), getBossBarStyle(), getBossBarProgress(), getBossBarDelay());
		}
	}

	/**
	 * Send firework.
	 *
	 * @param user
	 *            the user
	 */
	public void sendFirework(User user) {
		if (isFireworkEnabled()) {
			FireworkHandler.getInstance().launchFirework(user.getPlayer().getLocation(), getFireworkPower(),
					getFireworkColors(), getFireworkFadeOutColors(), isFireworkTrail(), isFireworkFlicker(),
					getFireworkTypes());
		}
	}

	/**
	 * Send message.
	 *
	 * @param user
	 *            the user
	 * @param money
	 *            the money
	 * @param exp
	 *            the exp
	 * @param placeholders
	 *            placeholders
	 */
	public void sendMessage(User user, int money, int exp, HashMap<String, String> placeholders) {
		ArrayList<String> itemsAndAmounts = new ArrayList<String>();
		for (Entry<String, Integer> entry : itemsAndAmountsGiven.entrySet()) {
			itemsAndAmounts.add(entry.getValue() + " " + entry.getKey());
		}
		String itemsAndAmountsMsg = ArrayUtils.getInstance().makeStringList(itemsAndAmounts);

		String broadcastMsg = StringUtils.getInstance().replacePlaceHolder(this.broadcastMsg, placeholders);
		broadcastMsg = StringUtils.getInstance().replacePlaceHolder(broadcastMsg, "player", user.getPlayerName());

		broadcastMsg = StringUtils.getInstance().replacePlaceHolder(broadcastMsg, "money", "" + money);
		broadcastMsg = StringUtils.getInstance().replacePlaceHolder(broadcastMsg, "exp", "" + exp);
		broadcastMsg = StringUtils.getInstance().replacePlaceHolder(broadcastMsg, "itemsandamount", itemsAndAmountsMsg);
		broadcastMsg = StringUtils.getInstance().replacePlaceHolder(broadcastMsg, "items",
				ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(getItems())));

		MiscUtils.getInstance()
				.broadcast(StringUtils.getInstance().replacePlaceHolders(user.getPlayer(), broadcastMsg));

		String msg = StringUtils.getInstance().replacePlaceHolder(rewardMsg, "player", user.getPlayerName());

		msg = StringUtils.getInstance().replacePlaceHolder(msg, "money", "" + money);
		msg = StringUtils.getInstance().replacePlaceHolder(msg, "exp", "" + exp);
		msg = StringUtils.getInstance().replacePlaceHolder(msg, "itemsandamount", itemsAndAmountsMsg);
		msg = StringUtils.getInstance().replacePlaceHolder(msg, "items",
				ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(getItems())));

		user.sendMessage(msg, placeholders);
	}

	/**
	 * Send title.
	 *
	 * @param user
	 *            the user
	 * @param placeholders
	 *            placeholders
	 */
	public void sendTitle(User user, HashMap<String, String> placeholders) {
		if (titleEnabled) {
			user.sendTitle(StringUtils.getInstance().replacePlaceHolder(titleTitle, placeholders),

					StringUtils.getInstance().replacePlaceHolder(titleSubTitle, placeholders),

					titleFadeIn, titleShowTime, titleFadeOut);
		}
	}

	/**
	 * Sets the action bar delay.
	 *
	 * @param actionBarDelay
	 *            the new action bar delay
	 */
	public void setActionBarDelay(int actionBarDelay) {
		this.actionBarDelay = actionBarDelay;
	}

	/**
	 * Sets the action bar msg.
	 *
	 * @param actionBarMsg
	 *            the new action bar msg
	 */
	public void setActionBarMsg(String actionBarMsg) {
		this.actionBarMsg = actionBarMsg;
	}

	/**
	 * Sets the boss bar color.
	 *
	 * @param bossBarColor
	 *            the new boss bar color
	 */
	public void setBossBarColor(String bossBarColor) {
		this.bossBarColor = bossBarColor;
	}

	/**
	 * Sets the boss bar delay.
	 *
	 * @param bossBarDelay
	 *            the new boss bar delay
	 */
	public void setBossBarDelay(int bossBarDelay) {
		this.bossBarDelay = bossBarDelay;
	}

	/**
	 * Sets the boss bar enabled.
	 *
	 * @param bossBarEnabled
	 *            the new boss bar enabled
	 */
	public void setBossBarEnabled(boolean bossBarEnabled) {
		this.bossBarEnabled = bossBarEnabled;
	}

	/**
	 * Sets the boss bar message.
	 *
	 * @param bossBarMessage
	 *            the new boss bar message
	 */
	public void setBossBarMessage(String bossBarMessage) {
		this.bossBarMessage = bossBarMessage;
	}

	/**
	 * Sets the boss bar progress.
	 *
	 * @param bossBarProgress
	 *            the new boss bar progress
	 */
	public void setBossBarProgress(double bossBarProgress) {
		this.bossBarProgress = bossBarProgress;
	}

	/**
	 * Sets the boss bar style.
	 *
	 * @param bossBarStyle
	 *            the new boss bar style
	 */
	public void setBossBarStyle(String bossBarStyle) {
		this.bossBarStyle = bossBarStyle;
	}

	/**
	 * Sets the broadcast msg.
	 *
	 * @param broadcastMsg
	 *            the new broadcast msg
	 */
	public void setBroadcastMsg(String broadcastMsg) {
		this.broadcastMsg = broadcastMsg;
	}

	/**
	 * Sets the chance.
	 *
	 * @param chance
	 *            the new chance
	 */
	public void setChance(double chance) {
		this.chance = chance;
	}

	/**
	 * Sets the console commands.
	 *
	 * @param consoleCommands
	 *            the new console commands
	 */
	public void setConsoleCommands(ArrayList<String> consoleCommands) {
		this.consoleCommands = consoleCommands;
	}

	/**
	 * Sets the delay enabled.
	 *
	 * @param delayEnabled
	 *            the new delay enabled
	 */
	public void setDelayEnabled(boolean delayEnabled) {
		this.delayEnabled = delayEnabled;
	}

	/**
	 * Sets the delay hours.
	 *
	 * @param delayHours
	 *            the new delay hours
	 */
	public void setDelayHours(int delayHours) {
		this.delayHours = delayHours;
	}

	/**
	 * Sets the delay minutes.
	 *
	 * @param delayMinutes
	 *            the new delay minutes
	 */
	public void setDelayMinutes(int delayMinutes) {
		this.delayMinutes = delayMinutes;
	}

	/**
	 * @param delaySeconds
	 *            the delaySeconds to set
	 */
	public void setDelaySeconds(int delaySeconds) {
		this.delaySeconds = delaySeconds;
	}

	/**
	 * Sets the effect data.
	 *
	 * @param effectData
	 *            the new effect data
	 */
	public void setEffectData(int effectData) {
		this.effectData = effectData;
	}

	/**
	 * Sets the effect effect.
	 *
	 * @param effectEffect
	 *            the new effect effect
	 */
	public void setEffectEffect(String effectEffect) {
		this.effectEffect = effectEffect;
	}

	/**
	 * Sets the effect enabled.
	 *
	 * @param effectEnabled
	 *            the new effect enabled
	 */
	public void setEffectEnabled(boolean effectEnabled) {
		this.effectEnabled = effectEnabled;
	}

	/**
	 * Sets the effect particles.
	 *
	 * @param effectParticles
	 *            the new effect particles
	 */
	public void setEffectParticles(int effectParticles) {
		this.effectParticles = effectParticles;
	}

	/**
	 * Sets the effect radius.
	 *
	 * @param effectRadius
	 *            the new effect radius
	 */
	public void setEffectRadius(int effectRadius) {
		this.effectRadius = effectRadius;
	}

	/**
	 * Sets the exp.
	 *
	 * @param exp
	 *            the new exp
	 */
	public void setExp(int exp) {
		this.exp = exp;
	}

	/**
	 * Sets the firework colors.
	 *
	 * @param fireworkColors
	 *            the new firework colors
	 */
	public void setFireworkColors(ArrayList<String> fireworkColors) {
		this.fireworkColors = fireworkColors;
	}

	/**
	 * Sets the firework enabled.
	 *
	 * @param fireworkEnabled
	 *            the new firework enabled
	 */
	public void setFireworkEnabled(boolean fireworkEnabled) {
		this.fireworkEnabled = fireworkEnabled;
	}

	/**
	 * Sets the firework fade out colors.
	 *
	 * @param fireworkFadeOutColors
	 *            the new firework fade out colors
	 */
	public void setFireworkFadeOutColors(ArrayList<String> fireworkFadeOutColors) {
		this.fireworkFadeOutColors = fireworkFadeOutColors;
	}

	/**
	 * Sets the firework flicker.
	 *
	 * @param fireworkFlicker
	 *            the new firework flicker
	 */
	public void setFireworkFlicker(boolean fireworkFlicker) {
		this.fireworkFlicker = fireworkFlicker;
	}

	/**
	 * Sets the firework power.
	 *
	 * @param fireworkPower
	 *            the new firework power
	 */
	public void setFireworkPower(int fireworkPower) {
		this.fireworkPower = fireworkPower;
	}

	/**
	 * Sets the firework trail.
	 *
	 * @param fireworkTrail
	 *            the new firework trail
	 */
	public void setFireworkTrail(boolean fireworkTrail) {
		this.fireworkTrail = fireworkTrail;
	}

	/**
	 * Sets the firework types.
	 *
	 * @param fireworkTypes
	 *            the new firework types
	 */
	public void setFireworkTypes(ArrayList<String> fireworkTypes) {
		this.fireworkTypes = fireworkTypes;
	}

	/**
	 * @param forceOffline
	 *            the forceOffline to set
	 */
	public void setForceOffline(boolean forceOffline) {
		this.forceOffline = forceOffline;
	}

	/**
	 * Sets the items.
	 *
	 * @param items
	 *            the new items
	 */
	public void setItems(Set<String> items) {
		this.items = items;
	}

	/**
	 * Sets the items and amounts given.
	 *
	 * @param itemsAndAmountsGiven
	 *            the items and amounts given
	 */
	public void setItemsAndAmountsGiven(HashMap<String, Integer> itemsAndAmountsGiven) {
		this.itemsAndAmountsGiven = itemsAndAmountsGiven;
	}

	/**
	 * Sets the javascript enabled.
	 *
	 * @param javascriptEnabled
	 *            the new javascript enabled
	 */
	public void setJavascriptEnabled(boolean javascriptEnabled) {
		this.javascriptEnabled = javascriptEnabled;
	}

	/**
	 * Sets the javascript expression.
	 *
	 * @param javascriptExpression
	 *            the new javascript expression
	 */
	public void setJavascriptExpression(String javascriptExpression) {
		this.javascriptExpression = javascriptExpression;
	}

	/**
	 * @param javascripts
	 *            the javascripts to set
	 */
	public void setJavascripts(ArrayList<String> javascripts) {
		this.javascripts = javascripts;
	}

	/**
	 * Sets the max exp.
	 *
	 * @param maxExp
	 *            the new max exp
	 */
	public void setMaxExp(int maxExp) {
		this.maxExp = maxExp;
	}

	/**
	 * Sets the max money.
	 *
	 * @param maxMoney
	 *            the new max money
	 */
	public void setMaxMoney(int maxMoney) {
		MaxMoney = maxMoney;
	}

	/**
	 * Sets the min exp.
	 *
	 * @param minExp
	 *            the new min exp
	 */
	public void setMinExp(int minExp) {
		this.minExp = minExp;
	}

	/**
	 * Sets the min money.
	 *
	 * @param minMoney
	 *            the new min money
	 */
	public void setMinMoney(int minMoney) {
		MinMoney = minMoney;
	}

	/**
	 * Sets the money.
	 *
	 * @param money
	 *            the new money
	 */
	public void setMoney(int money) {
		this.money = money;
	}

	/**
	 * Sets the permission.
	 *
	 * @param permission
	 *            the new permission
	 */
	public void setPermission(String permission) {
		this.permission = permission;
	}

	/**
	 * Sets the player commands.
	 *
	 * @param playerCommands
	 *            the new player commands
	 */
	public void setPlayerCommands(ArrayList<String> playerCommands) {
		this.playerCommands = playerCommands;
	}

	/**
	 * Sets the potions.
	 *
	 * @param potions
	 *            the new potions
	 */
	public void setPotions(Set<String> potions) {
		this.potions = potions;
	}

	/**
	 * Sets the potions amplifier.
	 *
	 * @param potionsAmplifier
	 *            the potions amplifier
	 */
	public void setPotionsAmplifier(HashMap<String, Integer> potionsAmplifier) {
		this.potionsAmplifier = potionsAmplifier;
	}

	/**
	 * Sets the potions duration.
	 *
	 * @param potionsDuration
	 *            the potions duration
	 */
	public void setPotionsDuration(HashMap<String, Integer> potionsDuration) {
		this.potionsDuration = potionsDuration;
	}

	/**
	 * Sets the random chance.
	 *
	 * @param randomChance
	 *            the new random chance
	 */
	public void setRandomChance(double randomChance) {
		this.randomChance = randomChance;
	}

	/**
	 * @param randomPickRandom
	 *            the randomPickRandom to set
	 */
	public void setRandomPickRandom(boolean randomPickRandom) {
		this.randomPickRandom = randomPickRandom;
	}

	/**
	 * Sets the require permission.
	 *
	 * @param requirePermission
	 *            the new require permission
	 */
	public void setRequirePermission(boolean requirePermission) {
		this.requirePermission = requirePermission;
	}

	/**
	 * Sets the reward msg.
	 *
	 * @param rewardMsg
	 *            the new reward msg
	 */
	public void setRewardMsg(String rewardMsg) {
		this.rewardMsg = rewardMsg;
	}

	/**
	 * Sets the reward type.
	 *
	 * @param rewardType
	 *            the new reward type
	 */
	public void setRewardType(String rewardType) {
		this.rewardType = rewardType;
	}

	/**
	 * Sets the sound enabled.
	 *
	 * @param soundEnabled
	 *            the new sound enabled
	 */
	public void setSoundEnabled(boolean soundEnabled) {
		this.soundEnabled = soundEnabled;
	}

	/**
	 * Sets the sound pitch.
	 *
	 * @param soundPitch
	 *            the new sound pitch
	 */
	public void setSoundPitch(float soundPitch) {
		this.soundPitch = soundPitch;
	}

	/**
	 * Sets the sound sound.
	 *
	 * @param soundSound
	 *            the new sound sound
	 */
	public void setSoundSound(String soundSound) {
		this.soundSound = soundSound;
	}

	/**
	 * Sets the sound volume.
	 *
	 * @param soundVolume
	 *            the new sound volume
	 */
	public void setSoundVolume(float soundVolume) {
		this.soundVolume = soundVolume;
	}

	/**
	 * Sets the timed enabled.
	 *
	 * @param timedEnabled
	 *            the new timed enabled
	 */
	public void setTimedEnabled(boolean timedEnabled) {
		this.timedEnabled = timedEnabled;
	}

	/**
	 * Sets the timed hour.
	 *
	 * @param timedHour
	 *            the new timed hour
	 */
	public void setTimedHour(int timedHour) {
		this.timedHour = timedHour;
	}

	/**
	 * Sets the timed minute.
	 *
	 * @param timedMinute
	 *            the new timed minute
	 */
	public void setTimedMinute(int timedMinute) {
		this.timedMinute = timedMinute;
	}

	/**
	 * Sets the title enabled.
	 *
	 * @param titleEnabled
	 *            the new title enabled
	 */
	public void setTitleEnabled(boolean titleEnabled) {
		this.titleEnabled = titleEnabled;
	}

	/**
	 * Sets the title fade in.
	 *
	 * @param titleFadeIn
	 *            the new title fade in
	 */
	public void setTitleFadeIn(int titleFadeIn) {
		this.titleFadeIn = titleFadeIn;
	}

	/**
	 * Sets the title fade out.
	 *
	 * @param titleFadeOut
	 *            the new title fade out
	 */
	public void setTitleFadeOut(int titleFadeOut) {
		this.titleFadeOut = titleFadeOut;
	}

	/**
	 * Sets the title show time.
	 *
	 * @param titleShowTime
	 *            the new title show time
	 */
	public void setTitleShowTime(int titleShowTime) {
		this.titleShowTime = titleShowTime;
	}

	/**
	 * Sets the title sub title.
	 *
	 * @param titleSubTitle
	 *            the new title sub title
	 */
	public void setTitleSubTitle(String titleSubTitle) {
		this.titleSubTitle = titleSubTitle;
	}

	/**
	 * Sets the title title.
	 *
	 * @param titleTitle
	 *            the new title title
	 */
	public void setTitleTitle(String titleTitle) {
		this.titleTitle = titleTitle;
	}

	/**
	 * @param usesWorlds
	 *            the usesWorlds to set
	 */
	public void setUsesWorlds(boolean usesWorlds) {
		this.usesWorlds = usesWorlds;
	}

	/**
	 * Sets the worlds.
	 *
	 * @param worlds
	 *            the new worlds
	 */
	public void setWorlds(ArrayList<String> worlds) {
		this.worlds = worlds;
	}

}
