/*
 *
 */
package com.Ben12345rocks.AdvancedCore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Commands.CommandLoader;
import com.Ben12345rocks.AdvancedCore.Commands.Executor.CommandAdvancedCore;
import com.Ben12345rocks.AdvancedCore.Commands.TabComplete.AdvancedCoreTabCompleter;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
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

	private ArrayList<Plugin> plugins;

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
		debug(this, msg);
	}

	public void debug(Plugin plug, String msg) {
		if (Config.getInstance().getDebugEnabled()) {
			plug.getLogger().info("Debug: " + msg);
			if (Config.getInstance().getDebugInfoIngame()) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.hasPermission("AdvancedCore.Debug")) {
						player.sendMessage(Utils.getInstance().colorize(
								"&c" + plug.getName() + " Debug: " + msg));
					}
				}
			}
		}
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
		plugin = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
	 */
	@Override
	public void onEnable() {
		plugin = this;
		plugins = new ArrayList<Plugin>();
		loadCommands();
		FilesManager.getInstance().loadFileEditngThread();
		com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().loadThread();
		setupFiles();
		if (setupEconomy()) {
			plugin.getLogger().info("Successfully hooked into Vault!");
		} else {
			plugin.getLogger().warning("Failed to hook into Vault");
		}
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this),
				this);

		loadRewards();

		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			debug("Failed to load metrics");
		}

		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,
				new Runnable() {

					@Override
					public void run() {
						plugin.run(new Runnable() {

							@Override
							public void run() {
								checkUpdate();
							}
						});
					}
				}, 10l);

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				if (plugin != null) {
					update();
				} else {
					cancel();
				}

			}
		}, 1 * 60 * 1000, 5 * 60 * 1000);

	}

	public void registerHook(Plugin plugin) {
		plugins.add(plugin);
		Main.plugin.getLogger().info("Registered hook for " + plugin.getName());
	}

	/** The rewards. */
	public ArrayList<Reward> rewards;

	/**
	 * Load rewards.
	 */
	public void loadRewards() {
		ConfigRewards.getInstance().setupExample();
		rewards = new ArrayList<Reward>();
		for (String reward : ConfigRewards.getInstance().getRewardNames()) {
			rewards.add(new Reward(reward));
		}
		plugin.debug("Loaded rewards");

	}

	/**
	 * Run.
	 *
	 * @param run
	 *            the run
	 */
	public void run(Runnable run) {
		com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().run(run);
	}

	/**
	 * Reload.
	 */
	public void reload() {
		Config.getInstance().reloadData();
		loadRewards();
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

	public void update() {
		ConfigRewards.getInstance().checkDelayedTimedRewards();
	}

	public ArrayList<Plugin> getHooks() {
		return plugins;
	}
}
