package com.Ben12345rocks.AdvancedCore.Rewards;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerRewardEvent;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInject;
import com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement.RequirementInject;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.Util.Annotation.AnnotationHandler;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

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
	private ArrayList<String> worlds;

	@Getter
	@Setter
	private Set<String> items;

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
	private String server;

	@Getter
	@Setter
	private File file;

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

	public boolean canGiveReward(User user, RewardOptions options) {
		if (checkRequirements(user, options)) {
			return true;
		}
		return false;
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

	public ItemStack getItem() {
		return new ItemStack(Material.STONE);
	}

	public ItemStack getItemStack(User user, String item) {
		return new ItemBuilder(getConfig().getItemSection(item)).setSkullOwner(user.getOfflinePlayer())
				.toItemStack(user.getPlayer());
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
			boolean Addplaceholder = inject.isAddAsPlaceholder();
			try {
				Object obj = null;
				plugin.extraDebug(
						getRewardName() + ": Attempting to give " + inject.getPath() + ":" + inject.getPriority());
				if (inject.isSynchronize()) {
					synchronized (inject.getObject()) {
						obj = inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
					}
				} else {
					obj = inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
				}
				if (Addplaceholder && obj != null) {
					String placeholderName = inject.getPlaceholderName();
					String value = "";
					if (obj instanceof Boolean) {
						Boolean b = (Boolean) obj;
						value = b.toString();
					} else if (obj instanceof String) {
						String b = (String) obj;
						value = b;
					} else if (obj instanceof Double) {
						Double b = (Double) obj;
						value = b.toString();
					} else if (obj instanceof Integer) {
						Integer b = (Integer) obj;
						value = b.toString();
					}
					plugin.extraDebug("Adding placeholder " + placeholderName + ":" + value);
					placeholders.put(placeholderName, value);
				}
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

		if (!rewardOptions.isOnlineSet()) {
			rewardOptions.setOnline(user.isOnline());
		}

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

		if (!checkRequirements(user, rewardOptions)) {
			return;
		}

		giveRewardUser(user, rewardOptions.getPlaceholders());

	}

	public boolean checkRequirements(User user, RewardOptions rewardOptions) {
		for (RequirementInject inject : RewardHandler.getInstance().getInjectedRequirements()) {
			try {
				if (!inject.onRequirementRequest(this, user, getConfig().getConfigData(), rewardOptions)) {
					return false;
				}
			} catch (Exception e) {
				plugin.debug("Failed to check requirement");
				e.printStackTrace();
				return false;
			}
		}
		return true;
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

			// non injectable rewards?
			checkChoiceRewards(user);

			giveInjectedRewards(user, placeholders);

			plugin.debug("Gave " + user.getPlayerName() + " reward " + name);
		}
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

		setWorlds(getConfig().getWorlds());

		setItems(getConfig().getItems());
		enableChoices = getConfig().getEnableChoices();
		if (enableChoices) {
			choices = getConfig().getChoices();
		}

		if (getWorlds().size() == 0) {
			usesWorlds = false;
		} else {
			usesWorlds = true;
		}

		server = getConfig().getServer();

		new AnnotationHandler().load(getConfig().getConfigData(), this);
	}

}
