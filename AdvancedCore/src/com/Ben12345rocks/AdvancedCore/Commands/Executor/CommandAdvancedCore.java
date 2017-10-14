package com.Ben12345rocks.AdvancedCore.Commands.Executor;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandAdvancedCore.
 */
public class CommandAdvancedCore implements CommandExecutor {

	/** The instance. */
	private static CommandAdvancedCore instance = new CommandAdvancedCore();

	/**
	 * Gets the single instance of CommandAdvancedCore.
	 *
	 * @return single instance of CommandAdvancedCore
	 */
	public static CommandAdvancedCore getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new command advanced core.
	 */
	private CommandAdvancedCore() {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.
	 * CommandSender , org.bukkit.command.Command, java.lang.String,
	 * java.lang.String[])
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		/*
		 * for (CommandHandler commandHandler : plugin.advancedCoreCommands) { if
		 * (commandHandler.runCommand(sender, args)) { return true; } }
		 */

		// invalid command
		sender.sendMessage(ChatColor.RED + "No valid arguments, see /advancedcore help!");
		return true;
	}

}
