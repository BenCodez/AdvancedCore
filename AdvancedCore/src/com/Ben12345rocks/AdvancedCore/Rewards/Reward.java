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

import lombok.Getter;
import lombok.Setter;

/**
 * The Class Reward.
 */
public class Reward {

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private RewardFileData config;

	@Getter
	@Setter
	private String rewardType;

	@Getter
	@Setter
	private boolean forceOffline;

	@Getter
	@Setter
	private boolean delayEnabled;

	@Getter
	@Setter
	private int delayHours;

	@Getter
	@Setter
	private int delayMinutes;

	@Getter
	@Setter
	private int delaySeconds;

	@Getter
	@Setter
	private boolean timedEnabled;

	@Getter
	@Setter
	private int timedHour;

	@Getter
	@Setter
	private int timedMinute;

	@Getter
	@Setter
	private double chance;

	@Getter
	@Setter
	private double randomChance;

	@Getter
	@Setter
	private boolean requirePermission;

	@Getter
	@Setter
	private ArrayList<String> worlds;

	@Getter
	@Setter
	private Set<String> items;

	@Getter
	@Setter
	private int money;

	@Getter
	@Setter
	private int MinMoney;

	@Getter
	@Setter
	private int MaxMoney;

	@Getter
	@Setter
	private int exp;

	@Getter
	@Setter
	private int minExp;

	@Getter
	@Setter
	private int maxExp;

	@Getter
	@Setter
	private ArrayList<String> consoleCommands;

	@Getter
	@Setter
	private ArrayList<String> playerCommands;

	@Getter
	@Setter
	private String permission;

	@Getter
	@Setter
	private HashMap<String, Integer> itemsAndAmountsGiven;

	@Getter
	@Setter
	private boolean enableChoices;

	@Getter
	@Setter
	private Set<String> choices;

	@Getter
	@Setter
	private boolean usesWorlds;

	@Getter
	@Setter
	private HashMap<Integer, String> luckyRewards;

	@Getter
	@Setter
	private boolean onlyOneLucky;

	@Getter
	@Setter
	private String server;

	@Getter
	@Setter
	private File file;

	@Getter
	@Setter
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

	public boolean checkDelayed(User user, HashMap<String, String> placeholders) {
		if (!isDelayEnabled()) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		time = time.plus(getDelayHours(), ChronoUnit.HOURS);
		time = time.plus(getDelayMinutes(), ChronoUnit.MINUTES);
		time = time.plus(getDelaySeconds(), ChronoUnit.SECONDS);
		checkRewardFile();
		user.addTimedReward(this, placeholders, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

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

	public boolean checkTimed(User user, HashMap<String, String> placeholders) {
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
		user.addTimedReward(this, placeholders, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

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

	public ItemStack getItem() {
		return new ItemStack(Material.STONE);
	}

	public ItemStack getItemStack(User user, String item) {
		return new ItemBuilder(getConfig().getItemSection(item)).setSkullOwner(user.getOfflinePlayer())
				.toItemStack(user.getPlayer());
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
	 * Gets the reward name.
	 *
	 * @return the reward name
	 */
	public String getRewardName() {
		return name;
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

	public void giveInjectedRewards(User user, HashMap<String, String> placeholders) {
		for (RewardInject inject : RewardHandler.getInstance().getInjectedRewards()) {
			try {
				plugin.extraDebug(getRewardName() + ": Attempting to give " + inject.getPath());
				inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
			if (checkDelayed(user, rewardOptions.getPlaceholders())) {
				return;
			}

			if (checkTimed(user, rewardOptions.getPlaceholders())) {
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

			// give items ahead for placeholders
			giveItems(user, placeholders);

			// item placeholders
			ArrayList<String> itemsAndAmounts = new ArrayList<String>();
			for (Entry<String, Integer> entry : itemsAndAmountsGiven.entrySet()) {
				itemsAndAmounts.add(entry.getValue() + " " + entry.getKey());
			}
			String itemsAndAmountsMsg = ArrayUtils.getInstance().makeStringList(itemsAndAmounts);
			placeholders.put("itemsandamount", itemsAndAmountsMsg);
			placeholders.put("items",
					ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(getItems())));

			// non injectable rewards
			giveMoney(user, money);
			giveExp(user, exp);
			checkChoiceRewards(user);

			// possible future injectable rewards
			giveRandom(user, true, placeholders);
			runCommands(user, placeholders);
			giveLucky(user, placeholders);

			// injected rewards
			giveInjectedRewards(user, placeholders);

			// execute reward within reward
			giveRewardsRewards(user, placeholders);

			plugin.debug("Gave " + user.getPlayerName() + " reward " + name);
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
		config = new RewardFileData(this, folder);
		loadValues();
	}

	public void load(String name, ConfigurationSection section) {
		config = new RewardFileData(this, section);
		this.name = name;
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

}
