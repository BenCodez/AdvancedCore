package com.bencodez.advancedcore.api.user;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.misc.effects.ActionBar;
import com.bencodez.advancedcore.api.misc.effects.BossBar;
import com.bencodez.advancedcore.api.misc.effects.Title;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;

import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class User.
 */
public class AdvancedCoreUser {

	@Getter
	private boolean cacheData = true;

	private UserData data;

	private boolean loadName = true;

	/** The player name. */
	private String playerName;

	@Getter
	private AdvancedCorePlugin plugin = null;

	@Getter
	private boolean tempCache = false;

	/** The uuid. */
	private String uuid;

	@Getter
	private boolean waitForCache = true;

	public AdvancedCoreUser(AdvancedCorePlugin plugin, AdvancedCoreUser user) {
		this.waitForCache = user.waitForCache;
		this.cacheData = user.cacheData;
		this.tempCache = user.tempCache;
		this.data = user.getUserData();
		this.uuid = user.getUUID();
		this.playerName = user.getPlayerName();
		this.loadName = user.loadName;
		this.plugin = plugin;
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin the plugin
	 * @param player the player
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, Player player) {
		this.plugin = plugin;
		loadData();
		uuid = player.getUniqueId().toString();
		setPlayerName(player.getName());
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin     the plugin
	 * @param playerName the player name
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, String playerName) {
		this.plugin = plugin;
		loadData();
		uuid = PlayerUtils.getInstance().getUUID(playerName);
		setPlayerName(playerName);
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin the plugin
	 * @param uuid   the uuid
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		loadData();
		setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid, false));
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin   the plugin
	 * @param uuid     the uuid
	 * @param loadName the load name
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, boolean loadName) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		this.loadName = loadName;
		loadData();
		if (this.loadName) {
			setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid));
		}

	}

	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, boolean loadName, boolean loadData) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		this.loadName = loadName;
		if (loadData) {
			loadData();
		}
		if (this.loadName) {
			setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid));
		}

	}

	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, String playerName) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		loadData();
		setPlayerName(playerName);
	}

	public void addPermission(String permission) {
		plugin.getPermissionHandler().addPermission(getPlayer(), permission);
	}

	public void addPermission(String permission, long delay) {
		plugin.getPermissionHandler().addPermission(getPlayer(), permission, delay);
	}

	public void removePermission(String permission) {
		plugin.getPermissionHandler().removePermission(UUID.fromString(getUUID()), getPlayerName(), permission);
	}

	public void addOfflineRewards(Reward reward, HashMap<String, String> placeholders) {
		synchronized (plugin) {
			ArrayList<String> offlineRewards = getOfflineRewards();
			offlineRewards
					.add(reward.getRewardName() + "%placeholders%" + ArrayUtils.getInstance().makeString(placeholders));
			setOfflineRewards(offlineRewards);
		}
	}

	public synchronized void addTimedReward(Reward reward, HashMap<String, String> placeholders, long epochMilli) {
		HashMap<String, Long> timed = getTimedRewards();
		String rewardName = reward.getRewardName();
		rewardName += "%extime%" + System.currentTimeMillis();

		timed.put(rewardName + "%placeholders%" + ArrayUtils.getInstance().makeString(placeholders), epochMilli);
		setTimedRewards(timed);
		loadTimedDelayedTimer(epochMilli);
	}

	public void addUnClaimedChoiceReward(String name) {
		ArrayList<String> choices = getUnClaimedChoices();
		choices.add(name);
		setUnClaimedChoice(choices);
	}

	public void cache() {
		plugin.getUserManager().getDataManager().cacheUser(UUID.fromString(uuid));
	}

	public void cacheIfNeeded() {
		plugin.getUserManager().getDataManager().cacheUserIfNeeded(UUID.fromString(uuid));
	}

	public void cacheAsync() {
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				cache();
			}
		});
	}

	public void checkDelayedTimedRewards() {
		plugin.debug("Checking timed/delayed for " + getPlayerName());
		HashMap<String, Long> timed = getTimedRewards();
		HashMap<String, Long> newTimed = new HashMap<String, Long>();
		for (Entry<String, Long> entry : timed.entrySet()) {
			long time = entry.getValue();

			if (time != 0) {
				Date timeDate = new Date(time);
				if (new Date().after(timeDate)) {
					String[] data = entry.getKey().split("%placeholders%");
					String rewardName = data[0];
					rewardName = rewardName.split("%extime%")[0];
					String placeholders = "";
					if (data.length > 1) {
						placeholders = data[1];
					}
					new RewardBuilder(plugin.getRewardHandler().getReward(rewardName)).setCheckTimed(false)
							.withPlaceHolder(ArrayUtils.getInstance().fromString(placeholders))
							.withPlaceHolder("date",
									"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(new Date(time)))
							.send(this);
					plugin.debug("Giving timed/delayed reward " + rewardName + " for " + getPlayerName()
							+ " with placeholders " + ArrayUtils.getInstance().fromString(placeholders));
				} else {
					newTimed.put(entry.getKey(), time);
				}
			}

		}
		setTimedRewards(newTimed);
	}

	/**
	 * Check offline rewards.
	 */
	public void checkOfflineRewards() {
		if (!plugin.getOptions().isProcessRewards()) {
			plugin.debug("Processing rewards is disabled");
			return;
		}
		setCheckWorld(false);
		final ArrayList<String> copy = getOfflineRewards();
		setOfflineRewards(new ArrayList<String>());
		final AdvancedCoreUser user = this;
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {

				@Override
				public void run() {
					for (String str : copy) {
						if (str != null && !str.equals("null")) {
							String[] args = str.split("%placeholders%");
							String placeholders = "";
							if (args.length > 1) {
								placeholders = args[1];
							}
							plugin.getRewardHandler().giveReward(user, args[0],
									new RewardOptions().setOnline(false).setGiveOffline(false).setCheckTimed(false)
											.setPlaceholders(ArrayUtils.getInstance().fromString(placeholders)));
						}
					}
				}
			}, 5l);
		}
	}

	public void clearCache() {
		if (isCached()) {
			getCache().clearCache();
		}
	}

	public void clearTempCache() {
		getUserData().clearTempCache();
	}

	public void closeInv() {
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					Player player = getPlayer();
					if (player != null) {
						player.closeInventory();
					}
				}
			});
		}
	}

	public AdvancedCoreUser dontCache() {
		cacheData = false;
		return this;
	}

	public AdvancedCoreUser cacheData() {
		cacheData = true;
		return this;
	}

	public UserDataCache getCache() {
		return plugin.getUserManager().getDataManager().getCache(java.util.UUID.fromString(getUUID()));
	}

	public String getChoicePreference(String rewardName) {
		ArrayList<String> data = getChoicePreferenceData();

		for (String str : data) {
			String[] data1 = str.split(":");
			if (data1.length > 1) {
				if (data1[0].equals(rewardName)) {
					return data1[1];
				}
			}
		}
		return "";
	}

	public ArrayList<String> getChoicePreferenceData() {
		return getData().getStringList("ChoicePreference", cacheData, waitForCache);
	}

	public UserData getData() {
		if (data == null) {
			loadData();
		}
		return data;
	}

	public String getInputMethod() {
		return getUserData().getString("InputMethod", cacheData, waitForCache);
	}

	public long getLastOnline() {
		String d = getData().getString("LastOnline", cacheData, waitForCache);
		long time = 0;
		if (d != null && !d.equals("") && !d.equals("null")) {
			time = Long.valueOf(d);
		}
		if (time == 0) {
			time = getOfflinePlayer().getLastPlayed();
			if (time > 0) {
				setLastOnline(time);
			}
		}
		return time;
	}

	public int getNumberOfDaysSinceLogin() {
		long time = getLastOnline();
		if (time > 0) {
			LocalDateTime online = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
			LocalDateTime now = LocalDateTime.now();
			Duration dur = Duration.between(online, now);
			return (int) dur.toDays();
		}

		return -1;
	}

	public OfflinePlayer getOfflinePlayer() {
		if (uuid != null && !uuid.equals("")) {
			return Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
		}
		return null;
	}

	public ArrayList<String> getOfflineRewards() {
		return getUserData().getStringList(plugin.getUserManager().getOfflineRewardsPath(), cacheData, waitForCache);
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public Player getPlayer() {
		if (uuid != null && !uuid.isEmpty()) {
			return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		}
		return null;
	}

	public ItemStack getPlayerHead() {
		return PlayerUtils.getInstance().getPlayerSkull(getPlayerName(), false);
	}

	/**
	 * Gets the player name.
	 *
	 * @return the player name
	 */
	public String getPlayerName() {
		if (playerName == null) {
			if (isTempCache()) {
				return getUserData().getString("PlayerName", false, false);
			}
			return "";
		} else {
			return playerName;
		}
	}

	public int getRepeatAmount(Reward reward) {
		return getData().getInt("Repeat" + reward.getName(), cacheData, waitForCache);
	}

	public HashMap<String, Long> getTimedRewards() {
		ArrayList<String> timedReward = getUserData().getStringList("TimedRewards", cacheData, waitForCache);
		HashMap<String, Long> timedRewards = new HashMap<String, Long>();
		for (String str : timedReward) {
			if (str != null && !str.equals("null")) {
				String[] data = str.split("%ExecutionTime/%");
				plugin.extraDebug("TimedReward: " + str);
				if (data.length > 1) {
					String name = data[0];

					String timeStr = data[1];
					timedRewards.put(name, Long.valueOf(timeStr));
				}
			}
		}
		return timedRewards;
	}

	public ArrayList<String> getUnClaimedChoices() {
		return getData().getStringList("UnClaimedChoices", cacheData, waitForCache);
	}

	public UserData getUserData() {
		if (data == null) {
			loadData();
		}
		return data;
	}

	public InputMethod getUserInputMethod() {
		String inputMethod = getInputMethod();
		if (inputMethod == null) {
			return InputMethod.getMethod(plugin.getOptions().getDefaultRequestMethod());
		}
		return InputMethod.getMethod(inputMethod);

	}

	/**
	 * Gets the uuid.
	 *
	 * @return the uuid
	 */
	public String getUUID() {
		return uuid;
	}

	public UUID getJavaUUID() {
		return UUID.fromString(uuid);
	}

	/**
	 * Give exp.
	 *
	 * @param exp the exp
	 */
	public void giveExp(int exp) {
		Player player = getPlayer();
		if (player != null) {
			player.giveExp(exp);
		}
	}

	public void giveExpLevels(int num) {
		Player p = getPlayer();
		if (p != null) {
			p.setLevel(p.getLevel() + num);
		}
	}

	public void giveItem(ItemBuilder builder) {
		giveItem(builder.toItemStack(getPlayer()));
	}

	/**
	 * Give item.
	 *
	 * @param item the item
	 */
	public void giveItem(ItemStack item) {
		if (item == null) {
			return;
		}
		if (item.getAmount() == 0) {
			return;
		}

		final Player player = getPlayer();

		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					if (player != null) {
						plugin.getFullInventoryHandler().giveItem(player, item);
					}
				}
			});
		}

	}
	
	public void giveItems(ItemStack... item) {
		if (item == null) {
			return;
		}

		final Player player = getPlayer();

		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					if (player != null) {
						plugin.getFullInventoryHandler().giveItem(player, item);
					}
				}
			});
		}

	}

	public void giveItem(ItemStack itemStack, HashMap<String, String> placeholders) {
		giveItem(new ItemBuilder(itemStack).setPlaceholders(placeholders).toItemStack(getPlayer()));
	}

	/**
	 * Give user money, needs vault installed
	 *
	 * @param m Amount of money to give
	 */
	public void giveMoney(double m) {
		if (!plugin.isEnabled()) {
			return;
		}
		if (plugin.getEcon() != null) {
			try {
				if (m > 0) {
					final double money = m;
					Bukkit.getScheduler().runTask(plugin, new Runnable() {

						@Override
						public void run() {
							plugin.getEcon().depositPlayer(getOfflinePlayer(), money);
						}
					});

				} else if (m < 0) {
					m = m * -1;
					final double money = m;
					Bukkit.getScheduler().runTask(plugin, new Runnable() {

						@Override
						public void run() {
							plugin.getEcon().withdrawPlayer(getOfflinePlayer(), money);
						}
					});

				}
			} catch (

			IllegalStateException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Give money.
	 *
	 * @param money the money
	 */
	public void giveMoney(int money) {
		giveMoney((double) money);
	}

	/**
	 * Give potion effect.
	 *
	 * @param potionName the potion name
	 * @param duration   the duration
	 * @param amplifier  the amplifier
	 */
	public void givePotionEffect(String potionName, int duration, int amplifier) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(getUUID()));
		if (player != null && plugin.isEnabled()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.addPotionEffect(
							new PotionEffect(PotionEffectType.getByName(potionName), 20 * duration, amplifier));
				}
			});

		}
	}

	public void giveReward(FileConfiguration data, String path, RewardOptions rewardOptions) {
		plugin.getRewardHandler().giveReward(this, data, path, rewardOptions);
	}

	public void giveReward(Reward reward, RewardOptions rewardOptions) {
		reward.giveReward(this, rewardOptions);
	}

	public boolean hasChoices() {
		return getUnClaimedChoices().size() > 0;
	}

	/**
	 * Check if player joined before
	 *
	 * @return true, if successful
	 */
	public boolean hasLoggedOnBefore() {
		OfflinePlayer player = Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			if (player.hasPlayedBefore() || player.isOnline()) {
				return true;
			}

		}
		ArrayList<String> uuids = plugin.getUserManager().getAllUUIDs();
		if (uuids.contains(getUUID())) {
			return true;
		}
		return false;
	}

	public boolean hasPermission(String perm) {
		Player player = getPlayer();
		if (player == null) {
			return false;
		}
		if (perm.startsWith("!")) {
			perm = perm.substring(1);
			return !player.hasPermission(perm);
		}
		return player.hasPermission(perm);
	}

	public boolean isBanned() {
		if (plugin.getBannedPlayers().contains(getUUID())) {
			return true;
		}
		return false;
	}

	public boolean isCached() {
		return plugin.getUserManager().getDataManager().isCached(UUID.fromString(uuid));
	}

	public boolean isCheckWorld() {
		if (!plugin.isLoadUserData()) {
			return false;
		}
		return Boolean.valueOf(getData().getString("CheckWorld", true));
	}

	public boolean isInWorld(ArrayList<String> worlds) {
		Player p = getPlayer();
		if (p != null) {
			for (String world : worlds) {
				if (p.getWorld().getName().equalsIgnoreCase(world)) {
					return true;
				}
			}
		}

		return false;
	}

	public boolean isInWorld(String world) {
		Player p = getPlayer();
		if (p != null) {
			return p.getWorld().getName().equalsIgnoreCase(world);
		}

		return false;
	}

	/**
	 * Checks if is online.
	 *
	 * @return true, if is online
	 */
	public boolean isOnline() {
		boolean online = PlayerUtils.getInstance().isPlayerOnline(getPlayerName());
		if (!online) {
			return false;
		}
		if (plugin.getOptions().isTreatVanishAsOffline()) {
			if (isVanished()) {
				return false;
			}
		}
		return true;
	}

	public boolean isVanished() {
		Player player = getPlayer();
		if (player != null) {
			for (MetadataValue meta : player.getMetadata("vanished")) {
				if (meta.asBoolean()) {
					return true;
				}
			}

			try {
				try {
					if (plugin.getCmiHandle() != null) {
						return plugin.getCmiHandle().isVanished(player);
					}
				} catch (Exception e) {
				}
			} catch (Exception e) {
				plugin.debug(e);
			}
		}
		return false;
	}

	public void loadCache() {
		plugin.getUserManager().getDataManager().cacheUser(UUID.fromString(uuid));
	}

	public void loadData() {
		data = new UserData(this);
	}

	public void loadTimedDelayedTimer(long time) {
		long delay = time - System.currentTimeMillis();
		if (delay < 0) {
			delay = 0;
		}
		delay += 500;
		plugin.getRewardHandler().getDelayedTimer().schedule(new Runnable() {

			@Override
			public void run() {
				checkDelayedTimedRewards();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	/**
	 * Play particle effect.
	 *
	 * @param effectName the effect name
	 * @param data       the data
	 * @param particles  the particles
	 * @param radius     the radius
	 */
	public void playEffect(String effectName, int data, int particles, int radius) {
		Player player = getPlayer();
		if ((player != null) && (effectName != null)) {
			try {
				Effect effect = Effect.valueOf(effectName);
				for (int i = 0; i < particles; i++) {
					player.getWorld().playEffect(player.getLocation(), effect, data, radius);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public void playParticle(String effectName, int data, int particles, int radius) {
		Player player = getPlayer();
		if ((player != null) && (effectName != null)) {
			try {
				Particle effect = Particle.valueOf(effectName);
				for (int i = 0; i < particles; i++) {
					player.getWorld().spawnParticle(effect, player.getLocation(), particles, radius, radius, radius,
							data);
				}

			} catch (Exception e) {
				plugin.getLogger().warning(
						"Failed to create particle: " + effectName + ", " + data + ", " + particles + ", " + radius);
				e.printStackTrace();
			}
		}
	}

	@Deprecated
	public void playParticleEffect(String effectName, int data, int particles, int radius) {
		playParticle(effectName, data, particles, radius);
	}

	/**
	 * Play sound.
	 *
	 * @param soundName the sound name
	 * @param volume    the volume
	 * @param pitch     the pitch
	 */
	public void playSound(String soundName, float volume, float pitch) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			Sound sound = Sound.valueOf(soundName);
			if (sound != null) {
				player.playSound(player.getLocation(), sound, volume, pitch);
			} else {
				plugin.debug("Invalid sound: " + soundName);
			}
		}
	}

	public void preformCommand(ArrayList<String> commands, HashMap<String, String> placeholders) {
		if (commands != null && !commands.isEmpty()) {
			final ArrayList<String> cmds = ArrayUtils.getInstance().replaceJavascript(getPlayer(),
					ArrayUtils.getInstance().replacePlaceHolder(commands, placeholders));

			final Player player = getPlayer();
			if (player != null && plugin.isEnabled()) {
				for (final String cmd : cmds) {
					plugin.debug("Executing player command for " + getPlayerName() + ": " + cmd);
					Bukkit.getScheduler().runTask(plugin, new Runnable() {

						@Override
						public void run() {
							player.performCommand(cmd);
						}
					});
				}
			}
		}
	}

	public void preformCommand(String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			final String cmd = StringParser.getInstance().replaceJavascript(getPlayer(),
					StringParser.getInstance().replacePlaceHolder(command, placeholders));
			plugin.debug("Executing player command for " + getPlayerName() + ": " + command);
			if (plugin.isEnabled()) {
				Bukkit.getScheduler().runTask(plugin, new Runnable() {

					@Override
					public void run() {
						Player player = getPlayer();
						if (player != null) {
							player.performCommand(cmd);
						}
					}
				});
			}
		}
	}

	public void remove() {
		plugin.debug("Removing " + getUUID() + " (" + getPlayerName() + ") from storage...");
		getData().remove();
	}

	public void removeUnClaimedChoiceReward(String name) {
		ArrayList<String> choices = getUnClaimedChoices();
		choices.remove(name);
		setUnClaimedChoice(choices);
	}

	/**
	 * Send action bar.
	 *
	 * @param msg   the msg
	 * @param delay the delay
	 */
	public void sendActionBar(String msg, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {

				try {
					ActionBar actionBar = new ActionBar(StringParser.getInstance().replaceJavascript(getPlayer(), msg),
							delay);
					actionBar.send(player);
				} catch (Exception ex) {
					plugin.debug("Failed to send ActionBar, turn debug on to see stack trace");
					plugin.debug(ex);
				}
			}
		}
	}

	/**
	 * Send boss bar.
	 *
	 * @param msg      the msg
	 * @param color    the color
	 * @param style    the style
	 * @param progress the progress
	 * @param delay    the delay
	 */
	public void sendBossBar(String msg, String color, String style, double progress, int delay) {
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {
				try {
					BossBar bossBar = new BossBar(StringParser.getInstance().replaceJavascript(getPlayer(), msg), color,
							style, progress);
					bossBar.send(player, delay);
				} catch (Exception ex) {
					plugin.debug("Failed to send BossBar");
					plugin.debug(ex);
				}
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param messages the messages
	 */
	public void sendJson(ArrayList<TextComponent> messages) {
		Player player = getPlayer();
		if ((player != null) && (messages != null)) {
			for (TextComponent txt : messages) {
				txt.setText(StringParser.getInstance().replaceJavascript(getPlayer(), txt.getText()));
				plugin.getServerHandle().sendMessage(player, txt);
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param message the message
	 */
	public void sendJson(TextComponent message) {
		Player player = getPlayer();
		if ((player != null) && (message != null)) {
			message.setText(StringParser.getInstance().replaceJavascript(getPlayer(), message.getText()));
			plugin.getServerHandle().sendMessage(player, message);
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(ArrayList<String> msg) {
		sendMessage(ArrayUtils.getInstance().convert(msg));
	}

	public void sendMessage(ArrayList<String> msg, HashMap<String, String> placeholders) {
		sendMessage(ArrayUtils.getInstance().convert(ArrayUtils.getInstance().replacePlaceHolder(msg, placeholders)));
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(String msg) {
		Player player = getPlayer();
		if ((player != null) && (msg != null)) {
			if (!msg.equals("")) {
				for (String str : msg.split("%NewLine%")) {
					plugin.getServerHandle().sendMessage(player,
							StringParser.getInstance().parseJson(StringParser.getInstance().parseText(player, str)));
				}
			}
		}
	}

	public void sendMessage(String msg, HashMap<String, String> placeholders) {
		sendMessage(StringParser.getInstance().replacePlaceHolder(msg, placeholders));
	}

	public void sendMessage(String msg, String toReplace, String replace) {
		sendMessage(StringParser.getInstance().replacePlaceHolder(msg, toReplace, replace));
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(String[] msg) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (msg != null)) {

			for (String str : msg) {
				sendMessage(str);
			}

		}
	}

	/**
	 * Send title.
	 *
	 * @param title    the title
	 * @param subTitle the sub title
	 * @param fadeIn   the fade in
	 * @param showTime the show time
	 * @param fadeOut  the fade out
	 */
	public void sendTitle(String title, String subTitle, int fadeIn, int showTime, int fadeOut) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			try {
				Title titleObject = new Title(StringParser.getInstance().replaceJavascript(getPlayer(), title),
						StringParser.getInstance().replaceJavascript(getPlayer(), subTitle), fadeIn, showTime, fadeOut);
				titleObject.send(player);
			} catch (Exception ex) {
				plugin.getLogger().info("Failed to send Title, turn debug on to see stack trace");
				plugin.debug(ex);
			}
		}
	}

	public void setCheckWorld(boolean b) {
		getData().setString("CheckWorld", "" + b);
	}

	public void setChoicePreference(String reward, String preference) {
		ArrayList<String> data = getChoicePreferenceData();
		ArrayList<String> choices = new ArrayList<String>();

		boolean added = false;
		for (String str : data) {
			String[] data1 = str.split(":");
			if (data1.length > 1) {
				if (data1[0].equals(reward)) {
					choices.add(reward + ":" + preference);
					added = true;
				} else {
					choices.add(str);
				}
			}
		}
		if (!added) {
			choices.add(reward + ":" + preference);
		}
		getData().setStringList("ChoicePreference", choices);
	}

	public void setInputMethod(String inputMethod) {
		data.setString("InputMethod", inputMethod);
	}

	public void setLastOnline(long online) {
		getData().setString("LastOnline", "" + online);
	}

	public void setOfflineRewards(ArrayList<String> offlineRewards) {
		data.setStringList(plugin.getUserManager().getOfflineRewardsPath(), offlineRewards);
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public void setRepeatAmount(Reward reward, int amount) {
		getData().setInt("Repeat" + reward.getName(), amount);
	}

	public void setTimedRewards(HashMap<String, Long> timed) {
		ArrayList<String> timedRewards = new ArrayList<String>();
		for (Entry<String, Long> entry : timed.entrySet()) {

			String str = "";
			str += entry.getKey() + "%ExecutionTime/%";
			str += entry.getValue();
			timedRewards.add(str);

		}
		data.setStringList("TimedRewards", timedRewards);
	}

	public void setUnClaimedChoice(ArrayList<String> rewards) {
		getData().setStringList("UnClaimedChoices", rewards);
	}

	public void setUserInputMethod(InputMethod method) {
		setInputMethod(method.toString());
	}

	/**
	 * Sets the uuid.
	 *
	 * @param uuid the new uuid
	 */
	public void setUUID(String uuid) {
		this.uuid = uuid;
	}

	public void setWaitForCache(boolean b) {
		waitForCache = b;
	}

	public AdvancedCoreUser tempCache() {
		tempCache = true;
		getUserData().tempCache();
		return this;
	}

	public void updateTempCacheWithColumns(ArrayList<Column> cols) {
		tempCache = true;
		getUserData().updateTempCacheWithColumns(cols);
	}

	public void updateName(boolean force) {
		if (getData().hasData() || force) {
			String playerName = getData().getString("PlayerName", true);
			if (playerName == null || !playerName.equals(getPlayerName())) {
				getData().setString("PlayerName", getPlayerName(), true);
			}
		}
	}

}