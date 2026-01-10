package com.bencodez.advancedcore.api.player;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;

/**
 * Central UUID <-> name lookup + caching for AdvancedCore.
 *
 * Goals: - Fast lookups (no scanning uuid->name map every call) - Works with
 * OnlineMode / OfflineMode (offline uses deterministic UUID from name) - Uses
 * Bukkit (online), local cache, storage (mysql/sqlite), then flatfile fallback
 *
 * Notes: - Name cache key is stored case-insensitive (lowercased key). - UUID
 * string is stored canonical (uuid.toString()).
 */
public class UuidLookup {

	private static final UuidLookup instance = new UuidLookup();

	public static UuidLookup getInstance() {
		return instance;
	}

	private final AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	// Fast local caches
	private final ConcurrentHashMap<String, String> uuidToName = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, String> nameToUuid = new ConcurrentHashMap<>();

	private UuidLookup() {
		// No plugin uuidNameCache anymore. Cache is owned here.
	}

	/*
	 * ========================= Public API =========================
	 */

	/**
	 * Resolve UUID string from a player name.
	 *
	 * Lookup order: 1) If input is already a UUID -> canonical UUID 2) Offline
	 * mode: deterministic UUID from name 3) Online player (Bukkit) 4) Local cache
	 * (name->uuid) 5) Storage (mysql/sqlite) or flatfile scan fallback 6) Bukkit
	 * OfflinePlayer fallback (best-effort)
	 *
	 * @param playerName player name or UUID string
	 * @return UUID string, or "" if not found / invalid input
	 */
	public String getUUID(String playerName) {
		if (playerName == null) {
			return "";
		}
		playerName = playerName.trim();
		if (playerName.isEmpty()) {
			return "";
		}

		// If the input is already a UUID, return canonical form.
		UUID parsed = tryParseUuid(playerName);
		if (parsed != null) {
			return parsed.toString();
		}

		// Offline mode: deterministic UUID
		if (!plugin.getOptions().isOnlineMode()) {
			String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8))
					.toString();
			// Cache mapping so later lookups are instant + casing preserved for this
			// session
			cacheMapping(uuid, playerName);
			return uuid;
		}

		// Online player
		Player online = Bukkit.getPlayerExact(playerName);
		if (online != null) {
			String uuid = online.getUniqueId().toString();
			cacheMapping(uuid, online.getName());
			return uuid;
		}

		// Local cache (fast)
		String cachedUuid = nameToUuid.get(normNameKey(playerName));
		if (isUuidString(cachedUuid)) {
			return cachedUuid;
		}

		// Storage lookup (mysql/sqlite) OR flatfile fallback
		String storageUuid = lookupUuidFromStorageOrFlatfile(playerName);
		if (isUuidString(storageUuid)) {
			cacheMapping(storageUuid, playerName);
			return storageUuid;
		}

		// Bukkit OfflinePlayer fallback (best-effort)
		try {
			@SuppressWarnings("deprecation")
			OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
			if (p != null && p.getUniqueId() != null) {
				String uuid = p.getUniqueId().toString();
				cacheMapping(uuid, playerName);
				return uuid;
			}
		} catch (Exception e) {
			plugin.getLogger().info("Unable to get UUID for: " + playerName);
			plugin.debug(e);
		}

		return "";
	}

	/**
	 * Resolve name from UUID string using user stored data + Bukkit if online.
	 *
	 * - Uses local cache first - If online, returns Bukkit name and updates stored
	 * PlayerName - Otherwise returns stored PlayerName (if any)
	 *
	 * @return name, or "" if none
	 */
	public String getPlayerName(AdvancedCoreUser user, String uuid) {
		return getPlayerName(user, uuid, true);
	}

	public String getPlayerName(AdvancedCoreUser user, String uuid, boolean useCache) {
		if (uuid == null) {
			plugin.debug("Null UUID");
			return "";
		}
		uuid = uuid.trim();
		if (uuid.isEmpty() || uuid.equalsIgnoreCase("null")) {
			plugin.debug("Null UUID");
			return "";
		}
		if (!isUuidString(uuid)) {
			plugin.debug("Invalid UUID: " + uuid);
			return "";
		}

		// Fast: local cache
		String cachedName = uuidToName.get(uuid);
		if (isGoodName(cachedName)) {
			return cachedName;
		}

		UUID u = UUID.fromString(uuid);

		// Online player
		Player player = Bukkit.getPlayer(u);

		String storedName = (user == null) ? ""
				: safeString(user.getData().getString("PlayerName", UserDataFetchMode.fromBooleans(useCache, true)));

		if (player != null) {
			String liveName = player.getName();
			cacheMapping(uuid, liveName);

			// Update stored PlayerName if it changed / missing
			if (user != null && user.getUserData().hasData()) {
				if (storedName.isEmpty() || storedName.equalsIgnoreCase("Error getting name")
						|| !liveName.equals(storedName)) {
					user.getData().setString("PlayerName", liveName);
				}
			}
			return liveName;
		}

		// Fall back to stored name if available
		if (isGoodName(storedName)) {
			cacheMapping(uuid, storedName);
			return storedName;
		}

		return "";
	}

	/**
	 * Force insert/update mapping in local caches.
	 */
	public void cacheMapping(String uuid, String name) {
		if (!isUuidString(uuid)) {
			return;
		}
		if (name == null) {
			return;
		}
		name = name.trim();
		if (name.isEmpty()) {
			return;
		}

		uuidToName.put(uuid, name);
		nameToUuid.put(normNameKey(name), uuid);
	}

	/**
	 * Remove both directions from local caches (does not remove from DB).
	 */
	public void invalidate(String uuidOrName) {
		if (uuidOrName == null) {
			return;
		}
		String s = uuidOrName.trim();
		if (s.isEmpty()) {
			return;
		}

		UUID u = tryParseUuid(s);
		if (u != null) {
			String uuid = u.toString();
			String name = uuidToName.remove(uuid);
			if (name != null) {
				nameToUuid.remove(normNameKey(name));
			}
			return;
		}

		String key = normNameKey(s);
		String uuid = nameToUuid.remove(key);
		if (uuid != null) {
			uuidToName.remove(uuid);
		}
	}

	/**
	 * Convenience: get cached uuid for name (or "").
	 */
	public String getCachedUUID(String playerName) {
		if (playerName == null) {
			return "";
		}
		String u = nameToUuid.get(normNameKey(playerName));
		return isUuidString(u) ? u : "";
	}

	/**
	 * Convenience: get cached name for uuid (or "").
	 */
	public String getCachedName(String uuid) {
		if (!isUuidString(uuid)) {
			return "";
		}
		String n = uuidToName.get(uuid);
		return isGoodName(n) ? n : "";
	}

	/*
	 * ========================= Internals =========================
	 */

	private String lookupUuidFromStorageOrFlatfile(String playerName) {
		try {
			if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
				String uuid = plugin.getMysql().getUUID(playerName);
				return safeString(uuid);
			} else if (plugin.getStorageType().equals(UserStorage.SQLITE)) {
				String uuid = plugin.getSQLiteUserTable().getUUID(playerName);
				return safeString(uuid);
			} else {
				// Flatfile / other: scan all UUIDs (expensive but consistent with prior
				// behavior)
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					if (!isUuidString(uuid)) {
						continue;
					}
					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					String storedName = user.getData().getString("PlayerName", UserDataFetchMode.NO_CACHE);
					if (storedName != null && storedName.equalsIgnoreCase(playerName)) {
						cacheMapping(uuid, storedName);
						return uuid;
					}
				}
			}
		} catch (Exception e) {
			plugin.debug(e);
		}
		return "";
	}

	private boolean isGoodName(String n) {
		return n != null && !n.trim().isEmpty() && !n.equalsIgnoreCase("Error getting name");
	}

	private String normNameKey(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isUuidString(String uuid) {
		return tryParseUuid(uuid) != null;
	}

	private UUID tryParseUuid(String s) {
		if (s == null) {
			return null;
		}
		s = s.trim();
		// quick reject
		if (s.length() < 32) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String safeString(String s) {
		return s == null ? "" : s.trim();
	}
}
