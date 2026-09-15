package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.validation.UserValidationFactory;
import com.bencodez.advancedcore.api.user.validation.UserValidationService;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueString;

import lombok.Getter;

/**
 * The Class UserManager.
 */
public class UserManager {

	@Getter
	private UserDataManager dataManager;

	private final Object obj = new Object();

	/** The plugin. */
	private final AdvancedCorePlugin plugin;

	@Getter
	private final ArrayList<UserDataChanged> userDataChange = new ArrayList<>();

	@Getter
	private UserValidationService validationService;

	public UserManager(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		load();
	}

	/**
	 * Capture the type and native owner as one observation. During shared-runtime
	 * replacement the manager publishes this pair atomically with its route;
	 * callers must not combine plugin.getStorageType() with a later provider read.
	 */
	private <T> T withActiveStorageOwner(Function<AdvancedCorePlugin.UserStorageOwner, T> operation) {
		return dataManager == null ? operation.apply(plugin.getNativeUserStorageOwner())
				: dataManager.withSharedNativeUserStorage(operation);
	}

	private UserStorage activeStorageType(AdvancedCorePlugin.UserStorageOwner owner) {
		return owner == null ? plugin.getStorageType() : owner.storageType();
	}

	private MySQL activeMysql(AdvancedCorePlugin.UserStorageOwner owner) {
		return owner != null && owner.storageType() == UserStorage.MYSQL && owner.mysql() != null
				? owner.mysql() : plugin.getMysql();
	}

	private UserTable activeTable(AdvancedCorePlugin.UserStorageOwner owner) {
		return owner != null && owner.storageType() == UserStorage.SQLITE && owner.table() != null
				? owner.table() : plugin.getSQLiteUserTable();
	}

	private AdvancedCorePlugin.UserStorageOwner ownerFor(UserStorage storage, AdvancedCorePlugin.UserStorageOwner owner) {
		if (owner != null && owner.storageType() != storage && dataManager != null && dataManager.hasSharedSqlBackend()
				&& !dataManager.isStorageMaintenanceActive()) {
			throw new IllegalStateException("Cannot access " + storage
					+ " user storage while the shared runtime owns " + owner.storageType());
		}
		return owner != null && owner.storageType() == storage ? owner : null;
	}

	public void copyColumnData(String columnFromName, String columnToName) {
		withActiveStorageOwner(owner -> {
			if (activeStorageType(owner).equals(UserStorage.MYSQL)) {
				activeMysql(owner).copyColumnData(columnFromName, columnToName, DataType.STRING);
			} else if (activeStorageType(owner).equals(UserStorage.SQLITE)) {
				activeTable(owner).copyColumnData(columnFromName, columnToName, DataType.STRING);
			}
			return null;
		});
	}

	public List<String> getAllColumns() {
		return withActiveStorageOwner(owner -> {
			UserStorage storage = activeStorageType(owner);
			if (storage.equals(UserStorage.SQLITE)) return activeTable(owner).getColumnsString();
			if (storage.equals(UserStorage.MYSQL)) return activeMysql(owner).getColumns();
			return new ArrayList<>();
		});
	}

	@Deprecated
	public HashMap<UUID, ArrayList<Column>> getAllKeys() {
		return withActiveStorageOwner(owner -> getAllKeys(activeStorageType(owner), owner));
	}

	public HashMap<UUID, ArrayList<Column>> getAllKeys(UserStorage storage) {
		return withActiveStorageOwner(owner -> getAllKeys(storage, owner));
	}

	private HashMap<UUID, ArrayList<Column>> getAllKeys(UserStorage storage, AdvancedCorePlugin.UserStorageOwner owner) {
		owner = ownerFor(storage, owner);
		if (storage.equals(UserStorage.SQLITE)) {
			return activeTable(owner).getAllQuery();
		}
		if (storage.equals(UserStorage.MYSQL)) {
			return activeMysql(owner).getAllQuery();
		}
		return new HashMap<>();
	}

	public ArrayList<String> getAllPlayerNames() {
		return withActiveStorageOwner(owner -> {
			if (!plugin.isLoadUserData()) return new ArrayList<>();
			UserStorage storage = activeStorageType(owner);
			ArrayList<String> names = new ArrayList<>();
			if (storage.equals(UserStorage.SQLITE)) {
				ArrayList<String> data = activeTable(owner).getNames();
				for (String name : data) {
					if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
						names.add(name);
					}
				}
			} else if (storage.equals(UserStorage.MYSQL)) {
				ArrayList<String> data = ArrayUtils.convert(activeMysql(owner).getNames());
				for (String name : data) {
					if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
						names.add(name);
					}
				}
			}
			return ArrayUtils.removeDuplicates(names);
		});
	}

	/**
	 * Storage-agnostic streaming iteration over all users + their column data.
	 *
	 * MYSQL: uses plugin.getMysql().forEachUser(...) SQLITE: uses
	 * plugin.getSQLiteUserTable().forEachUser(...).
	 * 
	 * @param perUser    BiConsumer called per user with UUID and column list
	 * @param onFinished Consumer called once after all users processed with total
	 */
	public void forEachUserKeys(BiConsumer<UUID, ArrayList<Column>> perUser, Consumer<Integer> onFinished) {
		withActiveStorageOwner(owner -> {
			UserStorage storage = activeStorageType(owner);
			if (storage == UserStorage.MYSQL) {
				activeMysql(owner).forEachUser((uuid, cols) -> perUser.accept(uuid, cols), (count) -> {
				if (onFinished != null) {
					onFinished.accept(count);
				}
			});
				return null;
			}
			if (storage == UserStorage.SQLITE) {
				activeTable(owner).forEachUser((uuid, cols) -> perUser.accept(uuid, cols), (count) -> {
				if (onFinished != null) {
					onFinished.accept(count);
				}
			});
				return null;
			}
			throw new IllegalStateException("User storage is not configured");
		});
	}

	public ArrayList<String> getAllUUIDs() {
		return withActiveStorageOwner(owner -> ArrayUtils.removeDuplicates(getAllUUIDs(activeStorageType(owner), owner)));
	}

	public ArrayList<String> getAllUUIDs(UserStorage storage) {
		return withActiveStorageOwner(owner -> getAllUUIDs(storage, owner));
	}

	private ArrayList<String> getAllUUIDs(UserStorage storage, AdvancedCorePlugin.UserStorageOwner owner) {
		if (plugin.isLoadUserData()) {
			owner = ownerFor(storage, owner);
			if (storage.equals(UserStorage.SQLITE)) {
				List<Column> cols = activeTable(owner).getRows();
				ArrayList<String> uuids = new ArrayList<>();
				for (Column col : cols) {
					if (col.getValue().isString()) {
						uuids.add(col.getValue().getString());
					}
				}
				return uuids;
			} else if (storage.equals(UserStorage.MYSQL)) {
				synchronized (obj) {
					ArrayList<String> uuids = new ArrayList<>();
					try {
						for (String uuid : activeMysql(owner).getUuids()) {
							uuids.add(uuid);
						}
					} catch (NullPointerException e) {
						e.printStackTrace();
					}
					return uuids;
				}
			}
		}
		return new ArrayList<>();
	}

	public ArrayList<Integer> getNumbersInColumn(String columnName) {
		return withActiveStorageOwner(owner -> {
			if (activeStorageType(owner).equals(UserStorage.MYSQL)) return activeMysql(owner).getNumbersInColumn(columnName);
			if (activeStorageType(owner).equals(UserStorage.SQLITE)) return activeTable(owner).getNumbersInColumn(columnName);
			return new ArrayList<>();
		});
	}

	public String getOfflineRewardsPath() {
		if (plugin.getOptions().isPerServerRewards()) {
			return "OfflineRewards" + plugin.getOptions().getServer().replace("-", "_");
		}
		return "OfflineRewards";
	}

	/**
	 * Returns the "proper" casing for a name if we have it. Uses UuidLookup cache
	 * first, then falls back to the historical storage-based name lists.
	 * 
	 * @param name Name to look up
	 * @return Properly-cased name if found, else the input name
	 */
	public String getProperName(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}

		// If it's a UUID string, try cache -> return cached name
		String cachedName = UuidLookup.getInstance().getCachedName(name);
		if (cachedName != null && !cachedName.isEmpty()) {
			return cachedName;
		}

		// If we have name->uuid cached, return the mapped cached name (preserves case
		// if cached)
		String cachedUuid = UuidLookup.getInstance().getCachedUUID(name);
		if (cachedUuid != null && !cachedUuid.isEmpty()) {
			String n = UuidLookup.getInstance().getCachedName(cachedUuid);
			if (n != null && !n.isEmpty()) {
				return n;
			}
		}

		// Fall back to storage-derived player-name lists
		for (String s : getAllPlayerNames()) {
			if (s.equalsIgnoreCase(name)) {
				return s;
			}
		}

		return name;
	}

	public AdvancedCoreUser getRandomUser() {
		ArrayList<String> uuids = getAllUUIDs();
		if (uuids == null || uuids.isEmpty()) {
			return null;
		}
		try {
			return getUser(UUID.fromString(uuids.get(0)));
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * Gets the user.
	 *
	 * @param player the player
	 * @return the user
	 */
	public AdvancedCoreUser getUser(OfflinePlayer player) {
		return getUser(player.getUniqueId(), player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param player the player
	 * @return the user
	 */
	public AdvancedCoreUser getUser(Player player) {
		return getUser(player.getUniqueId(), player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param playerName the player name
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public AdvancedCoreUser getUser(String playerName) {
		// keep behavior: AdvancedCoreUser handles name->uuid internally (but you can
		// refactor later)
		return new AdvancedCoreUser(plugin, getProperName(playerName));
	}

	/**
	 * Gets the user.
	 *
	 * @param uuid the uuid
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public AdvancedCoreUser getUser(UUID uuid) {
		return new AdvancedCoreUser(plugin, uuid);
	}

	@SuppressWarnings("deprecation")
	public AdvancedCoreUser getUser(UUID uuid, boolean loadName) {
		return new AdvancedCoreUser(plugin, uuid, loadName);
	}

	@SuppressWarnings("deprecation")
	public AdvancedCoreUser getUser(UUID uuid, String playerName) {
		return new AdvancedCoreUser(plugin, uuid, playerName);
	}

	public void load() {
		dataManager = new UserDataManager(AdvancedCorePlugin.getInstance());
		validationService = UserValidationFactory.create(plugin);
	}

	public void onChange(AdvancedCoreUser user, String... keys) {
		for (UserDataChanged change : userDataChange) {
			change.onChange(user, keys);
		}
	}

	public void purgeOldPlayersNow() {
		if (plugin.getOptions().isPurgeOldData()) {
			HashMap<UUID, ArrayList<Column>> cols = getAllKeys();
			for (Entry<UUID, ArrayList<Column>> playerData : cols.entrySet()) {
				String uuid = playerData.getKey().toString();
				if (plugin.isEnabled()) {
					if (uuid != null) {
						AdvancedCoreUser user = getUser(UUID.fromString(uuid), false);
						if (user != null) {
							user.userDataFetechMode(UserDataFetchMode.TEMP_ONLY);
							user.updateTempCacheWithColumns(playerData.getValue());
							int daysOld = plugin.getOptions().getPurgeMinimumDays();
							int days = user.getNumberOfDaysSinceLogin();
							if (days == -1) {
								user.setLastOnline(System.currentTimeMillis());
							} else if (days > daysOld) {
								plugin.debug("Removing " + user.getUUID() + " because of purge");
								user.remove();
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
		}
		getDataManager().clearCache();
	}

	public void purgeOldPlayersStartup() {
		if (plugin.getOptions().isPurgeOldData() && plugin.getOptions().isPurgeDataOnStartup()) {
			plugin.addUserStartup(new UserStartup() {

				@Override
				public void onFinish() {
					plugin.debug("Finished purging");
				}

				@Override
				public void onStart() {
				}

				@Override
				public void onStartUp(AdvancedCoreUser user) {
					int daysOld = plugin.getOptions().getPurgeMinimumDays();
					int days = user.getNumberOfDaysSinceLogin();
					if (days == -1) {
						user.setLastOnline(System.currentTimeMillis());
					} else if (days > daysOld) {
						plugin.debug("Removing " + user.getUUID() + " because of purge");
						user.remove();
					}
				}
			});
		}
		getDataManager().clearCache();
	}

	public void removeAllKeyValues(String key, DataType type) {
		withActiveStorageOwner(owner -> {
			if (activeStorageType(owner).equals(UserStorage.SQLITE)) activeTable(owner).wipeColumnData(key, type);
			else if (activeStorageType(owner).equals(UserStorage.MYSQL)) activeMysql(owner).wipeColumnData(key, type);
			return null;
		});
	}

	public boolean userExistStored(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}

		boolean exist = ArrayUtils.containsIgnoreCase(getAllPlayerNames(), name);
		if (exist) {
			return true;
		}

		try {
			UUID u = UUID.fromString(name);
			return userExist(u);
		} catch (Exception ignored) {
		}

		return false;
	}

	public void removeUUID(UUID key) {
		withActiveStorageOwner(owner -> {
			if (activeStorageType(owner).equals(UserStorage.SQLITE)) {
				activeTable(owner).delete(new Column("uuid", new DataValueString(key.toString())));
			} else if (activeStorageType(owner).equals(UserStorage.MYSQL)) {
				activeMysql(owner).deletePlayer(key.toString());
			}
			return null;
		});
	}

	public boolean userExist(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}

		// Fast path: check cached name->uuid mapping
		String cachedUuid = UuidLookup.getInstance().getCachedUUID(name);
		if (cachedUuid != null && !cachedUuid.isEmpty()) {
			return true;
		}

		// Storage-derived list
		boolean exist = ArrayUtils.containsIgnoreCase(getAllPlayerNames(), name);
		if (exist) {
			return true;
		}

		// Best-effort: if it's a UUID string and exists as user
		try {
			UUID u = UUID.fromString(name);
			return userExist(u);
		} catch (Exception ignored) {
		}

		return false;
	}

	public boolean userExist(UUID uuid) {
		if (uuid != null) {
			return getAllUUIDs().contains(uuid.toString());
		}
		return false;
	}
}
