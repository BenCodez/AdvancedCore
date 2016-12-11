package com.Ben12345rocks.AdvancedCore.Util.Misc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

public class MiscUtils {
	/** The instance. */
	static MiscUtils instance = new MiscUtils();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	public static MiscUtils getInstance() {
		return instance;
	}

	private MiscUtils() {
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

	/**
	 * Broadcast.
	 *
	 * @param broadcastMsg
	 *            the broadcast msg
	 */
	public void broadcast(String broadcastMsg) {
		if (broadcastMsg != null) {
			if (!broadcastMsg.equals("")) {
				Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

					@Override
					public void run() {
						Bukkit.broadcastMessage(StringUtils.getInstance().colorize(broadcastMsg));
					}
				});
			}
		}

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
	 * Sets the skull owner.
	 *
	 * @param playerName
	 *            the player name
	 * @return the item stack
	 */
	public ItemStack setSkullOwner(String playerName) {
		return new ItemBuilder(new ItemStack(Material.SKULL_ITEM, 1, (short) 3)).setSkullOwner(playerName)
				.toItemStack();
	}

}
