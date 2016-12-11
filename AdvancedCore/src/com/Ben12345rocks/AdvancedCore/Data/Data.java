package com.Ben12345rocks.AdvancedCore.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.User;
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
	public synchronized FileConfiguration getData(String uuid) {
		File dFile = getPlayerFile(uuid);
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
	public synchronized String getName(String uuid) {
		return getData(uuid).getString("PlayerName", "");
	}

	public void deletePlayerFile(String uuid) {
		File dFile = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data", uuid + ".yml");
		dFile.delete();
	}

	/**
	 * Gets the player file.
	 *
	 * @param user
	 *            the user
	 * @return the player file
	 */
	public synchronized File getPlayerFile(String uuid) {
		File dFile = new File(plugin.getPlugin().getDataFolder() + File.separator + "Data", uuid + ".yml");
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			FilesManager.getInstance().editFile(dFile, data);
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
	public ArrayList<String> getPlayersUUIDs() {
		ArrayList<String> files = getFiles();
		if (files != null) {
			ArrayList<String> uuids = new ArrayList<String>();
			if (files.size() > 0) {
				for (String playerFile : files) {
					String uuid = playerFile.replace(".yml", "");
					uuids.add(uuid);
				}
				return uuids;
			}
		}
		return new ArrayList<String>();

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
	 * Sets the.
	 *
	 * @param user
	 *            the user
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public synchronized void set(String uuid, String path, Object value) {
		try {
			File dFile = getPlayerFile(uuid);
			FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
			data.set(path, value);
			FilesManager.getInstance().editFile(dFile, data);
		} catch (IllegalArgumentException ex) {
			plugin.debug("Tried to set an empty path for a user. UUID: " + uuid + " Path: " + path);
		}
	}

	/**
	 * Sets the player name.
	 *
	 * @param user
	 *            the new player name
	 */
	public synchronized void setPlayerName(String uuid, String playerName) {
		set(uuid, "PlayerName", playerName);
	}
}
