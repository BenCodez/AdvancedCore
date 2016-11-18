package com.Ben12345rocks.AdvancedCore;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.Effects.FireworkHandler;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.MiscUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import net.md_5.bungee.api.chat.TextComponent;

// TODO: Auto-generated Javadoc
/**
 * The Class Utils.
 */
public class Utils {

	/** The instance. */
	static Utils instance = new Utils();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Utils.
	 *
	 * @return single instance of Utils
	 */
	public static Utils getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new utils.
	 */
	private Utils() {
	}

	/**
	 * Instantiates a new utils.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Utils(Main plugin) {
		Utils.plugin = plugin;
	}

	/**
	 * Adds the enchants.
	 *
	 * @param item
	 *            the item
	 * @param enchants
	 *            the enchants
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack addEnchants(ItemStack item, HashMap<String, Integer> enchants) {
		if ((enchants == null) || (enchants.size() == 0)) {
			return item;
		}
		ItemMeta meta = item.getItemMeta();
		for (String enchant : enchants.keySet()) {
			meta.addEnchant(Enchantment.getByName(enchant), enchants.get(enchant), false);
		}
		item.setItemMeta(meta);
		return item;

	}

	/**
	 * Adds the lore.
	 *
	 * @param item
	 *            the item
	 * @param lore
	 *            the lore
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack addLore(ItemStack item, ArrayList<String> lore) {
		if (lore == null) {
			return item;
		}
		if (item == null) {
			return null;
		}

		ItemMeta meta = item.getItemMeta();
		meta.setLore(colorize(lore));
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Adds the lore.
	 *
	 * @param item
	 *            the item
	 * @param lore
	 *            the lore
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack addLore(ItemStack item, List<String> lore) {
		if (lore == null) {
			return item;
		}
		if (item == null) {
			return null;
		}

		ItemMeta meta = item.getItemMeta();
		meta.setLore(colorize(lore));
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Broadcast.
	 *
	 * @param broadcastMsg
	 *            the broadcast msg
	 */
	@Deprecated
	public void broadcast(String broadcastMsg) {
		MiscUtils.getInstance().broadcast(broadcastMsg);;

	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> colorize(ArrayList<String> list) {
		return ArrayUtils.getInstance().colorize(list);
	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the list
	 */
	@Deprecated
	public List<String> colorize(List<String> list) {
		return ArrayUtils.getInstance().colorize(list);
	}

	/**
	 * Colorize.
	 *
	 * @param format
	 *            the format
	 * @return the string
	 */
	@Deprecated
	public String colorize(String format) {
		return StringUtils.getInstance().colorize(format);
	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the string[]
	 */
	@Deprecated
	public String[] colorize(String[] list) {
		return ArrayUtils.getInstance().colorize(list);
	}

	/**
	 * Compto string.
	 *
	 * @param comps
	 *            the comps
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> comptoString(ArrayList<TextComponent> comps) {
		return ArrayUtils.getInstance().comptoString(comps);
	}

	/**
	 * Comp to string.
	 *
	 * @param comp
	 *            the comp
	 * @return the string
	 */
	@Deprecated
	public String compToString(TextComponent comp) {
		return StringUtils.getInstance().compToString(comp);
	}

	/**
	 * Convert.
	 *
	 * @param array
	 *            the array
	 * @return the user[]
	 */
	@Deprecated
	public User[] convert(ArrayList<User> array) {
		return ArrayUtils.getInstance().convertUsers(array);
	}

	/**
	 * Convert.
	 *
	 * @param set
	 *            the set
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> convert(Set<String> set) {
		return ArrayUtils.getInstance().convert(set);
	}

	/**
	 * Convert array.
	 *
	 * @param list
	 *            the list
	 * @return the string[]
	 */
	@Deprecated
	public String[] convertArray(ArrayList<String> list) {
		return ArrayUtils.getInstance().convert(list);
	}

	/**
	 * Convert array.
	 *
	 * @param list
	 *            the list
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> convertArray(String[] list) {
		return ArrayUtils.getInstance().convert(list);
	}

	/**
	 * Convert set.
	 *
	 * @param set
	 *            the set
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<User> convertSet(Set<User> set) {
		return ArrayUtils.getInstance().convertUsers(set);
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
	@Deprecated
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
	@Deprecated
	public int getDayFromMili(long time) {
		Date date = new Date(time);
		return date.getDate();
	}

	/**
	 * Gets the hour from mili.
	 *
	 * @param time
	 *            the time
	 * @return the hour from mili
	 */
	@Deprecated
	public int getHourFromMili(long time) {
		Date date = new Date(time);
		return date.getHours();
	}

	/**
	 * Gets the minutes from mili.
	 *
	 * @param time
	 *            the time
	 * @return the minutes from mili
	 */
	@Deprecated
	public int getMinutesFromMili(long time) {
		Date date = new Date(time);
		return date.getMinutes();
	}

	/**
	 * Gets the month from mili.
	 *
	 * @param time
	 *            the time
	 * @return the month from mili
	 */
	@Deprecated
	public int getMonthFromMili(long time) {
		Date date = new Date(time);
		return date.getMonth();
	}

	/**
	 * Gets the month string.
	 *
	 * @param month
	 *            the month
	 * @return the month string
	 */
	@Deprecated
	public String getMonthString(int month) {
		return new DateFormatSymbols().getMonths()[month];
	}

	/**
	 * Gets the NMS class.
	 *
	 * @param nmsClassString
	 *            the nms class string
	 * @return the NMS class
	 * @throws ClassNotFoundException
	 *             the class not found exception
	 */
	@Deprecated
	public Class<?> getNMSClass(String nmsClassString) throws ClassNotFoundException {
		String version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3] + ".";
		String name = "net.minecraft.server." + version + nmsClassString;
		Class<?> nmsClass = Class.forName(name);
		return nmsClass;
	}

	/**
	 * Gets the player meta.
	 *
	 * @param player
	 *            the player
	 * @param str
	 *            the str
	 * @return the player meta
	 */
	@Deprecated
	public Object getPlayerMeta(Player player, String str) {
		return PlayerUtils.getInstance().getPlayerMeta(player, str);
	}

	/**
	 * Gets the player name.
	 *
	 * @param uuid
	 *            the uuid
	 * @return the player name
	 */
	@Deprecated
	public String getPlayerName(String uuid) {
		return PlayerUtils.getInstance().getPlayerName(uuid);

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
	@Deprecated
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
	 * Gets the uuid.
	 *
	 * @param playerName
	 *            the player name
	 * @return the uuid
	 */
	@Deprecated
	public String getUUID(String playerName) {
		return PlayerUtils.getInstance().getUUID(playerName);
	}

	/**
	 * Gets the year from mili.
	 *
	 * @param time
	 *            the time
	 * @return the year from mili
	 */
	@Deprecated
	public int getYearFromMili(long time) {
		Date date = new Date(time);
		return date.getYear();
	}

	/**
	 * Checks for permission.
	 *
	 * @param sender
	 *            the sender
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	@Deprecated
	public boolean hasPermission(CommandSender sender, String perm) {
		return PlayerUtils.getInstance().hasPermission(sender, perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param player
	 *            the player
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	@Deprecated
	public boolean hasPermission(Player player, String perm) {
		return PlayerUtils.getInstance().hasPermission(player, perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param playerName
	 *            the player name
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	@Deprecated
	public boolean hasPermission(String playerName, String perm) {
		return PlayerUtils.getInstance().hasPermission(playerName, perm);
	}

	/**
	 * Checks for server permission.
	 *
	 * @param playerName
	 *            the player name
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	@Deprecated
	public boolean hasServerPermission(String playerName, String perm) {
		return PlayerUtils.getInstance().hasServerPermission(playerName, perm);
	}

	/**
	 * Checks if is int.
	 *
	 * @param st
	 *            the st
	 * @return true, if is int
	 */
	@Deprecated
	public boolean isInt(String st) {
		return StringUtils.getInstance().isInt(st);
	}

	/**
	 * Checks if is player.
	 *
	 * @param sender
	 *            the sender
	 * @return true, if is player
	 */
	@Deprecated
	public boolean isPlayer(CommandSender sender) {
		return PlayerUtils.getInstance().isPlayer(sender);
	}

	/**
	 * Checks if is player online.
	 *
	 * @param playerName
	 *            the player name
	 * @return true, if is player online
	 */
	@Deprecated
	public boolean isPlayerOnline(String playerName) {
		return PlayerUtils.getInstance().isPlayerOnline(playerName);
	}

	/**
	 * Launch firework.
	 *
	 * @param loc
	 *            the loc
	 * @param power
	 *            the power
	 * @param colors
	 *            the colors
	 * @param fadeOutColor
	 *            the fade out color
	 * @param trail
	 *            the trail
	 * @param flicker
	 *            the flicker
	 * @param types
	 *            the types
	 */
	@Deprecated
	public void launchFirework(Location loc, int power, ArrayList<String> colors, ArrayList<String> fadeOutColor,
			boolean trail, boolean flicker, ArrayList<String> types) {
		FireworkHandler.getInstance().launchFirework(loc, power, colors, fadeOutColor, trail, flicker, types);
	}

	/**
	 * Make string.
	 *
	 * @param startIndex
	 *            the start index
	 * @param strs
	 *            the strs
	 * @return the string
	 */
	@Deprecated
	public String makeString(int startIndex, String[] strs) {
		return ArrayUtils.getInstance().makeString(startIndex, strs);
	}

	/**
	 * Make string list.
	 *
	 * @param list
	 *            the list
	 * @return the string
	 */
	@Deprecated
	public String makeStringList(ArrayList<String> list) {
		return ArrayUtils.getInstance().makeStringList(list);
	}

	/**
	 * Name item.
	 *
	 * @param item
	 *            the item
	 * @param name
	 *            the name
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack nameItem(ItemStack item, String name) {
		return setName(item, name);

	}

	/**
	 * Prints the map.
	 *
	 * @param map
	 *            the map
	 */
	@Deprecated
	public void printMap(HashMap<? extends User, Integer> map) {
		for (Entry<? extends User, Integer> entry : map.entrySet()) {
			plugin.debug("Key : " + entry.getKey().getPlayerName() + " Value : " + entry.getValue());
		}
	}

	/**
	 * Removes the duplicates.
	 *
	 * @param list
	 *            the list
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> removeDuplicates(ArrayList<String> list) {
		return ArrayUtils.getInstance().removeDuplicates(list);
	}

	/**
	 * Replace.
	 *
	 * @param list
	 *            the list
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the list
	 */
	@Deprecated
	public List<String> replace(List<String> list, String toReplace, String replaceWith) {
		return ArrayUtils.getInstance().replace(list, toReplace, replaceWith);
	}

	/**
	 * Replace ignore case.
	 *
	 * @param list
	 *            the list
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the array list
	 */
	@Deprecated
	public ArrayList<String> replaceIgnoreCase(ArrayList<String> list, String toReplace, String replaceWith) {
		return ArrayUtils.getInstance().replaceIgnoreCase(list, toReplace, replaceWith);
	}

	/**
	 * Replace ignore case.
	 *
	 * @param str
	 *            the str
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the string
	 */
	@Deprecated
	public String replaceIgnoreCase(String str, String toReplace, String replaceWith) {
		return StringUtils.getInstance().replaceIgnoreCase(str, toReplace, replaceWith);
	}

	/**
	 * Replace place holder.
	 *
	 * @param str
	 *            the str
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the string
	 */
	@Deprecated
	public String replacePlaceHolder(String str, String toReplace, String replaceWith) {
		return StringUtils.getInstance().replacePlaceHolder(str, toReplace, replaceWith);
	}

	/**
	 * Replace place holders.
	 *
	 * @param player
	 *            the player
	 * @param text
	 *            the text
	 * @return the string
	 */
	@Deprecated
	public String replacePlaceHolders(Player player, String text) {
		return StringUtils.getInstance().replacePlaceHolders(player, text);
	}

	/**
	 * Round decimals.
	 *
	 * @param num
	 *            the num
	 * @param decimals
	 *            the decimals
	 * @return the string
	 */
	@Deprecated
	public String roundDecimals(double num, int decimals) {
		return StringUtils.getInstance().roundDecimals(num, decimals);
	}

	/**
	 * Sets the contains ignore case.
	 *
	 * @param set
	 *            the set
	 * @param str
	 *            the str
	 * @return true, if successful
	 */
	@Deprecated
	public boolean setContainsIgnoreCase(Set<String> set, String str) {
		return ArrayUtils.getInstance().containsIgnoreCase(set, str);
	}

	/**
	 * Sets the durabilty.
	 *
	 * @param item
	 *            the item
	 * @param durability
	 *            the durability
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack setDurabilty(ItemStack item, int durability) {
		if (item == null) {
			return null;
		}
		if (durability > 0) {
			item.setDurability((short) durability);
		}
		return item;
	}

	/**
	 * Sets the name.
	 *
	 * @param item
	 *            the item
	 * @param name
	 *            the name
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack setName(ItemStack item, String name) {
		if (name == null) {
			return item;
		}
		if (item == null) {
			return null;
		}

		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(colorize(name));
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Sets the player meta.
	 *
	 * @param player
	 *            the player
	 * @param str
	 *            the str
	 * @param value
	 *            the value
	 */
	@Deprecated
	public void setPlayerMeta(Player player, String str, Object value) {
		PlayerUtils.getInstance().setPlayerMeta(player, str, value);
	}

	/**
	 * Sets the skull owner.
	 *
	 * @param item
	 *            the item
	 * @param playerName
	 *            the player name
	 * @return the item stack
	 */
	@Deprecated
	public ItemStack setSkullOwner(ItemStack item, String playerName) {
		if (item == null) {
			return null;
		}
		if (playerName == null || playerName.equalsIgnoreCase("")) {
			return item;
		}
		ItemMeta meta = item.getItemMeta();
		try {
			((SkullMeta) meta).setOwner(playerName);
		} catch (Exception ex) {
		}
		return item;
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
		return setSkullOwner(new ItemStack(Material.SKULL_ITEM, 1, (short) 3), playerName);
	}

	/**
	 * Sets the to array.
	 *
	 * @param set
	 *            the set
	 * @return the string[]
	 */
	@Deprecated
	public String[] setToArray(Set<String> set) {
		return ArrayUtils.getInstance().convertSet(set);
	}

	/**
	 * Sort by values.
	 *
	 * @param unsortMap
	 *            the unsort map
	 * @param order
	 *            the order
	 * @return the hash map
	 */
	@Deprecated
	public HashMap<User, Integer> sortByValues(HashMap<User, Integer> unsortMap, final boolean order) {
		return ArrayUtils.getInstance().sortByValues(unsortMap, order);
	}

	/**
	 * Starts with ignore case.
	 *
	 * @param str1
	 *            the str 1
	 * @param str2
	 *            the str 2
	 * @return true, if successful
	 */
	@Deprecated
	public boolean startsWithIgnoreCase(String str1, String str2) {
		return StringUtils.getInstance().startsWithIgnoreCase(str1, str2);
	}

	/**
	 * String to comp.
	 *
	 * @param string
	 *            the string
	 * @return the text component
	 */
	@Deprecated
	public TextComponent stringToComp(String string) {
		return StringUtils.getInstance().stringToComp(string);
	}
}
