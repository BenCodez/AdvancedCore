package com.bencodez.advancedcore.api.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class ArrayUtils {
	/** The instance. */
	static ArrayUtils instance = new ArrayUtils();

	public static ArrayUtils getInstance() {
		return instance;
	}

	private ArrayUtils() {
	}

	/**
	 * Colorize.
	 *
	 * @param list the list
	 * @return the array list
	 */
	public ArrayList<String> colorize(ArrayList<String> list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.size(); i++) {
			list.set(i, StringParser.getInstance().colorize(list.get(i)));
		}
		return list;
	}

	/**
	 * Colorize.
	 *
	 * @param list the list
	 * @return the list
	 */
	public List<String> colorize(List<String> list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.size(); i++) {
			list.set(i, StringParser.getInstance().colorize(list.get(i)));
		}
		return list;
	}

	/**
	 * Colorize.
	 *
	 * @param list the list
	 * @return the string[]
	 */
	public String[] colorize(String[] list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.length; i++) {
			list[i] = StringParser.getInstance().colorize(list[i]);
		}
		return list;
	}

	/**
	 * Compto string.
	 *
	 * @param comps the comps
	 * @return the array list
	 */
	public ArrayList<String> comptoString(ArrayList<TextComponent> comps) {
		ArrayList<String> txt = new ArrayList<String>();
		for (TextComponent comp : comps) {
			txt.add(StringParser.getInstance().compToString(comp));
		}
		return txt;
	}

	public boolean containsIgnoreCase(ArrayList<String> set, String str) {
		str = str.toLowerCase();
		for (String text : set) {
			text = text.toLowerCase();
			if (text.equals(str)) {
				return true;
			}
		}
		return false;
	}

	public boolean containsIgnoreCase(List<String> set, String str) {
		str = str.toLowerCase();
		for (String text : set) {
			text = text.toLowerCase();
			if (text.equals(str)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the contains ignore case.
	 *
	 * @param set the set
	 * @param str the str
	 * @return true, if successful
	 */
	public boolean containsIgnoreCase(Set<String> set, String str) {
		str = str.toLowerCase();
		for (String text : set) {
			text = text.toLowerCase();
			if (text.equals(str)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Convert array.
	 *
	 * @param list the list
	 * @return the string[]
	 */
	public String[] convert(ArrayList<String> list) {
		if (list == null) {
			return null;
		}
		String[] string = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			string[i] = list.get(i);
		}
		return string;

	}

	/**
	 * Convert.
	 *
	 * @param set1 the set
	 * @return the array list
	 */
	public ArrayList<String> convert(Set<String> set1) {
		Set<String> set = new HashSet<String>(set1);
		ArrayList<String> list = new ArrayList<String>();
		for (String st : set) {
			list.add(st);
		}
		return list;
	}

	/**
	 * Convert array.
	 *
	 * @param list the list
	 * @return the array list
	 */
	@SuppressWarnings("unused")
	public ArrayList<String> convert(String[] list) {
		if (list == null) {
			return null;
		}
		ArrayList<String> newlist = new ArrayList<String>();
		for (String element : list) {
			newlist.add(element);
		}
		if (newlist == null) {
			return null;
		} else {
			return newlist;
		}
	}

	public BaseComponent[] convertBaseComponent(ArrayList<BaseComponent> list) {
		if (list == null) {
			return null;
		}
		BaseComponent[] string = new BaseComponent[list.size()];
		for (int i = 0; i < list.size(); i++) {
			string[i] = list.get(i);
		}
		return string;
	}

	public ArrayList<BaseComponent> convertBaseComponent(BaseComponent[] list) {
		if (list == null) {
			return null;
		}
		ArrayList<BaseComponent> newlist = new ArrayList<BaseComponent>();
		for (BaseComponent element : list) {
			newlist.add(element);
		}
		return newlist;
	}

	/**
	 * Sets the to array.
	 *
	 * @param set the set
	 * @return the string[]
	 */
	@SuppressWarnings("unused")
	public String[] convertSet(Set<String> set) {
		String[] array = new String[set.size()];
		int i = 0;
		for (String item : set) {
			array[i] = item;
			i++;
		}
		if (array == null) {
			return null;
		} else {
			return array;
		}
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

		ArrayList<AdvancedCoreUser> list = new ArrayList<AdvancedCoreUser>();
		for (AdvancedCoreUser user : set) {
			list.add(user);
		}
		return list;
	}

	public HashMap<String, String> fromString(String str) {
		HashMap<String, String> map = new HashMap<String, String>();
		if (!str.equals("")) {
			for (String entry : str.split("%entry%")) {
				String[] values = entry.split("%pair%");
				if (values.length > 1) {
					String key = values[0];
					String value = values[1];
					map.put(key, value);
				}
			}
		}
		return map;
	}

	public String makeString(HashMap<String, String> placeholders) {
		String str = "";
		int count = 0;
		if (placeholders != null && !placeholders.isEmpty()) {
			for (Entry<String, String> entry : placeholders.entrySet()) {
				str += entry.getKey() + "%pair%" + entry.getValue();
				count++;
				if (count != placeholders.size()) {
					str += "%entry%";
				}
			}
		}
		return str;
	}

	/**
	 * Make string.
	 *
	 * @param startIndex the start index
	 * @param strs       the strs
	 * @return the string
	 */
	public String makeString(int startIndex, String[] strs) {
		String str = new String();
		for (int i = startIndex; i < strs.length; i++) {
			if (i == startIndex) {
				str += strs[i];
			} else {
				str += " " + strs[i];
			}

		}
		return str;
	}

	/**
	 * Make string list.
	 *
	 * @param list the list
	 * @return the string
	 */
	public String makeStringList(ArrayList<String> list) {
		if (list == null) {
			return "";
		}
		String string = new String();
		if (list.size() > 1) {
			for (int i = 0; i < list.size(); i++) {
				if (i == 0) {
					string += list.get(i);
				} else {
					string += ", " + list.get(i);
				}
			}
		} else if (list.size() == 1) {
			string = list.get(0);
		}
		return string;
	}

	public String pickRandom(ArrayList<String> list) {
		if (list != null) {
			return list.get(ThreadLocalRandom.current().nextInt(list.size()));
		}
		return "";
	}

	/**
	 * Removes the duplicates.
	 *
	 * @param list the list
	 * @return the array list
	 */
	public ArrayList<String> removeDuplicates(ArrayList<String> list) {
		Set<String> set = new HashSet<String>();
		set.addAll(list);
		list.clear();
		list.addAll(set);
		return list;
	}

	/**
	 * Replace.
	 *
	 * @param list        the list
	 * @param toReplace   the to replace
	 * @param replaceWith the replace with
	 * @return the list
	 */
	public List<String> replace(List<String> list, String toReplace, String replaceWith) {
		if (list == null) {
			return null;
		}
		if (replaceWith == null || toReplace == null) {
			return list;
		}
		for (int i = 0; i < list.size(); i++) {
			list.set(i, list.get(i).replace(toReplace, replaceWith));
		}
		return list;
	}

	/**
	 * Replace ignore case.
	 *
	 * @param list        the list
	 * @param toReplace   the to replace
	 * @param replaceWith the replace with
	 * @return the array list
	 */
	public ArrayList<String> replaceIgnoreCase(ArrayList<String> list, String toReplace, String replaceWith) {
		ArrayList<String> newList = new ArrayList<String>();
		for (String msg : list) {
			newList.add(StringParser.getInstance().replaceIgnoreCase(msg, toReplace, replaceWith));
		}
		return newList;
	}

	public ArrayList<String> replaceJavascript(AdvancedCoreUser user, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<String>();
		for (String str : list) {
			msg.add(StringParser.getInstance().replaceJavascript(user, str));
		}
		return msg;
	}

	public ArrayList<String> replaceJavascript(ArrayList<String> list) {
		return replaceJavascript(list, null);
	}

	public ArrayList<String> replaceJavascript(ArrayList<String> list, JavascriptEngine engine) {
		ArrayList<String> msg = new ArrayList<String>();
		for (String str : list) {
			msg.add(StringParser.getInstance().replaceJavascript(str, engine));
		}
		return msg;
	}

	public ArrayList<String> replaceJavascript(CommandSender sender, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<String>();
		for (String str : list) {
			msg.add(StringParser.getInstance().replaceJavascript(sender, str));
		}
		return msg;
	}

	public ArrayList<String> replaceJavascript(OfflinePlayer player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<String>();
		for (String str : list) {
			msg.add(StringParser.getInstance().replaceJavascript(player, str));
		}
		return msg;
	}

	public ArrayList<String> replaceJavascript(Player player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<String>();
		for (String str : list) {
			msg.add(StringParser.getInstance().replaceJavascript(player, str));
		}
		return msg;
	}

	public ArrayList<String> replacePlaceHolder(ArrayList<String> list, HashMap<String, String> placeholders) {
		ArrayList<String> newList = new ArrayList<String>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(StringParser.getInstance().replacePlaceHolder(list.get(i), placeholders));
		}
		return newList;
	}

	public ArrayList<String> replacePlaceHolders(ArrayList<String> list, Player p) {
		ArrayList<String> newList = new ArrayList<String>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(StringParser.getInstance().replacePlaceHolders(p, list.get(i)));
		}
		return newList;
	}

	public ArrayList<String> sort(ArrayList<String> list) {
		Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
		return list;
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

		List<Entry<AdvancedCoreUser, Integer>> list = new LinkedList<Entry<AdvancedCoreUser, Integer>>(
				unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<AdvancedCoreUser, Integer>>() {
			@Override
			public int compare(Entry<AdvancedCoreUser, Integer> o1, Entry<AdvancedCoreUser, Integer> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				} else {
					return o2.getValue().compareTo(o1.getValue());

				}
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<AdvancedCoreUser, Integer> sortedMap = new LinkedHashMap<AdvancedCoreUser, Integer>();
		for (Entry<AdvancedCoreUser, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	public HashMap<AdvancedCoreUser, Long> sortByValuesLong(HashMap<AdvancedCoreUser, Long> unsortMap,
			final boolean order) {

		List<Entry<AdvancedCoreUser, Long>> list = new LinkedList<Entry<AdvancedCoreUser, Long>>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<AdvancedCoreUser, Long>>() {
			@Override
			public int compare(Entry<AdvancedCoreUser, Long> o1, Entry<AdvancedCoreUser, Long> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				} else {
					return o2.getValue().compareTo(o1.getValue());

				}
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<AdvancedCoreUser, Long> sortedMap = new LinkedHashMap<AdvancedCoreUser, Long>();
		for (Entry<AdvancedCoreUser, Long> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	public HashMap<String, Integer> sortByValuesStr(HashMap<String, Integer> unsortMap, final boolean order) {

		List<Entry<String, Integer>> list = new LinkedList<Entry<String, Integer>>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				} else {
					return o2.getValue().compareTo(o1.getValue());

				}
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
		for (Entry<String, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	public LinkedHashMap<String, ItemStack> sortByValuesStrItem(HashMap<String, ItemStack> unsortMap) {

		ArrayList<String> sortedKeys = sort(new ArrayList<String>(unsortMap.keySet()));

		// Maintaining insertion order with the help of LinkedList
		LinkedHashMap<String, ItemStack> sortedMap = new LinkedHashMap<String, ItemStack>();
		for (String key : sortedKeys) {
			sortedMap.put(key, unsortMap.get(key));
		}

		return sortedMap;
	}
}
