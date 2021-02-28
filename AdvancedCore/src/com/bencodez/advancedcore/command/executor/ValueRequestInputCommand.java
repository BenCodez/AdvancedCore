package com.bencodez.advancedcore.command.executor;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.command.CommandLoader;

public class ValueRequestInputCommand extends BukkitCommand {
	public ValueRequestInputCommand(String name) {
		super(name);
		description = "ValueRequestInput";
		setAliases(new ArrayList<String>());
	}

	@Override
	public boolean execute(CommandSender sender, String alias, String[] args) {
		for (CommandHandler cmd : CommandLoader.getInstance().getValueRequestCommands()) {
			if (cmd.runCommand(sender, args)) {
				return true;
			}
		}

		return true;
	}
}