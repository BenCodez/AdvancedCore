package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

/**
 * The Class UserManager.
 */
public class UserManager {

	/** The instance. */
	static UserManager instance = new UserManager();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Gets the single instance of UserManager.
	 *
	 * @return single instance of UserManager
	 */
	public static UserManager getInstance() {
		return instance;
	}

	/** The users. */
	private ArrayList<User> users;

	/**
	 * Instantiates a new user manager.
	 */
	public UserManager() {
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
	public User getUser(String playerName) {
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
	public User getUser(UUID uuid) {
		for (User user : users) {
			if (user.getUUID().equals(uuid.getUUID())) {
				return user;
			}
		}
		User user = new User(plugin.getPlugin(), uuid);
		user.setPlayerName();
		users.add(user);
		return user;
	}

	/**
	 * Gets the users.
	 *
	 * @return the users
	 */
	public ArrayList<User> getUsers() {
		return users;
	}

	/**
	 * Load users.
	 */
	@SuppressWarnings("deprecation")
	public void loadUsers() {
		users = new ArrayList<User>();
		for (String name : Data.getInstance().getPlayerNames()) {
			User user = new User(plugin.getPlugin(), name);
			users.add(user);
		}
	}

}
