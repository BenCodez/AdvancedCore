package com.Ben12345rocks.AdvancedCore.Util.Misc;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.google.common.collect.Iterables;

public class PlayerUtils {
	/** The instance. */
	static PlayerUtils instance = new PlayerUtils();

	public static PlayerUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private PlayerUtils() {
	}

	/**
	 * Gets the player meta.
	 *
	 * @param player
	 *            the player
	 * @param str
	 *            the str
	 * @return the player meta
	 */
	public Object getPlayerMeta(Player player, String str) {
		for (MetadataValue meta : player.getMetadata(str)) {
			if (meta.getOwningPlugin().equals(plugin.getPlugin())) {
				return meta.value();
			}
		}
		return null;
	}

	/*
	 * private String getPlayerName(String uuid) { if ((uuid == null) ||
	 * uuid.equalsIgnoreCase("null")) { plugin.debug("Null UUID"); return null; }
	 *
	 * String name = "";
	 *
	 * java.util.UUID u = java.util.UUID.fromString(uuid); Player player =
	 * Bukkit.getPlayer(uuid); if (player == null) { OfflinePlayer p =
	 * Bukkit.getOfflinePlayer(u); if (p.hasPlayedBefore() || p.isOnline()) { name =
	 * p.getName(); } else if (plugin.isCheckNameMojang()) { name =
	 * Thread.getInstance().getThread().getName(u); } } else { name =
	 * player.getName(); }
	 *
	 * if (name.equals("")) { name = UserManager.getInstance().getUser(new
	 * UUID(uuid)).getData().getString("PlayerName"); if (!name.equals("")) { return
	 * name; } name = "Error getting name"; } return name;
	 *
	 * }
	 */

	public String getPlayerName(User user, String uuid) {
		if ((uuid == null) || uuid.equalsIgnoreCase("null") || uuid.isEmpty()) {
			plugin.debug("Null UUID");
			return "";
		}

		if (plugin.getUuids().containsValue(uuid)) {
			for (Entry<String, String> entry : plugin.getUuids().entrySet()) {
				if (entry.getValue().equals(uuid)) {
					String n = entry.getKey();
					if (n != null && !n.isEmpty() && !n.equalsIgnoreCase("Error getting name")) {
						return n;
					}
				}
			}

		}

		String name = "";

		java.util.UUID u = java.util.UUID.fromString(uuid);
		Player player = Bukkit.getPlayer(u);

		String storedName = user.getData().getString("PlayerName");
		// String storedName = "";
		if (player != null) {
			name = player.getName();

			if (storedName == null || name != storedName || storedName.isEmpty()
					|| storedName.equalsIgnoreCase("Error getting name")) {
				user.getData().setString("PlayerName", name);
			}
			return name;
		}
		return storedName;

	}

	public Player getRandomPlayer() {
		return Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
	}

	/**
	 * Gets the uuid.
	 *
	 * @param playerName
	 *            the player name
	 * @return the uuid
	 */
	@SuppressWarnings("deprecation")
	public String getUUID(String playerName) {
		if (playerName == null) {
			return null;
		}

		Player player = Bukkit.getPlayer(playerName);
		if (player != null) {
			return player.getUniqueId().toString();
		}

		String uuid = getUUIDLookup(playerName);

		if (!uuid.equals("")) {
			return uuid;
		}
		try {
			OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
			return p.getUniqueId().toString();
		} catch (Exception e) {
			e.printStackTrace();
			return getUUIDLookup(playerName);
		}

	}

	private String getUUIDLookup(String playerName) {
		ConcurrentHashMap<String, String> uuids = plugin.getUuids();
		if (uuids != null) {
			String uuid = uuids.get(playerName);
			if (uuid != null) {
				return uuid;
			} else {
				for (Entry<String, String> entry : uuids.entrySet()) {
					if (entry.getKey().equalsIgnoreCase(playerName)) {
						return entry.getValue();
					}
				}
			}
		}

		for (String uuid : UserManager.getInstance().getAllUUIDs()) {
			User user = UserManager.getInstance().getUser(new UUID(uuid));
			String name = user.getData().getString("PlayerName");
			if (name.equals(playerName)) {
				plugin.getUuids().put(playerName, uuid);
				return uuid;
			}
		}
		return "";
	}

	public boolean hasEitherPermission(CommandSender sender, String perm) {
		if (sender instanceof Player) {
			Player player = (Player) sender;

			if (perm.equals("")) {
				return true;
			}

			if (plugin.getPerms() != null) {
				boolean hasPerm = false;
				for (String permission : perm.split("\\|")) {

					boolean has = plugin.getPerms().playerHas(player, permission);
					if (!hasPerm) {
						hasPerm = has;
					}
				}

				return hasPerm;
			} else {
				boolean hasPerm = false;

				if (!perm.equals("")) {
					for (String permission : perm.split("\\|")) {
						if (sender.hasPermission(permission)) {
							hasPerm = true;
						}
					}
				} else {
					hasPerm = true;
				}
				return hasPerm;
			}

		} else {
			return true;
		}
	}

	/**
	 * Checks for permission.
	 *
	 * @param sender
	 *            the sender
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	public boolean hasPermission(CommandSender sender, String perm) {
		return sender.hasPermission(plugin.getPlugin().getName() + "." + perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param player
	 *            the player
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	public boolean hasPermission(Player player, String perm) {
		return player.hasPermission(plugin.getPlugin().getName() + "." + perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param playerName
	 *            the player name
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	public boolean hasPermission(String playerName, String perm) {
		if (playerName == null) {
			return false;
		}
		Player player = Bukkit.getPlayer(playerName);
		if (player != null) {
			return player.hasPermission(plugin.getPlugin().getName() + "." + perm);
		}
		return false;
	}

	/**
	 * Checks for server permission.
	 *
	 * @param playerName
	 *            the player name
	 * @param perm
	 *            the perm
	 * @return true, if successful
	 */
	public boolean hasServerPermission(String playerName, String perm) {
		if (playerName == null) {
			return false;
		}
		Player player = Bukkit.getPlayer(playerName);
		if (player != null) {
			return player.hasPermission(perm);
		}
		return false;
	}

	/**
	 * Checks if is player.
	 *
	 * @param sender
	 *            the sender
	 * @return true, if is player
	 */
	public boolean isPlayer(CommandSender sender) {
		if (sender instanceof Player) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if is player online.
	 *
	 * @param playerName
	 *            the player name
	 * @return true, if is player online
	 */
	public boolean isPlayerOnline(String playerName) {
		if (playerName == null) {
			return false;
		}
		Player player = Bukkit.getPlayer(playerName);
		if (player != null) {
			return true;
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public boolean isValidUser(String name) {
		Player player = Bukkit.getPlayer(name);
		if (player != null) {
			return true;
		}

		plugin.extraDebug("Checking if user exists in database: " + name);
		boolean userExist = UserManager.getInstance().userExist(name);
		if (userExist) {
			return userExist;
		}

		plugin.extraDebug("Checking offline player: " + name);
		OfflinePlayer p = Bukkit.getOfflinePlayer(name);
		if (p.hasPlayedBefore()) {
			// plugin.extraDebug(name + " has joined before");
			return true;
		}
		return false;
	}

	/**
	 * Sets the player meta.
	 *
	 * @param player
	 *            the player
	 * @param str
	 *            the str
	 * @param value
	 *            the value
	 */
	public void setPlayerMeta(Player player, String str, Object value) {
		player.removeMetadata(str, plugin.getPlugin());
		player.setMetadata(str, new MetadataValue() {

			@Override
			public boolean asBoolean() {

				return false;
			}

			@Override
			public byte asByte() {

				return 0;
			}

			@Override
			public double asDouble() {

				return 0;
			}

			@Override
			public float asFloat() {

				return 0;
			}

			@Override
			public int asInt() {

				return 0;
			}

			@Override
			public long asLong() {

				return 0;
			}

			@Override
			public short asShort() {

				return 0;
			}

			@Override
			public String asString() {

				return null;
			}

			@Override
			public Plugin getOwningPlugin() {
				return plugin.getPlugin();
			}

			@Override
			public void invalidate() {
			}

			@Override
			public Object value() {
				return value;
			}

		});
	}

}
