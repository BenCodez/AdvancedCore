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
import com.Ben12345rocks.AdvancedCore.Util.Updater.Updater;

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

	/** The updater. */
	public Updater updater;

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
	 * Check update.
	 */
	public void checkUpdate() {
		plugin.updater = new Updater(plugin, 28295, false);
		final Updater.UpdateResult result = plugin.updater.getResult();
		switch (result) {
		case FAIL_SPIGOT: {
			plugin.getLogger().info(
					"Failed to check for update for " + plugin.getName() + "!");
			break;
		}
		case NO_UPDATE: {
			plugin.getLogger().info(
					plugin.getName() + " is up to date! Version: "
							+ plugin.updater.getVersion());
			break;
		}
		case UPDATE_AVAILABLE: {
			plugin.getLogger().info(
					plugin.getName()
							+ " has an update available! Your Version: "
							+ plugin.getDescription().getVersion()
							+ " New Version: " + plugin.updater.getVersion());
			break;
		}
		default: {
			break;
		}
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
		com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().loadThread();
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

		run(new Runnable() {

			@Override
			public void run() {
				checkUpdate();
			}
		});
	}

	public void run(Runnable run) {
		com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().run(run);
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
