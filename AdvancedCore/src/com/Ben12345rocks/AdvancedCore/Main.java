/*
 *
 */
package com.Ben12345rocks.AdvancedCore;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Commands.CommandLoader;
import com.Ben12345rocks.AdvancedCore.Commands.Executor.CommandAdvancedCore;
import com.Ben12345rocks.AdvancedCore.Commands.TabComplete.AdvancedCoreTabCompleter;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.AdvancedCoreUpdateEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PluginUpdateVersionEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WorldChangeEvent;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;
import com.Ben12345rocks.AdvancedCore.Util.Logger.Logger;
import com.Ben12345rocks.AdvancedCore.Util.Metrics.BStatsMetrics;
import com.Ben12345rocks.AdvancedCore.Util.Metrics.MCStatsMetrics;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.Updater.Updater;

import net.milkbowl.vault.economy.Economy;

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

	/** The plugins. */
	private ArrayList<Plugin> plugins;

	/** The logger. */
	private Logger logger;

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
			plugin.getLogger().info("Failed to check for update for " + plugin.getName() + "!");
			break;
		}
		case NO_UPDATE: {
			plugin.getLogger().info(plugin.getName() + " is up to date! Version: " + plugin.updater.getVersion());
			break;
		}
		case UPDATE_AVAILABLE: {
			plugin.getLogger().info(plugin.getName() + " has an update available! Your Version: "
					+ plugin.getDescription().getVersion() + " New Version: " + plugin.updater.getVersion());
			break;
		}
		default: {
			break;
		}
		}
	}

	/**
	 * Check update event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public void checkUpdateEvent(JavaPlugin plugin) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				String oldVersion = ServerData.getInstance().getPluginVersion(plugin);
				if (!plugin.getDescription().getVersion().equals(oldVersion)) {
					ServerData.getInstance().setPluginVersion(plugin);
					PluginUpdateVersionEvent event = new PluginUpdateVersionEvent(plugin, oldVersion);
					getLogger().info(plugin.getDescription().getName() + " has updated from " + oldVersion + " to "
							+ plugin.getDescription().getVersion());
					Bukkit.getPluginManager().callEvent(event);
				}
			}
		});
	}

	/**
	 * Debug.
	 *
	 * @param plug
	 *            the plug
	 * @param msg
	 *            the msg
	 */
	public void debug(Plugin plug, String msg) {
		if (Config.getInstance().getDebugEnabled()) {
			plug.getLogger().info("Debug: " + msg);
			if (logger != null && Config.getInstance().getLogDebugToFile()) {
				String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
				logger.logToFile(str + " [" + plug.getName() + "] Debug: " + msg);
			}
			if (Config.getInstance().getDebugInfoIngame()) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.hasPermission("AdvancedCore.Debug")) {
						player.sendMessage(StringUtils.getInstance().colorize("&c" + plug.getName() + " Debug: " + msg));
					}
				}
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
	
	public void debug(Exception e) {
		if (Config.getInstance().getDebugEnabled()) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets the hooks.
	 *
	 * @return the hooks
	 */
	public ArrayList<Plugin> getHooks() {
		return plugins;
	}

	/**
	 * Load commands.
	 */
	public void loadCommands() {
		CommandLoader.getInstance().loadCommands();
		Bukkit.getPluginCommand("advancedcore").setExecutor(new CommandAdvancedCore(plugin));
		Bukkit.getPluginCommand("advancedcore").setTabCompleter(new AdvancedCoreTabCompleter());
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
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this), this);
		Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(this), this);
		Bukkit.getPluginManager().registerEvents(new AdvancedCoreUpdateEvent(this), this);

		RewardHandler.getInstance().addRewardFolder(new File(plugin.getDataFolder(), "Rewards"));
		UserManager.getInstance().loadUsers();

		try {
			MCStatsMetrics metrics = new MCStatsMetrics(this);
			metrics.start();
		} catch (IOException e) {
			debug("Failed to load metrics");
		}

		new BStatsMetrics(this);

		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

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

		logger = new Logger(this, new File(getDataFolder(), "Log" + File.separator + "Log.txt"));

		checkUpdateEvent(plugin);
	}

	/**
	 * Register hook.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public void registerHook(Plugin plugin) {
		plugins.add(plugin);

		Main.plugin.getLogger().info("Registered hook for " + plugin.getName());
	}

	/**
	 * Reload.
	 */
	public void reload() {
		Config.getInstance().reloadData();
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		update();
		UserManager.getInstance().loadUsers();
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
	 * Setup economy.
	 *
	 * @return true, if successful
	 */
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
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
		Config.getInstance().setup();
		ServerData.getInstance().setup();

	}

	/**
	 * Update.
	 */
	public synchronized void update() {
		run(new Runnable() {

			@Override
			public void run() {
				RewardHandler.getInstance().checkDelayedTimedRewards();
				TimeChecker.getInstance().update();
			}
		});

	}
}
