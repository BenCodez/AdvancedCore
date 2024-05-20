package com.bencodez.advancedcore.api.command;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;

import com.bencodez.simpleapi.command.TabCompleteHandler;

public class AdvancedCoreTabCompleteHandler {
	static AdvancedCoreTabCompleteHandler instance = new AdvancedCoreTabCompleteHandler();

	public static AdvancedCoreTabCompleteHandler getInstance() {
		return instance;
	}

	public ArrayList<String> getTabCompleteOptions(ArrayList<CommandHandler> handles, CommandSender sender,
			String[] args, int argNum) {
		ArrayList<String> tabComplete = new ArrayList<String>();
		ConcurrentHashMap<String, ArrayList<String>> options = TabCompleteHandler.getInstance().getTabCompleteOptions();
		for (CommandHandler h : handles) {
			tabComplete.addAll(h.getTabCompleteOptions(sender, args, argNum, options));
		}
		return tabComplete;
	}
}
