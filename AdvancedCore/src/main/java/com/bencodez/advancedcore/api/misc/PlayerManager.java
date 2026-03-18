package com.bencodez.advancedcore.api.misc;

import java.util.UUID;
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
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

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
		boolean isUnbreakable = false;
		try {
			isUnbreakable = meta.isUnbreakable();
		} catch (NoSuchMethodError e) {
			// Older versions don't have isUnbreakable(), ignore safely
		}
		if (meta instanceof Damageable && !isUnbreakable) {
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

	/**
	 * @deprecated Use {@link UuidLookup#getPlayerName(AdvancedCoreUser, String)}.
	 */
	@Deprecated
	public String getPlayerName(AdvancedCoreUser user, String uuid) {
		return UuidLookup.getInstance().getPlayerName(user, uuid);
	}

	/**
	 * @deprecated Use
	 *             {@link UuidLookup#getPlayerName(AdvancedCoreUser, String, boolean)}.
	 */
	@Deprecated
	public String getPlayerName(AdvancedCoreUser user, String uuid, boolean useCache) {
		return UuidLookup.getInstance().getPlayerName(user, uuid, useCache);
	}

	public ItemStack getPlayerSkull(UUID uuid, String name) {
		return getPlayerSkull(uuid, name, true);
	}

	public ItemStack getPlayerSkull(UUID uuid, String name, boolean force) {
		return plugin.getSkullCacheHandler().getSkull(uuid, name);
	}

	/**
	 * Gets the uuid.
	 *
	 * @param playerName the player name
	 * @return the uuid
	 *
	 * @deprecated Use {@link UuidLookup#getUUID(String)}.
	 */
	@Deprecated
	public String getUUID(String playerName) {
		return UuidLookup.getInstance().getUUID(playerName);
	}

	public boolean hasEitherPermission(CommandSender sender, String perm) {
		if (!(sender instanceof Player)) {
			return true;
		}
		Player player = (Player) sender;

		if (perm.equals("")) {
			return true;
		}

		if (AdvancedCorePlugin.getInstance().getOptions().isUseVaultPermissions() && plugin.getVaultHandler() != null
				&& plugin.getVaultHandler().getPerms() != null && plugin.getVaultHandler().getPerms().isEnabled()) {
			boolean hasPerm = false;
			for (String permission : perm.split("\\|")) {

				boolean has = plugin.getVaultHandler().getPerms().playerHas(player, permission);
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
	public boolean hasServerPermission(UUID playerUUID, String playername, String perm) {
		if (playerUUID == null) {
			return false;
		}

		Player player = Bukkit.getPlayer(playerUUID);
		if (player != null) {
			return player.hasPermission(perm);
		} else {
			player = Bukkit.getPlayer(playername);
			if (player != null) {
				return player.hasPermission(perm);
			}
		}

		if (plugin.getLuckPermsHandle() != null && plugin.getLuckPermsHandle().luckpermsApiLoaded()) {
			if (plugin.getLuckPermsHandle().hasPermission(playerUUID, perm)) {
				return true;
			}
		}

		if (AdvancedCorePlugin.getInstance().getOptions().isUseVaultPermissions() && plugin.getVaultHandler() != null
				&& plugin.getVaultHandler().getPerms() != null && plugin.getVaultHandler().getPerms().isEnabled()) {
			return plugin.getVaultHandler().getPerms().playerHas(Bukkit.getWorlds().get(0).getName(),
					Bukkit.getOfflinePlayer(playerUUID), perm);
		}

		return false;
	}

	public boolean isValidUser(String name) {
		return isValidUser(name, false);
	}

	@SuppressWarnings("deprecation")
	public boolean isValidUser(String name, boolean checkServer) {
		plugin.extraDebug("isValidUser START: name=" + name + ", checkServer=" + checkServer);

		if (name == null) {
			plugin.extraDebug("isValidUser: name is null -> false");
			return false;
		}

		name = name.trim();

		Player player = Bukkit.getPlayerExact(name);
		if (player != null) {
			plugin.extraDebug("isValidUser: matched ONLINE player -> true (" + player.getName() + ")");
			return true;
		}

		boolean userExist = plugin.getUserManager().userExist(name);
		plugin.extraDebug("isValidUser: userExist(" + name + ")=" + userExist);
		if (userExist) {
			plugin.extraDebug("isValidUser: returning true from userExist");
			return true;
		}

		if (name.isEmpty()) {
			plugin.extraDebug("isValidUser: empty name -> false");
			return false;
		}

		boolean isBedrock = plugin.getBedrockHandle().isBedrock(name);
		plugin.extraDebug("isValidUser: isBedrock(" + name + ")=" + isBedrock);
		if (isBedrock) {
			plugin.extraDebug("isValidUser: bedrock match -> true (skipping offline check)");
			return true;
		}

		if (checkServer) {
			String prefix = plugin.getOptions().getBedrockPlayerPrefix();
			plugin.extraDebug("isValidUser: checkServer enabled, bedrockPrefix=" + prefix);

			if (!name.startsWith(prefix)) {
				OfflinePlayer p = Bukkit.getOfflinePlayer(name);

				boolean hasPlayed = p.hasPlayedBefore();
				boolean isOnline = p.isOnline();
				long lastPlayed = p.getLastPlayed();

				plugin.extraDebug("isValidUser: OfflinePlayer check for " + name + " -> hasPlayedBefore=" + hasPlayed
						+ ", isOnline=" + isOnline + ", lastPlayed=" + lastPlayed);

				if (hasPlayed || isOnline || lastPlayed != 0) {
					plugin.extraDebug("isValidUser: checkServer match -> true");
					return true;
				}
			} else {
				plugin.extraDebug("isValidUser: skipping checkServer due to bedrock prefix match");
			}
		} else {
			plugin.extraDebug("isValidUser: checkServer disabled");
		}

		plugin.extraDebug("isValidUser: FINAL -> false (no checks passed for " + name + ")");
		return false;
	}
}
