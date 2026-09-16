package com.bencodez.advancedcore;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.bencodez.advancedcore.api.backup.BackupHandle;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.cmi.CMIHandler;
import com.bencodez.advancedcore.api.hologram.HologramHandler;
import com.bencodez.advancedcore.api.inventory.BInventoryListener;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderRequest;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.permissions.LuckPermsHandle;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserRuntimeBootstrap;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.command.CommandLoader;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.listeners.AuthMeLogin;
import com.bencodez.advancedcore.listeners.LoginSecurityLogin;
import com.bencodez.advancedcore.listeners.NLoginAuthenticate;
import com.bencodez.advancedcore.listeners.PlayerJoinEvent;
import com.bencodez.advancedcore.listeners.PlayerShowListener;
import com.bencodez.advancedcore.listeners.PluginUpdateVersionEvent;
import com.bencodez.advancedcore.listeners.WorldChangeEvent;
import com.bencodez.simpleapi.command.TabCompleteHandle;
import com.bencodez.simpleapi.command.TabCompleteHandler;
import com.bencodez.simpleapi.debug.DebugLevel;
import com.bencodez.simpleapi.dialog.UniDialogService;
import com.bencodez.simpleapi.file.YMLConfig;
import com.bencodez.simpleapi.messages.actionbar.ActionBar;
import com.bencodez.simpleapi.messages.actionbar.ActionBarManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.servercomm.pluginmessage.PluginMessage;
import com.bencodez.simpleapi.skull.SkullCacheHandler;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.sqlite.Database;
import com.bencodez.simpleapi.sql.sqlite.Table;
import com.bencodez.simpleapi.utils.PluginUtils;
import com.bencodez.simpleapi.valuerequest.InputMethod;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract base class for plugins that use AdvancedCore. Provides core
 * functionality for user management, rewards, permissions, and more.
 */
public abstract class AdvancedCorePlugin extends JavaPlugin {

	private static AdvancedCorePlugin javaPlugin;

	/**
	 * Gets the singleton instance of this plugin.
	 * 
	 * @return the plugin instance
	 */
	public static AdvancedCorePlugin getInstance() {
		return javaPlugin;
	}

	/**
	 * Sets the singleton instance of this plugin.
	 * 
	 * @param plugin the plugin instance to set
	 */
	public static void setInstance(AdvancedCorePlugin plugin) {
		javaPlugin = plugin;
	}

	/**
	 * Handler for Vault integration.
	 */
	@Getter
	public VaultHandler vaultHandler;

	/**
	 * Command loader for AdvancedCore commands.
	 * 
	 * @return the command loader
	 */
	@Getter
	private CommandLoader advancedCoreCommandLoader;

	/**
	 * Handler for skull caching.
	 * 
	 * @return the skull cache handler
	 */
	@Getter
	private SkullCacheHandler skullCacheHandler;

	/**
	 * Whether AuthMe is loaded.
	 * 
	 * @return true if AuthMe is loaded
	 */
	@Getter
	private boolean authMeLoaded = false;

	/**
	 * Whether nLogin is loaded.
	 * 
	 * @return true if nLogin is loaded
	 */
	@Getter
	private boolean nLoginLoaded = false;

	/**
	 * Whether LoginSecurity is loaded.
	 * 
	 * @return true if LoginSecurity is loaded
	 */
	@Getter
	private boolean loginSecurityLoaded = false;

	/**
	 * List of banned player UUIDs.
	 * 
	 * @return list of banned player UUIDs
	 */
	@Getter
	private ArrayList<String> bannedPlayers = new ArrayList<>();

	/**
	 * Build time of AdvancedCore.
	 * 
	 * @return the build time string
	 */
	@Getter
	private String buildTime = "";

	/**
	 * BungeeCord channel name.
	 * 
	 * @return the bungee channel name
	 * @param bungeeChannel the channel name to set
	 */
	@Getter
	@Setter
	private String bungeeChannel;

	/**
	 * Handler for CMI integration.
	 * 
	 * @return the CMI handler
	 */
	@Getter
	private CMIHandler cmiHandle;

	private Database database;
	/**
	 * A coherent native-owner snapshot for callers which need both the selected
	 * storage kind and its provider. Shared-runtime replacement publishes this
	 * as one volatile value; callers must not combine a separately observed
	 * storage type with the mutable provider fields.
	 */
	public record UserStorageOwner(UserStorage storageType, MySQL mysql, UserTable table) {
		public UserStorageOwner {
			if (storageType == null) throw new IllegalArgumentException("storageType");
			if (storageType == UserStorage.MYSQL && mysql == null) throw new IllegalArgumentException("mysql");
			if (storageType == UserStorage.SQLITE && table == null) throw new IllegalArgumentException("table");
		}
	}
	private volatile UserStorageOwner nativeUserStorageOwner;
	/** Native owners that could not be closed after an otherwise successful replacement. */
	private final Object pendingNativeUserStorageCloseLock = new Object();
	private final ArrayList<NativeUserStorageClose> pendingNativeUserStorageCloses = new ArrayList<>();
	/** Coalesces public storage reload requests while a replacement is being prepared. */
	private Object userStorageReloadLock = new Object();
	private CompletionStage<Void> userStorageReload;
	/**
	 * Storage-backed tab completion cannot enumerate the user provider on the
	 * Bukkit thread during a shared-storage replacement. Keep at most one
	 * enumeration in flight and apply only the newest completed snapshot.
	 */
	private final UuidTabCompletionRefresh uuidTabCompletionRefresh = new UuidTabCompletionRefresh();

	/**
	 * Handler for full inventory management.
	 * 
	 * @return the full inventory handler
	 */
	@Getter
	private FullInventoryHandler fullInventoryHandler;

	/**
	 * Handler for hologram management.
	 * 
	 * @return the hologram handler
	 */
	@Getter
	private HologramHandler hologramHandler;

	/**
	 * JavaScript engine context map.
	 * 
	 * @return the javascript engine map
	 * @param javascriptEngine the javascript engine map to set
	 */
	@Getter
	@Setter
	private HashMap<String, Object> javascriptEngine = new HashMap<>();
	/**
	 * List of JavaScript placeholder requests.
	 * 
	 * @return the javascript engine requests list
	 * @param javascriptEngineRequests the requests list to set
	 */
	@Getter
	@Setter
	private ArrayList<JavascriptPlaceholderRequest> javascriptEngineRequests = new ArrayList<>();

	/**
	 * Whether rewards should be loaded.
	 * 
	 * @return true if rewards should be loaded
	 * @param loadRewards true to load rewards
	 */
	@Getter
	@Setter
	private boolean loadRewards = true;

	/**
	 * Whether server data should be loaded.
	 * 
	 * @return true if server data should be loaded
	 * @param loadServerData true to load server data
	 */
	@Getter
	@Setter
	private boolean loadServerData = true;

	/**
	 * Whether user data should be loaded.
	 * 
	 * @return true if user data should be loaded
	 * @param loadUserData true to load user data
	 */
	@Getter
	@Setter
	private boolean loadUserData = true;
	/**
	 * MySQL database connection.
	 * 
	 * @return the mysql connection
	 */
	@Getter
	private MySQL mysql;
	/**
	 * Configuration options for AdvancedCore.
	 * 
	 * @return the configuration options
	 */
	@Getter
	private AdvancedCoreConfigOptions options = new AdvancedCoreConfigOptions();

	/**
	 * Whether PlaceholderAPI is enabled.
	 * 
	 * @return true if PlaceholderAPI is enabled
	 */
	@Getter
	private boolean placeHolderAPIEnabled;

	/**
	 * Plugin messaging handler.
	 * 
	 * @return the plugin messaging handler
	 */
	@Getter
	private PluginMessage pluginMessaging;

	/**
	 * Server data file handler.
	 * 
	 * @return the server data file
	 */
	@Getter
	private ServerData serverDataFile;

	/**
	 * Time checker for scheduled operations.
	 * 
	 * @return the time checker
	 */
	@Getter
	private TimeChecker timeChecker;

	/**
	 * Main timer for scheduled tasks.
	 * 
	 * @return the timer
	 */
	@Getter
	private ScheduledExecutorService timer;

	/**
	 * Timer for login-related tasks.
	 * 
	 * @return the login timer
	 */
	@Getter
	private ScheduledExecutorService loginTimer;

	/**
	 * Timer for inventory-related tasks.
	 * 
	 * @return the inventory timer
	 */
	@Getter
	private ScheduledExecutorService inventoryTimer;

	/**
	 * User manager instance.
	 * 
	 * @param userManager the user manager to set
	 */
	@Setter
	private UserManager userManager;

	private ArrayList<UserStartup> userStartup = new ArrayList<>();

	/**
	 * Version string of AdvancedCore.
	 * 
	 * @return the version string
	 */
	@Getter
	private String advancedCoreVersion = "";

	/**
	 * Build number of AdvancedCore.
	 * 
	 * @return the build number
	 */
	@Getter
	private String advancedCoreBuildNumber = "NOTSET";

	/**
	 * Handler for permission management.
	 * 
	 * @return the permission handler
	 */
	@Getter
	private PermissionHandler permissionHandler;

	/**
	 * Handler for rewards system.
	 * 
	 * @return the reward handler
	 */
	@Getter
	private RewardHandler rewardHandler;

	/**
	 * Handler for LuckPerms integration.
	 * 
	 * @return the LuckPerms handler
	 */
	@Getter
	private LuckPermsHandle luckPermsHandle;

	/**
	 * Bukkit scheduler wrapper.
	 * 
	 * @return the bukkit scheduler
	 */
	@Getter
	private BukkitScheduler bukkitScheduler;

	/**
	 * Whether Bedrock API should be loaded.
	 * 
	 * @return true if Bedrock API should be loaded
	 * @param loadBedrockAPI true to load Bedrock API
	 */
	@Getter
	@Setter
	private boolean loadBedrockAPI = true;

	/**
	 * Whether LuckPerms should be loaded.
	 * 
	 * @return true if LuckPerms should be loaded
	 * @param loadLuckPerms true to load LuckPerms
	 */
	@Getter
	@Setter
	private boolean loadLuckPerms = true;

	/**
	 * Handler for Bedrock player name resolution.
	 * 
	 * @return the bedrock handler
	 */
	@Getter
	private BedrockNameResolver bedrockHandle;

	@Getter
	@Setter
	private UniDialogService dialogService;

	/**
	 * Whether skull handler should be loaded.
	 * 
	 * @return true if skull handler should be loaded
	 * @param loadSkullHandler true to load skull handler
	 */
	@Getter
	@Setter
	private boolean loadSkullHandler = true;

	/**
	 * Whether Vault should be loaded.
	 * 
	 * @return true if Vault should be loaded
	 * @param loadVault true to load Vault
	 */
	@Getter
	@Setter
	private boolean loadVault = true;

	/**
	 * Adds a user startup task to be executed on plugin startup.
	 * 
	 * @param start the startup task to add
	 */
	public void addUserStartup(UserStartup start) {
		userStartup.add(start);
	}

	/**
	 * Allows downloading plugin updates from Spigot.
	 * 
	 * @param resourceId the Spigot resource ID
	 */
	public void allowDownloadingFromSpigot(int resourceId) {
		getOptions().setResourceId(resourceId);
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

	/**
	 * Checks for plugin updates in the background.
	 */
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

	/**
	 * Converts data from one storage type to another.
	 * 
	 * @param from the source storage type
	 * @param to   the target storage type
	 */
	public void convertDataStorage(UserStorage from, UserStorage to) {
		if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("User storage conversion must run asynchronously; use convertDataStorageAsync");
		}
		getUserManager().getDataManager().runStorageMaintenance(() -> convertDataStorageNow(from, to));
	}

	/**
	 * Start an explicit SQL-to-SQL conversion without blocking the server thread.
	 * The result completes after the shared cache generation was flushed and the
	 * converter has finished; callers must not report success before then.
	 */
	public CompletionStage<Void> convertDataStorageAsync(UserStorage from, UserStorage to) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		try {
			getBukkitScheduler().runTaskAsynchronously(this, () -> {
				try {
					convertDataStorage(from, to);
					result.complete(null);
				} catch (Throwable failure) { result.completeExceptionally(failure); }
			});
		} catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
		return result;
	}

	private void convertDataStorageNow(UserStorage from, UserStorage to) {
		debug("Starting convert process");
		if (to == null) {
			throw new RuntimeException("Invalid Storage Method");
		}
		if (from == null) throw new RuntimeException("Invalid Storage Method");
		// Do not recreate an already active source before reading it. In particular,
		// setMysql closes the old connection, which made MYSQL-to-SQLITE conversion
		// enumerate a closed shared-route owner (and sometimes copy no users).
		UserStorageOwner activeOwner = getNativeUserStorageOwner();
		MySQL activeMysql = mysql;
		Database activeDatabase = database;
		boolean restoreActiveOwner = activeOwner != null
				&& (activeOwner.storageType() == from || activeOwner.storageType() == to);
		boolean targetAlreadyActive = restoreActiveOwner && activeOwner.storageType() == to;
		NativeUserStorageClose temporarySource = null;
		try {
			if (activeOwner == null || activeOwner.storageType() != from) loadUserAPI(from);

			if (getMysql() != null) getMysql().clearCacheBasic();

			HashMap<UUID, ArrayList<Column>> cols = getUserManager().getAllKeys(from);
			// The source is fully materialized now. Restore every legacy provider field,
			// not only the owner snapshot, before writes target the already-active store.
			if (targetAlreadyActive) {
				temporarySource = restoreConversionTarget(activeOwner, activeMysql, activeDatabase);
			} else {
				loadUserAPI(to);
			}
			Queue<Entry<UUID, ArrayList<Column>>> players = new LinkedList<>(cols.entrySet());

			while (players.size() > 0) {
				Entry<UUID, ArrayList<Column>> entry = players.poll();
				AdvancedCoreUser user = getUserManager().getUser(entry.getKey(), false);
				user.userDataFetechMode(UserDataFetchMode.NO_CACHE);

				user.getData().setValues(to, user.getData().convert(entry.getValue()));
				debug("Finished convert for " + user.getUUID() + ", " + players.size() + " more left to go!");

				if (players.size() % 50 == 0) {
					getLogger().info("Working on converting data, about " + players.size() + " left to go!");
				}
			}
			debug("Convert finished!");
		} finally {
			if (restoreActiveOwner) {
				if (temporarySource == null) {
					temporarySource = restoreConversionTarget(activeOwner, activeMysql, activeDatabase);
				}
				retireNativeUserStorageOwner(temporarySource.mysql(), temporarySource.database());
			}
		}

	}

	private NativeUserStorageClose restoreConversionTarget(UserStorageOwner activeOwner, MySQL activeMysql,
			Database activeDatabase) {
		NativeUserStorageClose temporary = new NativeUserStorageClose(
				mysql != activeMysql ? mysql : null, database != activeDatabase ? database : null);
		mysql = activeMysql;
		database = activeDatabase;
		// Publish the coherent owner only after both legacy provider fields match it.
		nativeUserStorageOwner = activeOwner;
		return temporary;
	}

	/**
	 * Logs a debug message at the specified debug level.
	 * 
	 * @param debugLevel the debug level
	 * @param debug      the debug message
	 */
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
	}

	/**
	 * Show exception in console if debug is on
	 *
	 * @param e Exception
	 */
	public void debug(Throwable e) {
		if (getOptions().getDebug().isDebug()) {
			e.printStackTrace();
		}
	}

	/**
	 * Logs a debug message at INFO level.
	 * 
	 * @param debug the debug message
	 */
	public void debug(String debug) {
		debug(DebugLevel.INFO, debug);
	}

	/**
	 * Logs a debug message at DEV level.
	 * 
	 * @param debug the debug message
	 */
	public void devDebug(String debug) {
		debug(DebugLevel.DEV, debug);
	}

	/**
	 * Logs a debug message at EXTRA level.
	 * 
	 * @param debug the debug message
	 */
	public void extraDebug(String debug) {
		debug(DebugLevel.EXTRA, debug);
	}

	/**
	 * Gets the SQLite user table.
	 * 
	 * @return the user table, or null if not using SQLite
	 */
	public UserTable getSQLiteUserTable() {
		if (database == null && loadUserData) {
			loadUserAPI(getStorageType());
		}
		if (loadUserData) {
			for (Table table : database.getTables()) {
				if (table instanceof UserTable) {
					return (UserTable) table;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the current native provider as one immutable observation. This is
	 * intentionally separate from the legacy individual getters: user-facing
	 * bulk APIs use this snapshot while a shared route is being replaced.
	 */
	public UserStorageOwner getNativeUserStorageOwner() {
		UserStorageOwner owner = nativeUserStorageOwner;
		if (owner != null) return owner;
		UserStorage configured = getOptions().getStorageType();
		if (configured == UserStorage.MYSQL && mysql != null) return new UserStorageOwner(configured, mysql, null);
		if (configured == UserStorage.SQLITE && database != null) {
			for (Table table : database.getTables()) {
				if (table instanceof UserTable userTable) return new UserStorageOwner(configured, null, userTable);
			}
		}
		return null;
	}

	/**
	 * Gets the current storage type configuration.
	 * 
	 * @return the storage type
	 */
	public UserStorage getStorageType() {
		UserStorage configured = getOptions().getStorageType();
		UserManager loadedUsers = getLoadedUserManager();
		return loadedUsers == null ? configured
				: loadedUsers.getDataManager().effectiveStorageType(configured);
	}

	/**
	 * Gets the user manager instance.
	 * 
	 * @return the user manager
	 */
	public UserManager getUserManager() {
		if (userManager == null) {
			userManager = new UserManager(this);
		}
		return userManager;
	}

	/** Existing manager only; shutdown must not allocate a new user subsystem. */
	public UserManager getLoadedUserManager() { return userManager; }

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

	/**
	 * Checks if MySQL connection is okay.
	 * 
	 * @return true if not using MySQL or if MySQL is connected, false otherwise
	 */
	public boolean isMySQLOkay() {
		if (getStorageType().equals(UserStorage.MYSQL)) {
			return mysql != null;
		}
		return true;
	}

	/**
	 * Loads AdvancedCore event listeners.
	 */
	public void loadAdvancedCoreEvents() {
		if (loadUserData) {
			Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this), this);
			Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(this), this);
		}

		Bukkit.getPluginManager().registerEvents(FireworkHandler.getInstance(), this);
		Bukkit.getPluginManager().registerEvents(new BInventoryListener(this), this);
	}

	private void loadConfig(boolean userStorage) {
		getOptions().load(this);
		if (loadUserData && userStorage) {
			loadUserAPI(getOptions().getStorageType());
			bindSharedUserRuntime();
		}
	}

	/** Bind only after the native Bukkit storage owner has initialized successfully. */
	private void bindSharedUserRuntime() {
		UserDataManager manager = getUserManager().getDataManager();
		BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(this, manager);
	}

	private void loadHandle() {

		if (Bukkit.getOnlineMode()) {
			debug("Server in online mode");
		} else {
			debug("Server in offline mode");
		}
	}

	/**
	 * Load AdvancedCore hook
	 */
	public void loadHook() {
		serverDataFile = new ServerData(this);

		hologramHandler = new HologramHandler(this);

		if (loadLuckPerms) {
			if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
				luckPermsHandle = new LuckPermsHandle();
				luckPermsHandle.load(this);
			}
		}

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

		skullCacheHandler = new SkullCacheHandler(getOptions().getSkullLoadDelay()) {

			@Override
			public void debugException(Exception e) {
				debug(e);
			}

			@Override
			public void debugLog(String debug) {
				extraDebug(debug);
			}

			@Override
			public void log(String log) {
				getLogger().info(log);
			}
		};
		if (!getOptions().getSkullProfileAPIURL().isEmpty()) {
			debug("Setting API profile URL to " + getOptions().getSkullProfileAPIURL());
			skullCacheHandler.changeApiProfileURL(getOptions().getSkullProfileAPIURL());
		}
		skullCacheHandler.setBedrockPrefix(getOptions().getBedrockPlayerPrefix());
		skullCacheHandler.startTimer();

		if (loadBedrockAPI) {
			bedrockHandle = new BedrockNameResolver(this);
		}

		rewardHandler = new RewardHandler(this);
		rewardHandler.loadInjectedRewards();
		rewardHandler.loadInjectedRequirements();
		if (loadRewards) {
			File rewardsFolder = new File(this.getDataFolder(), "Rewards");
			rewardHandler.addRewardFolder(rewardsFolder, false, true);
			File file = new File(rewardsFolder.getAbsolutePath() + File.separator + "DirectlyDefined");
			rewardHandler.addRewardFolder(file, false, false);
			rewardHandler.loadRewards();
		}

		checkPluginUpdate();
		loadVersionFile();

		getUserManager().purgeOldPlayersStartup();

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

		if (Bukkit.getPluginManager().getPlugin("nLogin") != null) {
			nLoginLoaded = true;
			Bukkit.getPluginManager().registerEvents(new NLoginAuthenticate(this), this);
		}

		if (Bukkit.getPluginManager().getPlugin("LoginSecurity") != null) {
			loginSecurityLoaded = true;
			Bukkit.getPluginManager().registerEvents(new LoginSecurityLogin(this), this);
		}

		if (getOptions().isJavascriptEngineEnabled()) {
			getLogger().info("Javascript engine enabled, loading engine");
			JavascriptEngineHandler.getInstance().init(this, getOptions().isJavascriptEngineEnabled(),
					getOptions().isJavascriptEngineAutoDownload());

			JavascriptEngineHandler.getInstance().prepareEngine();
		} else {
			getLogger().info("Javascript engine disabled, skipping engine load");
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

		debug("Using AdvancedCore '" + getAdvancedCoreVersion() + "' built on '" + getBuildTime() + "' "
				+ buildNumberMsg + " Spigot Version: " + Bukkit.getVersion() + " Total RAM: " + PluginUtils.getMemory()
				+ " Free RAM: " + PluginUtils.getFreeMemory());

		debug(DebugLevel.INFO, "Debug Level: " + getOptions().getDebug().toString());
	}

	/**
	 * Loads tab completion options.
	 */
	public void loadTabComplete() {
		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(AllPlayer)", new ArrayList<>()) {

			@Override
			public void reload() {
				ArrayList<String> players = new ArrayList<>();

				// fetch all cached names; this avoids hitting the DB
				players.addAll(UuidLookup.getInstance().getAllCachedNames());

				// Always include currently online players
				for (Player p : Bukkit.getOnlinePlayers()) {
					if (!players.contains(p.getName())) {
						players.add(p.getName());
					}
				}

				setReplace(players);
			}

			@Override
			public void updateReplacements() {
				// ensure new online players are added to the replacement list
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (!getReplace().contains(player.getName())) {
						getReplace().add(player.getName());
					}
				}
			}
		});

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(Player)", new ArrayList<>()) {

			@Override
			public void reload() {
				ArrayList<String> list = new ArrayList<>();
				for (Player player : Bukkit.getOnlinePlayers()) {
					list.add(player.getName());
				}
				setReplace(list);
			}

			@Override
			public void updateReplacements() {
				ArrayList<String> list = new ArrayList<>();
				for (Player player : Bukkit.getOnlinePlayers()) {
					list.add(player.getName());
				}
				setReplace(list);
			}
		}.updateOnLoginLogout());

		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(PlayerExact)", new ArrayList<>()) {

					@Override
					public void reload() {
						ArrayList<String> list = new ArrayList<>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}

					@Override
					public void updateReplacements() {
						ArrayList<String> list = new ArrayList<>();
						for (Player player : Bukkit.getOnlinePlayers()) {
							list.add(player.getName());
						}
						setReplace(list);
					}
				}.updateOnLoginLogout());

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(uuid)", new ArrayList<>()) {

			@Override
			public void reload() {
				uuidTabCompletionRefresh.request(AdvancedCorePlugin.this, this);
			}

			@Override
			public void updateReplacements() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					String uuid = getOptions().isOnlineMode() ? player.getUniqueId().toString()
							: UuidLookup.getInstance().getUUID(player.getName()); // name-derived in offline-mode

					if (uuid != null && !uuid.isEmpty() && !getReplace().contains(uuid)) {
						getReplace().add(uuid);
					}
				}
			}
		}.updateEveryXMinutes(getTimer(), 30));

		ArrayList<String> options = new ArrayList<>();
		options.add("True");
		options.add("False");
		TabCompleteHandler.getInstance().addTabCompleteOption("(Boolean)", options);
		options = new ArrayList<>();
		TabCompleteHandler.getInstance().addTabCompleteOption("(List)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(String)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(Text)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(Reward)", options) {

			@Override
			public void reload() {
				ArrayList<String> rewards = new ArrayList<>();
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
				ArrayList<String> rewards = new ArrayList<>();
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

		ArrayList<String> userStorage = new ArrayList<>();
		for (UserStorage storage : UserStorage.values()) {
			userStorage.add(storage.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(UserStorage)", userStorage);

		ArrayList<String> times = new ArrayList<>();
		for (TimeType ty : TimeType.values()) {
			times.add(ty.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(TimeType)", times);

		TabCompleteHandler.getInstance().addTabCompleteOption("(InputMethod)", InputMethod.getMethodNames());
	}

	/**
	 * Loads user API for specified storage type.
	 * 
	 * @param storageType the storage type to load
	 */
	public void loadUserAPI(UserStorage storageType) {
		if (storageType == null) {
			throw new IllegalArgumentException("User storage must be SQLITE or MYSQL");
		}
		requireUserStorageMaintenanceWindow();
		if (storageType.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<>();
			Column key = new Column("uuid", DataType.STRING);
			columns.add(key);
			UserTable table = new UserTable(this, "Users", columns, key);
			database = new Database(this, "Users", table);
			table.addCustomColumns();
			nativeUserStorageOwner = new UserStorageOwner(UserStorage.SQLITE, null, table);
		} else if (storageType.equals(UserStorage.MYSQL)) {
			if (getOptions().getYmlConfig().getData().contains("Database")) {
				setMysql(new MySQL(javaPlugin, javaPlugin.getName() + "_Users",
						getOptions().getYmlConfig().getData().getConfigurationSection("Database")));
			} else {
				getLogger().warning(
						"Deprecated MySQL config detected, please update your mysql config to use 'Database' section");
				setMysql(new MySQL(javaPlugin, javaPlugin.getName() + "_Users",
						getOptions().getYmlConfig().getData().getConfigurationSection("MySQL")));
			}

		}
	}

	/**
	 * The shared runtime owns cache flushing and the lifecycle admission for the
	 * native SQL provider. Replacing that provider in-place would let an in-flight
	 * flush target a connection that reload has already replaced or closed. There
	 * is no safe synchronous Bukkit hot-reload boundary for this today, so require
	 * a full plugin restart before mutating either native storage owner.
	 */
	private void requireUserStorageMaintenanceWindow() {
		UserManager loadedUsers = getLoadedUserManager();
		if (loadedUsers != null && loadedUsers.getDataManager().hasSharedRuntimeLifecycle()
				&& !loadedUsers.getDataManager().isStorageMaintenanceActive()) {
			throw new IllegalStateException("User storage reload requires a full plugin restart while shared user storage is active or retiring");
		}
	}

	private void loadUUIDs() {

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
				String name = user.getData().getString("PlayerName", UserDataFetchMode.NO_DB_LOOKUP);

				boolean add = true;

				if (name == null || name.isEmpty() || name.equalsIgnoreCase("Error getting name")
						|| name.equalsIgnoreCase("null")) {
					add = false;
				}

				if (uuid == null || uuid.isEmpty()) {
					debug("Invalid uuid: " + uuid);
					add = false;
				}

				if (add) {
					// Seed the centralized cache
					UuidLookup.getInstance().cacheMapping(uuid, name);
				}
			}
		});

		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
		TabCompleteHandler.getInstance().loadTimer(getTimer());
	}

	/**
	 * Coalesces the storage-backed UUID replacement refresh so a reload never
	 * enumerates storage from the Bukkit thread. Bukkit player access and the
	 * replacement mutation remain on the global scheduler. A newer request wins
	 * over an older worker result.
	 */
	static final class UuidTabCompletionRefresh {
		private final Object lock = new Object();
		private long nextGeneration;
		private RefreshRequest current;
		private boolean refreshInProgress;

		void request(AdvancedCorePlugin plugin, TabCompleteHandle handle) {
			RefreshRequest refresh;
			boolean schedule = false;
			synchronized (lock) {
				refresh = new RefreshRequest(++nextGeneration, handle);
				current = refresh;
				if (!refreshInProgress) {
					refreshInProgress = true;
					schedule = true;
				}
			}
			if (schedule) schedule(plugin, refresh);
		}

		private void schedule(AdvancedCorePlugin plugin, RefreshRequest refresh) {
			try {
				plugin.getBukkitScheduler().runTaskAsynchronously(plugin, () -> enumerateStorage(plugin, refresh));
			} catch (Throwable failure) {
				finish(plugin, refresh);
				plugin.debug(failure);
			}
		}

		private void enumerateStorage(AdvancedCorePlugin plugin, RefreshRequest refresh) {
			ArrayList<String> storageUuids = new ArrayList<>();
			Throwable failure = null;
			try {
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					if (uuid != null && !uuid.isEmpty()) storageUuids.add(uuid);
				}
			} catch (Throwable caught) {
				failure = caught;
			}
			final Throwable refreshFailure = failure;
			try {
				plugin.getBukkitScheduler().runTask(plugin,
						() -> applyOnGlobalScheduler(plugin, refresh, storageUuids, refreshFailure));
			} catch (Throwable schedulingFailure) {
				finish(plugin, refresh);
				plugin.debug(schedulingFailure);
			}
		}

		private void applyOnGlobalScheduler(AdvancedCorePlugin plugin, RefreshRequest refresh,
				ArrayList<String> storageUuids, Throwable failure) {
			try {
				if (!isCurrent(refresh)) return;
				if (failure != null) {
					plugin.debug(failure);
					return;
				}

				LinkedHashSet<String> uuids = new LinkedHashSet<>(storageUuids);
				for (Player player : Bukkit.getOnlinePlayers()) {
					String uuid = plugin.getOptions().isOnlineMode() ? player.getUniqueId().toString()
							: UuidLookup.getInstance().getUUID(player.getName());
					if (uuid != null && !uuid.isEmpty()) uuids.add(uuid);
				}

				ArrayList<String> replacements = new ArrayList<>(uuids);
				refresh.handle().setReplace(replacements);
				TabCompleteHandler.getInstance().getTabCompleteOptions().put(refresh.handle().getToReplace(), replacements);
			} finally {
				finish(plugin, refresh);
			}
		}

		private boolean isCurrent(RefreshRequest refresh) {
			synchronized (lock) {
				return refresh.equals(current);
			}
		}

		private void finish(AdvancedCorePlugin plugin, RefreshRequest completed) {
			RefreshRequest next = null;
			synchronized (lock) {
				if (!completed.equals(current)) {
					next = current;
				} else {
					refreshInProgress = false;
				}
			}
			if (next != null) schedule(plugin, next);
		}

		private record RefreshRequest(long generation, TabCompleteHandle handle) {
		}
	}

	/**
	 * Loads Vault integration.
	 */
	public void loadVault() {
		vaultHandler = new VaultHandler();
		if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
			debug("Attempting to hook into vault");
			vaultHandler.loadVault(this);
		}
	}

	private void loadVersionFile() {
		YamlConfiguration conf = getVersionFile();
		advancedCoreVersion = conf.getString("version", "Unknown");
		buildTime = conf.getString("time", "Unknown");
		advancedCoreBuildNumber = conf.getString("buildnumber", "NOTSET");
	}

	@Override
	public void onDisable() {
		new com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle(this).shutdown();
		javaPlugin = null;
	}

	@Override
	public void onEnable() {
		javaPlugin = this;
		com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle.RuntimeExecutors runtimeExecutors = com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle
				.createRuntimeExecutors(this);
		bukkitScheduler = runtimeExecutors.getBukkitScheduler();
		timer = runtimeExecutors.getTimer();
		loginTimer = runtimeExecutors.getLoginTimer();
		advancedCoreCommandLoader = CommandLoader.getInstance();
		inventoryTimer = runtimeExecutors.getInventoryTimer();

		onPreLoad();

		ActionBarManager actionBarManager = new ActionBarManager(this);
		ActionBar.setManager(actionBarManager);

		loadHook();
		onPostLoad();

		ValueRequest.initializeInputMethodChanger(
				"&7Input method not working? [Text=\"&e[Change Input Method]\",hover=\"&eClick to change your input method\",command=\"/av inputmethod\"]");

		try {
			dialogService = new UniDialogService(this, getName().toLowerCase() + "_dialogs");
			dialogService.register();
		} catch (Exception e) {
			debug("Failed to register UniDialogService, dialogs will not work");
			debug(e);
		}
		getRewardHandler().checkSubRewards();
		getRewardHandler().checkDirectlyDefinedRewardFiles();
	}

	/**
	 * Called after plugin loads. Subclasses must implement this method.
	 */
	public abstract void onPostLoad();

	/**
	 * Called before plugin loads. Subclasses must implement this method.
	 */
	public abstract void onPreLoad();

	/**
	 * Called when plugin unloads. Subclasses must implement this method.
	 */
	public abstract void onUnLoad();

	/**
	 * Registers BungeeCord messaging channels.
	 * 
	 * @param name the channel name
	 */
	public void registerBungeeChannels(String name) {
		this.bungeeChannel = name;
		getServer().getMessenger().registerOutgoingPluginChannel(this, name);
		pluginMessaging = new PluginMessage(this, name);
		getServer().getMessenger().registerIncomingPluginChannel(this, name, pluginMessaging);
		getLogger().info("Loaded plugin message channels: " + name);
	}

	/**
	 * Registers a listener.
	 * 
	 * @param listener the listener to register
	 */
	public void registerEvents(Listener listener) {
		Bukkit.getPluginManager().registerEvents(listener, this);
	}

	/**
	 * Reloads plugin configuration. Subclasses must implement this method.
	 */
	public abstract void reload();

	/**
	 * Reloads AdvancedCore configuration.
	 * 
	 * @deprecated use {@link #reloadAdvancedCore(boolean)} instead
	 */
	@Deprecated
	public void reloadAdvancedCore() {
		reloadAdvancedCore(false);
	}

	/**
	 * Starts an AdvancedCore configuration reload.
	 *
	 * <p>When {@code userStorage} is true and a shared runtime is active, this
	 * method only starts the non-blocking reload; it returns before storage has
	 * been flushed, replaced, or confirmed. It deliberately has no success
	 * signal. Call {@link #reloadAdvancedCoreAsync(boolean)} when a caller must
	 * continue, notify an administrator, or report status after completion.
	 *
	 * @param userStorage whether to reload user storage
	 */
	public void reloadAdvancedCore(boolean userStorage) {
		if (userStorage && hasActiveSharedUserRuntime()) {
			reloadAdvancedCoreAsync(true).whenComplete((ignored, failure) -> {
				if (failure != null) {
					getLogger().warning("User storage reload did not complete: " + failure.getMessage());
					debug(failure);
				}
			});
			return;
		}
		reloadAdvancedCoreNow(userStorage);
	}

	/**
	 * Reload AdvancedCore without making a Bukkit thread wait for a shared user
	 * storage flush or a database connection. Callers that need to report a
	 * confirmed storage reload should await this stage rather than assuming that
	 * the legacy void overload has completed synchronously.
	 *
	 * @param userStorage whether to reload user storage
	 * @return completion of the reload, including the shared backend replacement
	 */
	public CompletionStage<Void> reloadAdvancedCoreAsync(boolean userStorage) {
		if (!userStorage || !hasActiveSharedUserRuntime()) {
			CompletableFuture<Void> completion = new CompletableFuture<>();
			try {
				reloadAdvancedCoreNow(userStorage);
				completion.complete(null);
			} catch (Throwable failure) { completion.completeExceptionally(failure); }
			return completion;
		}
		synchronized (userStorageReloadLock()) {
			if (userStorageReload != null && !userStorageReload.toCompletableFuture().isDone()) return userStorageReload;
			CompletableFuture<Void> completion = new CompletableFuture<>();
			userStorageReload = completion;
			try {
				getBukkitScheduler().runTask(this, () -> beginSharedUserStorageReload(completion));
			} catch (RuntimeException | Error failure) {
				completion.completeExceptionally(failure);
				userStorageReload = null;
			}
			return completion;
		}
	}

	private boolean hasActiveSharedUserRuntime() {
		UserManager users = getLoadedUserManager();
		return users != null && users.getDataManager().hasSharedRuntime();
	}

	private void beginSharedUserStorageReload(CompletableFuture<Void> completion) {
		try {
			getServerDataFile().reloadData();
			rewardHandler.loadRewards();
			getOptions().load(this);
			getBukkitScheduler().runTaskAsynchronously(this, () -> replaceSharedUserStorage(completion));
		} catch (Throwable failure) {
			completion.completeExceptionally(failure);
			clearSharedUserStorageReload(completion);
		}
	}

	private void replaceSharedUserStorage(CompletableFuture<Void> completion) {
		UserStorageReplacement replacement = null;
		try {
			replacement = prepareUserStorageReplacement(getOptions().getStorageType());
			UserStorageReplacement prepared = replacement;
			getUserManager().getDataManager().replaceSharedSqlBackendAsync(prepared.backend(),
					() -> installUserStorageReplacement(prepared)).whenComplete((ignored, failure) -> {
				if (failure != null) {
					// SharedUserDataRuntime reports an exception here only before it
					// publishes the replacement route. A post-publication old-owner close
					// is retained there for retry and deliberately completes successfully.
					failSharedUserStorageReplacement(completion, prepared, failure);
					return;
				}
				try {
					getBukkitScheduler().runTask(this, () -> completeSharedUserStorageReload(completion));
				} catch (Throwable completionFailure) {
					completion.completeExceptionally(completionFailure);
					clearSharedUserStorageReload(completion);
				}
			});
		} catch (Throwable failure) {
			failSharedUserStorageReplacement(completion, replacement, failure);
		}
	}

	/**
	 * Cleanup must never strand the public reload stage. Keep the replacement
	 * failure as the primary cause and attach every cleanup failure to it.
	 */
	private void failSharedUserStorageReplacement(CompletableFuture<Void> completion,
			UserStorageReplacement replacement, Throwable failure) {
		Throwable reported = failure;
		try {
			if (replacement != null) replacement.closeUnpublished();
		} catch (Throwable closeFailure) {
			reported = preserveFailure(reported, closeFailure);
		} finally {
			try { completion.completeExceptionally(reported); }
			finally { clearSharedUserStorageReload(completion); }
		}
	}

	private void completeSharedUserStorageReload(CompletableFuture<Void> completion) {
		try {
			finishReloadAdvancedCore();
			completion.complete(null);
		} catch (Throwable failure) { completion.completeExceptionally(failure); }
		finally { clearSharedUserStorageReload(completion); }
	}

	private void clearSharedUserStorageReload(CompletableFuture<Void> completion) {
		synchronized (userStorageReloadLock()) {
			if (userStorageReload == completion) userStorageReload = null;
		}
	}

	private Object userStorageReloadLock() {
		Object lock = userStorageReloadLock;
		if (lock != null) return lock;
		synchronized (this) {
			if (userStorageReloadLock == null) userStorageReloadLock = new Object();
			return userStorageReloadLock;
		}
	}

	private UserStorageReplacement prepareUserStorageReplacement(UserStorage storageType) {
		if (storageType == null) throw new IllegalArgumentException("User storage must be SQLITE or MYSQL");
		if (storageType == UserStorage.SQLITE) {
			ArrayList<Column> columns = new ArrayList<>();
			Column key = new Column("uuid", DataType.STRING);
			columns.add(key);
			UserTable table = new UserTable(this, "Users", columns, key);
			Database replacementDatabase = new Database(this, "Users", table);
			table.addCustomColumns();
			return new UserStorageReplacement(storageType, replacementDatabase, null,
					new BukkitSqlUserBackend(this, storageType, null, table));
		}
		ConfigurationSection section = getOptions().getYmlConfig().getData()
				.getConfigurationSection(getOptions().getYmlConfig().getData().contains("Database") ? "Database" : "MySQL");
		if (section == null) throw new IllegalStateException("MySQL user storage configuration is missing");
		MySQL replacementMysql = new MySQL(javaPlugin, javaPlugin.getName() + "_Users", section);
		return new UserStorageReplacement(storageType, null, replacementMysql,
				new BukkitSqlUserBackend(this, storageType, replacementMysql, null));
	}

	private void installUserStorageReplacement(UserStorageReplacement replacement) {
		retryPendingNativeUserStorageCloses();
		MySQL previousMysql = mysql;
		Database previousDatabase = database;
		mysql = replacement.mysql();
		database = replacement.database();
		// Publish only after both legacy fields have been assigned. The shared route
		// picks this exact snapshot up in the same lifecycle write admission.
		nativeUserStorageOwner = replacement.owner();
		retireNativeUserStorageOwner(previousMysql != mysql ? previousMysql : null,
				previousDatabase != database ? previousDatabase : null);
	}

	private void retireNativeUserStorageOwner(MySQL previousMysql, Database previousDatabase) {
		if (previousMysql == null && previousDatabase == null) return;
		NativeUserStorageClose retired = new NativeUserStorageClose(previousMysql, previousDatabase);
		Throwable closeFailure = retired.close();
		if (closeFailure == null) return;
		debug(closeFailure);
		synchronized (pendingNativeUserStorageCloseLock) { pendingNativeUserStorageCloses.add(retired); }
	}

	private void retryPendingNativeUserStorageCloses() {
		ArrayList<NativeUserStorageClose> pending;
		synchronized (pendingNativeUserStorageCloseLock) {
			if (pendingNativeUserStorageCloses.isEmpty()) return;
			pending = new ArrayList<>(pendingNativeUserStorageCloses);
			pendingNativeUserStorageCloses.clear();
		}
		for (NativeUserStorageClose retired : pending) {
			Throwable closeFailure = retired.close();
			if (closeFailure == null) continue;
			debug(closeFailure);
			synchronized (pendingNativeUserStorageCloseLock) { pendingNativeUserStorageCloses.add(retired); }
		}
	}

	/** Retry retired native storage owners during plugin shutdown as well. */
	public void closePendingNativeUserStorageOwners() { retryPendingNativeUserStorageCloses(); }

	private static Throwable preserveFailure(Throwable primary, Throwable additional) {
		if (primary == null) return additional;
		if (primary != additional) primary.addSuppressed(additional);
		return primary;
	}

	private void reloadAdvancedCoreNow(boolean userStorage) {
		if (userStorage) requireUserStorageMaintenanceWindow();
		getServerDataFile().reloadData();
		rewardHandler.loadRewards();
		loadConfig(userStorage);
		if (userStorage) {
			getUserManager().getDataManager().clearCache();
			if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null) getMysql().clearCacheBasic();
		}
		finishReloadAdvancedCore();
	}

	private void finishReloadAdvancedCore() {
		timeChecker.update();
		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
		getRewardHandler().checkSubRewards();
		if (skullCacheHandler != null) {
			if (!getOptions().getSkullProfileAPIURL().isEmpty()) {
				debug("Setting API profile URL to " + getOptions().getSkullProfileAPIURL());
				skullCacheHandler.changeApiProfileURL(getOptions().getSkullProfileAPIURL());
			}
			getSkullCacheHandler().setBedrockPrefix(getOptions().getBedrockPlayerPrefix());
		}
	}

	private record UserStorageReplacement(UserStorage storageType, Database database, MySQL mysql,
			BukkitSqlUserBackend backend) {
		private UserStorageOwner owner() {
			return new UserStorageOwner(storageType, mysql, database == null ? null : findUserTable(database));
		}

		private static UserTable findUserTable(Database database) {
			for (Table table : database.getTables()) if (table instanceof UserTable userTable) return userTable;
			throw new IllegalStateException("Replacement SQLite user table is unavailable");
		}
		private void closeUnpublished() {
			Throwable failure = null;
			try { backend.close(); }
			catch (Throwable closeFailure) { failure = preserveFailure(failure, closeFailure); }
			if (mysql != null) {
				try { mysql.close(); }
				catch (Throwable closeFailure) { failure = preserveFailure(failure, closeFailure); }
			}
			if (database != null) {
				try { database.getDB().closeConnection(); }
				catch (Throwable closeFailure) { failure = preserveFailure(failure, closeFailure); }
			}
			if (failure instanceof RuntimeException runtime) throw runtime;
			if (failure instanceof Error error) throw error;
			if (failure != null) throw new IllegalStateException("Failed to close unpublished user storage", failure);
		}
	}

	private record NativeUserStorageClose(MySQL mysql, Database database) {
		private Throwable close() {
			Throwable failure = null;
			if (mysql != null) {
				try { mysql.close(); }
				catch (Throwable closeFailure) { failure = preserveFailure(failure, closeFailure); }
			}
			if (database != null) {
				try { database.getDB().closeConnection(); }
				catch (Throwable closeFailure) { failure = preserveFailure(failure, closeFailure); }
			}
			return failure;
		}
	}

	/**
	 * @param configData the configData to set
	 */
	@Deprecated
	public void setConfigData(ConfigurationSection configData) {
		getOptions().setYmlConfig(new YMLConfig(this, configData) {

			@Override
			public void createSection(String key) {

			}

			@Override
			public void saveData() {

			}

			@Override
			public void setValue(String path, Object value) {

			}
		});
	}

	/**
	 * Sets configuration data.
	 * 
	 * @param ymlConfig the configuration to set
	 */
	public void setConfigData(YMLConfig ymlConfig) {
		getOptions().setYmlConfig(ymlConfig);
	}

	/**
	 * Sets the MySQL database connection.
	 * 
	 * @param mysql the mysql connection to set
	 */
	public void setMysql(MySQL mysql) {
		if (this.mysql != null) {
			this.mysql.close();
			this.mysql = null;
		}
		this.mysql = mysql;
		if (mysql != null) nativeUserStorageOwner = new UserStorageOwner(UserStorage.MYSQL, mysql, null);
	}

	/**
	 * Runs user startup tasks.
	 */
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

				getUserManager().forEachUserKeys((uuid, columns) -> {
					AdvancedCoreUser user = getUserManager().getUser(uuid, false);
					if (user != null) {
						user.userDataFetechMode(UserDataFetchMode.TEMP_ONLY);
						user.updateTempCacheWithColumns(columns);
						for (UserStartup start : userStartup) {
							if (start.isProcess()) {
								start.onStartUp(user);
							}
						}
						user.clearTempCache();
						user = null;
					}
				}, (count) -> {
					for (UserStartup start : userStartup) {
						start.onFinish();
					}

					for (UserStartup start : userStartup) {
						start.onPostFinish();
					}

					debug("User Startup finished");
				});

			}
		}, 5);
	}
}
