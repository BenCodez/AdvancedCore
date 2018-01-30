package com.Ben12345rocks.AdvancedCore.UserManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Objects.UserStartup;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.sql.Column;

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

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Instantiates a new user manager.
	 */
	public UserManager() {
	}

	public ArrayList<String> getAllUUIDs() {
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			File folder = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data");
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
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			List<Column> cols = AdvancedCoreHook.getInstance().getSQLiteUserTable().getRows();
			ArrayList<String> uuids = new ArrayList<String>();
			for (Column col : cols) {
				uuids.add((String) col.getValue());
			}
			return uuids;
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			return ArrayUtils.getInstance().convert(AdvancedCoreHook.getInstance().getMysql().getUuids());
		}
		return new ArrayList<String>();
	}

	public String getProperName(String name) {

		for (String s : plugin.getUuids().keySet()) {
			if (s.equalsIgnoreCase(name)) {
				return s;
			}
		}
		return name;
	}

	/**
	 * Gets the user.
	 *
	 * @param player
	 *            the player
	 * @return the user
	 */
	public User getUser(OfflinePlayer player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param player
	 *            the player
	 * @return the user
	 */
	public User getUser(Player player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param playerName
	 *            the player name
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public User getUser(String playerName) {
		return new User(plugin.getPlugin(), getProperName(playerName));
	}

	/**
	 * Gets the user.
	 *
	 * @param uuid
	 *            the uuid
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public User getUser(UUID uuid) {
		return new User(plugin.getPlugin(), uuid);
	}

	public User getUser(java.util.UUID uuid) {
		return getUser(new UUID(uuid.toString()));
	}

	public void purgeOldPlayers() {
		if (plugin.isPurgeOldData()) {
			plugin.addUserStartup(new UserStartup() {

				@Override
				public void onStartUp(User user) {
					int daysOld = plugin.getPurgeMinimumDays();
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

				@Override
				public void onFinish() {
					plugin.debug("Finished purgining");
				}
			});
		}
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)
				&& AdvancedCoreHook.getInstance().getMysql() != null) {
			AdvancedCoreHook.getInstance().getMysql().clearCacheBasic();
		}
	}

	public boolean userExist(String name) {
		for (String s : plugin.getUuids().keySet()) {
			if (s.equalsIgnoreCase(name)) {
				//plugin.extraDebug("Found " + name + " loaded in uuid map");
				return true;
			}
		}

		for (String uuid : getAllUUIDs()) {
			User user1 = getUser(new UUID(uuid));
			if (user1.getPlayerName().equalsIgnoreCase(name)) {
				//plugin.extraDebug("Found " + name + " in database");
				return true;
			}
		}
		return false;
	}

	public boolean userExist(UUID uuid) {
		if (uuid != null && uuid.getUUID() != null) {
			if (getAllUUIDs().contains(uuid.getUUID())) {
				// plugin.debug(uuid.getUUID() + " exists");
				return true;
			}
			// plugin.debug(uuid.getUUID() + " not exist");
		}

		return false;
	}
}
