package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
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

	private HashMap<String, User> users = new HashMap<String, User>();

	/**
	 * Instantiates a new user manager.
	 */
	public UserManager() {
	}

	public synchronized ArrayList<String> getAllUUIDs() {
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
		return Data.getInstance().getPlayersUUIDs();
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			List<Column> cols = AdvancedCoreHook.getInstance().getSQLiteUserTable().getRows();
			ArrayList<String> uuids = new ArrayList<String>();
			for (Column col : cols) {
				uuids.add((String) col.getValue());
			}
			return uuids;
		}
		return new ArrayList<String>();
	}

	/**
	 * Gets the user.
	 *
	 * @param player
	 *            the player
	 * @return the user
	 */
	public synchronized User getUser(OfflinePlayer player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param player
	 *            the player
	 * @return the user
	 */
	public synchronized User getUser(Player player) {
		return getUser(player.getName());
	}

	/**
	 * Gets the user.
	 *
	 * @param playerName
	 *            the player name
	 * @return the user
	 */
	public synchronized User getUser(String playerName) {
		return getUser(new UUID(PlayerUtils.getInstance().getUUID(playerName)));
	}

	/**
	 * Gets the user.
	 *
	 * @param uuid
	 *            the uuid
	 * @return the user
	 */
	@SuppressWarnings("deprecation")
	public synchronized User getUser(UUID uuid) {
		if (users.containsKey(uuid.toString())) {
			return users.get(uuid.toString());
		}
		User user = new User(plugin.getPlugin(), uuid);
		users.put(uuid.getUUID(), user);
		return user;
	}
}
