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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
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
import com.bencodez.advancedcore.api.command.TabCompleteHandle;
import com.bencodez.advancedcore.api.command.TabCompleteHandler;
import com.bencodez.advancedcore.api.inventory.BInventoryListener;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderRequest;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.PluginUtils;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.skull.SkullHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.updater.UpdateDownloader;
import com.bencodez.advancedcore.api.user.UUID;
import com.bencodez.advancedcore.api.user.User;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.Column;
import com.bencodez.advancedcore.api.user.userstorage.sql.DataType;
import com.bencodez.advancedcore.api.user.userstorage.sql.Database;
import com.bencodez.advancedcore.api.user.userstorage.sql.Table;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.sign.SignMenu;
import com.bencodez.advancedcore.bungeeapi.pluginmessage.PluginMessage;
import com.bencodez.advancedcore.command.executor.ValueRequestInputCommand;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.listeners.AuthMeLogin;
import com.bencodez.advancedcore.listeners.PlayerJoinEvent;
import com.bencodez.advancedcore.listeners.PluginUpdateVersionEvent;
import com.bencodez.advancedcore.listeners.WorldChangeEvent;
import com.bencodez.advancedcore.logger.Logger;
import com.bencodez.advancedcore.nms.NMSManager;
import com.bencodez.advancedcore.serverhandle.CraftBukkitHandle;
import com.bencodez.advancedcore.serverhandle.IServerHandle;
import com.bencodez.advancedcore.serverhandle.SpigotHandle;
import com.bencodez.advancedcore.thread.Thread;

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
	private ConcurrentHashMap<String, String> uuidNameCache;

	@Getter
	@Setter
	private boolean loadRewards = true;

	@Getter
	private SignMenu signMenu;

	@Getter
	private boolean placeHolderAPIEnabled;

	private Database database;

	@Getter
	private MySQL mysql;

	@Getter
	private IServerHandle serverHandle;

	@Getter
	private Logger pluginLogger;

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

	@Getter
	@Setter
	private boolean loadServerData = true;

	@Getter
	@Setter
	private boolean loadUserData = true;

	public void addUserStartup(UserStartup start) {
		userStartup.add(start);
	}

	public void allowDownloadingFromSpigot(int resourceId) {
		getOptions().setResourceId(resourceId);
	}

	private void checkAutoUpdate() {
		Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				if (getOptions().isAutoDownload() && getOptions().getResourceId() != 0) {
					UpdateDownloader.getInstance().checkAutoDownload(javaPlugin, getOptions().getResourceId());
				}
			}
		});

	}

	private void checkPlaceHolderAPI() {
		Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {

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
		Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				String version = ServerData.getInstance().getPluginVersion(javaPlugin);
				if (!version.equals(javaPlugin.getDescription().getVersion())) {
					PluginUpdateVersionEvent event = new PluginUpdateVersionEvent(javaPlugin, version);
					Bukkit.getServer().getPluginManager().callEvent(event);
				}
				ServerData.getInstance().setPluginVersion(javaPlugin);
			}
		});

	}

	public void debug(DebugLevel debugLevel, String debug) {
		if (debugLevel.equals(DebugLevel.EXTRA)) {
			debug = "ExtraDebug: " + debug;
		} else if (debugLevel.equals(DebugLevel.INFO)) {
			debug = "Debug: " + debug;
		} else if (debugLevel.equals(DebugLevel.DEV)) {
			debug = "Developer Debug: " + debug;
		}
		if (getOptions().getDebug().isDebug()) {
			getLogger().info(debug);
		}
		if (getOptions().isDebugIngame()) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.hasPermission(this.getName() + ".Debug")) {
					player.sendMessage(StringParser.getInstance().colorize("&c" + getName() + " Debug: " + debug));
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
		if (getOptions().getDebug().equals(DebugLevel.INFO)) {
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
		if (database == null) {
			loadUserAPI(getStorageType());
		}
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
		Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this), this);
		Bukkit.getPluginManager().registerEvents(FireworkHandler.getInstance(), this);
		Bukkit.getPluginManager().registerEvents(new WorldChangeEvent(this), this);
		Bukkit.getPluginManager().registerEvents(new BInventoryListener(this), this);
	}

	public void loadAutoUpdateCheck() {
		long delay = 1000 * 60 * 60;
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				checkAutoUpdate();
			}
		}, delay, delay);
	}

	private void loadConfig(boolean userStorage) {
		getOptions().load(this);
		if (loadUserData && userStorage) {
			loadUserAPI(getOptions().getStorageType());
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
			getLogger().warning("Detected server running craftbukkit. It is recommended to use spigot instead");
		}
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

		loadSignAPI();
		loadUUIDs();
		getOptions().setPermPrefix(this.getName());
		checkPlaceHolderAPI();
		loadHandle();
		loadVault();
		loadAdvancedCoreEvents();
		if (loadServerData) {
			TimeChecker.getInstance().loadTimer(1);
			ServerData.getInstance().setup();
		}

		RewardHandler.getInstance().loadInjectedRewards();
		RewardHandler.getInstance().loadInjectedRequirements();
		if (loadRewards) {
			RewardHandler.getInstance().addRewardFolder(new File(this.getDataFolder(), "Rewards"));
		}

		loadValueRequestInputCommands();
		checkPluginUpdate();
		RewardHandler.getInstance().checkDelayedTimedRewards();
		loadAutoUpdateCheck();
		loadVersionFile();

		loadConfig(true);

		UserManager.getInstance().purgeOldPlayers();

		SkullHandler.getInstance().load();

		userStartup();
		loadTabComplete();

		for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
			bannedPlayers.add(p.getUniqueId().toString());
		}

		Bukkit.getPluginManager().registerEvents(BackupHandle.getInstance(), this);

		if (Bukkit.getPluginManager().getPlugin("authme") != null) {
			authMeLoaded = true;
			Bukkit.getPluginManager().registerEvents(new AuthMeLogin(), this);
		}

		debug("Using AdvancedCore '" + getVersion() + "' built on '" + getBuildTime() + "' Spigot Version: "
				+ Bukkit.getVersion() + " Total RAM: " + PluginUtils.getInstance().getMemory() + " Free RAM: "
				+ PluginUtils.getInstance().getFreeMemory());

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
			try {
				this.signMenu = new SignMenu(this);
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
				for (Reward reward : RewardHandler.getInstance().getRewards()) {
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

	public void loadUserAPI(UserStorage storageType) {
		if (storageType.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column key = new Column("uuid", DataType.STRING);
			columns.add(key);
			Table table = new Table("Users", columns, key);
			database = new Database(this, "Users", table);
		} else if (storageType.equals(UserStorage.MYSQL)) {
			Thread.getInstance().run(new Runnable() {

				@Override
				public void run() {
					setMysql(new MySQL(javaPlugin.getName() + "_Users",
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
				String name = user.getData().getString("PlayerName", true);
				boolean add = true;
				if (uuidNameCache.containsKey(uuid)) {
					debug("Duplicate uuid? " + uuid);
				}

				if (name == null || name.equals("") || name.equals("Error getting name")) {
					// extraDebug("Invalid player name: " + uuid);
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
					try {
						boolean delete = true;
						List<Column> list = user.getData().getMySqlRow(true);
						if (list != null) {
							for (Column col : list) {
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
					} catch (Exception e) {
						debug(e);
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

			commandMap.register(this.getName() + "valuerequestinput",
					new ValueRequestInputCommand(this.getName() + "valuerequestinput"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadVault() {
		Bukkit.getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				if (setupEconomy()) {
					getLogger().info("Successfully hooked into vault economy!");
				} else {
					getLogger().warning("Failed to hook into vault economy");
				}

				if (setupPermissions()) {
					getLogger().info("Hooked into vault permissions");

					RewardHandler.getInstance().addInjectedRequirements(new RequirementInjectString("VaultGroup", "") {

						@Override
						public boolean onRequirementsRequest(Reward reward, User user, String type,
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
									Reward reward = (Reward) getInv().getData("Reward");
									reward.getConfig().set(getKey(), value);
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
	}

	@Override
	public void onDisable() {

		if (getOptions().getStorageType().equals(UserStorage.MYSQL)) {
			getMysql().updateBatchShutdown();
			getMysql().close();
		}
		timer.cancel();
		onUnLoad();
		SkullHandler.getInstance().close();

		// Thread.getInstance().getThread().interrupt();
		// FileThread.getInstance().getThread().interrupt();
	}

	@Override
	public void onEnable() {
		javaPlugin = this;
		onPreLoad();
		loadHook();
		onPostLoad();
	}

	public abstract void onPostLoad();

	public abstract void onPreLoad();

	public abstract void onUnLoad();

	public void registerBungeeChannels() {
		getServer().getMessenger().registerOutgoingPluginChannel(this,
				this.getName().toLowerCase() + ":" + this.getName().toLowerCase());
		getServer().getMessenger().registerIncomingPluginChannel(this,
				this.getName().toLowerCase() + ":" + this.getName().toLowerCase(), PluginMessage.getInstance());
	}

	public void registerEvents(Listener listener) {
		Bukkit.getPluginManager().registerEvents(listener, this);
	}

	public abstract void reload();

	/**
	 * Reload
	 */
	public void reloadAdvancedCore(boolean userStorage) {
		ServerData.getInstance().reloadData();
		RewardHandler.getInstance().loadRewards();
		loadConfig(userStorage);
		if (getStorageType().equals(UserStorage.MYSQL) && getMysql() != null && userStorage) {
			getMysql().clearCache();
		}
		TimeChecker.getInstance().update();
		RewardHandler.getInstance().checkDelayedTimedRewards();
		TabCompleteHandler.getInstance().reload();
		TabCompleteHandler.getInstance().loadTabCompleteOptions();
	}

	@Deprecated
	public void reloadAdvancedCore() {
		reloadAdvancedCore(false);
	}

	/**
	 * Run.
	 *
	 * @param run the run
	 */
	public void run(Runnable run) {
		com.bencodez.advancedcore.thread.Thread.getInstance().run(run);
	}

	/**
	 * @param configData the configData to set
	 */
	public void setConfigData(ConfigurationSection configData) {
		getOptions().setConfigData(configData);
	}

	/**
	 * @param mysql the mysql to set
	 */
	public void setMysql(MySQL mysql) {
		if (this.mysql != null) {
			this.mysql.updateBatchShutdown();
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

	public void userStartup() {
		Bukkit.getScheduler().runTaskLaterAsynchronously(this, new Runnable() {

			@Override
			public void run() {
				debug("User Startup starting");
				for (UserStartup start : userStartup) {
					start.onStart();
				}
				ArrayList<User> users = new ArrayList<User>();
				for (String uuid : UserManager.getInstance().getAllUUIDs()) {
					User user = UserManager.getInstance().getUser(new UUID(uuid));
					if (user != null) {
						users.add(user);
						for (UserStartup start : userStartup) {
							start.onStartUp(user);
						}
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
