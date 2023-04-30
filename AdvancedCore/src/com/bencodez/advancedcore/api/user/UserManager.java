package com.bencodez.advancedcore.api.user;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;

import lombok.Getter;

/**
 * The Class UserManager.
 */
public class UserManager {

	@Getter
	private UserDataManager dataManager;

	private Object obj = new Object();

	/** The plugin. */
	private AdvancedCorePlugin plugin;

	@Getter
	private ArrayList<UserDataChanged> userDataChange = new ArrayList<UserDataChanged>();

	public UserManager(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		load();
	}

	@SuppressWarnings("deprecation")
	public ArrayList<String> getAllPlayerNames() {
		if (plugin.isLoadUserData()) {
			ArrayList<String> names = new ArrayList<String>();
			if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
				for (String uuid : getAllUUIDs()) {
					AdvancedCoreUser user = getUser(java.util.UUID.fromString(uuid));
					String name = user.getPlayerName();
					if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
						names.add(name);
					}
				}
			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
				ArrayList<String> data = plugin.getSQLiteUserTable().getNames();
				for (String name : data) {
					if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
						names.add(name);
					}
				}
			} else if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
				ArrayList<String> data = ArrayUtils.getInstance().convert(plugin.getMysql().getNames());
				for (String name : data) {
					if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
						names.add(name);
					}
				}
			}
			return ArrayUtils.getInstance().removeDuplicates(names);
		}
		return new ArrayList<String>();

	}

	public ArrayList<String> getAllUUIDs() {
		return ArrayUtils.getInstance().removeDuplicates(getAllUUIDs(plugin.getStorageType()));
	}

	@SuppressWarnings("deprecation")
	public ArrayList<String> getAllUUIDs(UserStorage storage) {
		if (plugin.isLoadUserData()) {
			if (storage.equals(UserStorage.FLAT)) {
				File folder = new File(plugin.getDataFolder() + File.separator + "Data");
				String[] fileNames = folder.list();
				ArrayList<String> uuids = new ArrayList<String>();
				if (fileNames != null) {
					for (String playerFile : fileNames) {
						if (!playerFile.equals("null") && !playerFile.equals("")) {
							String uuid = playerFile.replace(".yml", "");
							uuids.add(uuid);
						}
					}
				}
				return uuids;
			} else if (storage.equals(UserStorage.SQLITE)) {
				List<Column> cols = plugin.getSQLiteUserTable().getRows();
				ArrayList<String> uuids = new ArrayList<String>();
				for (Column col : cols) {
					if (col.getValue().isString()) {
						uuids.add(col.getValue().getString());
					}
				}
				return uuids;
			} else if (storage.equals(UserStorage.MYSQL)) {
				synchronized (obj) {
					ArrayList<String> uuids = new ArrayList<String>();
					try {
						for (String uuid : plugin.getMysql().getUuids()) {
							uuids.add(uuid);
						}
					} catch (NullPointerException e) {
						e.printStackTrace();
					}
					return uuids;
				}
			}
		}
		return new ArrayList<String>();
	}

	public String getOfflineRewardsPath() {
		if (plugin.getOptions().isPerServerRewards()) {
			return "OfflineRewards" + plugin.getOptions().getServer().replace("-", "_");
		} else {
			return "OfflineRewards";
		}
	}

	public String getProperName(String name) {

		for (String s : plugin.getUuidNameCache().values()) {
			if (s.equalsIgnoreCase(name)) {
				return s;
			}
		}
		return name;
	}

	public AdvancedCoreUser getRandomUser() {
		if (getAllUUIDs().size() > 0) {
			getUser(getAllUUIDs().get(0));
		}
		return null;
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
	}

	public void purgeOldPlayers() {
		if (plugin.getOptions().isPurgeOldData()) {
			plugin.addUserStartup(new UserStartup() {

				@Override
				public void onFinish() {
					plugin.debug("Finished purgining");
				}

				@Override
				public void onStart() {

				}

				@Override
				public void onStartUp(AdvancedCoreUser user) {
					int daysOld = plugin.getOptions().getPurgeMinimumDays();
					int days = user.getNumberOfDaysSinceLogin();
					if (days == -1) {
						// fix ones with no last online
						user.setLastOnline(System.currentTimeMillis());
					}
					if (days > daysOld) {
						plugin.debug("Removing " + user.getUUID() + " because of purge");
						user.remove();
					}
				}
			});
		}
		getDataManager().clearCache();
	}

	public void purgeOldPlayersNow() {
		if (plugin.getOptions().isPurgeOldData()) {
			for (String uuid : getAllUUIDs()) {
				AdvancedCoreUser user = getUser(UUID.fromString(uuid));
				int daysOld = plugin.getOptions().getPurgeMinimumDays();
				int days = user.getNumberOfDaysSinceLogin();
				if (days == -1) {
					// fix ones with no last online
					user.setLastOnline(System.currentTimeMillis());
				}
				if (days > daysOld) {
					plugin.debug("Removing " + user.getUUID() + " because of purge");
					user.remove();
				}
				user.clearCache();
			}
		}
		getDataManager().clearCache();
	}

	public void removeAllKeyValues(String key, DataType type) {
		if (plugin.getStorageType().equals(UserStorage.SQLITE)) {
			plugin.getSQLiteUserTable().wipeColumnData(key);
		} else if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
			plugin.getMysql().wipeColumnData(key);
		} else {
			for (String uuid : getAllUUIDs()) {
				AdvancedCoreUser user = getUser(UUID.fromString(uuid));
				user.dontCache();
				switch (type) {
				case INTEGER:
					user.getData().setInt(key, 0);
					break;
				case STRING:
					user.getData().setString(key, "");
					break;
				default:
					break;
				}

			}
		}
	}

	public boolean userExist(String name) {
		boolean exist = getAllPlayerNames().contains(name);
		if (exist) {
			return exist;
		}

		for (String s : plugin.getUuidNameCache().values()) {
			if (s.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public void copyColumnData(String columnFromName, String columnToName) {
		if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
			plugin.getMysql().copyColumnData(columnFromName, columnToName);
		} else if (plugin.getStorageType().equals(UserStorage.SQLITE)) {
			plugin.getSQLiteUserTable().copyColumnData(columnFromName, columnToName);
		} else if (plugin.getStorageType().equals(UserStorage.FLAT)) {
			for (String uuid : getAllUUIDs()) {
				AdvancedCoreUser user = getUser(UUID.fromString(uuid));
				user.dontCache();
				DataValue data = user.getData().getDataValue(columnFromName);
				user.getData().setValues(columnToName, data);
			}
		}
	}

	public boolean userExist(UUID uuid) {
		if (uuid != null) {
			if (getAllUUIDs().contains(uuid.toString())) {
				return true;
			}
		}

		return false;
	}

	public void onChange(AdvancedCoreUser user, String... keys) {
		for (UserDataChanged change : userDataChange) {
			change.onChange(user, keys);
		}
	}

	public ArrayList<Integer> getNumbersInColumn(String columnName) {
		if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
			return plugin.getMysql().getNumbersInColumn(columnName);
		} else if (plugin.getStorageType().equals(UserStorage.SQLITE)) {
			return plugin.getSQLiteUserTable().getNumbersInColumn(columnName);
		}
		return new ArrayList<Integer>();
	}
}
