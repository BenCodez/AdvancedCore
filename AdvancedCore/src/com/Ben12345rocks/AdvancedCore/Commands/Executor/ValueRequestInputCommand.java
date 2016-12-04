package com.Ben12345rocks.AdvancedCore.Commands.Executor;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import com.Ben12345rocks.AdvancedCore.Commands.CommandLoader;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;

public class ValueRequestInputCommand extends BukkitCommand {
	public ValueRequestInputCommand(String name) {
		super(name);
		this.description = "ValueRequestInput";
		this.setAliases(new ArrayList<String>());
	}

	@Override
	public boolean execute(CommandSender sender, String alias, String[] args) {
		for (CommandHandler cmd : CommandLoader.getInstance().getValueReqestCommands()) {
			if (cmd.runCommand(sender, args))
				return true;
		}

		return true;
	}
}