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
import com.bencodez.advancedcore.api.user.userstorage.Column;

import lombok.Getter;

/**
 * The Class UserManager.
 */
public class UserManager {

	/** The instance. */
	static UserManager instance = new UserManager();

	/**
	 * Gets the single instance of UserManager.
	 *
	 * @return single instance of UserManager
	 */
	public static UserManager getInstance() {
		return instance;
	}

	private Object obj = new Object();

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	@Getter
	private UserDataManager dataManager;

	public void load() {
		dataManager = new UserDataManager(AdvancedCorePlugin.getInstance());
	}

	/**
	 * Instantiates a new user manager.
	 */
	public UserManager() {
		load();
	}

	public ArrayList<String> getAllPlayerNames() {
		ArrayList<String> names = new ArrayList<String>();
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			for (String uuid : getAllUUIDs()) {
				AdvancedCoreUser user = UserManager.getInstance().getUser(java.util.UUID.fromString(uuid));
				String name = user.getPlayerName();
				if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
					names.add(name);
				}
			}
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<String> data = AdvancedCorePlugin.getInstance().getSQLiteUserTable().getNames();
			for (String name : data) {
				if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
					names.add(name);
				}
			}
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			ArrayList<String> data = ArrayUtils.getInstance()
					.convert(AdvancedCorePlugin.getInstance().getMysql().getNames());
			for (String name : data) {
				if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Error getting name")) {
					names.add(name);
				}
			}
		}
		return names;
	}

	public ArrayList<String> getAllUUIDs() {
		return getAllUUIDs(plugin.getStorageType());
	}

	public ArrayList<String> getAllUUIDs(UserStorage storage) {
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
			List<Column> cols = AdvancedCorePlugin.getInstance().getSQLiteUserTable().getRows();
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
					for (String uuid : AdvancedCorePlugin.getInstance().getMysql().getUuids()) {
						uuids.add(uuid);
					}
				} catch (NullPointerException e) {
					e.printStackTrace();
				}
				return uuids;
			}
		}
		return new ArrayList<String>();
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

	@SuppressWarnings("deprecation")
	public AdvancedCoreUser getUser(UUID uuid, String playerName) {
		return new AdvancedCoreUser(plugin, uuid, playerName);
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
		return getUser(player.getName());
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
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)
				&& AdvancedCorePlugin.getInstance().getMysql() != null) {
			AdvancedCorePlugin.getInstance().getMysql().clearCacheBasic();
		}
	}

	public boolean userExist(String name) {
		boolean exist = UserManager.getInstance().getAllPlayerNames().contains(name);
		if (exist) {
			return exist;
		}

		for (String s : plugin.getUuidNameCache().values()) {
			if (s.equalsIgnoreCase(name)) {
				// plugin.extraDebug("Found " + name + " loaded in uuid map");
				return true;
			}
		}
		return false;
	}

	public boolean userExist(UUID uuid) {
		if (uuid != null) {
			if (getAllUUIDs().contains(uuid.toString())) {
				// plugin.debug(uuid.getUUID() + " exists");
				return true;
			}
			// plugin.debug(uuid.getUUID() + " not exist");
		}

		return false;
	}
}
