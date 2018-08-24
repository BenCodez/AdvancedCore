package com.Ben12345rocks.AdvancedCore.CommandAPI;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.command.CommandSender;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

public class TabCompleteHandler {
	static TabCompleteHandler instance = new TabCompleteHandler();

	public static TabCompleteHandler getInstance() {
		return instance;
	}

	private AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private ConcurrentLinkedQueue<TabCompleteHandle> tabCompletes = new ConcurrentLinkedQueue<TabCompleteHandle>();

	private ArrayList<String> tabCompleteReplaces = new ArrayList<String>();

	private ConcurrentHashMap<String, ArrayList<String>> tabCompleteOptions = new ConcurrentHashMap<String, ArrayList<String>>();

	public void addTabCompleteOption(String toReplace, ArrayList<String> options) {
		addTabCompleteOption(new TabCompleteHandle(toReplace, options) {

			@Override
			public void reload() {
			}

			@Override
			public void updateReplacements() {
			}
		});
	}

	public void addTabCompleteOption(String toReplace, String... options) {
		addTabCompleteOption(toReplace, ArrayUtils.getInstance().convert(options));
	}

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

	public ConcurrentHashMap<String, ArrayList<String>> getTabCompleteOptions() {
		loadTabCompleteOptions();
		return tabCompleteOptions;
	}

	public ArrayList<String> getTabCompleteOptions(ArrayList<CommandHandler> handles, CommandSender sender,
			String[] args, int argNum) {
		ArrayList<String> tabComplete = new ArrayList<String>();
		ConcurrentHashMap<String, ArrayList<String>> options = getTabCompleteOptions();
		for (CommandHandler h : handles) {
			tabComplete.addAll(h.getTabCompleteOptions(sender, args, argNum, options));
		}
		return tabComplete;
	}

	public ArrayList<String> getTabCompleteReplaces() {
		return tabCompleteReplaces;
	}

	public void loadTabCompleteOptions() {
		for (TabCompleteHandle h : tabCompletes) {
			h.updateReplacements();
		}
		tabCompleteOptions.clear();
		for (TabCompleteHandle h : tabCompletes) {
			tabCompleteOptions.put(h.getToReplace(), h.getReplace());
		}
	}

	public void reload() {
		for (TabCompleteHandle h : tabCompletes) {
			h.reload();
		}
	}
}
