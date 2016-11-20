package com.Ben12345rocks.AdvancedCore.Util.Misc;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Data.Data;
import com.Ben12345rocks.AdvancedCore.Objects.User;

public class PlayerUtils {
	/** The instance. */
	static PlayerUtils instance = new PlayerUtils();

	/** The plugin. */
	static Main plugin = Main.plugin;

	public static PlayerUtils getInstance() {
		return instance;
	}

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
			if (meta.getOwningPlugin().equals(Main.plugin)) {
				return meta.value();
			}
		}
		return null;
	}

	/**
	 * Gets the player name.
	 *
	 * @param uuid
	 *            the uuid
	 * @return the player name
	 */
	public String getPlayerName(String uuid) {
		if ((uuid == null) || uuid.equalsIgnoreCase("null")) {

			plugin.debug("Null UUID");

			return null;
		}

		@SuppressWarnings("deprecation")
		User user = new User(plugin, new com.Ben12345rocks.AdvancedCore.Objects.UUID(uuid), false);
		String playerName = Data.getInstance().getName(user);

		if (playerName != null && !playerName.equals("null")) {
			return playerName;
		}

		Player player = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		if (player == null) {
			playerName = Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid)).getName();
			return playerName;
		} else {
			return player.getName();
		}

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
		if (player == null) {
			return Bukkit.getOfflinePlayer(playerName).getUniqueId().toString();
		} else {
			return player.getUniqueId().toString();
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
		return sender.hasPermission(plugin.getName() + "." + perm);
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
		return player.hasPermission(plugin.getName() + "." + perm);
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
			return player.hasPermission(plugin.getName() + "." + perm);
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
		player.removeMetadata(str, plugin);
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
				return Main.plugin;
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
