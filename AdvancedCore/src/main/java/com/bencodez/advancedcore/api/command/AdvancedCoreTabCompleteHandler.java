package com.bencodez.advancedcore.api.command;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;

import com.bencodez.simpleapi.command.TabCompleteHandler;

/**
 * Handles tab completion for AdvancedCore commands.
 */
public class AdvancedCoreTabCompleteHandler {
	static AdvancedCoreTabCompleteHandler instance = new AdvancedCoreTabCompleteHandler();

	/**
	 * Gets the singleton instance.
	 * 
	 * @return the tab complete handler instance
	 */
	public static AdvancedCoreTabCompleteHandler getInstance() {
		return instance;
	}

	/**
	 * Gets tab complete options for a command.
	 * 
	 * @param handles the command handlers
	 * @param sender the command sender
	 * @param args the command arguments
	 * @param argNum the argument number
	 * @return list of tab complete options
	 */
	public ArrayList<String> getTabCompleteOptions(ArrayList<CommandHandler> handles, CommandSender sender,
			String[] args, int argNum) {
		ArrayList<String> tabComplete = new ArrayList<>();
		ConcurrentHashMap<String, ArrayList<String>> options = TabCompleteHandler.getInstance().getTabCompleteOptions();
		for (CommandHandler h : handles) {
			tabComplete.addAll(h.getTabCompleteOptions(sender, args, argNum, options));
		}
		return tabComplete;
	}
}
