package com.Ben12345rocks.AdvancedCore.Objects;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
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

	private UserData data;

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
		loadData();
		uuid = player.getUniqueId().toString();
		setPlayerName(player.getName());
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
		loadData();
		uuid = PlayerUtils.getInstance().getUUID(playerName);
		setPlayerName(playerName);
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
		loadData();
		setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid));

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
		loadData();
		if (this.loadName) {
			setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid));
		}

	}

	@Deprecated
	public User(Plugin plugin, UUID uuid, boolean loadName, boolean loadData) {
		this.plugin = plugin;
		this.uuid = uuid.getUUID();
		this.loadName = loadName;
		if (loadData) {
			loadData();
		}
		if (this.loadName) {
			setPlayerName(PlayerUtils.getInstance().getPlayerName(this, this.uuid));
		}

	}

	/**
	 * Adds the choice reward.
	 *
	 * @param reward
	 *            the reward
	 */
	public void addChoiceReward(Reward reward) {
		ArrayList<String> choiceRewards = getChoiceRewards();
		choiceRewards.add(reward.getRewardName());
		setChoiceRewards(choiceRewards);
	}

	public void addOfflineRewards(Reward reward, HashMap<String, String> placeholders) {
		synchronized (AdvancedCoreHook.getInstance()) {
			ArrayList<String> offlineRewards = getOfflineRewards();
			offlineRewards
					.add(reward.getRewardName() + "%placeholders%" + ArrayUtils.getInstance().makeString(placeholders));
			setOfflineRewards(offlineRewards);
		}
	}

	public void addTimedReward(Reward reward, long epochMilli) {
		HashMap<Reward, ArrayList<Long>> timed = getTimedRewards();
		ArrayList<Long> times = timed.get(reward);
		if (times == null) {
			times = new ArrayList<Long>();
		}
		times.add(epochMilli);
		timed.put(reward, times);
		setTimedRewards(timed);
		loadTimedDelayedTimer(epochMilli);
	}

	public void checkDelayedTimedRewards() {
		AdvancedCoreHook.getInstance().debug("Checking timed/delayed for " + getPlayerName());
		HashMap<Reward, ArrayList<Long>> timed = getTimedRewards();
		for (Entry<Reward, ArrayList<Long>> entry : timed.entrySet()) {
			ArrayList<Long> times = entry.getValue();
			ListIterator<Long> iterator = times.listIterator();
			while (iterator.hasNext()) {
				long time = iterator.next();
				if (time != 0) {
					Date timeDate = new Date(time);
					if (new Date().after(timeDate)) {
						new RewardBuilder(entry.getKey()).setCheckTimed(false)
								.withPlaceHolder("date",
										"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(new Date(time)))
								.send(this);
						AdvancedCoreHook.getInstance().debug(
								"Giving timed/delayed reward " + entry.getKey().getName() + " for " + getPlayerName());
						iterator.remove();
					}
				}
			}
			timed.put(entry.getKey(), times);
		}
		setTimedRewards(timed);
	}

	/**
	 * Check offline rewards.
	 */
	@SuppressWarnings("deprecation")
	public void checkOfflineRewards() {
		ArrayList<String> copy = getOfflineRewards();
		setOfflineRewards(new ArrayList<String>());
		for (String str : copy) {
			String[] args = str.split("%placeholders%");
			String placeholders = "";
			if (args.length > 1) {
				placeholders = args[1];
			}
			RewardHandler.getInstance().giveReward(this, args[0], false, true, false,
					ArrayUtils.getInstance().fromString(placeholders));
		}
	}

	public ArrayList<String> getChoiceRewards() {
		return getUserData().getStringList("ChoiceRewards");
	}

	public UserData getData() {
		return data;
	}

	public String getInputMethod() {
		return getUserData().getString("InputMethod");
	}

	public ArrayList<String> getOfflineRewards() {
		return getUserData().getStringList("OfflineRewards");
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public Player getPlayer() {
		return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
	}

	public OfflinePlayer getOfflinePlayer() {
		return Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
	}

	/**
	 * Gets the player name.
	 *
	 * @return the player name
	 */
	public String getPlayerName() {
		if (playerName == null) {
			return "";
		} else {
			return playerName;
		}
	}

	public HashMap<Reward, ArrayList<Long>> getTimedRewards() {
		ArrayList<String> timedReward = getUserData().getStringList("TimedRewards");
		HashMap<Reward, ArrayList<Long>> timedRewards = new HashMap<Reward, ArrayList<Long>>();
		for (String str : timedReward) {
			String[] data = str.split("//");
			if (data.length > 1) {
				String rewardName = data[0];
				Reward reward = RewardHandler.getInstance().getReward(rewardName);
				String timeStr = data[1];
				ArrayList<Long> t = new ArrayList<Long>();
				for (String ti : timeStr.split("&")) {
					t.add(Long.valueOf(ti));
				}
				timedRewards.put(reward, t);
			}
		}
		return timedRewards;
	}

	public UserData getUserData() {
		return data;
	}

	public InputMethod getUserInputMethod() {
		String inputMethod = getInputMethod();
		if (inputMethod == null) {
			return InputMethod.getMethod(AdvancedCoreHook.getInstance().getDefaultRequestMethod());
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
	@Deprecated
	public void giveItem(int id, int amount, int data, String itemName, List<String> lore,
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
	public void giveItem(ItemStack item) {
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

	public void giveItem(ItemStack itemStack, HashMap<String, String> placeholders) {
		giveItem(new ItemBuilder(itemStack).setPlaceholders(placeholders).toItemStack(getPlayer()));
	}

	/**
	 * Give user money, needs vault installed
	 *
	 * @param money
	 *            Amount of money to give
	 */
	public void giveMoney(double money) {
		if (hook.getEcon() != null) {
			if (money > 0) {
				hook.getEcon().depositPlayer(getOfflinePlayer(), money);
			} else if (money < 0) {
				money = money * -1;
				hook.getEcon().withdrawPlayer(getOfflinePlayer(), money);
			}
		}
	}

	/**
	 * Give money.
	 *
	 * @param money
	 *            the money
	 */
	public void giveMoney(int money) {
		giveMoney((double) money);
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
							new PotionEffect(PotionEffectType.getByName(potionName), 20 * duration, amplifier), true);
				}
			});

		}
	}

	/**
	 * Give reward
	 *
	 * @param data
	 *            Data
	 * @param path
	 *            Path
	 */
	public void giveReward(FileConfiguration data, String path) {
		RewardHandler.getInstance().giveReward(this, data, path);
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
		return false;
	}

	public boolean hasPermission(String perm) {
		Player player = getPlayer();
		if (player == null) {
			return false;
		}
		return player.hasPermission(perm);
	}

	/**
	 * Checks if is online.
	 *
	 * @return true, if is online
	 */
	public boolean isOnline() {
		return PlayerUtils.getInstance().isPlayerOnline(getPlayerName());
	}

	public void loadData() {
		data = new UserData(this);
	}

	public void loadTimedDelayedTimer(long time) {
		long delay = time - System.currentTimeMillis();
		if (delay < 0) {
			delay = 0;
		}
		delay += 250;
		AdvancedCoreHook.getInstance().getTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				checkDelayedTimedRewards();
			}
		}, delay);
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
	public void playParticleEffect(String effectName, int data, int particles, int radius) {
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
	public void playSound(String soundName, float volume, float pitch) {
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

	public void preformCommand(ArrayList<String> commands, HashMap<String, String> placeholders) {
		if (commands != null && !commands.isEmpty()) {
			final ArrayList<String> cmds = ArrayUtils.getInstance().replaceJavascript(getPlayer(),
					ArrayUtils.getInstance().replacePlaceHolder(commands, placeholders));
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					Player player = getPlayer();
					if (player != null) {
						for (String cmd : cmds) {
							player.performCommand(cmd);
						}
					}
				}
			});
		}
	}

	public void preformCommand(String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			final String cmd = StringUtils.getInstance().replaceJavascript(getPlayer(),
					StringUtils.getInstance().replacePlaceHolder(command, placeholders));
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

	public void saveData() {
		setChoiceRewards(getChoiceRewards());
		setOfflineRewards(getOfflineRewards());
		setTimedRewards(getTimedRewards());
		setInputMethod(getInputMethod());
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
					ActionBar actionBar = new ActionBar(StringUtils.getInstance().replaceJavascript(getPlayer(), msg),
							delay);
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
	public void sendBossBar(String msg, String color, String style, double progress, int delay) {
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {
				try {
					BossBar bossBar = new BossBar(StringUtils.getInstance().replaceJavascript(getPlayer(), msg), color,
							style, progress);
					bossBar.send(player, delay);
				} catch (Exception ex) {
					hook.debug("Failed to send BossBar");
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
	public void sendJson(ArrayList<TextComponent> messages) {
		Player player = getPlayer();
		if ((player != null) && (messages != null)) {
			for (TextComponent txt : messages) {
				txt.setText(StringUtils.getInstance().replaceJavascript(getPlayer(), txt.getText()));
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
	public void sendJson(TextComponent message) {
		Player player = getPlayer();
		if ((player != null) && (message != null)) {
			message.setText(StringUtils.getInstance().replaceJavascript(getPlayer(), message.getText()));
			AdvancedCoreHook.getInstance().getServerHandle().sendMessage(player, message);
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg
	 *            the msg
	 */
	public void sendMessage(ArrayList<String> msg) {
		sendMessage(ArrayUtils.getInstance().convert(msg));
	}

	public boolean isBanned() {
		OfflinePlayer p = getOfflinePlayer();
		if (p != null) {
			return p.isBanned();
		} else {
			return false;
		}
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
			if (!msg.equals("")) {
				for (String str : msg.split("%NewLine%")) {
					player.sendMessage(StringUtils.getInstance().colorize(StringUtils.getInstance().replaceJavascript(
							getPlayer(), StringUtils.getInstance().replacePlaceHolders(player, str))));
				}
			}
		}
	}

	public void sendMessage(String msg, HashMap<String, String> placeholders) {
		sendMessage(StringUtils.getInstance().replacePlaceHolder(msg, placeholders));
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

			for (String str : msg) {
				sendMessage(str);
			}

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
	public void sendTitle(String title, String subTitle, int fadeIn, int showTime, int fadeOut) {
		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			try {
				Title titleObject = new Title(StringUtils.getInstance().replaceJavascript(getPlayer(), title),
						StringUtils.getInstance().replaceJavascript(getPlayer(), subTitle), fadeIn, showTime, fadeOut);
				titleObject.send(player);
			} catch (Exception ex) {
				plugin.getLogger().info("Failed to send Title, turn debug on to see stack trace");
				hook.debug(ex);
			}
		}
	}

	public void setChoiceRewards(ArrayList<String> choiceRewards) {
		data.setStringList("ChoiceRewards", choiceRewards);
	}

	public void setInputMethod(String inputMethod) {
		data.setString("InputMethod", inputMethod);
	}

	public void setOfflineRewards(ArrayList<String> offlineRewards) {
		data.setStringList("OfflineRewards", offlineRewards);
	}

	private void setPlayerName(String playerName) {
		this.playerName = playerName;
		updateName();
	}

	public void setTimedRewards(HashMap<Reward, ArrayList<Long>> timed) {
		ArrayList<String> timedRewards = new ArrayList<String>();
		for (Entry<Reward, ArrayList<Long>> entry : timed.entrySet()) {
			if (entry.getValue().size() > 0) {
				String str = "";
				str += entry.getKey().getRewardName() + "//";
				for (int i = 0; i < entry.getValue().size(); i++) {
					if (i != 0) {
						str += "&";
					}
					long time = entry.getValue().get(i);
					str += time;
				}
				timedRewards.add(str);
			}
		}
		data.setStringList("TimedRewards", timedRewards);
	}

	public void setUserInputMethod(InputMethod method) {
		setInputMethod(method.toString());
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

	public void updateName() {
		if (!getData().getString("PlayerName").equals(getPlayerName())) {
			getData().setString("PlayerName", getPlayerName());
		}
	}

}