package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

public class TabCompleteHandler {
	static TabCompleteHandler instance = new TabCompleteHandler();

	public static TabCompleteHandler getInstance() {
		return instance;
	}

	private AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private ArrayList<TabCompleteHandle> tabCompletes = (ArrayList<TabCompleteHandle>) Collections.synchronizedList(new ArrayList<TabCompleteHandle>());

	public void addTabCompleteOption(TabCompleteHandle handle) {
		for (TabCompleteHandle h : tabCompletes) {
			if (h.getToReplace().equals(handle.getToReplace())) {
				plugin.debug("Tabcompletehandle not added, one already exists for " + handle.getToReplace());
				return;
			}
		}
		handle.reload();
		tabCompletes.add(handle);
		loadTabCompleteOptions();

		ArrayList<String> list = new ArrayList<String>();
		for (TabCompleteHandle h : tabCompletes) {
			list.add(h.getToReplace());
			h.updateReplacements();
		}
		tabCompleteReplaces.clear();
		tabCompleteReplaces.addAll(list);
	}

	public ArrayList<String> getTabCompleteReplaces() {
		return tabCompleteReplaces;
	}

	private ArrayList<String> tabCompleteReplaces = new ArrayList<String>();

	public void loadTabCompleteOptions() {
		for (TabCompleteHandle h : tabCompletes) {
			h.updateReplacements();
		}
		tabCompleteOptions.clear();
		for (TabCompleteHandle h : tabCompletes) {
			tabCompleteOptions.put(h.getToReplace(), h.getReplace());
		}
	}

	private ConcurrentHashMap<String, ArrayList<String>> tabCompleteOptions = new ConcurrentHashMap<String, ArrayList<String>>();

	public ConcurrentHashMap<String, ArrayList<String>> getTabCompleteOptions() {
		loadTabCompleteOptions();
		return tabCompleteOptions;
	}

	public void addTabCompleteOption(String toReplace, ArrayList<String> options) {
		addTabCompleteOption(new TabCompleteHandle(toReplace, options) {

			@Override
			public void updateReplacements() {
			}

			@Override
			public void reload() {
			}
		});
	}

	public void addTabCompleteOption(String toReplace, String... options) {
		addTabCompleteOption(toReplace, ArrayUtils.getInstance().convert(options));
	}

	public void reload() {
		for (TabCompleteHandle h : tabCompletes) {
			h.reload();
		}
	}
}
