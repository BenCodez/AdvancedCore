package com.bencodez.advancedcore.command.executor;

import java.util.ArrayList;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.command.CommandHandler;

public class ValueRequestInputCommand extends Command implements PluginIdentifiableCommand {
	private final AdvancedCorePlugin plugin;

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

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
