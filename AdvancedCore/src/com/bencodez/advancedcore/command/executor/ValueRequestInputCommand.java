package com.bencodez.advancedcore.command.executor;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.command.CommandHandler;

public class ValueRequestInputCommand extends BukkitCommand {
	private AdvancedCorePlugin plugin;

	public ValueRequestInputCommand(AdvancedCorePlugin plugin, String name) {
		super(name);
		this.plugin = plugin;
		description = "ValueRequestInput";
		setAliases(new ArrayList<String>());
	}

	@Override
	public boolean execute(CommandSender sender, String alias, String[] args) {
		for (CommandHandler cmd : plugin.getAdvancedCoreCommandLoader().getValueRequestCommands()) {
			if (cmd.runCommand(sender, args)) {
				return true;
			}
		}

		return true;
	}
}