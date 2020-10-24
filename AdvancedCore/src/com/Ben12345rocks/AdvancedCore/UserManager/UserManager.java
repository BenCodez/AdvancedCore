package com.Ben12345rocks.AdvancedCore.UserManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.Column;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

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
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	private Object obj = new Object();

	/**
	 * Instantiates a new user manager.
	 */
	public UserManager() {
	}

	public ArrayList<String> getAllPlayerNames() {
		ArrayList<String> names = new ArrayList<String>();
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			for (String uuid : getAllUUIDs()) {
				User user = UserManager.getInstance().getUser(new UUID(uuid));
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
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
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
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			List<Column> cols = AdvancedCorePlugin.getInstance().getSQLiteUserTable().getRows();
			ArrayList<String> uuids = new ArrayList<String>();
			for (Column col : cols) {
				uuids.add((String) col.getValue());
			}
			return uuids;
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
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

	public User getRandomUser() {
		if (getAllUUIDs().size() > 0) {
			getUser(getAllUUIDs().get(0));
		}
		return null;
	}

	public User getUser(java.util.UUID uuid) {
		return getUser(new UUID(uuid.toString()));
	}

	/**
	 * Gets the user.
	 *
	 * @param player the player
	 * @return the user
	 */
	public User getUser(OfflinePlayer player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param player the player
	 * @return the user
	 */
	public User getUser(Player player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param playerName the player name
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public User getUser(String playerName) {
		return new User(plugin, getProperName(playerName));
	}

	/**
	 * Gets the user.
	 *
	 * @param uuid the uuid
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public User getUser(UUID uuid) {
		return new User(plugin, uuid);
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
				public void onStartUp(User user) {
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
