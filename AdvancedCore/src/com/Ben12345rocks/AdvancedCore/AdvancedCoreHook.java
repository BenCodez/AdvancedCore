package com.Ben12345rocks.AdvancedCore;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.Ben12345rocks.AdvancedCore.Commands.Executor.ValueRequestInputCommand;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PluginUpdateVersionEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WorldChangeEvent;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.ServerHandle.CraftBukkitHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.IServerHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.SpigotHandle;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.Util.Logger.Logger;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.mysql.MySQL;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;
import com.Ben12345rocks.AdvancedCore.sql.Database;
import com.Ben12345rocks.AdvancedCore.sql.Table;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class AdvancedCoreHook {
	private static AdvancedCoreHook instance = new AdvancedCoreHook();

	public static AdvancedCoreHook getInstance() {
		return instance;
	}

	private Plugin plugin;
	private boolean placeHolderAPIEnabled;
	private boolean timerLoaded = false;
	private boolean debug = false;
	private boolean debugIngame = false;
	private boolean logDebugToFile = true;
	private String defaultRequestMethod = "ANVIL";
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();
	private String formatNoPerms = "&cYou do not have enough permission!";
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	private Database database;
	private MySQL mysql;
	private UserStorage storageType = UserStorage.SQLITE;
	private String permPrefix;
	private IServerHandle serverHandle;
	private Logger logger;
	private boolean preloadUsers = false;
	private boolean sendScoreboards = true;
	private int resourceId = 0;
	private String jarName;
	private boolean extraDebug = false;

	private boolean checkOnWorldChange = false;

	/** The econ. */
	private Economy econ = null;

	private Permission perms;

	private AdvancedCoreHook() {

	}

	public void allowDownloadingFromSpigot(int resourceId, String jarName) {
		this.resourceId = resourceId;
		this.jarName = jarName;
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

	public void checkPluginUpdate() {
		String version = ServerData.getInstance().getPluginVersion(plugin);
		if (!version.equals(plugin.getDescription().getVersion())) {
			PluginUpdateVersionEvent event = new PluginUpdateVersionEvent(plugin, version);
			Bukkit.getServer().getPluginManager().callEvent(event);
		}
		ServerData.getInstance().setPluginVersion(plugin);
	}

	/**
	 * Show exception in console if debug is on
	 *
	 * @param e
	 *            Exception
	 */
	public void debug(Exception e) {
		if (debug) {
			e.printStackTrace();
			loadLogger();
			if (logger != null && logDebugToFile) {
				logger.logToFile(e.toString());
			}
		}
	}

	/**
	 * Show debug in console, file, and/or ingame
	 *
	 * @param plug
	 *            Plugin
	 * @param msg
	 *            Debug message
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
	 * Show debug in console, file, and/or ingame
	 *
	 * @param msg
	 *            Debug message
	 */
	public void debug(String msg) {
		debug(plugin, msg);
	}

	public String getDefaultRequestMethod() {
		return defaultRequestMethod;
	}

	public ArrayList<String> getDisabledRequestMethods() {
		return disabledRequestMethods;
	}

	public Economy getEcon() {
		return econ;
	}

	public String getFormatNoPerms() {
		return formatNoPerms;
	}

	public String getFormatNotNumber() {
		return formatNotNumber;
	}

	public String getHelpLine() {
		return helpLine;
	}

	/**
	 * @return the jarName
	 */
	public String getJarName() {
		return jarName;
	}

	public Logger getLogger() {
		return logger;
	}

	/**
	 * @return the mysql
	 */
	public MySQL getMysql() {
		return mysql;
	}

	public String getPermPrefix() {
		return permPrefix;
	}

	public Permission getPerms() {
		return perms;
	}

	public Plugin getPlugin() {
		return plugin;
	}

	/**
	 * @return the resourceId
	 */
	public int getResourceId() {
		return resourceId;
	}

	public IServerHandle getServerHandle() {
		return serverHandle;
	}

	public Table getSQLiteUserTable() {
		for (Table table : database.getTables()) {
			if (table.getName().equalsIgnoreCase("Users")) {
				return table;
			}
		}
		return null;
	}

	public UserStorage getStorageType() {
		return storageType;
	}

	public synchronized boolean isCheckOnWorldChange() {
		return checkOnWorldChange;
	}

	public boolean isDebug() {
		return debug;
	}

	public boolean isDebugIngame() {
		return debugIngame;
	}

	/**
	 * @return the extraDebug
	 */
	public boolean isExtraDebug() {
		return extraDebug;
	}

	public boolean isLogDebugToFile() {
		return logDebugToFile;
	}

	public boolean isPlaceHolderAPIEnabled() {
		return placeHolderAPIEnabled;
	}

	public boolean isPreloadUsers() {
		return preloadUsers;
	}

	/**
	 * @return the sendScoreboards
	 */
	public boolean isSendScoreboards() {
		return sendScoreboards;
	}

	public boolean isTimerLoaded() {
		return timerLoaded;
	}

	/**
	 * Load background
	 *
	 * @param minutes
	 *            Minutes
	 */
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

	/**
	 * Load AdvancedCore hook without most things loaded
	 *
	 * @param plugin
	 *            Plugin that is hooking in
	 */
	public void loadBasicHook(Plugin plugin) {
		this.plugin = plugin;
		permPrefix = plugin.getName();
		checkPlaceHolderAPI();
		loadHandle();
		loadEconomy();
		ServerData.getInstance().setup();
		loadRewards();
	}

	public void loadEconomy() {
		if (setupEconomy()) {
			plugin.getLogger().info("Successfully hooked into Vault!");
		} else {
			plugin.getLogger().warning("Failed to hook into Vault");
		}
	}

	public void loadEvents() {
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(plugin), plugin);
		if (checkOnWorldChange) {
			Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(plugin), plugin);
		}
	}

	private void loadHandle() {
		try {
			Class.forName("org.spigotmc.SpigotConfig");
			serverHandle = new SpigotHandle();
			debug("Detected using spigot");
		} catch (Exception ex) {
			serverHandle = new CraftBukkitHandle();
			debug("Detected using craftbukkit");
		}
	}

	/**
	 * Load AdvancedCore hook
	 *
	 * @param plugin
	 *            Plugin that is hooking in
	 */
	public void loadHook(Plugin plugin) {
		this.plugin = plugin;
		permPrefix = plugin.getName();
		loadUserAPI(UserStorage.SQLITE);
		checkPlaceHolderAPI();
		loadHandle();
		loadEconomy();
		loadEvents();
		ServerData.getInstance().setup();
		loadRewards();
		loadBackgroundTimer(1);
		loadValueRequestInputCommands();
		checkPluginUpdate();
	}

	/**
	 * Load logger
	 */
	public void loadLogger() {
		if (logDebugToFile && logger == null) {
			logger = new Logger(plugin, new File(plugin.getDataFolder(), "Log" + File.separator + "Log.txt"));
		}
	}

	public void loadPermissions() {
		if (setupPermissions()) {
			plugin.getLogger().info("Hooked into Vault permissions");
		}
	}

	/**
	 * Setup Reward Files
	 */
	public void loadRewards() {
		RewardHandler.getInstance().addRewardFolder(new File(plugin.getDataFolder(), "Rewards"));
	}

	public void loadUserAPI(UserStorage storageType) {
		if (storageType.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column key = new Column("uuid", DataType.STRING);
			columns.add(key);
			Table table = new Table("Users", columns, key);
			database = new Database(plugin, "Users", table);
		} else if (storageType.equals(UserStorage.MYSQL)) {
			mysql = null;
		}
	}

	public void loadValueRequestInputCommands() {
		try {
			final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

			bukkitCommandMap.setAccessible(true);
			CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

			commandMap.register(plugin.getName() + "valuerequestinput",
					new ValueRequestInputCommand(plugin.getName() + "valuerequestinput"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reload
	 */
	public void reload() {
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		update();
		if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null) {
			getMysql().clearCache();
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

	public synchronized void setCheckOnWorldChange(boolean checkOnWorldChange) {
		this.checkOnWorldChange = checkOnWorldChange;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void setDebugIngame(boolean debugIngame) {
		this.debugIngame = debugIngame;
	}

	public void setDefaultRequestMethod(String defaultRequestMethod) {
		this.defaultRequestMethod = defaultRequestMethod;
	}

	public void setDisabledRequestMethods(ArrayList<String> disabledRequestMethods) {
		this.disabledRequestMethods = disabledRequestMethods;
	}

	/**
	 * @param extraDebug
	 *            the extraDebug to set
	 */
	public void setExtraDebug(boolean extraDebug) {
		this.extraDebug = extraDebug;
	}

	public void setFormatNoPerms(String formatNoPerms) {
		this.formatNoPerms = formatNoPerms;
	}

	public void setFormatNotNumber(String formatNotNumber) {
		this.formatNotNumber = formatNotNumber;
	}

	public void setHelpLine(String helpLine) {
		this.helpLine = helpLine;
	}

	public void setLogDebugToFile(boolean logDebugToFile) {
		this.logDebugToFile = logDebugToFile;
	}

	/**
	 * @param mysql
	 *            the mysql to set
	 */
	public void setMysql(MySQL mysql) {
		if (this.mysql != null) {
			this.mysql.close();
			this.mysql = null;
		}
		this.mysql = mysql;
	}

	public void setPermPrefix(String permPrefix) {
		this.permPrefix = permPrefix;
	}

	public void setPlugin(Plugin plugin) {
		this.plugin = plugin;
	}

	public void setPreloadUsers(boolean preloadUsers) {
		this.preloadUsers = preloadUsers;
	}

	/**
	 * @param sendScoreboards
	 *            the sendScoreboards to set
	 */
	public void setSendScoreboards(boolean sendScoreboards) {
		this.sendScoreboards = sendScoreboards;
	}

	public void setStorageType(UserStorage storageType) {
		this.storageType = storageType;
	}

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

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager()
				.getRegistration(Permission.class);
		perms = rsp.getProvider();
		return perms != null;
	}

	/**
	 * Update.
	 */
	public void update() {
		Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), new Runnable() {

			@Override
			public void run() {
				RewardHandler.getInstance().checkDelayedTimedRewards();
				TimeChecker.getInstance().update();
			}
		});
	}
}
