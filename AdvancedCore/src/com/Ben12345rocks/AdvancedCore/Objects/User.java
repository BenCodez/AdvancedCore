package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Util.Effects.ActionBar;
import com.Ben12345rocks.AdvancedCore.Util.Effects.BossBar;
import com.Ben12345rocks.AdvancedCore.Util.Effects.Title;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;

import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class User.
 */
public class User {

	/** The plugin. */
	public Plugin plugin = null;

	/** The player name. */
	private String playerName;

	/** The uuid. */
	private String uuid;

	private AdvancedCoreHook hook = AdvancedCoreHook.getInstance();

	private boolean loadName = true;

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param player
	 *            the player
	 */
	@Deprecated
	public User(Plugin plugin, Player player) {
		this.plugin = plugin;
		playerName = player.getName();
		uuid = player.getUniqueId().toString();
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param playerName
	 *            the player name
	 */
	@Deprecated
	public User(Plugin plugin, String playerName) {
		this.plugin = plugin;
		this.playerName = playerName;
		uuid = PlayerUtils.getInstance().getUUID(playerName);
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param uuid
	 *            the uuid
	 */
	@Deprecated
	public User(Plugin plugin, UUID uuid) {
		this.plugin = plugin;
		this.uuid = uuid.getUUID();
		playerName = PlayerUtils.getInstance().getPlayerName(this.uuid);
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param uuid
	 *            the uuid
	 * @param loadName
	 *            the load name
	 */
	@Deprecated
	public User(Plugin plugin, UUID uuid, boolean loadName) {
		this.plugin = plugin;
		this.uuid = uuid.getUUID();
		this.loadName = loadName;
		if (this.loadName) {
			playerName = PlayerUtils.getInstance().getPlayerName(this.uuid);
		}
	}

	private synchronized FileConfiguration loadData() {
		return Data.getInstance().getData(uuid);
	}

	/**
	 * Adds the choice reward.
	 *
	 * @param reward
	 *            the reward
	 */
	public synchronized void addChoiceReward(Reward reward) {
		ArrayList<String> rewards = getChoiceRewards();
		rewards.add(reward.getRewardName());
		setChoiceRewards(rewards);
	}

	/**
	 * Check offline rewards.
	 */
	public synchronized void checkOfflineRewards() {
		RewardHandler.getInstance().giveReward(this, false, ArrayUtils.getInstance().convert(getOfflineRewards()));
		setOfflineRewards(new ArrayList<String>());
	}

	@SuppressWarnings("unchecked")
	public synchronized ArrayList<String> getChoiceRewards() {
		return (ArrayList<String>) getRawData().getList("ChoiceRewardsList", new ArrayList<String>());
	}

	/**
	 * Gets the offline rewards.
	 *
	 * @return the offline rewards
	 */
	@SuppressWarnings("unchecked")
	public synchronized ArrayList<String> getOfflineRewards() {
		return (ArrayList<String>) getRawData().getList("OfflineRewards", new ArrayList<String>());
	}

	/**
	 * Gets the offline reward world.
	 *
	 * @param reward
	 *            the reward
	 * @param world
	 *            the world
	 * @return the offline reward world
	 */
	public synchronized int getOfflineRewardWorld(String reward, String world) {
		if (world == null) {
			world = "AllTheWorlds";
		}
		return getRawData().getInt("OfflineVotesWorld." + reward + "." + world);
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public synchronized Player getPlayer() {
		return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
	}

	/**
	 * Gets the player name.
	 *
	 * @return the player name
	 */
	public synchronized String getPlayerName() {
		if ((playerName == null || playerName.equalsIgnoreCase("null")) && loadName) {
			playerName = PlayerUtils.getInstance().getPlayerName(uuid);
		}
		return playerName;
	}

	/**
	 * Gets the plugin data.
	 *
	 * @return the plugin data
	 */
	public synchronized ConfigurationSection getPluginData() {
		boolean isSection = getRawData().isConfigurationSection(plugin.getName());
		if (!isSection) {
			return getRawData().createSection(plugin.getName());
		}
		return getRawData().getConfigurationSection(plugin.getName());
	}

	/**
	 * Gets the raw data.
	 *
	 * @return the raw data
	 */
	public synchronized FileConfiguration getRawData() {
		return loadData();
	}

	/**
	 * Gets the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed reward
	 */
	@SuppressWarnings("unchecked")
	public synchronized ArrayList<Long> getTimedReward(Reward reward) {
		return (ArrayList<Long>) getRawData().getList("TimedRewards." + reward.getRewardName(), new ArrayList<Long>());
	}

	/**
	 * Adds the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @param time
	 *            the time
	 */
	public synchronized void addTimedReward(Reward reward, long time) {
		ArrayList<Long> times = getTimedReward(reward);
		times.add(Long.valueOf(time));
		setTimedReward(reward, times);
		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				RewardHandler.getInstance().checkDelayedTimedRewards();

			}
		}, new Date(time));
	}

	/**
	 * Removes the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @param time
	 *            the time
	 */
	public synchronized void removeTimedReward(Reward reward, long time) {
		ArrayList<Long> times = getTimedReward(reward);
		times.remove(Long.valueOf(time));
		setTimedReward(reward, times);
	}

	/**
	 * Gets the user input method.
	 *
	 * @return the user input method
	 */
	public synchronized InputMethod getUserInputMethod() {
		return InputMethod.getMethod(getRawData().getString("InputMethod", hook.getDefaultRequestMethod()));
	}

	/**
	 * Gets the uuid.
	 *
	 * @return the uuid
	 */
	public synchronized String getUUID() {
		return uuid;
	}

	/**
	 * Give exp.
	 *
	 * @param exp
	 *            the exp
	 */
	public synchronized void giveExp(int exp) {
		Player player = getPlayer();
		if (player != null) {
			player.giveExp(exp);
		}
	}

	/**
	 * Give item.
	 *
	 * @param id
	 *            the id
	 * @param amount
	 *            the amount
	 * @param data
	 *            the data
	 * @param itemName
	 *            the item name
	 * @param lore
	 *            the lore
	 * @param enchants
	 *            the enchants
	 */
	@SuppressWarnings("deprecation")
	public synchronized void giveItem(int id, int amount, int data, String itemName, List<String> lore,
			HashMap<String, Integer> enchants) {

		if (amount == 0) {
			return;
		}

		ItemBuilder builder = new ItemBuilder(Material.getMaterial(id), amount, (short) data);
		builder.setLore(lore);
		builder.setName(itemName);
		builder.addEnchantments(enchants);

		Player player = getPlayer();
		HashMap<Integer, ItemStack> excess = player.getInventory().addItem(builder.toItemStack());
		for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.getWorld().dropItem(player.getLocation(), me.getValue());
				}
			});
		}

		player.updateInventory();

	}

	/**
	 * Give item.
	 *
	 * @param item
	 *            the item
	 */
	public synchronized void giveItem(ItemStack item) {
		if (item.getAmount() == 0) {
			return;
		}

		Player player = getPlayer();

		HashMap<Integer, ItemStack> excess = player.getInventory().addItem(item);
		for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.getWorld().dropItem(player.getLocation(), me.getValue());
				}
			});

		}

		player.updateInventory();

	}

	@SuppressWarnings("deprecation")
	/**
	 * Give user money, needs vault installed
	 * 
	 * @param money
	 *            Amount of money to give
	 */
	public synchronized void giveMoney(double money) {
		String playerName = getPlayerName();
		if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
			if (money > 0) {
				hook.getEcon().depositPlayer(playerName, money);
			} else if (money < 0) {
				money = money * -1;
				hook.getEcon().withdrawPlayer(playerName, money);
			}
		}
	}

	/**
	 * Give money.
	 *
	 * @param money
	 *            the money
	 */
	@SuppressWarnings("deprecation")
	public synchronized void giveMoney(int money) {
		String playerName = getPlayerName();
		if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null && hook.getEcon() != null) {
			if (money > 0) {
				hook.getEcon().depositPlayer(playerName, money);
			} else if (money < 0) {
				money = money * -1;
				hook.getEcon().withdrawPlayer(playerName, money);
			}
		}
	}

	/**
	 * Give potion effect.
	 *
	 * @param potionName
	 *            the potion name
	 * @param duration
	 *            the duration
	 * @param amplifier
	 *            the amplifier
	 */
	public synchronized void givePotionEffect(String potionName, int duration, int amplifier) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(getUUID()));
		if (player != null) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.addPotionEffect(
							new PotionEffect(PotionEffectType.getByName(potionName), 20 * duration, amplifier), true);
				}
			});

		}
	}

	/**
	 * Give reward.
	 *
	 * @param reward
	 *            the reward
	 * @param online
	 *            the online
	 */
	public synchronized void giveReward(Reward reward, boolean online) {
		reward.giveReward(this, online);
	}

	/**
	 * Checks for joined before.
	 *
	 * @return true, if successful
	 */
	public synchronized boolean hasJoinedBefore() {
		return Data.getInstance().hasJoinedBefore(this);
	}

	/**
	 * Checks if is online.
	 *
	 * @return true, if is online
	 */
	public synchronized boolean isOnline() {
		return PlayerUtils.getInstance().isPlayerOnline(getPlayerName());
	}

	/**
	 * Off vote world.
	 *
	 * @param world
	 *            the world
	 */
	public synchronized void offVoteWorld(String world) {

		for (Reward reward : RewardHandler.getInstance().getRewards()) {
			if (reward.isUsesWorlds()) {
				ArrayList<String> worlds = reward.getWorlds();
				if ((world != null) && (worlds != null)) {
					if (reward.isGiveInEachWorld()) {
						for (String worldName : worlds) {

							hook.debug("Checking world: " + worldName + ", reward: " + reward);

							if (worldName != "") {
								if (worldName.equals(world)) {

									hook.debug("Giving reward...");

									int worldRewards =

											getOfflineRewardWorld(reward.getRewardName(), worldName);

									while (worldRewards > 0 && isOnline()) {
										reward.giveRewardUser(this);
										worldRewards--;
										setOfflineRewardWorld(reward.getRewardName(), world, worldRewards);
									}
								}
							}

						}
					} else {
						if (worlds.contains(world)) {
							int worldRewards = getOfflineRewardWorld(reward.getRewardName(), world);

							while (worldRewards > 0 && isOnline()) {
								reward.giveRewardUser(this);
								worldRewards--;
								setOfflineRewardWorld(reward.getRewardName(), world, worldRewards);
							}

						}
					}
				}
			}
		}

	}

	/**
	 * Play particle effect.
	 *
	 * @param effectName
	 *            the effect name
	 * @param data
	 *            the data
	 * @param particles
	 *            the particles
	 * @param radius
	 *            the radius
	 */
	public synchronized void playParticleEffect(String effectName, int data, int particles, int radius) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (effectName != null)) {
			Effect effect = Effect.valueOf(effectName);
			for (int i = 0; i < particles; i++) {
				player.getWorld().playEffect(player.getLocation(), effect, data, radius);
			}

		}
	}

	/**
	 * Play sound.
	 *
	 * @param soundName
	 *            the sound name
	 * @param volume
	 *            the volume
	 * @param pitch
	 *            the pitch
	 */
	public synchronized void playSound(String soundName, float volume, float pitch) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			Sound sound = Sound.valueOf(soundName);
			if (sound != null) {
				player.playSound(player.getLocation(), sound, volume, pitch);
			} else {
				hook.debug("Invalid sound: " + soundName);
			}
		}
	}

	/**
	 * Send action bar.
	 *
	 * @param msg
	 *            the msg
	 * @param delay
	 *            the delay
	 */
	public synchronized void sendActionBar(String msg, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {

				try {
					ActionBar actionBar = new ActionBar(msg, delay);
					actionBar.send(player);
				} catch (Exception ex) {
					hook.debug("Failed to send ActionBar, turn debug on to see stack trace");
					hook.debug(ex);
				}
			}
		}
	}

	/**
	 * Send boss bar.
	 *
	 * @param msg
	 *            the msg
	 * @param color
	 *            the color
	 * @param style
	 *            the style
	 * @param progress
	 *            the progress
	 * @param delay
	 *            the delay
	 */
	public synchronized void sendBossBar(String msg, String color, String style, double progress, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {
				try {
					BossBar bossBar = new BossBar(msg, color, style, progress);
					bossBar.send(player, delay);
				} catch (Exception ex) {
					hook.debug("Failed to send BossBar, turn debug on to see stack trace");
					hook.debug(ex);
				}
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param messages
	 *            the messages
	 */
	public synchronized void sendJson(ArrayList<TextComponent> messages) {
		Player player = getPlayer();
		if ((player != null) && (messages != null)) {
			/*
			 * TextComponent msg = new TextComponent(); TextComponent newLine =
			 * new TextComponent( ComponentSerializer.parse("{text: \"\n\"}"));
			 * for (int i = 0; i < messages.size(); i++) {
			 * msg.addExtra(messages.get(i)); if (i != (messages.size() - 1)) {
			 * msg.addExtra(newLine); } } player.spigot().sendMessage(msg);
			 */
			for (TextComponent txt : messages) {
				AdvancedCoreHook.getInstance().getServerHandle().sendMessage(player, txt);
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param message
	 *            the message
	 */
	public synchronized void sendJson(TextComponent message) {
		Player player = getPlayer();
		if ((player != null) && (message != null)) {
			AdvancedCoreHook.getInstance().getServerHandle().sendMessage(player, message);
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public synchronized void sendMessage(ArrayList<String> msg) {
		sendMessage(ArrayUtils.getInstance().convert(msg));
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public synchronized void sendMessage(String msg) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (msg != null)) {
			if (msg != "") {
				for (String str : msg.split("%NewLine%")) {
					player.sendMessage(StringUtils.getInstance()
							.colorize(StringUtils.getInstance().replacePlaceHolders(player, str)));
				}
			}
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public synchronized void sendMessage(String[] msg) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (msg != null)) {

			for (int i = 0; i < msg.length; i++) {
				msg[i] = StringUtils.getInstance().replacePlaceHolders(player, msg[i]);
			}
			player.sendMessage(ArrayUtils.getInstance().colorize(msg));

		}
	}

	/**
	 * Send title.
	 *
	 * @param title
	 *            the title
	 * @param subTitle
	 *            the sub title
	 * @param fadeIn
	 *            the fade in
	 * @param showTime
	 *            the show time
	 * @param fadeOut
	 *            the fade out
	 */
	public synchronized void sendTitle(String title, String subTitle, int fadeIn, int showTime, int fadeOut) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			// Title.getInstance().sendTitle(player, title, subTitle, fadeIn,
			// showTime, fadeOut);
			try {
				Title titleObject = new Title(title, subTitle, fadeIn, showTime, fadeOut);
				titleObject.send(player);
			} catch (Exception ex) {
				plugin.getLogger().info("Failed to send Title, turn debug on to see stack trace");
				hook.debug(ex);
			}
		}
	}

	public synchronized void setChoiceRewards(ArrayList<String> rewards) {
		setRawData("ChoiceRewardsList", rewards);
	}

	public synchronized void setOfflineRewards(ArrayList<String> rewards) {
		setRawData("OfflineRewards", rewards);
	}

	/**
	 * Sets the offline reward world.
	 *
	 * @param reward
	 *            the reward
	 * @param world
	 *            the world
	 * @param value
	 *            the value
	 */
	public synchronized void setOfflineRewardWorld(String reward, String world, int value) {
		if (world == null) {
			world = "AllTheWorlds";
		}
		setRawData("OfflineVotesWorld." + reward + "." + world, value);
	}

	/**
	 * Sets the player name.
	 */
	public synchronized void setPlayerName() {
		Data.getInstance().setPlayerName(uuid, playerName);
	}

	/**
	 * Sets the player name.
	 *
	 * @param playerName
	 *            the new player name
	 */
	public synchronized void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	/**
	 * Sets the plugin data.
	 *
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public synchronized void setPluginData(String path, Object value) {
		setRawData(plugin.getName() + "." + path, value);
	}

	/**
	 * Sets the raw data.
	 *
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public synchronized void setRawData(String path, Object value) {
		Data.getInstance().set(uuid, path, value);
	}

	/**
	 * Sets the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public synchronized void setTimedReward(Reward reward, ArrayList<Long> value) {
		setRawData("TimedRewards." + reward.getRewardName(), value);
	}

	/**
	 * Sets the user input method.
	 *
	 * @param method
	 *            the new user input method
	 */
	public synchronized void setUserInputMethod(InputMethod method) {
		if (method == null) {
			method = InputMethod.ANVIL;
		}
		setRawData("InputMethod", method.toString());
	}

	/**
	 * Sets the uuid.
	 *
	 * @param uuid
	 *            the new uuid
	 */
	public synchronized void setUUID(String uuid) {
		this.uuid = uuid;
	}

	public synchronized void addOfflineRewards(Reward reward) {
		ArrayList<String> rewards = getOfflineRewards();
		rewards.add(reward.name);
		setOfflineRewards(rewards);
	}

}
