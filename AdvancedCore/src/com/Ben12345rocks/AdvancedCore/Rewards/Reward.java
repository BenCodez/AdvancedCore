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
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
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

	/** The reward msg. */
	private String rewardMsg;

	/** The broadcast msg. */
	private String broadcastMsg;

	/** The permission. */
	private String permission;

	/** The items and amounts given. */
	private HashMap<String, Integer> itemsAndAmountsGiven;

	/** The choice rewards enabled. */
	private boolean enableChoices;

	private Set<String> choices;

	/** The uses worlds. */
	private boolean usesWorlds;

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

			giveRandom(user, true, placeholders);
			giveMoney(user, money);
			giveItems(user, placeholders);
			giveExp(user, exp);
			runCommands(user, placeholders);
			sendMessage(user, money, exp, placeholders);
			checkChoiceRewards(user);
			giveLucky(user, placeholders);

			giveRewardsRewards(user, placeholders);

			giveInjectedRewards(user, placeholders);

			plugin.debug("Gave " + user.getPlayerName() + " reward " + name);
		}
	}

	public void giveInjectedRewards(User user, HashMap<String, String> placeholders) {
		for (RewardInject inject : RewardHandler.getInstance().getInjectedRewards()) {
			inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
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
	 * Checks if is delay enabled.
	 *
	 * @return true, if is delay enabled
	 */
	public boolean isDelayEnabled() {
		return delayEnabled;
	}

	/**
	 * @return the enableChoices
	 */
	public boolean isEnableChoices() {
		return enableChoices;
	}

	/**
	 * @return the forceOffline
	 */
	public boolean isForceOffline() {
		return forceOffline;
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
	 * Checks if is timed enabled.
	 *
	 * @return true, if is timed enabled
	 */
	public boolean isTimedEnabled() {
		return timedEnabled;
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

		setRewardMsg(getConfig().getMessagesPlayer());

		broadcastMsg = getConfig().getMessagesBroadcast();

		permission = getConfig().getPermission();

		enableChoices = getConfig().getEnableChoices();
		if (enableChoices) {
			choices = getConfig().getChoices();
		}

		if (getWorlds().size() == 0) {
			usesWorlds = false;
		} else {
			usesWorlds = true;
		}

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
	 * Sets the exp.
	 *
	 * @param exp
	 *            the new exp
	 */
	public void setExp(int exp) {
		this.exp = exp;
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
