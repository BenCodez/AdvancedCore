package com.Ben12345rocks.AdvancedCore;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.CodeSource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Commands.Executor.ValueRequestInputCommand;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PluginUpdateVersionEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WorldChangeEvent;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Objects.TabCompleteHandle;
import com.Ben12345rocks.AdvancedCore.Objects.TabCompleteHandler;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Objects.UserStartup;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.ServerHandle.CraftBukkitHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.IServerHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.SpigotHandle;
import com.Ben12345rocks.AdvancedCore.Thread.Thread;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeType;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Effects.FireworkHandler;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptPlaceholderRequest;
import com.Ben12345rocks.AdvancedCore.Util.Logger.Logger;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.Sign.SignMenu;
import com.Ben12345rocks.AdvancedCore.Util.Updater.SpigetUpdater;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;
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

	private ConcurrentHashMap<String, String> uuids;

	private SignMenu signMenu;
	private JavaPlugin plugin;
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
	private boolean sendScoreboards = true;
	private int resourceId = 0;
	private boolean extraDebug = false;
	private boolean disableCheckOnWorldChange = false;
	private Timer timer = new Timer();
	private boolean autoDownload = false;
	private ArrayList<JavascriptPlaceholderRequest> javascriptEngineRequests = new ArrayList<JavascriptPlaceholderRequest>();
	private String version = "";
	private String buildTime = "";
	private boolean autoKillInvs = true;
	private String prevPageTxt = "&aPrevious Page";
	private String nextPageTxt = "&aNext Page";
	private boolean checkNameMojang = true;

	private HashMap<String, Object> javascriptEngine = new HashMap<String, Object>();

	/** The econ. */
	private Economy econ = null;

	private Permission perms;

	private boolean alternateUUIDLookUp;

	private boolean purgeOldData = false;

	private int purgeMinimumDays = 90;

	private ConfigurationSection configData;

	private ArrayList<UserStartup> userStartup = new ArrayList<UserStartup>();

	private String formatInvFull;

	private AdvancedCoreHook() {
	}

	public void addUserStartup(UserStartup start) {
		userStartup.add(start);
	}

	public void allowDownloadingFromSpigot(int resourceId) {
		this.resourceId = resourceId;
	}

	private void checkAutoUpdate() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (isAutoDownload() && getResourceId() != 0) {
					SpigetUpdater.getInstance().checkAutoDownload(getPlugin(), getResourceId());
				}
			}
		});

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
		Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), new Runnable() {

			@Override
			public void run() {
				String version = ServerData.getInstance().getPluginVersion(plugin);
				if (!version.equals(plugin.getDescription().getVersion())) {
					PluginUpdateVersionEvent event = new PluginUpdateVersionEvent(plugin, version);
					Bukkit.getServer().getPluginManager().callEvent(event);
				}
				ServerData.getInstance().setPluginVersion(plugin);
			}
		});

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

	public void extraDebug(Plugin plug, String msg) {
		if (extraDebug) {
			debug(plug, "[Extra] " + msg);
		}
	}

	public void extraDebug(String msg) {
		if (extraDebug) {
			debug(plugin, "[Extra] " + msg);
		}
	}

	/**
	 * @return the configData
	 */
	public ConfigurationSection getConfigData() {
		return configData;
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

	public HashMap<String, Object> getJavascriptEngine() {
		return javascriptEngine;
	}

	/**
	 * @return the javascriptEngineRequests
	 */
	public ArrayList<JavascriptPlaceholderRequest> getJavascriptEngineRequests() {
		return javascriptEngineRequests;
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

	public String getNextPageTxt() {
		return nextPageTxt;
	}

	public String getPermPrefix() {
		return permPrefix;
	}

	public Permission getPerms() {
		return perms;
	}

	public JavaPlugin getPlugin() {
		return plugin;
	}

	public String getPrevPageTxt() {
		return prevPageTxt;
	}

	public int getPurgeMinimumDays() {
		return purgeMinimumDays;
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

	public SignMenu getSignMenu() {
		return this.signMenu;
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

	public String getTime() {
		return buildTime;
	}

	/**
	 * @return the timer
	 */
	public Timer getTimer() {
		return timer;
	}

	public UserManager getUserManager() {
		return UserManager.getInstance();
	}

	public ConcurrentHashMap<String, String> getUuids() {
		return uuids;
	}

	/**
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	private YamlConfiguration getVersionFile() {
		try {
			CodeSource src = this.getClass().getProtectionDomain().getCodeSource();
			if (src != null) {
				URL jar = src.getLocation();
				ZipInputStream zip = null;
				zip = new ZipInputStream(jar.openStream());
				while (true) {
					ZipEntry e = zip.getNextEntry();
					if (e != null) {
						String name = e.getName();
						if (name.equals("version.yml")) {
							Reader defConfigStream = new InputStreamReader(zip);
							if (defConfigStream != null) {
								YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
								defConfigStream.close();
								return defConfig;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean isAlternateUUIDLookUp() {
		return alternateUUIDLookUp;
	}

	/**
	 * @return the autoDownload
	 */
	public boolean isAutoDownload() {
		return autoDownload;
	}

	public boolean isAutoKillInvs() {
		return autoKillInvs;
	}

	/**
	 * @return the checkNameMojang
	 */
	public boolean isCheckNameMojang() {
		return checkNameMojang;
	}

	public boolean isDebug() {
		return debug;
	}

	public boolean isDebugIngame() {
		return debugIngame;
	}

	/**
	 * @return the dsiableCheckOnWorldChange
	 */
	public boolean isDisableCheckOnWorldChange() {
		return disableCheckOnWorldChange;
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

	public boolean isPurgeOldData() {
		return purgeOldData;
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

	public void loadAutoUpdateCheck() {
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				checkAutoUpdate();
			}
		}, 20, 20 * 1000 * 60 * 60);
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
	 * Avoid using this unless you really want to
	 *
	 * @param plugin
	 *            Plugin that is hooking in
	 */
	public void loadBasicHook(JavaPlugin plugin) {
		this.plugin = plugin;
		loadSignAPI();
		loadUUIDs();
		permPrefix = plugin.getName();
		checkPlaceHolderAPI();
		loadHandle();
		loadEconomy();
		loadPermissions();
		ServerData.getInstance().setup();
		loadRewards();
		RewardHandler.getInstance().checkDelayedTimedRewards();
		loadAutoUpdateCheck();
		loadVersionFile();

		userStartup();
		debug("Using AdvancedCore '" + getVersion() + "' built on '" + getTime() + "'");
	}

	/**
	 * @return the formatInvFull
	 */
	public String getFormatInvFull() {
		return formatInvFull;
	}

	/**
	 * @param formatInvFull
	 *            the formatInvFull to set
	 */
	public void setFormatInvFull(String formatInvFull) {
		this.formatInvFull = formatInvFull;
	}

	@SuppressWarnings("unchecked")
	private void loadConfig() {
		if (configData != null) {
			debug = configData.getBoolean("Debug", false);
			debugIngame = configData.getBoolean("DebugInGame", false);
			defaultRequestMethod = configData.getString("RequestAPI.DefaultMethod", "Anvil");
			disabledRequestMethods = (ArrayList<String>) configData.getList("RequestAPI.DisabledMethods",
					new ArrayList<String>());
			formatNoPerms = configData.getString("Format.NoPerms", "&cYou do not have enough permission!");
			formatNotNumber = configData.getString("Format.NotNumber", "&cError on &6%arg%&c, number expected!");
			formatInvFull = configData.getString("Format.InvFull", "&cInventory full, dropping items on ground");
			helpLine = configData.getString("Format.HelpLine", "&3&l%Command% - &3%HelpMessage%");
			logDebugToFile = configData.getBoolean("LogDebugToFile", false);
			sendScoreboards = configData.getBoolean("SendScoreboards", true);
			alternateUUIDLookUp = configData.getBoolean("AlternateUUIDLookup", false);
			autoKillInvs = configData.getBoolean("AutoKillInvs", true);
			prevPageTxt = configData.getString("Format.PrevPage", "&aPrevious Page");
			nextPageTxt = configData.getString("Format.NextPage", "&aNext Page");
			purgeOldData = configData.getBoolean("PurgeOldData");
			purgeMinimumDays = configData.getInt("PurgeMin", 90);
			checkNameMojang = configData.getBoolean("CheckNameMojang", true);
			disableCheckOnWorldChange = configData.getBoolean("DisableCheckOnWorldChange");
			autoDownload = configData.getBoolean("AutoDownload", false);
			extraDebug = configData.getBoolean("ExtraDebug", false);
			storageType = UserStorage.value(configData.getString("DataStorage", "SQLITE"));

			if (storageType.equals(UserStorage.MYSQL)) {
				Thread.getInstance().run(new Runnable() {

					@Override
					public void run() {
						setMysql(new MySQL(getPlugin().getName() + "_Users",
								configData.getConfigurationSection("MySQL")));
					}
				});
			}
		}
	}

	public void loadEconomy() {
		Bukkit.getScheduler().runTaskLater(getPlugin(), new Runnable() {

			@Override
			public void run() {
				if (setupEconomy()) {
					plugin.getLogger().info("Successfully hooked into Vault!");
				} else {
					plugin.getLogger().warning("Failed to hook into Vault");
				}
			}
		}, 5);
	}

	public void loadEvents() {
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(plugin), plugin);
		Bukkit.getPluginManager().registerEvents(FireworkHandler.getInstance(), plugin);
		Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(plugin), plugin);
	}

	private void loadHandle() {
		try {
			Class.forName("org.spigotmc.SpigotConfig");
			serverHandle = new SpigotHandle();
			debug("Detected using spigot");
		} catch (Exception ex) {
			serverHandle = new CraftBukkitHandle();
			debug("Detected using craftbukkit");
			plugin.getLogger().info("Detected server running craftbukkit. It is recommended to use spigot instead");
		}
		if (Bukkit.getOnlineMode()) {
			debug("Server in online mode");
		} else {
			debug("Server in offline mode");
		}
	}

	/**
	 * Load AdvancedCore hook
	 *
	 * @param plugin
	 *            Plugin that is hooking in
	 */
	public void loadHook(JavaPlugin plugin) {
		this.plugin = plugin;
		loadSignAPI();
		loadUUIDs();
		permPrefix = plugin.getName();
		checkPlaceHolderAPI();
		loadUserAPI(UserStorage.SQLITE);
		loadHandle();
		loadEconomy();
		loadPermissions();
		loadEvents();
		ServerData.getInstance().setup();
		loadRewards();
		loadBackgroundTimer(5);
		loadValueRequestInputCommands();
		checkPluginUpdate();
		RewardHandler.getInstance().checkDelayedTimedRewards();
		loadAutoUpdateCheck();
		loadVersionFile();
		loadTabComplete();

		loadConfig();

		UserManager.getInstance().purgeOldPlayers();

		userStartup();

		debug("Using AdvancedCore '" + getVersion() + "' built on '" + getTime() + "'");

		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (NMSManager.getInstance().isVersion("1.7", "1.8")) {
					plugin.getLogger().warning(
							"Detected using an old version, the plugin may not function properly, this version is also not fully supported");
				}
			}
		}, 20l);
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
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {

			@Override
			public void run() {
				if (setupPermissions()) {
					plugin.getLogger().info("Hooked into Vault permissions");
				}
			}
		}, 2);

	}

	/**
	 * Setup Reward Files
	 */
	public void loadRewards() {
		RewardHandler.getInstance().addRewardFolder(new File(plugin.getDataFolder(), "Rewards"));
	}

	private void loadSignAPI() {
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null
				&& !NMSManager.getInstance().isVersion("1.8", "1.9", "1.10", "1.11")) {
			try {
				this.signMenu = new SignMenu(plugin);
			} catch (Exception e) {
				debug(e);
			}
		}
	}

	public void loadTabComplete() {
		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(Player)", new ArrayList<String>()) {

					@Override
					public void reload() {
						ArrayList<String> players = new ArrayList<String>();
						for (String name : AdvancedCoreHook.getInstance().getUuids().keySet()) {
							if (!players.contains(name)) {
								players.add(name);
							}
						}
						setReplace(players);
					}

					@Override
					public void updateReplacements() {
						for (Player player : Bukkit.getOnlinePlayers()) {
							if (!getReplace().contains(player.getName())) {
								getReplace().add(player.getName());
							}
						}

					}
				});

		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(OnlinePlayer)", new ArrayList<String>()) {

					@Override
					public void reload() {
					}

					@Override
					public void updateReplacements() {
						ArrayList<String> list = new ArrayList<String>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}
				});

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(uuid)", new ArrayList<String>()) {

			@Override
			public void reload() {
				ArrayList<String> uuids = new ArrayList<String>();
				for (String name : AdvancedCoreHook.getInstance().getUuids().values()) {
					if (!uuids.contains(name)) {
						uuids.add(name);
					}
				}
				setReplace(uuids);
			}

			@Override
			public void updateReplacements() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (!getReplace().contains(player.getUniqueId().toString())) {
						getReplace().add(player.getUniqueId().toString());
					}
				}
			}
		});

		ArrayList<String> options = new ArrayList<String>();
		options.add("True");
		options.add("False");
		TabCompleteHandler.getInstance().addTabCompleteOption("(Boolean)", options);
		options = new ArrayList<String>();
		TabCompleteHandler.getInstance().addTabCompleteOption("(List)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(String)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(Reward)", options) {

			@Override
			public void reload() {
				ArrayList<String> rewards = new ArrayList<String>();
				for (Reward reward : RewardHandler.getInstance().getRewards()) {
					rewards.add(reward.getRewardName());
				}
				setReplace(rewards);
			}

			@Override
			public void updateReplacements() {

			}
		});

		ArrayList<String> method = new ArrayList<String>();
		for (InputMethod me : InputMethod.values()) {
			method.add(me.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(RequestMethod)", method);

		ArrayList<String> userStorage = new ArrayList<String>();
		for (UserStorage storage : UserStorage.values()) {
			userStorage.add(storage.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(UserStorage)", userStorage);

		ArrayList<String> times = new ArrayList<String>();
		for (TimeType ty : TimeType.values()) {
			times.add(ty.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(TimeType)", times);
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

	private void loadUUIDs() {

		uuids = new ConcurrentHashMap<String, String>();

		addUserStartup(new UserStartup() {

			@Override
			public void onFinish() {
				TabCompleteHandler.getInstance().reload();
				debug("Finished loading uuids");
			}

			@Override
			public void onStart() {
				debug("Starting background uuid task");
			}

			@Override
			public void onStartUp(User user) {
				String uuid = user.getUUID();
				String name = user.getData().getString("PlayerName");
				boolean add = true;
				if (uuids.containsValue(uuid)) {
					debug("Duplicate uuid? " + uuid);
				}

				if (name == null || name.equals("") || name.equals("Error getting name")) {
					debug("Invalid player name: " + uuid);
					add = false;
				} else {
					if (uuids.containsKey(name)) {
						debug("Duplicate player name?" + name);
					}
				}
				if (uuid == null || uuid.equals("")) {
					debug("Invalid uuid: " + uuid);
					add = false;
				}

				if (getStorageType().equals(UserStorage.MYSQL)) {
					boolean delete = true;
					for (Column col : user.getData().getMySqlRow()) {
						if (!col.getName().equals("uuid") && !col.getName().equalsIgnoreCase("playername")) {
							if (col.getValue() != null) {
								if (!col.getValue().toString().isEmpty()) {
									delete = false;
								}
							}
						}
					}
					if (delete) {
						add = false;
						debug("Deleting " + uuid);
						getMysql().deletePlayer(uuid);
					}

				}

				if (add) {
					uuids.put(name, uuid);
				}
			}
		});

		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
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

	private void loadVersionFile() {
		YamlConfiguration conf = getVersionFile();
		version = conf.getString("version", "Unknown");
		buildTime = conf.getString("time", "Unknown");
	}

	/**
	 * Reload
	 */
	public void reload() {
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		loadConfig();
		update();
		if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null) {
			getMysql().clearCache();
		}
		RewardHandler.getInstance().checkDelayedTimedRewards();
		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
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

	public void setAlternateUUIDLookUp(boolean alternateUUIDLookUp) {
		this.alternateUUIDLookUp = alternateUUIDLookUp;
	}

	/**
	 * @param autoDownload
	 *            the autoDownload to set
	 */
	public void setAutoDownload(boolean autoDownload) {
		this.autoDownload = autoDownload;
	}

	public void setAutoKillInvs(boolean autoKillInvs) {
		this.autoKillInvs = autoKillInvs;
	}

	/**
	 * @param checkNameMojang
	 *            the checkNameMojang to set
	 */
	public void setCheckNameMojang(boolean checkNameMojang) {
		this.checkNameMojang = checkNameMojang;
	}

	/**
	 * @param configData
	 *            the configData to set
	 */
	public void setConfigData(ConfigurationSection configData) {
		this.configData = configData;
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

	/**
	 * @param disableCheckOnWorldChange
	 *            the dsiableCheckOnWorldChange to set
	 */
	public void setDisableCheckOnWorldChange(boolean disableCheckOnWorldChange) {
		this.disableCheckOnWorldChange = disableCheckOnWorldChange;
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

	/**
	 * @param javascriptEngine
	 *            the javascriptEngine to set
	 */
	public void setJavascriptEngine(HashMap<String, Object> javascriptEngine) {
		this.javascriptEngine = javascriptEngine;
	}

	/**
	 * @param javascriptEngineRequests
	 *            the javascriptEngineRequests to set
	 */
	public void setJavascriptEngineRequests(ArrayList<JavascriptPlaceholderRequest> javascriptEngineRequests) {
		this.javascriptEngineRequests = javascriptEngineRequests;
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
			this.mysql.updateBatch();
			this.mysql.close();
			this.mysql = null;
		}
		this.mysql = mysql;
	}

	public void setNextPageTxt(String nextPageTxt) {
		this.nextPageTxt = nextPageTxt;
	}

	public void setPermPrefix(String permPrefix) {
		this.permPrefix = permPrefix;
	}

	public void setPlugin(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void setPrevPageTxt(String prevPageTxt) {
		this.prevPageTxt = prevPageTxt;
	}

	public void setPurgeMinimumDays(int purgeMinimumDays) {
		this.purgeMinimumDays = purgeMinimumDays;
	}

	public void setPurgeOldData(boolean purgeOldData) {
		this.purgeOldData = purgeOldData;
	}

	/**
	 * @param resourceId
	 *            the resourceId to set
	 */
	public void setResourceId(int resourceId) {
		this.resourceId = resourceId;
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
		RegisteredServiceProvider<Permission> rsp = plugin.getServer().getServicesManager()
				.getRegistration(Permission.class);
		perms = rsp.getProvider();
		return perms != null;
		/*
		 * RegisteredServiceProvider<Permission> rsp =
		 * Bukkit.getServer().getServicesManager() .getRegistration(Permission.class);
		 * perms = rsp.getProvider(); return perms != null;
		 */
	}

	/**
	 * Update.
	 */
	public void update() {
		TimeChecker.getInstance().update();
	}

	public void userStartup() {
		Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), new Runnable() {

			@Override
			public void run() {
				debug("User Startup starting");
				for (UserStartup start : userStartup) {
					start.onStart();
				}
				ArrayList<User> users = new ArrayList<User>();
				for (String uuid : UserManager.getInstance().getAllUUIDs()) {
					User user = UserManager.getInstance().getUser(new UUID(uuid));
					users.add(user);
					for (UserStartup start : userStartup) {
						start.onStartUp(user);
					}
				}
				for (UserStartup start : userStartup) {
					start.setUsers(users);
					start.onFinish();
				}
			}
		}, 30);
	}
}
