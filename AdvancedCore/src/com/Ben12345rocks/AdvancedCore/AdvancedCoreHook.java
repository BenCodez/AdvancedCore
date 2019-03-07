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
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Backups.BackupHandle;
import com.Ben12345rocks.AdvancedCore.CommandAPI.TabCompleteHandle;
import com.Ben12345rocks.AdvancedCore.CommandAPI.TabCompleteHandler;
import com.Ben12345rocks.AdvancedCore.Commands.Executor.ValueRequestInputCommand;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.AuthMeLogin;
import com.Ben12345rocks.AdvancedCore.Listeners.PlayerJoinEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PluginUpdateVersionEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WorldChangeEvent;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardHandler;
import com.Ben12345rocks.AdvancedCore.ServerHandle.CraftBukkitHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.IServerHandle;
import com.Ben12345rocks.AdvancedCore.ServerHandle.SpigotHandle;
import com.Ben12345rocks.AdvancedCore.Thread.Thread;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeType;
import com.Ben12345rocks.AdvancedCore.UserManager.UUID;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStartup;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;
import com.Ben12345rocks.AdvancedCore.UserStorage.mysql.MySQL;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.Column;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.DataType;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.Database;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.Table;
import com.Ben12345rocks.AdvancedCore.Util.Effects.FireworkHandler;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptPlaceholderRequest;
import com.Ben12345rocks.AdvancedCore.Util.Logger.Logger;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.PluginMessage.PluginMessage;
import com.Ben12345rocks.AdvancedCore.Util.Sign.SignMenu;
import com.Ben12345rocks.AdvancedCore.Util.Updater.UpdateDownloader;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;

import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class AdvancedCoreHook {
	private static AdvancedCoreHook instance = new AdvancedCoreHook();

	public static AdvancedCoreHook getInstance() {
		return instance;
	}

	@Getter
	private ConcurrentHashMap<String, String> uuidNameCache;

	@Getter
	private SignMenu signMenu;
	@Getter
	@Setter
	private JavaPlugin plugin;
	@Getter
	private boolean placeHolderAPIEnabled;

	private Database database;
	@Getter
	private MySQL mysql;

	@Getter
	private IServerHandle serverHandle;
	@Getter
	private Logger logger;

	@Getter
	private Timer timer = new Timer();

	@Getter
	@Setter
	private ArrayList<JavascriptPlaceholderRequest> javascriptEngineRequests = new ArrayList<JavascriptPlaceholderRequest>();
	@Getter
	private String version = "";
	@Getter
	private String buildTime = "";

	@Getter
	@Setter
	private String jenkinsSite = "";

	@Getter
	@Setter
	private HashMap<String, Object> javascriptEngine = new HashMap<String, Object>();

	@Getter
	private Economy econ = null;

	@Getter
	private Permission perms;

	@Getter
	private AdvancedCoreConfigOptions options = new AdvancedCoreConfigOptions();

	private ArrayList<UserStartup> userStartup = new ArrayList<UserStartup>();

	@Getter
	private ArrayList<String> bannedPlayers = new ArrayList<String>();

	@Getter
	private boolean authMeLoaded = false;
	
	private AdvancedCoreHook() {
	}

	public void addUserStartup(UserStartup start) {
		userStartup.add(start);
	}

	public void allowDownloadingFromSpigot(int resourceId) {
		getOptions().setResourceId(resourceId);
	}

	private void checkAutoUpdate() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (getOptions().isAutoDownload() && getOptions().getResourceId() != 0) {
					UpdateDownloader.getInstance().checkAutoDownload(getPlugin(), getOptions().getResourceId());
				}
			}
		});

	}

	private void checkPlaceHolderAPI() {
		Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), new Runnable() {

			@Override
			public void run() {
				if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
					placeHolderAPIEnabled = true;
					debug("PlaceholderAPI found, will attempt to parse placeholders");
				} else {
					placeHolderAPIEnabled = false;
					debug("PlaceholderAPI not found, PlaceholderAPI placeholders will not work");
				}
			}
		});

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
		if (getOptions().isDebug()) {
			e.printStackTrace();
			if (logger != null) {
				if (getOptions().isLogDebugToFile()) {
					String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
					logger.logToFile(str + " [" + plugin.getName() + "] ExceptionDebug: " + e.getMessage());
				}
			} else {
				loadLogger();
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
		if (getOptions().isDebug()) {
			plug.getLogger().info("Debug: " + msg);
			if (getOptions().isDebugIngame()) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.hasPermission(plugin.getName() + ".Debug")) {
						player.sendMessage(
								StringUtils.getInstance().colorize("&c" + plug.getName() + " Debug: " + msg));
					}
				}
			}
		}
		if (logger != null) {
			if (getOptions().isLogDebugToFile()) {
				String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
				logger.logToFile(str + " [" + plug.getName() + "] Debug: " + msg);
			}
		} else {
			loadLogger();
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
		if (getOptions().isExtraDebug()) {
			debug(plug, "[Extra] " + msg);
		}
	}

	public void extraDebug(String msg) {
		if (getOptions().isExtraDebug()) {
			debug(plugin, "[Extra] " + msg);
		}
	}

	public Server getServer() {
		return getPlugin().getServer();
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
		return getOptions().getStorageType();
	}

	public UserManager getUserManager() {
		return UserManager.getInstance();
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

	public void loadAutoUpdateCheck() {
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				checkAutoUpdate();
			}
		}, 20, 20 * 1000 * 60 * 60);
	}

	private void loadConfig() {
		getOptions().load();
		loadUserAPI(getOptions().getStorageType());
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
		getOptions().setPermPrefix(plugin.getName());
		checkPlaceHolderAPI();
		loadHandle();
		loadVault();
		loadEvents();
		TimeChecker.getInstance().loadTimer(2);
		ServerData.getInstance().setup();
		RewardHandler.getInstance().addRewardFolder(new File(plugin.getDataFolder(), "Rewards"));
		RewardHandler.getInstance().loadInjectedRewards();
		loadValueRequestInputCommands();
		checkPluginUpdate();
		RewardHandler.getInstance().checkDelayedTimedRewards();
		loadAutoUpdateCheck();
		loadVersionFile();
		loadTabComplete();

		loadConfig();

		if (getOptions().isPreloadSkulls()) {
			PlayerUtils.getInstance().loadSkulls();
		}

		UserManager.getInstance().purgeOldPlayers();

		userStartup();

		for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
			bannedPlayers.add(p.getUniqueId().toString());
		}

		Bukkit.getPluginManager().registerEvents(BackupHandle.getInstance(), getPlugin());

		if (Bukkit.getPluginManager().getPlugin("authme") != null) {
			authMeLoaded = true;
			Bukkit.getPluginManager().registerEvents(new AuthMeLogin(), getPlugin());
		}

		debug("Using AdvancedCore '" + getVersion() + "' built on '" + getBuildTime() + "'");
	}

	/**
	 * Load logger
	 */
	public void loadLogger() {
		if (getOptions().isLogDebugToFile() && logger == null && plugin != null) {
			logger = new Logger(plugin, new File(plugin.getDataFolder(), "Log" + File.separator + "Log.txt"));
		}
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
				.addTabCompleteOption(new TabCompleteHandle("(AllPlayer)", new ArrayList<String>()) {

					@Override
					public void reload() {
						ArrayList<String> players = new ArrayList<String>();
						for (String name : AdvancedCoreHook.getInstance().getUuidNameCache().values()) {
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
				.addTabCompleteOption(new TabCompleteHandle("(Player)", new ArrayList<String>()) {

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
				for (String name : AdvancedCoreHook.getInstance().getUuidNameCache().keySet()) {
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
		TabCompleteHandler.getInstance().addTabCompleteOption("(Text)", options);
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

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(ChoiceReward)", options) {

			@Override
			public void reload() {
				ArrayList<String> rewards = new ArrayList<String>();
				for (Reward reward : RewardHandler.getInstance().getRewards()) {
					if (reward.isEnableChoices()) {
						rewards.add(reward.getRewardName());
					}
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
			Thread.getInstance().run(new Runnable() {

				@Override
				public void run() {
					setMysql(new MySQL(getPlugin().getName() + "_Users",
							getOptions().getConfigData().getConfigurationSection("MySQL")));
				}
			});
		}
	}

	private void loadUUIDs() {

		uuidNameCache = new ConcurrentHashMap<String, String>();

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
				if (uuidNameCache.containsKey(uuid)) {
					debug("Duplicate uuid? " + uuid);
				}

				if (name == null || name.equals("") || name.equals("Error getting name")) {
					debug("Invalid player name: " + uuid);
					add = false;
				} else {
					if (uuidNameCache.containsValue(name)) {
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
					uuidNameCache.put(uuid, name);
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

	public void loadVault() {
		Bukkit.getScheduler().runTaskLater(getPlugin(), new Runnable() {

			@Override
			public void run() {
				if (setupEconomy()) {
					plugin.getLogger().info("Successfully hooked into vault economy!");
				} else {
					plugin.getLogger().warning("Failed to hook into vault economy");
				}

				if (setupPermissions()) {
					plugin.getLogger().info("Hooked into vault permissions");
				} else {
					plugin.getLogger().warning("Failed to hook into vault permissions");
				}
			}
		}, 5);
	}

	private void loadVersionFile() {
		YamlConfiguration conf = getVersionFile();
		version = conf.getString("version", "Unknown");
		buildTime = conf.getString("time", "Unknown");
	}

	public void registerBungeeChannels() {
		getServer().getMessenger().registerOutgoingPluginChannel(getPlugin(),
				getPlugin().getName().toLowerCase() + ":" + getPlugin().getName().toLowerCase());
		getServer().getMessenger().registerIncomingPluginChannel(getPlugin(),
				getPlugin().getName().toLowerCase() + ":" + getPlugin().getName().toLowerCase(),
				PluginMessage.getInstance());
	}

	/**
	 * Reload
	 */
	public void reload() {
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		loadConfig();
		if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null) {
			getMysql().clearCache();
		}
		TimeChecker.getInstance().update();
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

	/**
	 * @param configData
	 *            the configData to set
	 */
	public void setConfigData(ConfigurationSection configData) {
		getOptions().setConfigData(configData);
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
		if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Permission> rsp = plugin.getServer().getServicesManager()
				.getRegistration(Permission.class);
		if (rsp == null) {
			return false;
		}
		perms = rsp.getProvider();
		return perms != null;
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
