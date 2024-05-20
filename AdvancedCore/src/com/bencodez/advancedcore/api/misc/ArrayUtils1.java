package com.bencodez.advancedcore.api.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public class ArrayUtils1 {
	/** The instance. */
	static ArrayUtils1 instance = new ArrayUtils1();

	public static ArrayUtils1 getInstance() {
		return instance;
	}

	private ArrayUtils1() {
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

}
