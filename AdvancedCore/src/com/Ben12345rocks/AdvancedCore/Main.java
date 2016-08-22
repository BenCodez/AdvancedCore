/*
 *
 */
package com.Ben12345rocks.AdvancedCore;

import java.io.IOException;
import java.util.ArrayList;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Commands.CommandLoader;
import com.Ben12345rocks.AdvancedCore.Commands.Executor.CommandAdvancedCore;
import com.Ben12345rocks.AdvancedCore.Commands.TabComplete.AdvancedCoreTabCompleter;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;
import com.Ben12345rocks.AdvancedCore.Util.Metrics.Metrics;

// TODO: Auto-generated Javadoc
/**
 * The Class Main.
 */
public class Main extends JavaPlugin {

	/** The plugin. */
	public static Main plugin;

	/** The advanced core commands. */
	public ArrayList<CommandHandler> advancedCoreCommands;

	/** The place holder API enabled. */
	public boolean placeHolderAPIEnabled;

	/** The econ. */
	public Economy econ = null;

	/**
	 * Check place holder API.
	 */
	public void checkPlaceHolderAPI() {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			placeHolderAPIEnabled = true;
			plugin.debug("PlaceholderAPI found, will attempt to parse placeholders");
		} else {
			placeHolderAPIEnabled = false;
			plugin.debug("PlaceholderAPI not found, PlaceholderAPI placeholders will not work");
		}
	}

	/**
	 * Debug.
	 *
	 * @param msg
	 *            the msg
	 */
	public void debug(String msg) {
		if (Config.getInstance().getDebugEnabled()) {
			plugin.getLogger().info("Debug: " + msg);
		}
	}

	/**
	 * Gets the main.
	 *
	 * @return the main
	 */
	public Main getMain() {
		return this;
	}

	/**
	 * Load commands.
	 */
	public void loadCommands() {
		new CommandLoader().loadCommands();
		Bukkit.getPluginCommand("advancedcore").setExecutor(
				new CommandAdvancedCore(plugin));
		Bukkit.getPluginCommand("advancedcore").setTabCompleter(
				new AdvancedCoreTabCompleter());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
	 */
	@Override
	public void onDisable() {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
	 */
	@Override
	public void onEnable() {
		plugin = this;
		loadCommands();
		FilesManager.getInstance().loadFileEditngThread();
		setupFiles();
		setupEconomy();
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this),
				this);

		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			debug("Failed to load metrics");
		}
	}

	/**
	 * Reload.
	 */
	public void reload() {
		Config.getInstance().reloadData();

	}

	/**
	 * Setup economy.
	 *
	 * @return true, if successful
	 */
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer()
				.getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	/**
	 * Setup files.
	 */
	private void setupFiles() {
		Config.getInstance().setup(this);

	}
}
