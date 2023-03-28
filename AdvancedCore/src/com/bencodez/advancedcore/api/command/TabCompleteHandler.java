package com.bencodez.advancedcore.api.command;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.bukkit.command.CommandSender;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;

public class TabCompleteHandler {
	static TabCompleteHandler instance = new TabCompleteHandler();

	public static TabCompleteHandler getInstance() {
		return instance;
	}

	private AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	private ConcurrentHashMap<String, ArrayList<String>> tabCompleteOptions = new ConcurrentHashMap<String, ArrayList<String>>();

	private ArrayList<String> tabCompleteReplaces = new ArrayList<String>();

	private ConcurrentLinkedQueue<TabCompleteHandle> tabCompletes = new ConcurrentLinkedQueue<TabCompleteHandle>();

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

	public void loadTimer() {
		plugin.getTimer().scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				loadTabCompleteOptions();

			}
		}, 5, 10, TimeUnit.MINUTES);
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
