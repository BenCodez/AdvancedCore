package com.bencodez.advancedcore.api.command;

import org.bukkit.command.CommandSender;

import com.bencodez.advancedcore.AdvancedCorePlugin;

public abstract class PlayerCommandHandler extends CommandHandler {

	public PlayerCommandHandler(AdvancedCorePlugin plugin) {
		super(plugin);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm) {
		super(plugin, args, perm);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage) {
		super(plugin, args, perm, helpMessage);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole) {
		super(plugin, args, perm, helpMessage, allowConsole);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole, boolean forceConsole) {
		super(plugin, args, perm, helpMessage, allowConsole);
		figureOutPlayerArg();
	}

	private int playerArg = -1;

	private void figureOutPlayerArg() {
		for (int i = 0; i < getArgs().length; i++) {
			if (getArgs()[i].equalsIgnoreCase("(player)")) {
				playerArg = i;
				return;
			}
		}
		getPlugin().devDebug("Failed to figure out player arg number for: " + getArgs());
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (playerArg >= 0) {
			if (args[playerArg].equalsIgnoreCase("all")) {
				executeAll(sender, args);
			}
		}
		executeSinglePlayer(sender, args);
	}

	public abstract void executeSinglePlayer(CommandSender sender, String[] args);
	
	public abstract void executeAll(CommandSender sender, String[] args);

	@Override
	public void setArgs(String[] args) {
		super.setArgs(args);
		figureOutPlayerArg();
	}

}
