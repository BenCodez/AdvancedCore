package com.Ben12345rocks.AdvancedCore.Commands.Executor;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandVote.
 */
public class CommandAdvancedCore implements CommandExecutor {

	/** The instance. */
	private static CommandAdvancedCore instance = new CommandAdvancedCore();

	/** The plugin. */
	private static Main plugin;

	/**
	 * Gets the single instance of CommandVote.
	 *
	 * @return single instance of CommandVote
	 */
	public static CommandAdvancedCore getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new command vote.
	 */
	private CommandAdvancedCore() {
	}

	/**
	 * Instantiates a new command vote.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public CommandAdvancedCore(Main plugin) {
		CommandAdvancedCore.plugin = plugin;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender
	 * , org.bukkit.command.Command, java.lang.String, java.lang.String[])
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label,
			String[] args) {

		for (CommandHandler commandHandler : plugin.advancedCoreCommands) {
			if (commandHandler.runCommand(sender, args)) {
				return true;
			}
		}

		// invalid command
		sender.sendMessage(ChatColor.RED
				+ "No valid arguments, see /vote help!");
		return true;
	}

}
