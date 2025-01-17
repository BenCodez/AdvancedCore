package com.bencodez.advancedcore.api.misc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.player.PlayerUtils;

import net.md_5.bungee.api.ChatColor;

public class MiscUtils {
	/** The instance. */
	static MiscUtils instance = new MiscUtils();

	public static MiscUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	private MiscUtils() {
	}

	public Date addSeconds(Date date, int seconds) {
		Calendar c = Calendar.getInstance();
		c.setTime(new Date());
		c.add(Calendar.SECOND, seconds);
		return c.getTime();
	}

	/**
	 * Broadcast.
	 *
	 * @param broadcastMsg the broadcast msg
	 */
	public void broadcast(String broadcastMsg) {
		broadcast(broadcastMsg, new ArrayList<>(Bukkit.getOnlinePlayers()));
	}

	public void broadcast(String broadcastMsg, ArrayList<Player> players) {
		if (broadcastMsg != null && !broadcastMsg.equals("")) {
			String consoleMsg = broadcastMsg;
			for (Player player : players) {
				for (String str1 : broadcastMsg.split(Pattern.quote("%newline%"))) {
					for (String str : str1.split(Pattern.quote("%NewLine%"))) {
						String msg = MessageAPI.colorize(PlaceholderUtils.replacePlaceHolders(player, str));
						PlayerUtils.getServerHandle().sendMessage(player, PlaceholderUtils.parseJson(msg));
					}
				}
			}
			Bukkit.getServer().getConsoleSender().sendMessage(MessageAPI.colorize(consoleMsg));
		}
	}

	public boolean checkChance(double chance, double outOf) {
		if ((chance == 0) || (chance == outOf)) {
			return true;
		}

		double randomNum = ThreadLocalRandom.current().nextDouble(outOf);

		plugin.extraDebug("Chance Required: " + chance + ", RandomNum: " + randomNum);

		if (randomNum <= chance) {
			return true;
		}
		return false;
	}

	/**
	 * Convert.
	 *
	 * @param array the array
	 * @return the user[]
	 */
	public AdvancedCoreUser[] convertUsers(ArrayList<AdvancedCoreUser> array) {
		if (array == null) {
			return null;
		}
		AdvancedCoreUser[] list = new AdvancedCoreUser[array.size()];
		for (int i = 0; i < array.size(); i++) {
			list[i] = array.get(i);
		}
		return list;
	}

	/**
	 * Convert set.
	 *
	 * @param set the set
	 * @return the array list
	 */
	public ArrayList<AdvancedCoreUser> convertUsers(Set<AdvancedCoreUser> set) {
		if (set == null) {
			return null;
		}

		ArrayList<AdvancedCoreUser> list = new ArrayList<>();
		for (AdvancedCoreUser user : set) {
			list.add(user);
		}
		return list;
	}

	public void executeConsoleCommands(final ArrayList<String> cmds, final HashMap<String, String> placeholders,
			boolean stagger) {
		if (cmds != null && !cmds.isEmpty()) {
			final ArrayList<String> commands = PlaceholderUtils.replacePlaceHolder(cmds, placeholders);
			int tick = 0;
			for (final String cmd : commands) {
				plugin.debug("Executing console command: " + cmd);
				runConsoleCommand(null, cmd, tick, stagger);
			}

		}
	}

	public void executeConsoleCommands(final Player player, final ArrayList<String> cmds,
			final HashMap<String, String> placeholders, boolean stagger) {
		if (cmds != null && !cmds.isEmpty()) {
			placeholders.put("player", player.getName());
			final ArrayList<String> commands = PlaceholderUtils.replaceJavascript(player,
					PlaceholderUtils.replacePlaceHolder(cmds, placeholders));
			int tick = 0;
			for (final String cmd : commands) {
				plugin.debug("Executing console command: " + cmd);
				runConsoleCommand(player, cmd, tick, stagger);
			}

		}
	}

	public void executeConsoleCommands(Player player, String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			final String cmd = PlaceholderUtils.replaceJavascript(player,
					PlaceholderUtils.replacePlaceHolder(command, placeholders));

			plugin.debug("Executing console command: " + command);
			plugin.getBukkitScheduler().executeOrScheduleSync(plugin, new Runnable() {

				@Override
				public void run() {
					Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
				}

			}, player);
		}

	}

	@SuppressWarnings("deprecation")
	public void executeConsoleCommands(final String playerName, final ArrayList<String> cmds,
			final HashMap<String, String> placeholders, boolean stagger) {
		if (cmds != null && !cmds.isEmpty()) {
			placeholders.put("player", playerName);
			OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
			ArrayList<String> commands1 = cmds;
			if (p != null) {
				commands1 = PlaceholderUtils.replaceJavascript(p, commands1);
			}
			final ArrayList<String> commands = PlaceholderUtils.replacePlaceHolder(commands1, placeholders);
			int tick = 0;
			for (final String cmd : commands) {
				plugin.debug("Executing console command: " + cmd);
				runConsoleCommand(Bukkit.getPlayer(playerName), cmd, tick, stagger);
			}
		}
	}

	public void executeConsoleCommands(String playerName, String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			Player p = Bukkit.getPlayer(playerName);
			if (p != null) {
				command = PlaceholderUtils.replaceJavascript(p, command);
			}
			if (command.startsWith("/")) {
				command.replaceFirst("/", "");
			}
			final String cmd = PlaceholderUtils.replacePlaceHolder(command, placeholders);

			plugin.debug("Executing console command: " + command);
			plugin.getBukkitScheduler().executeOrScheduleSync(plugin, new Runnable() {

				@Override
				public void run() {
					Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
				}

			});
		}

	}

	public Object getBlockMeta(Block block, String str) {
		for (MetadataValue meta : block.getMetadata(str)) {
			if (meta.getOwningPlugin().equals(plugin)) {
				return meta.value();
			}
		}
		return null;
	}

	/**
	 * Gets the connection.
	 *
	 * @param player the player
	 * @return the connection
	 * @throws SecurityException         the security exception
	 * @throws NoSuchMethodException     the no such method exception
	 * @throws NoSuchFieldException      the no such field exception
	 * @throws IllegalArgumentException  the illegal argument exception
	 * @throws IllegalAccessException    the illegal access exception
	 * @throws InvocationTargetException the invocation target exception
	 */
	public Object getConnection(Player player) throws SecurityException, NoSuchMethodException, NoSuchFieldException,
			IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Method getHandle = player.getClass().getMethod("getHandle");
		Object nmsPlayer = getHandle.invoke(player);
		Field conField = nmsPlayer.getClass().getField("playerConnection");
		Object con = conField.get(nmsPlayer);
		return con;
	}

	/**
	 * Gets the day from mili.
	 *
	 * @param time the time
	 * @return the day from mili
	 */
	public int getDayFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
				.plusHours(AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet()).getDayOfMonth();
	}

	@SuppressWarnings("deprecation")
	public Enchantment getEnchant(String enchant, String enchant2) {
		try {
			Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchant.toLowerCase()));
			if (ench != null) {
				return ench;
			}
			return Enchantment.getByKey(NamespacedKey.minecraft(enchant2.toLowerCase()));
		} catch (Exception e) {
			plugin.debug(e);
			for (Enchantment ench : Enchantment.values()) {
				if (ench.toString().equalsIgnoreCase(enchant) || ench.toString().equalsIgnoreCase(enchant2)) {
					return ench;
				}
			}
		}
		return null;
	}

	public Object getEntityMeta(Entity entity, String str) {
		for (MetadataValue meta : entity.getMetadata(str)) {
			if (meta.getOwningPlugin().equals(plugin)) {
				return meta.value();
			}
		}
		return null;
	}

	public EntityType getEntityType(String entity, String entity2) {
		try {
			return EntityType.valueOf(entity);
		} catch (Exception e) {
			return EntityType.valueOf(entity2);
		}

	}

	/**
	 * Gets the hour from mili.
	 *
	 * @param time the time
	 * @return the hour from mili
	 */
	public int getHourFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
				.plusHours(AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet()).getHour();
	}

	/**
	 * Gets the minutes from mili.
	 *
	 * @param time the time
	 * @return the minutes from mili
	 */
	public int getMinutesFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
				.plusHours(AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet()).getMinute();
	}

	/**
	 * Gets the month from mili.
	 *
	 * @param time the time
	 * @return the month from mili
	 */
	public int getMonthFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
				.plusHours(AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet()).getMonthValue();
	}

	/**
	 * Gets the month string.
	 *
	 * @param month the month
	 * @return the month string
	 */
	public String getMonthString(int month) {
		return new DateFormatSymbols().getMonths()[month];
	}

	@SuppressWarnings("deprecation")
	public PotionEffectType getPotionType(String potion, String potion2) {
		try {
			return PotionEffectType.getByKey(NamespacedKey.minecraft(potion));
		} catch (Exception e) {
			return PotionEffectType.getByKey(NamespacedKey.minecraft(potion2));
		}

	}

	/*
	 * Gotten from:
	 * https://www.spigotmc.org/threads/progress-bars-and-percentages.276020/
	 */
	public String getProgressBar(int current, int max, int totalBars, String symbol, String completedColor,
			String notCompletedColor) {

		float percent = (float) current / max;

		int progressBars = (int) (totalBars * percent);

		int leftOver = (totalBars - progressBars);

		StringBuilder sb = new StringBuilder();
		sb.append(ChatColor.translateAlternateColorCodes('&', completedColor));
		for (int i = 0; i < progressBars; i++) {
			sb.append(symbol);
		}
		sb.append(ChatColor.translateAlternateColorCodes('&', notCompletedColor));
		for (int i = 0; i < leftOver; i++) {
			sb.append(symbol);
		}
		return sb.toString();
	}

	/**
	 * Gets the region blocks.
	 *
	 * @param world the world
	 * @param loc1  the loc 1
	 * @param loc2  the loc 2
	 * @return the region blocks
	 */
	public List<Block> getRegionBlocks(World world, Location loc1, Location loc2) {
		List<Block> blocks = new ArrayList<>();

		for (double x = loc1.getX(); x <= loc2.getX(); x++) {
			for (double y = loc1.getY(); y <= loc2.getY(); y++) {
				for (double z = loc1.getZ(); z <= loc2.getZ(); z++) {
					Location loc = new Location(world, x, y, z);
					blocks.add(loc.getBlock());
				}
			}
		}

		return blocks;
	}

	public LocalDateTime getTime(long mills) {
		return Instant.ofEpochMilli(mills).atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	public ArrayList<String> getWorldNames() {
		ArrayList<String> worlds = new ArrayList<>();
		for (World w : Bukkit.getWorlds()) {
			worlds.add(w.getName());
		}
		return worlds;
	}

	/**
	 * Gets the year from mili.
	 *
	 * @param time the time
	 * @return the year from mili
	 */
	public int getYearFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getYear();
	}

	private void runConsoleCommand(Player player, String command, int delay, boolean hasDelay) {
		if (hasDelay && delay > 0) {
			if (player != null) {
				plugin.getBukkitScheduler().runTaskLater(plugin, new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
					}
				}, delay, player.getLocation());
			} else {
				plugin.getBukkitScheduler().runTaskLater(plugin, new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
					}
				}, delay);
			}
		} else {
			if (player != null) {
				plugin.getBukkitScheduler().executeOrScheduleSync(plugin, new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
					}
				}, player.getLocation());
			} else {
				plugin.getBukkitScheduler().runTask(plugin, new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
					}
				});
			}
		}
	}

	public void setBlockMeta(Block block, String str, Object value) {
		block.removeMetadata(str, plugin);
		block.setMetadata(str, new MetadataValue() {

			@Override
			public boolean asBoolean() {

				return false;
			}

			@Override
			public byte asByte() {

				return 0;
			}

			@Override
			public double asDouble() {

				return 0;
			}

			@Override
			public float asFloat() {

				return 0;
			}

			@Override
			public int asInt() {

				return 0;
			}

			@Override
			public long asLong() {

				return 0;
			}

			@Override
			public short asShort() {

				return 0;
			}

			@Override
			public String asString() {

				return null;
			}

			@Override
			public Plugin getOwningPlugin() {
				return plugin;
			}

			@Override
			public void invalidate() {
			}

			@Override
			public Object value() {
				return value;
			}

		});
	}

	public void setEntityMeta(Entity entity, String str, Object value) {
		entity.removeMetadata(str, plugin);
		entity.setMetadata(str, new MetadataValue() {

			@Override
			public boolean asBoolean() {

				return false;
			}

			@Override
			public byte asByte() {

				return 0;
			}

			@Override
			public double asDouble() {

				return 0;
			}

			@Override
			public float asFloat() {

				return 0;
			}

			@Override
			public int asInt() {

				return 0;
			}

			@Override
			public long asLong() {

				return 0;
			}

			@Override
			public short asShort() {

				return 0;
			}

			@Override
			public String asString() {

				return null;
			}

			@Override
			public Plugin getOwningPlugin() {
				return plugin;
			}

			@Override
			public void invalidate() {
			}

			@Override
			public Object value() {
				return value;
			}

		});
	}

	public ItemStack setSkullOwner(OfflinePlayer player) {
		if ((player.hasPlayedBefore() || player.isOnline()) || Bukkit.getOnlineMode()) {
			return new ItemBuilder("PLAYER_HEAD").setSkullOwner(player).toItemStack(player);
		}
		return setSkullOwner(player.getName());
	}

	/**
	 * Sets the skull owner.
	 *
	 * @param playerName the player name
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack setSkullOwner(String playerName) {
		return new ItemBuilder("PLAYER_HEAD").setSkullOwner(playerName).toItemStack();
	}

	public LinkedHashMap<Double, String> sortByKeys(LinkedHashMap<Double, String> topVoterAllTime,
			final boolean order) {

		List<Entry<Double, String>> list = new LinkedList<>(topVoterAllTime.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<Double, String>>() {
			@Override
			public int compare(Entry<Double, String> o1, Entry<Double, String> o2) {
				if (order) {
					return o1.getKey().compareTo(o2.getKey());
				}
				return o2.getKey().compareTo(o1.getKey());
			}
		});

		// Maintaining insertion order with the help of LinkedList
		LinkedHashMap<Double, String> sortedMap = new LinkedHashMap<>();
		for (Entry<Double, String> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	/**
	 * Sort by values.
	 *
	 * @param unsortMap the unsort map
	 * @param order     the order
	 * @return the hash map
	 */
	public HashMap<AdvancedCoreUser, Integer> sortByValues(HashMap<AdvancedCoreUser, Integer> unsortMap,
			final boolean order) {

		List<Entry<AdvancedCoreUser, Integer>> list = new LinkedList<>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<AdvancedCoreUser, Integer>>() {
			@Override
			public int compare(Entry<AdvancedCoreUser, Integer> o1, Entry<AdvancedCoreUser, Integer> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				}
				return o2.getValue().compareTo(o1.getValue());
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<AdvancedCoreUser, Integer> sortedMap = new LinkedHashMap<>();
		for (Entry<AdvancedCoreUser, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	public HashMap<AdvancedCoreUser, Long> sortByValuesLong(HashMap<AdvancedCoreUser, Long> unsortMap,
			final boolean order) {

		List<Entry<AdvancedCoreUser, Long>> list = new LinkedList<>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<AdvancedCoreUser, Long>>() {
			@Override
			public int compare(Entry<AdvancedCoreUser, Long> o1, Entry<AdvancedCoreUser, Long> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				}
				return o2.getValue().compareTo(o1.getValue());
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<AdvancedCoreUser, Long> sortedMap = new LinkedHashMap<>();
		for (Entry<AdvancedCoreUser, Long> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}
}
