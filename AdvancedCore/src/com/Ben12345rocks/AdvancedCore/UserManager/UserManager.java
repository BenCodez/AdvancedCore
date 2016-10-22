package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;

public class UserManager {
	/** The instance. */
	static UserManager instance = new UserManager();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of UserManager.
	 *
	 * @return single instance of UserManager
	 */
	public static UserManager getInstance() {
		return instance;
	}

	private ArrayList<User> users;

	/**
	 * Instantiates a new utils.
	 */
	public UserManager() {
	}

	public User getUser(OfflinePlayer player) {
		return getUser(player.getName());
	}

	public User getUser(Player player) {
		return getUser(player.getName());
	}

	public User getUser(String playerName) {
		return getUser(new UUID(Utils.getInstance().getUUID(playerName)));
	}

	@SuppressWarnings("deprecation")
	public User getUser(UUID uuid) {
		for (User user : users) {
			if (user.getUUID().equals(uuid.getUUID())) {
				return user;
			}
		}
		User user = new User(plugin, uuid);
		users.add(user);
		return user;
	}

	public ArrayList<User> getUsers() {
		return users;
	}

	@SuppressWarnings("deprecation")
	public void loadUsers() {
		users = new ArrayList<User>();
		for (String name : Data.getInstance().getPlayerNames()) {
			User user = new User(plugin, name);
			users.add(user);
		}
	}

	public void saveUsers() {
		for (User user : users) {
			user.save();
		}
	}

}
