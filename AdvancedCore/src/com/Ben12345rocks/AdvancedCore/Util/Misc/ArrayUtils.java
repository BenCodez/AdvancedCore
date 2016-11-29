package com.Ben12345rocks.AdvancedCore.Util.Misc;

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

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.User;

import net.md_5.bungee.api.chat.TextComponent;

public class ArrayUtils {
	/** The instance. */
	static ArrayUtils instance = new ArrayUtils();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	public static ArrayUtils getInstance() {
		return instance;
	}

	private ArrayUtils() {
	}

	/**
	 * Convert.
	 *
	 * @param array
	 *            the array
	 * @return the user[]
	 */
	public User[] convertUsers(ArrayList<User> array) {
		if (array == null) {
			return null;
		}
		User[] list = new User[array.size()];
		for (int i = 0; i < array.size(); i++) {
			list[i] = array.get(i);
		}
		return list;
	}

	/**
	 * Convert.
	 *
	 * @param set
	 *            the set
	 * @return the array list
	 */
	public ArrayList<String> convert(Set<String> set) {
		ArrayList<String> list = new ArrayList<String>();
		for (String st : set) {
			list.add(st);
		}
		return list;
	}

	/**
	 * Convert array.
	 *
	 * @param list
	 *            the list
	 * @return the string[]
	 */
	@SuppressWarnings("unused")
	public String[] convert(ArrayList<String> list) {
		if (list == null) {
			return null;
		}
		String[] string = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			string[i] = list.get(i);
		}
		if (string == null) {
			return null;
		} else {
			return string;
		}
	}

	/**
	 * Convert array.
	 *
	 * @param list
	 *            the list
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

	/**
	 * Convert set.
	 *
	 * @param set
	 *            the set
	 * @return the array list
	 */
	public ArrayList<User> convertUsers(Set<User> set) {
		if (set == null) {
			return null;
		}

		ArrayList<User> list = new ArrayList<User>();
		for (User user : set) {
			list.add(user);
		}
		return list;
	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the string[]
	 */
	public String[] colorize(String[] list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.length; i++) {
			list[i] = StringUtils.getInstance().colorize(list[i]);
		}
		return list;
	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the array list
	 */
	public ArrayList<String> colorize(ArrayList<String> list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.size(); i++) {
			list.set(i, StringUtils.getInstance().colorize(list.get(i)));
		}
		return list;
	}

	/**
	 * Colorize.
	 *
	 * @param list
	 *            the list
	 * @return the list
	 */
	public List<String> colorize(List<String> list) {
		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.size(); i++) {
			list.set(i, StringUtils.getInstance().colorize(list.get(i)));
		}
		return list;
	}

	/**
	 * Compto string.
	 *
	 * @param comps
	 *            the comps
	 * @return the array list
	 */
	public ArrayList<String> comptoString(ArrayList<TextComponent> comps) {
		ArrayList<String> txt = new ArrayList<String>();
		for (TextComponent comp : comps) {
			txt.add(StringUtils.getInstance().compToString(comp));
		}
		return txt;
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
	 * @param list
	 *            the list
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

	/**
	 * Removes the duplicates.
	 *
	 * @param list
	 *            the list
	 * @return the array list
	 */
	public ArrayList<String> removeDuplicates(ArrayList<String> list) {
		Set<String> set = new HashSet<String>(list);
		return new ArrayList<String>(set);
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
	 * @param list
	 *            the list
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the array list
	 */
	public ArrayList<String> replaceIgnoreCase(ArrayList<String> list, String toReplace, String replaceWith) {
		ArrayList<String> newList = new ArrayList<String>();
		for (String msg : list) {
			newList.add(StringUtils.getInstance().replaceIgnoreCase(msg, toReplace, replaceWith));
		}
		return newList;
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
	 * Sets the to array.
	 *
	 * @param set
	 *            the set
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
	 * Sort by values.
	 *
	 * @param unsortMap
	 *            the unsort map
	 * @param order
	 *            the order
	 * @return the hash map
	 */
	public HashMap<User, Integer> sortByValues(HashMap<User, Integer> unsortMap, final boolean order) {

		List<Entry<User, Integer>> list = new LinkedList<Entry<User, Integer>>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<User, Integer>>() {
			@Override
			public int compare(Entry<User, Integer> o1, Entry<User, Integer> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				} else {
					return o2.getValue().compareTo(o1.getValue());

				}
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<User, Integer> sortedMap = new LinkedHashMap<User, Integer>();
		for (Entry<User, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}
}
