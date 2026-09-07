package com.bencodez.advancedcore.api.command;

import org.bukkit.command.CommandSender;

import java.util.regex.Pattern;

import com.bencodez.advancedcore.AdvancedCorePlugin;

public abstract class PlayerCommandHandler extends CommandHandler {

	private int playerArg = -1;

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
		super(plugin, args, perm, helpMessage, allowConsole, forceConsole);
		figureOutPlayerArg();
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (playerArg >= 0) {
			if (args[playerArg].equalsIgnoreCase("all")) {
				if (hasAllPermission(sender)) {
					executeAll(sender, args);
				}
				return;
			}
		}
		executeSinglePlayer(sender, args);
	}

	public abstract void executeAll(CommandSender sender, String[] args);

	public abstract void executeSinglePlayer(CommandSender sender, String[] args);

	/**
	 * Checks the stronger permission required for the special {@code all} target.
	 * The first configured permission is treated as the granular command permission
	 * and receives an {@code .All} suffix. Any alternative permissions, such as an
	 * administrator permission, continue to act as overrides.
	 *
	 * @param sender command sender
	 * @return whether bulk execution is authorized
	 */
	public boolean hasAllPermission(CommandSender sender) {
		String permission = getPerm();
		if (permission == null || permission.isEmpty()) {
			return false;
		}
		String[] permissions = permission.split(Pattern.quote("|"));
		if (sender.hasPermission(permissions[0] + ".All")) {
			return true;
		}
		if (isAllowMultiplePermissions()) {
			for (int i = 1; i < permissions.length; i++) {
				if (sender.hasPermission(permissions[i])) {
					return true;
				}
			}
		}
		return false;
	}

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
	public void setArgs(String[] args) {
		super.setArgs(args);
		figureOutPlayerArg();
	}

}
