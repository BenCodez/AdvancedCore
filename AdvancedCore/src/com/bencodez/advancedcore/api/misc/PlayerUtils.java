package com.bencodez.advancedcore.api.misc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.jsonparser.JsonParser;
import com.bencodez.advancedcore.api.skull.SkullHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.nms.NMSManager;
import com.google.common.collect.Iterables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PlayerUtils {
	private static final BlockFace[] axis = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

	/** The instance. */
	static PlayerUtils instance = new PlayerUtils();

	private static final BlockFace[] radial = { BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST,
			BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST };

	public static PlayerUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	private PlayerUtils() {
	}

	public boolean canBreakBlock(Player p, Block b) {
		BlockBreakEvent block = new BlockBreakEvent(b, p);
		Bukkit.getPluginManager().callEvent(block);
		if (!block.isCancelled()) {
			return true;
		}
		return false;
	}

	public boolean canInteract(Player p, Block clickedBlock, Action action, ItemStack item, BlockFace clickedFace) {
		PlayerInteractEvent event = new PlayerInteractEvent(p, action, item, clickedBlock, clickedFace);
		Bukkit.getPluginManager().callEvent(event);
		if (event.useItemInHand().equals(Event.Result.DENY)) {
			return false;
		}
		return true;
	}

	public java.util.UUID fetchUUID(String playerName) throws Exception {
		// Get response from Mojang API
		URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.connect();

		if (connection.getResponseCode() == 400) {
			plugin.debug("There is no player with the name \"" + playerName + "\"!");
			return null;
		}

		InputStream inputStream = connection.getInputStream();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

		// Parse JSON response and get UUID

		JsonElement element = JsonParser.parseReader(bufferedReader);
		JsonObject object = element.getAsJsonObject();
		String uuidAsString = object.get("id").getAsString();

		// Return UUID
		return parseUUIDFromString(uuidAsString);
	}

	/**
	 * Gets the player meta.
	 *
	 * @param player the player
	 * @param str    the str
	 * @return the player meta
	 */
	public Object getPlayerMeta(Player player, String str) {
		for (MetadataValue meta : player.getMetadata(str)) {
			if (meta.getOwningPlugin().equals(plugin)) {
				return meta.value();
			}
		}
		return null;
	}

	public String getPlayerName(AdvancedCoreUser user, String uuid) {
		return getPlayerName(user, uuid, true);
	}

	public String getPlayerName(AdvancedCoreUser user, String uuid, boolean useCache) {
		if ((uuid == null) || uuid.equalsIgnoreCase("null") || uuid.isEmpty()) {
			plugin.debug("Null UUID");
			return "";
		}
		
		if (plugin.getOptions().isGeyserPrefixSupport()) {
			if (plugin.getGeyserHandler().isFloodgatePlayer(UUID.fromString(uuid))) {
				return plugin.getGeyserHandler().getFloodgateName(UUID.fromString(uuid));
			}
		}

		if (plugin.getUuidNameCache().containsKey(uuid)) {
			String n = plugin.getUuidNameCache().get(uuid);
			if (n != null && !n.isEmpty() && !n.equalsIgnoreCase("Error getting name")) {
				return n;
			}
		}

		String name = "";

		if (uuid.length() > 5) {
			java.util.UUID u = java.util.UUID.fromString(uuid);
			Player player = Bukkit.getPlayer(u);

			String storedName = user.getData().getString("PlayerName", useCache, true);
			// String storedName = "";
			if (player != null) {
				name = player.getName();

				if (storedName == null || name != storedName || storedName.isEmpty()
						|| storedName.equalsIgnoreCase("Error getting name")) {
					if (user.getUserData().hasData()) {
						user.getData().setString("PlayerName", name);
					}
				}
				return name;
			}
			return storedName;
		} else {
			return "Error getting name";
		}
	}

	public ItemStack getPlayerSkull(String playerName) {
		return getPlayerSkull(playerName, true);
	}

	/*
	 * private String getPlayerName(String uuid) { if ((uuid == null) ||
	 * uuid.equalsIgnoreCase("null")) { plugin.debug("Null UUID"); return null; }
	 * String name = ""; java.util.UUID u = java.util.UUID.fromString(uuid); Player
	 * player = Bukkit.getPlayer(uuid); if (player == null) { OfflinePlayer p =
	 * Bukkit.getOfflinePlayer(u); if (p.hasPlayedBefore() || p.isOnline()) { name =
	 * p.getName(); } else if (plugin.isCheckNameMojang()) { name =
	 * Thread.getInstance().getThread().getName(u); } } else { name =
	 * player.getName(); } if (name.equals("")) { name =
	 * plugin.getUserManager().getUser(new
	 * UUID(uuid)).getData().getString("PlayerName"); if (!name.equals("")) { return
	 * name; } name = "Error getting name"; } return name; }
	 */

	@SuppressWarnings("deprecation")
	public ItemStack getPlayerSkull(String playerName, boolean force) {
		String skullMaterial = "PLAYER_HEAD";
		if (NMSManager.getInstance().isVersion("1.12")) {
			skullMaterial = "PAPER";
		}
		if (AdvancedCorePlugin.getInstance().getOptions().isLoadSkulls()) {
			if (SkullHandler.getInstance().hasSkull(playerName)) {
				try {
					return SkullHandler.getInstance().getItemStack(playerName);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				SkullHandler.getInstance().loadSkull(playerName);
				if (force) {
					return new ItemBuilder(Material.valueOf(skullMaterial), 1).setSkullOwner(playerName).toItemStack();
				} else {
					return new ItemBuilder(Material.valueOf(skullMaterial), 1).toItemStack();
				}
			}
		}
		return new ItemBuilder(Material.valueOf(skullMaterial), 1).setSkullOwner(playerName).toItemStack();

	}

	public Player getRandomOnlinePlayer() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			return player;
		}

		return null;
	}

	public Player getRandomPlayer() {
		return Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
	}

	/**
	 * Gets the uuid.
	 *
	 * @param playerName the player name
	 * @return the uuid
	 */
	@SuppressWarnings("deprecation")
	public String getUUID(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return null;
		}

		if (plugin.getOptions().isGeyserPrefixSupport()) {
			if (plugin.getGeyserHandler().isFloodgatePlayer(playerName)) {
				if (!playerName.startsWith(plugin.getOptions().getGeyserPrefix())) {
					return getUUID(plugin.getOptions().getGeyserPrefix() + playerName);
				}
			}
		}

		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) {
			return player.getUniqueId().toString();
		}

		if (plugin.getOptions().isOnlineMode()) {

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
		} else {
			return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();
		}
	}

	private String getUUIDLookup(String playerName) {
		if (playerName == null) {
			return "";
		}
		ConcurrentHashMap<String, String> uuids = plugin.getUuidNameCache();
		if (uuids != null) {
			for (Entry<String, String> entry : uuids.entrySet()) {
				if (entry.getValue().equalsIgnoreCase(playerName)) {
					return entry.getKey();
				}
			}
		}

		for (String uuid : plugin.getUserManager().getAllUUIDs()) {
			AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
			user.dontCache();
			String name = user.getData().getString("PlayerName", true);
			if (name != null && name.equals(playerName)) {
				plugin.getUuidNameCache().put(uuid, playerName);
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

			if (AdvancedCorePlugin.getInstance().getOptions().isUseVaultPermissions() && plugin.getPerms() != null
					&& plugin.getPerms().isEnabled()) {
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

				for (String permission : perm.split("\\|")) {
					if (sender.hasPermission(permission)) {
						hasPerm = true;
					}
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
	 * @param sender the sender
	 * @param perm   the perm
	 * @return true, if successful
	 */
	public boolean hasPermission(CommandSender sender, String perm) {
		return sender.hasPermission(plugin.getName() + "." + perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param player the player
	 * @param perm   the perm
	 * @return true, if successful
	 */
	public boolean hasPermission(Player player, String perm) {
		return player.hasPermission(plugin.getName() + "." + perm);
	}

	/**
	 * Checks for permission.
	 *
	 * @param playerName the player name
	 * @param perm       the perm
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
	 * @param playerUUID the player UUID
	 * @param perm       the perm
	 * @return true, if successful
	 */
	public boolean hasServerPermission(UUID playerUUID, String perm) {
		if (playerUUID == null) {
			return false;
		}

		if (AdvancedCorePlugin.getInstance().getOptions().isUseVaultPermissions() && plugin.getPerms() != null
				&& plugin.getPerms().isEnabled()) {
			return plugin.getPerms().playerHas(Bukkit.getWorlds().get(0).getName(), Bukkit.getOfflinePlayer(playerUUID),
					perm);
		}

		Player player = Bukkit.getPlayer(playerUUID);
		if (player != null) {
			return player.hasPermission(perm);
		}
		return false;
	}

	/**
	 * Checks for server permission.
	 *
	 * @param playerName the player name
	 * @param perm       the perm
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
	 * @param sender the sender
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
	 * @param playerName the player name
	 * @return true, if is player online
	 */
	public boolean isPlayerOnline(String playerName) {
		if (playerName == null) {
			return false;
		}
		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) {
			return true;
		}
		return false;
	}

	public boolean isValidUser(String name) {
		return isValidUser(name, false);
	}

	@SuppressWarnings("deprecation")
	public boolean isValidUser(String name, boolean checkServer) {
		Player player = Bukkit.getPlayerExact(name);
		if (player != null) {
			return true;
		}

		// plugin.extraDebug("Checking if user exists in database: " + name);
		boolean userExist = plugin.getUserManager().userExist(name);
		if (userExist) {
			return userExist;
		}

		if (name.isEmpty()) {
			return false;
		}

		if (checkServer) {
			// plugin.extraDebug("Checking offline player: " + name);
			OfflinePlayer p = Bukkit.getOfflinePlayer(name);
			if (p.hasPlayedBefore() || p.isOnline() || p.getLastPlayed() != 0) {
				// plugin.extraDebug(name + " has joined before");
				return true;
			}
		}

		if (plugin.getOptions().isGeyserPrefixSupport() && !name.startsWith(plugin.getOptions().getGeyserPrefix())) {
			return isValidUser(plugin.getOptions().getGeyserPrefix() + name);
		}
		plugin.extraDebug("Player " + name + " does not exist");
		return false;
	}

	private java.util.UUID parseUUIDFromString(String uuidAsString) {
		String[] parts = { "0x" + uuidAsString.substring(0, 8), "0x" + uuidAsString.substring(8, 12),
				"0x" + uuidAsString.substring(12, 16), "0x" + uuidAsString.substring(16, 20),
				"0x" + uuidAsString.substring(20, 32) };

		long mostSigBits = Long.decode(parts[0]).longValue();
		mostSigBits <<= 16;
		mostSigBits |= Long.decode(parts[1]).longValue();
		mostSigBits <<= 16;
		mostSigBits |= Long.decode(parts[2]).longValue();

		long leastSigBits = Long.decode(parts[3]).longValue();
		leastSigBits <<= 48;
		leastSigBits |= Long.decode(parts[4]).longValue();

		return new java.util.UUID(mostSigBits, leastSigBits);
	}

	/**
	 * Sets the player meta.
	 *
	 * @param player the player
	 * @param str    the str
	 * @param value  the value
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
				return plugin;
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

	public BlockFace yawToFace(float yaw, boolean useSubCardinalDirections) {
		if (useSubCardinalDirections) {
			return radial[Math.round(yaw / 45f) & 0x7].getOppositeFace();
		}

		return axis[Math.round(yaw / 90f) & 0x3].getOppositeFace();
	}

}
