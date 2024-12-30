package com.bencodez.advancedcore.api.misc;

import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserStorage;

public class PlayerManager {

	/** The instance. */
	static PlayerManager instance = new PlayerManager();

	public static PlayerManager getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	private PlayerManager() {
	}

	public boolean damageItemInHand(Player player, int damage) {
		ItemStack itemInHand = player.getInventory().getItemInMainHand();
		ItemMeta meta = itemInHand.getItemMeta();
		if (meta instanceof Damageable) {
			Damageable dMeta = (Damageable) meta;
			int level = itemInHand.getEnchantmentLevel(MiscUtils.getInstance().getEnchant("UNBREAKING", "DURABILITY"));
			int chance = (100 / (level + 1));
			int addedDamage = 0;
			for (int i = 0; i < damage; i++) {
				if (chance == 100 || ThreadLocalRandom.current().nextInt(100) < chance) {
					addedDamage++;
				}
			}
			if (addedDamage > 0) {
				dMeta.setDamage(dMeta.getDamage() + addedDamage);
				itemInHand.setItemMeta(dMeta);
				if (dMeta.getDamage() > (itemInHand.getType().getMaxDurability())) {
					player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public String getPlayerName(AdvancedCoreUser user, String uuid) {
		return getPlayerName(user, uuid, true);
	}

	public String getPlayerName(AdvancedCoreUser user, String uuid, boolean useCache) {
		if ((uuid == null) || uuid.equalsIgnoreCase("null") || uuid.isEmpty()) {
			plugin.debug("Null UUID");
			return "";
		}

		if (plugin.getUuidNameCache().containsKey(uuid)) {
			String n = plugin.getUuidNameCache().get(uuid);
			if (n != null && !n.isEmpty() && !n.equalsIgnoreCase("Error getting name")) {
				return n;
			}
		}

		String name = "";

		if (uuid.length() <= 5) {
			return "Error getting name";
		}
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
	}

	public ItemStack getPlayerSkull(UUID uuid, String name) {
		return getPlayerSkull(uuid, name, true);
	}

	public ItemStack getPlayerSkull(UUID uuid, String name, boolean force) {
		return plugin.getSkullCacheHandler().getSkull(uuid, name);
		/*
		 * String skullMaterial = "PLAYER_HEAD"; if
		 * (NMSManager.getInstance().isVersion("1.12")) { skullMaterial = "PAPER"; } if
		 * (AdvancedCorePlugin.getInstance().getOptions().isLoadSkulls()) { if
		 * (SkullHandler.getInstance().hasSkull(playerName)) { try { return
		 * SkullHandler.getInstance().getItemStack(playerName); } catch (Exception e) {
		 * e.printStackTrace(); } } else {
		 * SkullHandler.getInstance().loadSkull(playerName); if (force) { return new
		 * ItemBuilder(Material.valueOf(skullMaterial),
		 * 1).setSkullOwner(playerName).toItemStack(); } else { return new
		 * ItemBuilder(Material.valueOf(skullMaterial), 1).toItemStack(); } } } return
		 * new ItemBuilder(Material.valueOf(skullMaterial),
		 * 1).setSkullOwner(playerName).toItemStack();
		 */

	}

	/**
	 * Gets the uuid.
	 *
	 * @param playerName the player name
	 * @return the uuid
	 */
	public String getUUID(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return null;
		}

		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) {
			return player.getUniqueId().toString();
		}

		if (!plugin.getOptions().isOnlineMode()) {
			return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();
		}
		String uuid = getUUIDLookup(playerName);

		if (!uuid.equals("")) {
			return uuid;
		}

		try {
			@SuppressWarnings("deprecation")
			OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
			return p.getUniqueId().toString();
		} catch (Exception e) {
			e.printStackTrace();
			return getUUIDLookup(playerName);
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

		if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
			String name = plugin.getMysql().getUUID(playerName);
			if (name != null) {
				return name;
			}
		} else if (plugin.getStorageType().equals(UserStorage.SQLITE)) {
			String name = plugin.getSQLiteUserTable().getUUID(playerName);
			if (name != null) {
				return name;
			}
		} else {
			for (String uuid : plugin.getUserManager().getAllUUIDs()) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
				user.dontCache();
				String name = user.getData().getString("PlayerName", true);
				if (name != null && name.equals(playerName)) {
					plugin.getUuidNameCache().put(uuid, playerName);
					return uuid;
				}
			}
		}
		return "";
	}

	public boolean hasEitherPermission(CommandSender sender, String perm) {
		if (!(sender instanceof Player)) {
			return true;
		}
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

		Player player = Bukkit.getPlayer(playerUUID);
		if (player != null) {
			return player.hasPermission(perm);
		}

		if (plugin.getLuckPermsHandle() != null && plugin.getLuckPermsHandle().luckpermsApiLoaded()) {
			// plugin.devDebug("Attempting to use luckperms");
			if (plugin.getLuckPermsHandle().hasPermission(playerUUID, perm)) {
				// plugin.devDebug("does have permission: " + perm);
				return true;
			}
		}

		if (AdvancedCorePlugin.getInstance().getOptions().isUseVaultPermissions() && plugin.getPerms() != null
				&& plugin.getPerms().isEnabled()) {
			return plugin.getPerms().playerHas(Bukkit.getWorlds().get(0).getName(), Bukkit.getOfflinePlayer(playerUUID),
					perm);
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

		plugin.extraDebug("Player " + name + " does not exist");
		return false;
	}

}
