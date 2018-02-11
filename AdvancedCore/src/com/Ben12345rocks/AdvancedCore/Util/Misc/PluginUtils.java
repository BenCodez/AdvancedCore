package com.Ben12345rocks.AdvancedCore.Util.Misc;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

public class PluginUtils {
	/** The instance. */
	static PluginUtils instance = new PluginUtils();

	public static PluginUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private PluginUtils() {
	}

	public void registerEvents(Listener listener, JavaPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(listener, plugin);
	}

	public void registerCommands(JavaPlugin plugin, String commandText, CommandExecutor executor, TabCompleter tab) {
		plugin.getCommand(commandText).setExecutor(executor);
		if (tab != null) {
			plugin.getCommand(commandText).setTabCompleter(tab);
		}
	}

}
