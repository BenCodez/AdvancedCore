package com.bencodez.advancedcore;

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
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.bencodez.advancedcore.api.backup.BackupHandle;
import com.bencodez.advancedcore.api.cmi.CMIHandler;
import com.bencodez.advancedcore.api.geyser.GeyserHandle;
import com.bencodez.advancedcore.api.hologram.HologramHandler;
import com.bencodez.advancedcore.api.inventory.BInventoryListener;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderRequest;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.permissions.LuckPermsHandle;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.skull.SkullHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.updater.UpdateDownloader;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.Database;
import com.bencodez.advancedcore.api.user.userstorage.sql.Table;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.sign.SignMenu;
import com.bencodez.advancedcore.api.yml.YMLConfig;
import com.bencodez.advancedcore.bungeeapi.pluginmessage.PluginMessage;
import com.bencodez.advancedcore.command.CommandLoader;
import com.bencodez.advancedcore.command.executor.ValueRequestInputCommand;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.listeners.AuthMeLogin;
import com.bencodez.advancedcore.listeners.LoginSecurityLogin;
import com.bencodez.advancedcore.listeners.PlayerJoinEvent;
import com.bencodez.advancedcore.listeners.PlayerShowListener;
import com.bencodez.advancedcore.listeners.PluginUpdateVersionEvent;
import com.bencodez.advancedcore.listeners.WorldChangeEvent;
import com.bencodez.advancedcore.logger.Logger;
import com.bencodez.simpleapi.command.TabCompleteHandle;
import com.bencodez.simpleapi.command.TabCompleteHandler;
import com.bencodez.simpleapi.debug.DebugLevel;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.nms.NMSManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.utils.PluginUtils;

import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public abstract class AdvancedCorePlugin extends JavaPlugin {

	private static AdvancedCorePlugin javaPlugin;

	public static AdvancedCorePlugin getInstance() {
		return javaPlugin;
	}

	@Getter
	private CommandLoader advancedCoreCommandLoader;

	@Getter
	private boolean authMeLoaded = false;

	@Getter
	private boolean loginSecurityLoaded = false;

	@Getter
	private ArrayList<String> bannedPlayers = new ArrayList<String>();

	@Getter
	private String buildTime = "";

	@Getter
	@Setter
	private String bungeeChannel;

	@Getter
	private CMIHandler cmiHandle;

	private Database database;

	@Getter
	private Economy econ = null;

	@Getter
	private FullInventoryHandler fullInventoryHandler;

	@Getter
	private HologramHandler hologramHandler;

	@Getter
	@Setter
	private HashMap<String, Object> javascriptEngine = new HashMap<String, Object>();
	@Getter
	@Setter
	private ArrayList<JavascriptPlaceholderRequest> javascriptEngineRequests = new ArrayList<JavascriptPlaceholderRequest>();

	@Getter
	@Setter
	private String jenkinsSite = "";

	@Getter
	@Setter
	private boolean loadRewards = true;

	@Getter
	@Setter
	private boolean loadServerData = true;

	@Getter
	@Setter
	private boolean loadUserData = true;
	@Getter
	private MySQL mysql;
	@Getter
	private AdvancedCoreConfigOptions options = new AdvancedCoreConfigOptions();

	@Getter
	private Permission perms;

	@Getter
	private boolean placeHolderAPIEnabled;

	@Getter
	private Logger pluginLogger;

	@Getter
	private PluginMessage pluginMessaging;

	@Getter
	private ServerData serverDataFile;

	@Getter
	private SignMenu signMenu;

	@Getter
	private TimeChecker timeChecker;

	@Getter
	private ScheduledExecutorService timer;

	@Getter
	private ScheduledExecutorService loginTimer;

	@Getter
	private ScheduledExecutorService inventoryTimer;

	@Setter
	private UserManager userManager;

	private ArrayList<UserStartup> userStartup = new ArrayList<UserStartup>();

	@Getter
	private ConcurrentHashMap<String, String> uuidNameCache;

	@Getter
	private String version = "";

	@Getter
	private String advancedCoreBuildNumber = "NOTSET";

	@Getter
	private PermissionHandler permissionHandler;

	@Getter
	private RewardHandler rewardHandler;

	@Getter
	private LuckPermsHandle luckPermsHandle;

	@Getter
	private BukkitScheduler bukkitScheduler;

	public void addUserStartup(UserStartup start) {
		userStartup.add(start);
	}

	public void allowDownloadingFromSpigot(int resourceId) {
		getOptions().setResourceId(resourceId);
	}

	private void checkAutoUpdate() {
		getBukkitScheduler().runTaskAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				if (getOptions().isAutoDownload() && getOptions().getResourceId() != 0) {
					UpdateDownloader.getInstance().checkAutoDownload(javaPlugin, getOptions().getResourceId());
				}
			}
		});

	}

	private void checkCMI() {
		getBukkitScheduler().runTaskAsynchronously(javaPlugin, new Runnable() {

			@Override
			public void run() {
				if (Bukkit.getPluginManager().getPlugin("CMI") != null) {
					getLogger().info("CMI found, loading hook");
					cmiHandle = new CMIHandler();
				}
			}
		});
	}

	private void checkPlaceHolderAPI() {
		getBukkitScheduler().runTaskAsynchronously(this, new Runnable() {

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
		if (!loadServerData) {
			return;
		}
		getBukkitScheduler().runTaskAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				String version = getServerDataFile().getPluginVersion(javaPlugin);
				if (!version.equals(javaPlugin.getDescription().getVersion())) {
					PluginUpdateVersionEvent event = new PluginUpdateVersionEvent(javaPlugin, version);
					Bukkit.getServer().getPluginManager().callEvent(event);
				}
				getServerDataFile().setPluginVersion(javaPlugin);
			}
		});

	}

	public void convertDataStorage(UserStorage from, UserStorage to) {
		debug("Starting convert process");
		if (to == null) {
			throw new RuntimeException("Invalid Storage Method");
		}
		loadUserAPI(from);
		loadUserAPI(to);

		if (getMysql() != null) {
			getMysql().clearCacheBasic();
		}

		HashMap<UUID, ArrayList<Column>> cols = getUserManager().getAllKeys(from);
		Queue<Entry<UUID, ArrayList<Column>>> players = new LinkedList<Entry<UUID, ArrayList<Column>>>(cols.entrySet());

		while (players.size() > 0) {
			Entry<UUID, ArrayList<Column>> entry = players.poll();
			AdvancedCoreUser user = getUserManager().getUser(entry.getKey(), false);
			user.dontCache();

			user.getData().setValues(to, user.getData().convert(entry.getValue()));
			debug("Finished convert for " + user.getUUID() + ", " + players.size() + " more left to go!");

			if (players.size() % 50 == 0) {
				getLogger().info("Working on converting data, about " + players.size() + " left to go!");
			}
		}
		debug("Convert finished!");

	}

	public void debug(DebugLevel debugLevel, String debug) {
		if (debugLevel.equals(DebugLevel.EXTRA)) {
			debug = "ExtraDebug: " + debug;
		} else if (debugLevel.equals(DebugLevel.INFO)) {
			debug = "Debug: " + debug;
		} else if (debugLevel.equals(DebugLevel.DEV)) {
			debug = "Developer Debug: " + debug;
		}

		if (getOptions().getDebug().isDebug(debugLevel)) {
			getLogger().info(debug);
		}
		if (getOptions().isDebugIngame()) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.hasPermission(this.getName() + ".Debug")) {
					player.sendMessage(MessageAPI.colorize("&c" + getName() + " Debug: " + debug));
				}
			}
		}
		if (getOptions().isLogDebugToFile()) {
			if (pluginLogger == null) {
				loadLogger();
			}
			String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
			pluginLogger.logToFile(str + ":" + debug);
		}
	}

	/**
	 * Show exception in console if debug is on
	 *
	 * @param e Exception
	 */
	public void debug(Exception e) {
		if (getOptions().getDebug().isDebug()) {
			e.printStackTrace();
		}
		if (getOptions().isLogDebugToFile()) {
			if (pluginLogger != null) {
				String str = new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(Calendar.getInstance().getTime());
				pluginLogger.logToFile(str + " [" + this.getName() + "] ExceptionDebug: " + e.getMessage());
			} else {
				loadLogger();
			}
		}
	}

	public void debug(String debug) {
		debug(DebugLevel.INFO, debug);
	}

	public void devDebug(String debug) {
		debug(DebugLevel.DEV, debug);
	}

	public void extraDebug(String debug) {
		debug(DebugLevel.EXTRA, debug);
	}

	public Table getSQLiteUserTable() {
		if (database == null && loadUserData) {
			loadUserAPI(getStorageType());
		}
		if (loadUserData) {
			for (Table table : database.getTables()) {
				if (table.getName().equalsIgnoreCase("Users")) {
					return table;
				}
			}
		}
		return null;
	}

	public UserStorage getStorageType() {
		return getOptions().getStorageType();
	}

	public UserManager getUserManager() {
		if (userManager == null) {
			userManager = new UserManager(this);
		}
		return userManager;
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
						if (name.equals("advancedcoreversion.yml")) {
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

	public void loadAdvancedCoreEvents() {
		if (loadUserData) {
			Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this), this);
			Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(this), this);
		}

		Bukkit.getPluginManager().registerEvents(FireworkHandler.getInstance(), this);
		Bukkit.getPluginManager().registerEvents(new BInventoryListener(this), this);
	}

	public void loadAutoUpdateCheck() {
		long delay = 60 * 60;
		timer.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				checkAutoUpdate();
			}
		}, delay, delay, TimeUnit.SECONDS);
	}

	private void loadConfig(boolean userStorage) {
		getOptions().load(this);
		if (loadUserData && userStorage) {
			loadUserAPI(getOptions().getStorageType());
		}
	}

	private void loadHandle() {

		if (Bukkit.getOnlineMode()) {
			debug("Server in online mode");
		} else {
			debug("Server in offline mode");
		}
	}

	@Getter
	@Setter
	private boolean loadGeyserAPI = true;

	@Getter
	@Setter
	private boolean loadLuckPerms = true;

	@Getter
	private GeyserHandle geyserHandle;

	@Getter
	@Setter
	private boolean loadSkullHandler = true;

	/**
	 * Load AdvancedCore hook
	 */
	@SuppressWarnings("deprecation")
	public void loadHook() {
		serverDataFile = new ServerData(this);

		hologramHandler = new HologramHandler(this);

		if (loadLuckPerms) {
			if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
				luckPermsHandle = new LuckPermsHandle();
				luckPermsHandle.load(this);
			}
		}

		loadSignAPI();
		loadUUIDs();
		getOptions().setPermPrefix(this.getName());
		checkPlaceHolderAPI();
		checkCMI();
		loadHandle();
		loadVault();
		loadAdvancedCoreEvents();
		timeChecker = new TimeChecker(this);
		if (loadServerData) {
			serverDataFile.setup();
			timeChecker.loadTimer();
		}

		// load usermanager
		getUserManager();
		permissionHandler = new PermissionHandler(this);

		loadConfig(true);

		if (loadGeyserAPI) {
			geyserHandle = new GeyserHandle();
			geyserHandle.load();
		}

		rewardHandler = RewardHandler.getInstance();
		rewardHandler.loadInjectedRewards();
		rewardHandler.loadInjectedRequirements();
		if (loadRewards) {
			File rewardsFolder = new File(this.getDataFolder(), "Rewards");
			rewardHandler.addRewardFolder(rewardsFolder, false, true);
			File file = new File(rewardsFolder.getAbsolutePath() + File.separator + "DirectlyDefined");
			rewardHandler.addRewardFolder(file, false, false);
			rewardHandler.loadRewards();
		}

		loadValueRequestInputCommands();
		checkPluginUpdate();
		loadAutoUpdateCheck();
		loadVersionFile();

		getUserManager().purgeOldPlayers();

		try {
			if (loadSkullHandler) {
				SkullHandler.getInstance().load();
			}
		} catch (Exception e) {
			getLogger().warning("Failed to load skull handler");
			e.printStackTrace();
		}

		userStartup();
		loadTabComplete();

		fullInventoryHandler = new FullInventoryHandler(this);

		for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
			bannedPlayers.add(p.getUniqueId().toString());
		}

		Bukkit.getPluginManager().registerEvents(BackupHandle.getInstance(), this);

		if (Bukkit.getPluginManager().getPlugin("authme") != null) {
			authMeLoaded = true;
			Bukkit.getPluginManager().registerEvents(new AuthMeLogin(this), this);
		}
		if (Bukkit.getPluginManager().getPlugin("LoginSecurity") != null) {
			loginSecurityLoaded = true;
			Bukkit.getPluginManager().registerEvents(new LoginSecurityLogin(this), this);
		}

		try {
			Class.forName("de.myzelyam.api.vanish.PostPlayerShowEvent");
			registerEvents(new PlayerShowListener(this));
			debug("Loaded PostPlayerShowEvent");
		} catch (ClassNotFoundException e) {
			debug("Not loading PostPlayerShowEvent");
		}

		String buildNumberMsg = "";
		if (!advancedCoreBuildNumber.equals("NOTSET")) {
			buildNumberMsg = ", build number: " + advancedCoreBuildNumber + ", ";
		}

		debug("Using AdvancedCore '" + getVersion() + "' built on '" + getBuildTime() + "' " + buildNumberMsg
				+ " Spigot Version: " + Bukkit.getVersion() + " Total RAM: " + PluginUtils.getMemory() + " Free RAM: "
				+ PluginUtils.getFreeMemory());

		debug(DebugLevel.INFO, "Debug Level: " + getOptions().getDebug().toString());
	}

	/**
	 * Load logger
	 */
	public void loadLogger() {
		if (getOptions().isLogDebugToFile() && pluginLogger == null) {
			pluginLogger = new Logger(this, new File(this.getDataFolder(), "Log" + File.separator + "Log.txt"));
		}
	}

	private void loadSignAPI() {
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null
				&& !NMSManager.getInstance().isVersion("1.8", "1.9", "1.10", "1.11")) {
			if (Bukkit.getPluginManager().getPlugin("ProtocolLib").isEnabled()) {
				try {
					this.signMenu = new SignMenu(this);
				} catch (Exception e) {
					getLogger().warning("ProtocolLib may not be up to date? Failed to load SignMenu");
					debug(e);
				}
			}
		}
	}

	public void loadTabComplete() {
		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(AllPlayer)", new ArrayList<String>()) {

					@Override
					public void reload() {
						ArrayList<String> players = new ArrayList<String>();
						for (String name : getUuidNameCache().values()) {
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
						ArrayList<String> list = new ArrayList<String>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}

					@Override
					public void updateReplacements() {
						ArrayList<String> list = new ArrayList<String>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}
				}.updateOnLoginLogout());

		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(PlayerExact)", new ArrayList<String>()) {

					@Override
					public void reload() {
						ArrayList<String> list = new ArrayList<String>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}

					@Override
					public void updateReplacements() {
						ArrayList<String> list = new ArrayList<String>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}
				}.updateOnLoginLogout());

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(uuid)", new ArrayList<String>()) {

			@Override
			public void reload() {
				ArrayList<String> uuids = new ArrayList<String>();
				for (String name : getUuidNameCache().keySet()) {
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
		}.updateEveryXMinutes(getTimer(), 30));

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
				for (Reward reward : rewardHandler.getRewards()) {
					if (!reward.getConfig().isDirectlyDefinedReward()) {
						rewards.add(reward.getRewardName());
					}
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
				for (Reward reward : rewardHandler.getRewards()) {
					if (reward.getConfig().getEnableChoices()) {
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

	@SuppressWarnings("deprecation")
	public void loadUserAPI(UserStorage storageType) {
		if (storageType.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column key = new Column("uuid", DataType.STRING);
			columns.add(key);
			Table table = new Table(this, "Users", columns, key);
			database = new Database(this, "Users", table);
			table.addCustomColumns();
		} else if (storageType.equals(UserStorage.MYSQL)) {
			setMysql(new MySQL(javaPlugin, javaPlugin.getName() + "_Users",
					getOptions().getYmlConfig().getData().getConfigurationSection("MySQL")));
		} else if (storageType.equals(UserStorage.FLAT)) {
			getLogger().severe("Detected using FLAT storage, this will be removed in the future!");
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
				debug("Starting background uuid/name task");
			}

			@Override
			public void onStartUp(AdvancedCoreUser user) {
				String uuid = user.getUUID();
				String name = user.getData().getString("PlayerName", false, true);
				boolean add = true;
				if (uuidNameCache.containsKey(uuid)) {
					debug("Duplicate uuid? " + uuid + "/" + name);
				}

				if (name == null || name.equals("") || name.equals("Error getting name") || name.equals("null")) {
					// extraDebug("Invalid player name: " + uuid);
					add = false;
				} else {
					if (uuidNameCache.containsValue(name)) {
						debug("Duplicate player name?" + uuid + "/" + name);
					}
				}
				if (uuid == null || uuid.equals("")) {
					debug("Invalid uuid: " + uuid);
					add = false;
				}

				if (add) {
					uuidNameCache.put(uuid, name);
				}
			}
		});

		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
		TabCompleteHandler.getInstance().loadTimer(getTimer());
	}

	public void loadValueRequestInputCommands() {
		CommandLoader.getInstance().loadValueRequestCommands();
		try {
			final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

			bukkitCommandMap.setAccessible(true);
			CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
			commandMap.register(this.getName() + "valuerequestinput",
					new ValueRequestInputCommand(this, this.getName() + "valuerequestinput"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadVault() {
		getBukkitScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				if (setupEconomy()) {
					getLogger().info("Successfully hooked into vault economy!");
				} else {
					getLogger().warning("Failed to hook into vault economy");
				}

				if (setupPermissions()) {
					getLogger().info("Hooked into vault permissions");

					rewardHandler.addInjectedRequirements(new RequirementInjectString("VaultGroup", "") {

						@Override
						public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String type,
								RewardOptions rewardOptions) {
							if (type.equals("")) {
								return true;
							}
							String group = "";
							if (!rewardOptions.isGiveOffline() && user.isOnline()) {
								group = getPerms().getPrimaryGroup(user.getPlayer());
							} else {
								group = getPerms().getPrimaryGroup(null, user.getOfflinePlayer());
							}
							if (group.equalsIgnoreCase(type)) {
								return true;
							}
							return false;
						}
					}.priority(100).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
							new EditGUIValueString("VaultGroup", null) {

								@Override
								public void setValue(Player player, String value) {
									RewardEditData reward = (RewardEditData) getInv().getData("Reward");
									reward.setValue(getKey(), value);
									reloadAdvancedCore(false);
								}
							}.addOptions(getPerms().getGroups()))));
				} else {
					getLogger().warning("Failed to hook into vault permissions");
				}
			}
		}, 5);
	}

	private void loadVersionFile() {
		YamlConfiguration conf = getVersionFile();
		version = conf.getString("version", "Unknown");
		buildTime = conf.getString("time", "Unknown");
		advancedCoreBuildNumber = conf.getString("buildnumber", "NOTSET");
	}

	@Override
	public void onDisable() {

		if (getOptions().getStorageType().equals(UserStorage.MYSQL)) {
			getMysql().close();
		}
		getServerDataFile().setLastUpdated();
		timer.shutdown();
		loginTimer.shutdown();
		timeChecker.getTimer().shutdown();
		inventoryTimer.shutdown();
		try {
			getLogger().info("Allowing background tasks to finish, this could take up to 5 seconds");
			loginTimer.awaitTermination(2, TimeUnit.SECONDS);
			timer.awaitTermination(2, TimeUnit.SECONDS);
			timeChecker.getTimer().awaitTermination(2, TimeUnit.SECONDS);
			inventoryTimer.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			debug(e);
		}
		rewardHandler.shutdown();
		loginTimer.shutdownNow();
		timer.shutdownNow();
		inventoryTimer.shutdownNow();
		timeChecker.getTimer().shutdownNow();
		onUnLoad();
		SkullHandler.getInstance().close();
		fullInventoryHandler.save();
		unRegisterValueRequest();

		hologramHandler.onShutDown();

		if (getPermissionHandler() != null) {
			getPermissionHandler().shutDown();
		}

		javaPlugin = null;
	}

	@Override
	public void onEnable() {
		javaPlugin = this;
		bukkitScheduler = new BukkitScheduler(this);
		timer = Executors.newSingleThreadScheduledExecutor();
		loginTimer = Executors.newSingleThreadScheduledExecutor();
		advancedCoreCommandLoader = CommandLoader.getInstance();
		inventoryTimer = Executors.newSingleThreadScheduledExecutor();

		onPreLoad();
		loadHook();
		onPostLoad();
		getRewardHandler().checkSubRewards();
	}

	public abstract void onPostLoad();

	public abstract void onPreLoad();

	public abstract void onUnLoad();

	public void registerBungeeChannels(String name) {
		this.bungeeChannel = name;
		getServer().getMessenger().registerOutgoingPluginChannel(this, name);
		pluginMessaging = new PluginMessage(this);
		getServer().getMessenger().registerIncomingPluginChannel(this, name, pluginMessaging);
		getLogger().info("Loaded plugin message channels: " + name);
	}

	public void registerEvents(Listener listener) {
		Bukkit.getPluginManager().registerEvents(listener, this);
	}

	public abstract void reload();

	@Deprecated
	public void reloadAdvancedCore() {
		reloadAdvancedCore(false);
	}

	public void reloadAdvancedCore(boolean userStorage) {
		getServerDataFile().reloadData();
		rewardHandler.loadRewards();
		loadConfig(userStorage);
		if (userStorage) {
			getUserManager().getDataManager().clearCache();
			if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null) {
				getMysql().clearCacheBasic();
			}
		}
		timeChecker.update();
		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
		getRewardHandler().checkSubRewards();
	}

	/**
	 * @param configData the configData to set
	 */
	@Deprecated
	public void setConfigData(ConfigurationSection configData) {
		getOptions().setYmlConfig(new YMLConfig(this, configData) {

			@Override
			public void setValue(String path, Object value) {

			}

			@Override
			public void saveData() {

			}

			@Override
			public void createSection(String key) {

			}
		});
	}

	public void setConfigData(YMLConfig ymlConfig) {
		getOptions().setYmlConfig(ymlConfig);
	}

	public boolean isMySQLOkay() {
		if (getStorageType().equals(UserStorage.MYSQL)) {
			return mysql != null;
		}
		return true;
	}

	/**
	 * @param mysql the mysql to set
	 */
	public void setMysql(MySQL mysql) {
		if (this.mysql != null) {
			this.mysql.close();
			this.mysql = null;
		}
		this.mysql = mysql;
	}

	private boolean setupEconomy() {
		if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	private boolean setupPermissions() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Permission> rsp = this.getServer().getServicesManager()
				.getRegistration(Permission.class);
		if (rsp == null) {
			return false;
		}
		perms = rsp.getProvider();
		return perms != null;
	}

	public void unRegisterValueRequest() {
		try {
			final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

			bukkitCommandMap.setAccessible(true);
			CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

			commandMap.getCommand(this.getName() + "valuerequestinput").unregister(commandMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void userStartup() {
		if (!loadUserData) {
			debug("Not loading userdata");
			return;
		}
		rewardHandler.startup();
		getBukkitScheduler().runTaskLaterAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				debug("User Startup starting");
				for (UserStartup start : userStartup) {
					start.onStart();
				}
				boolean onlineMode = getOptions().isOnlineMode();
				int offlineAmount = 0;
				HashMap<UUID, ArrayList<Column>> cols = getUserManager().getAllKeys();
				for (Entry<UUID, ArrayList<Column>> playerData : cols.entrySet()) {
					String uuid = playerData.getKey().toString();
					if (onlineMode) {
						if (uuid.charAt(14) == '3') {
							offlineAmount++;
						}
					}
					if (javaPlugin != null) {
						if (uuid != null) {
							AdvancedCoreUser user = getUserManager().getUser(UUID.fromString(uuid), false);
							if (user != null) {
								user.dontCache();
								user.updateTempCacheWithColumns(playerData.getValue());
								for (UserStartup start : userStartup) {
									start.onStartUp(user);
								}
								user.clearTempCache();
								cols.put(playerData.getKey(), null);
								user = null;
							}
						}
					}
				}
				cols.clear();
				cols = null;
				for (UserStartup start : userStartup) {
					start.onFinish();
				}
				if (offlineAmount > 0 && onlineMode) {
					debug("Detected offline uuids in a online server, this could mean an error for your server setup: "
							+ offlineAmount);
				}
				debug("User Startup finished");
			}
		}, 5);
	}
}
