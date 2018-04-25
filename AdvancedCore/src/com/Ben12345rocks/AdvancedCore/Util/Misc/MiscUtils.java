package com.Ben12345rocks.AdvancedCore.Util.Misc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

import net.md_5.bungee.api.ChatColor;

public class MiscUtils {
	/** The instance. */
	static MiscUtils instance = new MiscUtils();

	public static MiscUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private MiscUtils() {
	}

	/**
	 * Broadcast.
	 *
	 * @param broadcastMsg
	 *            the broadcast msg
	 */
	public void broadcast(String broadcastMsg) {
		if (broadcastMsg != null) {
			if (!broadcastMsg.equals("")) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					String msg = StringUtils.getInstance()
							.colorize(StringUtils.getInstance().replacePlaceHolders(player, broadcastMsg));
					for (String str : msg.split("%NewLine%")) {
						AdvancedCoreHook.getInstance().getServerHandle().sendMessage(player,
								StringUtils.getInstance().parseJson(str));
					}

				}
			}
		}
	}

	public boolean checkChance(double chance, double outOf) {
		if ((chance == 0) || (chance == outOf)) {
			return true;
		}

		double randomNum = ThreadLocalRandom.current().nextDouble(outOf);

		plugin.debug("Chance Required: " + chance + ", RandomNum: " + randomNum);

		if (randomNum <= chance) {
			return true;
		} else {
			return false;
		}
	}

	public void executeConsoleCommands(final Player player, final ArrayList<String> cmds,
			final HashMap<String, String> placeholders) {
		if (cmds != null && !cmds.isEmpty()) {
			placeholders.put("player", player.getName());
			final ArrayList<String> commands = ArrayUtils.getInstance().replaceJavascript(player,
					ArrayUtils.getInstance().replacePlaceHolder(cmds, placeholders));
			for (final String cmd : commands) {
				plugin.debug("Executing console command: " + cmd);
				Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
					}

				});
			}

		}
	}

	public void executeConsoleCommands(Player player, String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			final String cmd = StringUtils.getInstance().replaceJavascript(player,
					StringUtils.getInstance().replacePlaceHolder(command, placeholders));

			plugin.debug("Executing console command: " + command);
			Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

				@Override
				public void run() {
					Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
				}

			});
		}

	}

	@SuppressWarnings("deprecation")
	public void executeConsoleCommands(final String playerName, final ArrayList<String> cmds,
			final HashMap<String, String> placeholders) {
		if (cmds != null && !cmds.isEmpty()) {
			placeholders.put("player", playerName);
			final ArrayList<String> commands = ArrayUtils.getInstance().replaceJavascript(
					Bukkit.getOfflinePlayer(playerName),
					ArrayUtils.getInstance().replacePlaceHolder(cmds, placeholders));
			for (final String cmd : commands) {
				plugin.debug("Executing console command: " + cmd);
				Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

					@Override
					public void run() {
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
					}
				});
			}
		}
	}

	public void executeConsoleCommands(String playerName, String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			Player p = Bukkit.getPlayer(playerName);
			if (p != null) {
				command = StringUtils.getInstance().replaceJavascript(p, command);
			}
			if (command.startsWith("/")) {
				command.replaceFirst("/", "");
			}
			final String cmd = StringUtils.getInstance().replacePlaceHolder(command, placeholders);

			plugin.debug("Executing console command: " + command);
			Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

				@Override
				public void run() {
					Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
				}

			});
		}

	}

	/**
	 * Gets the connection.
	 *
	 * @param player
	 *            the player
	 * @return the connection
	 * @throws SecurityException
	 *             the security exception
	 * @throws NoSuchMethodException
	 *             the no such method exception
	 * @throws NoSuchFieldException
	 *             the no such field exception
	 * @throws IllegalArgumentException
	 *             the illegal argument exception
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
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
	 * @param time
	 *            the time
	 * @return the day from mili
	 */
	public int getDayFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getDayOfMonth();
	}

	/**
	 * Gets the hour from mili.
	 *
	 * @param time
	 *            the time
	 * @return the hour from mili
	 */
	public int getHourFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getHour();
	}

	/**
	 * Gets the minutes from mili.
	 *
	 * @param time
	 *            the time
	 * @return the minutes from mili
	 */
	public int getMinutesFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getMinute();
	}

	/**
	 * Gets the month from mili.
	 *
	 * @param time
	 *            the time
	 * @return the month from mili
	 */
	public int getMonthFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getMonthValue();
	}

	/**
	 * Gets the month string.
	 *
	 * @param month
	 *            the month
	 * @return the month string
	 */
	public String getMonthString(int month) {
		return new DateFormatSymbols().getMonths()[month];
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
	 * @param world
	 *            the world
	 * @param loc1
	 *            the loc 1
	 * @param loc2
	 *            the loc 2
	 * @return the region blocks
	 */
	public List<Block> getRegionBlocks(World world, Location loc1, Location loc2) {
		List<Block> blocks = new ArrayList<Block>();

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

	/**
	 * Gets the year from mili.
	 *
	 * @param time
	 *            the time
	 * @return the year from mili
	 */
	public int getYearFromMili(long time) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).getYear();
	}

	public ItemStack setSkullOwner(OfflinePlayer player) {
		if ((player.hasPlayedBefore() || player.isOnline()) || Bukkit.getOnlineMode()) {
			return new ItemBuilder(new ItemStack(Material.SKULL_ITEM, 1, (short) 3)).setSkullOwner(player)
					.toItemStack(player);
		} else {
			return setSkullOwner(player.getName());
		}
	}

	/**
	 * Sets the skull owner.
	 *
	 * @param playerName
	 *            the player name
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack setSkullOwner(String playerName) {
		return new ItemBuilder(new ItemStack(Material.SKULL_ITEM, 1, (short) 3)).setSkullOwner(playerName)
				.toItemStack();
	}
}
