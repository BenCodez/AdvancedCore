package com.Ben12345rocks.AdvancedCore.Commands.Executor;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import com.Ben12345rocks.AdvancedCore.CommandAPI.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Commands.CommandLoader;

public class ValueRequestInputCommand extends BukkitCommand {
	public ValueRequestInputCommand(String name) {
		super(name);
		description = "ValueRequestInput";
		setAliases(new ArrayList<String>());
	}

	@Override
	public boolean execute(CommandSender sender, String alias, String[] args) {
		for (CommandHandler cmd : CommandLoader.getInstance().getValueReqestCommands()) {
			if (cmd.runCommand(sender, args)) {
				return true;
			}
		}

		return true;
	}
}