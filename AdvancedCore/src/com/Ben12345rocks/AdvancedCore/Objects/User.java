package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Util.Effects.ActionBar;
import com.Ben12345rocks.AdvancedCore.Util.Effects.BossBar;
import com.Ben12345rocks.AdvancedCore.Util.Effects.Title;
import com.Ben12345rocks.AdvancedCore.Util.Request.RequestManager;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;

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

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param player
	 *            the player
	 */
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
	public User(Plugin plugin, String playerName) {
		this.plugin = plugin;
		this.playerName = playerName;
		uuid = Utils.getInstance().getUUID(playerName);

	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin
	 *            the plugin
	 * @param uuid
	 *            the uuid
	 */
	public User(Plugin plugin, UUID uuid) {
		this.plugin = plugin;
		this.uuid = uuid.getUUID();
		playerName = Utils.getInstance().getPlayerName(this.uuid);
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
	public User(Plugin plugin, UUID uuid, boolean loadName) {
		this.plugin = plugin;
		this.uuid = uuid.getUUID();
		if (loadName) {
			playerName = Utils.getInstance().getPlayerName(this.uuid);
		}
	}

	/**
	 * Check offline rewards.
	 */
	public void checkOfflineRewards() {
		for (Reward reward : Main.plugin.rewards) {
			int offVotes = getOfflineRewards(reward);
			for (int i = 0; i < offVotes; i++) {
				giveReward(reward, false);
			}
			setOfflineRewards(reward, 0);
		}
	}

	/**
	 * Gets the input method.
	 *
	 * @return the input method
	 */
	@Deprecated
	public RequestManager.InputMethod getInputMethod() {
		return RequestManager.InputMethod.getMethod(getRawData().getString(
				"InputMethod",
				Config.getInstance().getRequestAPIDefaultMethod()));
	}

	public InputMethod getUserInputMethod() {
		return InputMethod.getMethod(getRawData().getString("InputMethod",
				Config.getInstance().getRequestAPIDefaultMethod()));
	}

	/**
	 * Gets the offline rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the offline rewards
	 */
	public int getOfflineRewards(Reward reward) {
		return getPluginData()
				.getInt("OfflineReward." + reward.getRewardName());
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
	public int getOfflineRewardWorld(String reward, String world) {
		if (world == null) {
			world = "AllTheWorlds";
		}
		return getPluginData().getInt(
				"OfflineVotesWorld." + reward + "." + world);
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public Player getPlayer() {
		return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
	}

	/**
	 * Gets the player name.
	 *
	 * @return the player name
	 */
	public String getPlayerName() {
		return playerName;
	}

	/**
	 * Gets the plugin data.
	 *
	 * @return the plugin data
	 */
	public ConfigurationSection getPluginData() {
		boolean isSection = Data.getInstance().getData(this)
				.isConfigurationSection(plugin.getName());
		if (!isSection) {
			return Data.getInstance().getData(this)
					.createSection(plugin.getName());
		}
		return Data.getInstance().getData(this)
				.getConfigurationSection(plugin.getName());
	}

	/**
	 * Gets the raw data.
	 *
	 * @return the raw data
	 */
	public FileConfiguration getRawData() {
		return Data.getInstance().getData(this);
	}

	/**
	 * Gets the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed reward
	 */
	public long getTimedReward(Reward reward) {
		return getPluginData().getLong("Timed." + reward.getRewardName());
	}

	/**
	 * Gets the uuid.
	 *
	 * @return the uuid
	 */
	public String getUUID() {
		return uuid;
	}

	/**
	 * Give exp.
	 *
	 * @param exp
	 *            the exp
	 */
	public void giveExp(int exp) {
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
	/**
	 * Give the user an item
	 * @param id	Item id
	 * @param amount	Item amount
	 * @param data		Item data
	 * @param itemName	Item name
	 * @param lore		Item lore
	 * @param enchants	Item enchants
	 */
	public void giveItem(int id, int amount, int data, String itemName,
			List<String> lore, HashMap<String, Integer> enchants) {

		if (amount == 0) {
			return;
		}

		String playerName = getPlayerName();

		ItemStack item = new ItemStack(id, amount, (short) data);
		item = Utils.getInstance().nameItem(item, itemName);
		item = Utils.getInstance().addLore(item, lore);
		Player player = Bukkit.getPlayer(playerName);
		// player.getInventory().addItem(item);

		item = Utils.getInstance().addEnchants(item, enchants);

		HashMap<Integer, ItemStack> excess = player.getInventory()
				.addItem(item);
		for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.getWorld().dropItem(player.getLocation(),
							me.getValue());
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
	public void giveItem(ItemStack item) {
		if (item.getAmount() == 0) {
			return;
		}

		String playerName = getPlayerName();

		Player player = Bukkit.getPlayer(playerName);

		HashMap<Integer, ItemStack> excess = player.getInventory()
				.addItem(item);
		for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.getWorld().dropItem(player.getLocation(),
							me.getValue());
				}
			});

		}

		player.updateInventory();

	}

	/**
	 * Give money.
	 *
	 * @param money
	 *            the money
	 */
	@SuppressWarnings("deprecation")
	/**
	 * Give user money, needs vault installed
	 * @param money		Amount of money to give
	 */
	public void giveMoney(double money) {
		String playerName = getPlayerName();
		if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
			if (money > 0) {
				Main.plugin.econ.depositPlayer(playerName, money);
			} else if (money < 0) {
				money = money * -1;
				Main.plugin.econ.withdrawPlayer(playerName, money);
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
	/**
	 * Give user money, needs vault installed
	 * @param money		Amount of money to give
	 */
	public void giveMoney(int money) {
		String playerName = getPlayerName();
		if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null
				&& Main.plugin.econ != null) {
			if (money > 0) {
				Main.plugin.econ.depositPlayer(playerName, money);
			} else if (money < 0) {
				money = money * -1;
				Main.plugin.econ.withdrawPlayer(playerName, money);
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
	public void givePotionEffect(String potionName, int duration, int amplifier) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(getUUID()));
		if (player != null) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					player.addPotionEffect(
							new PotionEffect(PotionEffectType
									.getByName(potionName), 20 * duration,
									amplifier), true);
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
	public void giveReward(Reward reward, boolean online) {
		reward.giveReward(this, online);
	}

	/**
	 * Checks for joined before.
	 *
	 * @return true, if successful
	 */
	public boolean hasJoinedBefore() {
		return Data.getInstance().hasJoinedBefore(this);
	}

	/**
	 * Checks if is online.
	 *
	 * @return true, if is online
	 */
	public boolean isOnline() {
		return Utils.getInstance().isPlayerOnline(getPlayerName());
	}

	/**
	 * Off vote world.
	 *
	 * @param world
	 *            the world
	 */
	public void offVoteWorld(String world) {

		for (Reward reward : Main.plugin.rewards) {
			ArrayList<String> worlds = reward.getWorlds();
			if ((world != null) && (worlds != null)) {
				if (reward.isGiveInEachWorld()) {
					for (String worldName : worlds) {

						Main.plugin.debug("Checking world: " + worldName
								+ ", reard: " + reward);

						if (worldName != "") {
							if (worldName.equals(world)) {

								Main.plugin.debug("Giving reward...");

								int worldRewards =

								getOfflineRewardWorld(reward.getRewardName(),
										worldName);

								while (worldRewards > 0) {
									reward.giveRewardUser(this);
									worldRewards--;
								}

								setOfflineRewardWorld(reward.getRewardName(),
										worldName, 0);
							}
						}

					}
				} else {
					if (worlds.contains(world)) {
						int worldRewards = getOfflineRewardWorld(
								reward.getRewardName(), world);

						while (worldRewards > 0) {
							reward.giveRewardUser(this);
							worldRewards--;
						}

						setOfflineRewardWorld(reward.getRewardName(), world, 0);
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
	@SuppressWarnings("deprecation")
	public synchronized void playParticleEffect(String effectName, int data,
			int particles, int radius) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (effectName != null)) {
			Effect effect = Effect.valueOf(effectName);
			player.spigot().playEffect(player.getLocation(), effect,
					effect.getId(), data, 0f, 0f, 0f, 1f, particles, radius);
			// player.getWorld().spigot().playEffect(player.getLocation(),
			// effect);
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
	public synchronized void playSound(String soundName, float volume,
			float pitch) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			Sound sound = Sound.valueOf(soundName);
			if (sound != null) {
				player.playSound(player.getLocation(), sound, volume, pitch);
			} else {
				Main.plugin.debug("Invalid sound: " + soundName);
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
	public void sendActionBar(String msg, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {

				try {
					ActionBar actionBar = new ActionBar(msg, delay);
					actionBar.send(player);
				} catch (Exception ex) {
					Main.plugin
							.debug("Failed to send ActionBar, turn debug on to see stack trace");
					if (Config.getInstance().getDebugEnabled()) {
						ex.printStackTrace();
					}
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
	public void sendBossBar(String msg, String color, String style,
			double progress, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {
				try {
					BossBar bossBar = new BossBar(msg, color, style, progress);
					bossBar.send(player, delay);
				} catch (Exception ex) {
					Main.plugin
							.debug("Failed to send BossBar, turn debug on to see stack trace");
					if (Config.getInstance().getDebugEnabled()) {
						ex.printStackTrace();
					}
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
	public void sendJson(ArrayList<TextComponent> messages) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (messages != null)) {
			/*
			 * TextComponent msg = new TextComponent(); TextComponent newLine =
			 * new TextComponent( ComponentSerializer.parse("{text: \"\n\"}"));
			 * for (int i = 0; i < messages.size(); i++) {
			 * msg.addExtra(messages.get(i)); if (i != (messages.size() - 1)) {
			 * msg.addExtra(newLine); } } player.spigot().sendMessage(msg);
			 */
			for (TextComponent txt : messages) {
				player.spigot().sendMessage(txt);
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param message
	 *            the message
	 */
	public void sendJson(TextComponent message) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (message != null)) {
			player.spigot().sendMessage(message);
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public void sendMessage(ArrayList<String> msg) {
		sendMessage(Utils.getInstance().convertArray(msg));
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public void sendMessage(String msg) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (msg != null)) {
			if (msg != "") {
				for (String str : msg.split("%NewLine%")) {
					player.sendMessage(Utils.getInstance().colorize(
							Utils.getInstance()
									.replacePlaceHolders(player, str)));
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
	public void sendMessage(String[] msg) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if ((player != null) && (msg != null)) {

			for (int i = 0; i < msg.length; i++) {
				msg[i] = Utils.getInstance()
						.replacePlaceHolders(player, msg[i]);
			}
			player.sendMessage(Utils.getInstance().colorize(msg));

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
	public void sendTitle(String title, String subTitle, int fadeIn,
			int showTime, int fadeOut) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			// Title.getInstance().sendTitle(player, title, subTitle, fadeIn,
			// showTime, fadeOut);
			try {
				Title titleObject = new Title(title, subTitle, fadeIn,
						showTime, fadeOut);
				titleObject.send(player);
			} catch (Exception ex) {
				plugin.getLogger()
						.info("Failed to send Title, turn debug on to see stack trace");
				if (Config.getInstance().getDebugEnabled()) {
					ex.printStackTrace();
				}
			}
		}
	}

	/**
	 * Sets the input method.
	 *
	 * @param method
	 *            the new input method
	 */
	@Deprecated
	public void setInputMethod(RequestManager.InputMethod method) {
		setRawData("InputMethod", method.toString());
	}

	public void setUserInputMethod(InputMethod method) {
		setRawData("InputMethod", method.toString());
	}

	/**
	 * Sets the offline rewards.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setOfflineRewards(Reward reward, int value) {
		setPluginData("OfflineReward." + reward.getRewardName(), value);
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
	public void setOfflineRewardWorld(String reward, String world, int value) {
		if (world == null) {
			world = "AllTheWorlds";
		}
		setPluginData("OfflineVotesWorld." + reward + "." + world, value);
	}

	/**
	 * Sets the player name.
	 */
	public void setPlayerName() {
		User user = this;
		Data.getInstance().setPlayerName(user);
	}

	/**
	 * Sets the player name.
	 *
	 * @param playerName
	 *            the new player name
	 */
	public void setPlayerName(String playerName) {
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
	public void setPluginData(String path, Object value) {
		Data.getInstance().set(this, plugin.getName() + "." + path, value);
	}

	/**
	 * Sets the raw data.
	 *
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public void setRawData(String path, Object value) {
		Data.getInstance().set(this, path, value);
	}

	/**
	 * Sets the timed reward.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setTimedReward(Reward reward, long value) {
		setPluginData("Timed." + reward.getRewardName(), value);
	}

	/**
	 * Sets the uuid.
	 *
	 * @param uuid
	 *            the new uuid
	 */
	public void setUUID(String uuid) {
		this.uuid = uuid;
	}

}
