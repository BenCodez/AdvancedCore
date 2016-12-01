package com.Ben12345rocks.AdvancedCore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WorldChangeEvent;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.ServerHandle.CraftBukkitHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.IServerHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.SpigotHandle;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Logger.Logger;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import net.milkbowl.vault.economy.Economy;

public class AdvancedCoreHook {
	private Plugin plugin;
	private boolean placeHolderAPIEnabled;

	private boolean timerLoaded = false;
	private boolean debug = false;
	private boolean debugIngame = false;
	private boolean logDebugToFile = true;
	private String defaultRequestMethod = "ANVIL";
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();
	private String TimeZone = "UTC";
	private String formatNoPerms = "&cYou do not have enough permission!";
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	private String permPrefix;
	private IServerHandle serverHandle;

	public String getPermPrefix() {
		return permPrefix;
	}

	public void setPermPrefix(String permPrefix) {
		this.permPrefix = permPrefix;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public boolean isDebugIngame() {
		return debugIngame;
	}

	public void setDebugIngame(boolean debugIngame) {
		this.debugIngame = debugIngame;
	}

	public boolean isLogDebugToFile() {
		return logDebugToFile;
	}

	public void setLogDebugToFile(boolean logDebugToFile) {
		this.logDebugToFile = logDebugToFile;
	}

	public String getDefaultRequestMethod() {
		return defaultRequestMethod;
	}

	public void setDefaultRequestMethod(String defaultRequestMethod) {
		this.defaultRequestMethod = defaultRequestMethod;
	}

	public ArrayList<String> getDisabledRequestMethods() {
		return disabledRequestMethods;
	}

	public void setDisabledRequestMethods(ArrayList<String> disabledRequestMethods) {
		this.disabledRequestMethods = disabledRequestMethods;
	}

	public String getTimeZone() {
		return TimeZone;
	}

	public void setTimeZone(String timeZone) {
		TimeZone = timeZone;
	}

	public String getFormatNoPerms() {
		return formatNoPerms;
	}

	public void setFormatNoPerms(String formatNoPerms) {
		this.formatNoPerms = formatNoPerms;
	}

	public String getFormatNotNumber() {
		return formatNotNumber;
	}

	public void setFormatNotNumber(String formatNotNumber) {
		this.formatNotNumber = formatNotNumber;
	}

	public String getHelpLine() {
		return helpLine;
	}

	public void setHelpLine(String helpLine) {
		this.helpLine = helpLine;
	}

	public Plugin getPlugin() {
		return plugin;
	}

	public boolean isPlaceHolderAPIEnabled() {
		return placeHolderAPIEnabled;
	}

	public Logger getLogger() {
		return logger;
	}

	public Economy getEcon() {
		return econ;
	}

	private Logger logger;
	private static AdvancedCoreHook instance = new AdvancedCoreHook();

	public static AdvancedCoreHook getInstance() {
		return instance;
	}

	public void loadHook(Plugin plugin) {
		this.plugin = plugin;
		this.permPrefix = plugin.getName();
		checkPlaceHolderAPI();
		if (setupEconomy()) {
			plugin.getLogger().info("Successfully hooked into Vault!");
		} else {
			plugin.getLogger().warning("Failed to hook into Vault");
		}
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(plugin), plugin);
		Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(plugin), plugin);
		ServerData.getInstance().setup();
		loadRewards();
		loadUsers();
		loadBackgroundTimer(5);
	}

	public boolean isTimerLoaded() {
		return timerLoaded;
	}

	public IServerHandle getServerHandle() {
		return serverHandle;
	}

	public void loadBasicHook(Plugin plugin) {
		this.plugin = plugin;
		this.permPrefix = plugin.getName();
		checkPlaceHolderAPI();
		if (setupEconomy()) {
			plugin.getLogger().info("Successfully hooked into Vault!");
		} else {
			plugin.getLogger().warning("Failed to hook into Vault");
		}
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(plugin), plugin);
		Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(plugin), plugin);
		ServerData.getInstance().setup();
		loadRewards();
		loadUsers();
	}

	private AdvancedCoreHook() {
		try {
			Class.forName("org.spigotmc.SpigotConfig");
			serverHandle = new SpigotHandle();
			debug("Detected using spigot");
		} catch (Exception ex) {
			serverHandle = new CraftBukkitHandle();
			debug("Detected using craftbukkit");
		}
	}

	public void loadRewards() {
		RewardHandler.getInstance().addRewardFolder(new File(plugin.getDataFolder(), "Rewards"));
	}

	public void reload() {
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		update();
		UserManager.getInstance().loadUsers();
	}

	public void loadUsers() {
		UserManager.getInstance().loadUsers();
	}

	public void loadLogger() {
		if (logDebugToFile && logger == null)
			logger = new Logger(plugin, new File(plugin.getDataFolder(), "Log" + File.separator + "Log.txt"));
	}

	public void loadBackgroundTimer(int minutes) {
		if (!timerLoaded) {
			timerLoaded = true;
			new Timer().schedule(new TimerTask() {

				@Override
				public void run() {
					if (plugin != null) {
						update();
					} else {
						cancel();
					}

				}
			}, 60 * 1000, minutes * 60 * 1000);
		} else {
			debug("Timer is already loaded");
		}

	}

	private void checkPlaceHolderAPI() {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			placeHolderAPIEnabled = true;
			debug("PlaceholderAPI found, will attempt to parse placeholders");
		} else {
			placeHolderAPIEnabled = false;
			debug("PlaceholderAPI not found, PlaceholderAPI placeholders will not work");
		}
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

	/** The econ. */
	public Economy econ = null;

	/**
	 * Setup economy.
	 *
	 * @return true, if successful
	 */
	private boolean setupEconomy() {
		if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
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

	/**
	 * Debug.
	 *
	 * @param plug
	 *            the plug
	 * @param msg
	 *            the msg
	 */
	public void debug(Plugin plug, String msg) {
		if (debug) {
			loadLogger();
			plug.getLogger().info("Debug: " + msg);
			if (logger != null && logDebugToFile) {
				String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
				logger.logToFile(str + " [" + plug.getName() + "] Debug: " + msg);
			}
			if (debugIngame) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.hasPermission(plugin.getName() + ".Debug")) {
						player.sendMessage(
								StringUtils.getInstance().colorize("&c" + plug.getName() + " Debug: " + msg));
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
		debug(this.plugin, msg);
	}

	public void debug(Exception e) {
		if (debug) {
			e.printStackTrace();
			loadLogger();
			if (logger != null && logDebugToFile) {
				logger.logToFile(e.toString());
			}
		}
	}
}
