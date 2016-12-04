package com.Ben12345rocks.AdvancedCore.Data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

// TODO: Auto-generated Javadoc
/**
 * The Class Data.
 */
public class Data {

	/** The instance. */
	static Data instance = new Data();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Gets the single instance of Data.
	 *
	 * @return single instance of Data
	 */
	public static Data getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new data.
	 */
	private Data() {
	}

	/**
	 * Gets the data.
	 *
	 * @param user
	 *            the user
	 * @return the data
	 */
	public FileConfiguration getData(User user) {
		File dFile = getPlayerFile(user);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		return data;
	}

	/**
	 * Gets the files.
	 *
	 * @return the files
	 */
	public ArrayList<String> getFiles() {
		File folder = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data");
		String[] fileNames = folder.list();
		if (fileNames != null) {
			return ArrayUtils.getInstance().convert(fileNames);
		} else {
			return new ArrayList<String>();
		}
	}

	/**
	 * Gets the name.
	 *
	 * @param user
	 *            the user
	 * @return the name
	 */
	public String getName(User user) {
		return getData(user).getString("Name");
	}

	/**
	 * Gets the player file.
	 *
	 * @param user
	 *            the user
	 * @return the player file
	 */
	public File getPlayerFile(User user) {
		String playerName = user.getPlayerName();
		String uuid = user.getUUID();
		// plugin.debug(playerName + ":" + uuid);
		// plugin.debug(plugin.toString());
		File dFile = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data", uuid + ".yml");
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			try {
				data.save(dFile);
				setPlayerName(user);

				plugin.debug("Created file: " + uuid + ".yml from player: " + playerName);

			} catch (IOException e) {
				Bukkit.getServer().getLogger()
						.severe(ChatColor.RED + "Could not create " + uuid + ".yml! Name: " + playerName);

			}
		}
		return dFile;

	}

	/**
	 * Gets the player names.
	 *
	 * @return the player names
	 */
	public ArrayList<String> getPlayerNames() {
		ArrayList<String> files = getFiles();
		ArrayList<String> names = new ArrayList<String>();
		if (files != null) {
			for (String playerFile : files) {
				String uuid = playerFile.replace(".yml", "");
				String playerName = PlayerUtils.getInstance().getPlayerName(uuid);
				if (playerName != null) {
					names.add(playerName);
				}
			}
			Set<String> namesSet = new HashSet<String>(names);
			names = ArrayUtils.getInstance().convert(namesSet);
			return names;
		}
		return new ArrayList<String>();
	}

	/**
	 * Gets the players UUI ds.
	 *
	 * @return the players UUI ds
	 */
	@SuppressWarnings("unused")
	public ArrayList<String> getPlayersUUIDs() {
		ArrayList<String> files = getFiles();
		if (files != null) {
			ArrayList<String> uuids = new ArrayList<String>();
			if (files.size() > 0) {
				for (String playerFile : files) {
					String uuid = playerFile.replace(".yml", "");
					uuids.add(uuid);
				}
				if (uuids == null) {
					return null;
				} else {
					return uuids;
				}
			}
		}
		return null;

	}

	/**
	 * Gets the users.
	 *
	 * @return the users
	 */
	@Deprecated
	public Set<User> getUsers() {
		Set<User> users = new HashSet<User>();
		ArrayList<User> players = UserManager.getInstance().getUsers();

		for (User user : players) {
			users.add(user);

		}
		return users;
	}

	/**
	 * Checks for joined before.
	 *
	 * @param user
	 *            the user
	 * @return true, if successful
	 */
	public boolean hasJoinedBefore(User user) {
		try {
			return getPlayersUUIDs().contains(user.getUUID());
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Save data.
	 *
	 * @param user
	 *            the user
	 */
	public void saveData(User user) {
		File dFile = getPlayerFile(user);
		String playerName = user.getPlayerName();
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		try {
			data.save(dFile);
		} catch (IOException e) {
			Bukkit.getServer().getLogger().severe(
					ChatColor.RED + "Could not save " + PlayerUtils.getInstance().getUUID(playerName) + ".yml!");
		}

	}

	/**
	 * Sets the.
	 *
	 * @param user
	 *            the user
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public void set(User user, String path, Object value) {
		try {
			File dFile = getPlayerFile(user);
			FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
			data.set(path, value);
			FilesManager.getInstance().editFile(dFile, data);
		} catch (IllegalArgumentException ex) {
			plugin.debug("Tried to set an empty path for a user. Name: " + user.getPlayerName() + " Path: " + path);
		}
	}

	/**
	 * Sets the player name.
	 *
	 * @param user
	 *            the new player name
	 */
	public void setPlayerName(User user) {
		set(user, "PlayerName", user.getPlayerName());
	}

	/**
	 * Sets the up.
	 *
	 * @param user
	 *            the new up
	 */
	public void setup(User user) {
		if (!plugin.getPlugin().getDataFolder().exists()) {
			plugin.getPlugin().getDataFolder().mkdir();
		}

		String uuid = user.getUUID();
		String playerName = user.getPlayerName();
		if (playerName == null) {
			PlayerUtils.getInstance().getPlayerName(uuid);
		}

		if (playerName == null) {
			return;
		}

		File dFile = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data", uuid + ".yml");
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			try {
				data.save(dFile);
				setPlayerName(user);

				plugin.debug("Created file: " + uuid + ".yml from player: " + playerName);

			} catch (IOException e) {
				plugin.getPlugin().getLogger()
						.severe(ChatColor.RED + "Could not create " + uuid + ".yml! Name: " + playerName);

			}
		}
	}
}
