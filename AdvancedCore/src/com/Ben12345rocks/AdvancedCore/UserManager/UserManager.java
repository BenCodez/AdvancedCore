package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Thread.Thread;
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

	private HashMap<String, User> users = new HashMap<String, User>();

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
		if (users.containsKey(uuid.getUUID())) {
			return users.get(uuid.getUUID());
		}
		User user = new User(plugin.getPlugin(), uuid);
		user.setPlayerName();
		users.put(uuid.getUUID(), user);
		return user;
	}

	public ArrayList<String> getAllPlayerNames() {
		return Data.getInstance().getPlayerNames();
	}

	public ArrayList<String> getAllUUIDs() {
		return Data.getInstance().getPlayersUUIDs();
	}

	public void load() {
		Thread.getInstance().run(new Runnable() {
			@Override
			public void run() {
				for (String uuid : getAllUUIDs()) {
					User user = getUser(new UUID(uuid));
					Set<String> data = user.getRawData().getKeys(false);
					if (data.size() < 2) {
						Data.getInstance().deletePlayerFile(uuid);
						users.remove(uuid);
						AdvancedCoreHook.getInstance().debug("Deleted file: " + uuid + ".yml");
					}
				}
			}
		});
	}

}
