package com.bencodez.advancedcore.api.bedrock;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;

/**
 * Resolves whether a given *name* belongs to a Bedrock player and returns a
 * prefixed name if needed. Works with online or offline names.
 *
 * Resolution order: 1) Online player UUID -> BedrockDetect (authoritative) 2)
 * In-memory cache learned on join 3) Persistent user flag "isBedrock"
 * (optional) 4) Name already has the bedrock prefix (fallback)
 *
 * No reliance on OfflinePlayer(name) UUIDs. This version preserves original
 * name casing (no lowercasing of stored names), but still supports
 * case-insensitive matching via a side index.
 */
public final class BedrockNameResolver {

	private final BedrockDetect bedrockDetect;
	private final UserManager userManager;
	private final String bedrockPrefix;
	private final AdvancedCorePlugin plugin;

	/** Canonical cache: ORIGINAL-CASE name -> isBedrock */
	private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
	/**
	 * Case-insensitive index: lower(name) -> ORIGINAL-CASE name (canonical key for
	 * cache). We only use lowercase for this index key; we never alter the stored
	 * name.
	 */
	private final Map<String, String> ciIndex = new ConcurrentHashMap<>();

	public BedrockNameResolver(AdvancedCorePlugin plugin) {
		this.bedrockDetect = new BedrockDetect();
		this.plugin = plugin;
		this.bedrockDetect.load();
		this.userManager = plugin.getUserManager();
		this.bedrockPrefix = plugin.getOptions().getBedrockPlayerPrefix();
		plugin.addUserStartup(new UserStartup() {
			@Override
			public void onStart() {
				clearCache();
			}

			@Override
			public void onStartUp(AdvancedCoreUser user) {
				learn(user);
			}

			@Override
			public void onFinish() {
				plugin.debug("BedrockNameResolver: startup loading complete");
			}
		});
	}

	public void learn(AdvancedCoreUser user) {
		if (user == null)
			return;
		String name = user.getPlayerName(); // keep original case
		if (name == null || name.isEmpty())
			return;
		putLearned(name, user.isBedrockUser()); // uses your existing helper that maintains ciIndex
	}

	/**
	 * Learn authoritative Bedrock status when the player logs in / joins. Call this
	 * from your login/join event (where you have the real UUID). Preserves original
	 * name casing in cache.
	 */
	public void learn(Player player) {
		if (player == null)
			return;

		final UUID uuid = player.getUniqueId();
		final boolean isBedrock = bedrockDetect.isBedrock(uuid);
		final String originalName = player.getName();

		if (isBedrock) {
			plugin.debug("Learned Bedrock player: " + originalName + " (" + uuid.toString() + ")");

			// Store with original casing
			cache.put(originalName, isBedrock);
			ciIndex.put(originalName.toLowerCase(Locale.ROOT), originalName);

			// Optional: persist a flag for offline lookups later
			AdvancedCoreUser user = userManager.getUser(player);
			if (user != null) {
				user.setBedrockUser(isBedrock);
			}
		}
	}

	/**
	 * Returns true if this name (no prefix) should be treated as Bedrock. Safe for
	 * online and offline cases. Never alters 'name' casing.
	 */
	public boolean isBedrockName(String name) {
		if (name == null || name.isEmpty())
			return false;

		// 1) Online (authoritative, case-insensitive match without changing 'name')
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getName().equalsIgnoreCase(name)) {
				return bedrockDetect.isBedrock(p.getUniqueId());
			}
		}

		// 2) Learned cache (case-insensitive via index; stored names keep original
		// case)
		Boolean cached = getCachedCaseInsensitive(name);
		if (cached != null)
			return cached;

		// 3) Persistent user flag (optional)
		try {
			// Try exact case first
			AdvancedCoreUser user = userManager.getUser(name);
			if (user == null) {
				// Try canonical original name via CI index
				String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
				if (canonical != null)
					user = userManager.getUser(canonical);
			}
			if (user != null && user.isBedrockUser()) {
				return true;
			}
		} catch (Throwable ignored) {
		}

		// 4) Already-prefixed names should be treated as Bedrock
		return !bedrockPrefix.isEmpty() && name.startsWith(bedrockPrefix);
	}

	/**
	 * Returns a decision for vote/name resolution: - finalName: the name you should
	 * credit (maybe prefixed, preserves original case) - isBedrock: true if we
	 * believe this is a Bedrock user - rationale: how we decided (for
	 * logging/debug)
	 */
	public Result resolve(String incomingName) {
		if (incomingName == null || incomingName.isEmpty()) {
			return new Result(incomingName, false, "empty-name");
		}

		// 1) Prefer online UUID (authoritative), equalsIgnoreCase without mutating
		// 'incomingName'
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getName().equalsIgnoreCase(incomingName)) {
				boolean bedrock = bedrockDetect.isBedrock(p.getUniqueId());
				String finalName = addPrefixIfNeeded(incomingName, bedrock);
				return new Result(finalName, bedrock, bedrock ? "online-uuid-bedrock" : "online-uuid-java");
			}
		}

		// 2) Cache (case-insensitive via index)
		Boolean cached = getCachedCaseInsensitive(incomingName);
		if (cached != null) {
			boolean bedrock = cached;
			String finalName = addPrefixIfNeeded(incomingName, bedrock);
			return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java"));
		}

		// 3) DB flag (try exact, then canonical from index)
		try {
			AdvancedCoreUser user = userManager.getUser(incomingName);
			if (user == null) {
				String canonical = ciIndex.get(incomingName.toLowerCase(Locale.ROOT));
				if (canonical != null)
					user = userManager.getUser(canonical);
			}
			if (user != null) {
				boolean bedrock = user.isBedrockUser();
				String finalName = addPrefixIfNeeded(incomingName, bedrock);
				return new Result(finalName, bedrock, "db-" + (bedrock ? "bedrock" : "java"));
			}
		} catch (Throwable ignored) {
		}

		// 4) Fallback: if already prefixed, treat as bedrock
		if (!bedrockPrefix.isEmpty() && incomingName.startsWith(bedrockPrefix)) {
			return new Result(incomingName, true, "prefixed");
		}

		// Unknown -> default to Java (no prefix)
		return new Result(incomingName, false, "unknown-default-java");
	}

	/**
	 * Quickly return a prefixed name if we consider this a Bedrock name; otherwise
	 * return the original. Preserves original case.
	 */
	public String getPrefixedIfBedrock(String name) {
		return addPrefixIfNeeded(name, isBedrockName(name));
	}

	/** Clear learned cache (e.g., on plugin reload). */
	public void clearCache() {
		cache.clear();
		ciIndex.clear();
	}

	private void putLearned(String originalCaseName, boolean isBedrock) {
		if (originalCaseName == null || originalCaseName.isEmpty())
			return;
		cache.put(originalCaseName, isBedrock);
		ciIndex.put(originalCaseName.toLowerCase(Locale.ROOT), originalCaseName);
	}

	// -------------------- helpers --------------------

	private Boolean getCachedCaseInsensitive(String name) {
		// Try exact first (preserves canonical casing)
		Boolean exact = cache.get(name);
		if (exact != null)
			return exact;

		// Fall back to CI index → canonical name → cache lookup
		String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
		return (canonical != null) ? cache.get(canonical) : null;
	}

	private String addPrefixIfNeeded(String name, boolean bedrock) {
		if (!bedrock)
			return name;
		if (bedrockPrefix.isEmpty())
			return name;
		if (name.startsWith(bedrockPrefix))
			return name;
		return bedrockPrefix + name;
	}

	// Result DTO
	public static final class Result {
		public final String finalName;
		public final boolean isBedrock;
		public final String rationale;

		public Result(String finalName, boolean isBedrock, String rationale) {
			this.finalName = finalName;
			this.isBedrock = isBedrock;
			this.rationale = rationale;
		}
	}

	// ========================================================================
	// ================ Embedded BedrockDetect (Floodgate/Geyser) ===========
	// ========================================================================

	/**
	 * Soft-dependency Bedrock detector for Floodgate and Geyser. - No compile-time
	 * dependency required. - Prefer Floodgate (linked/true Bedrock) -> then Geyser
	 * (connected via proxy). - Falls back to false if neither present or an error
	 * occurs.
	 */
	public static final class BedrockDetect {
		// ----- Floodgate cached refs -----
		private volatile boolean floodgateAvailable = false;
		private Class<?> floodgateApiClass;
		private Object floodgateApi; // FloodgateApi instance
		private Method fgIsFloodgatePlayer; // boolean isFloodgatePlayer(UUID)
		private Method fgGetPlayer; // FloodgatePlayer getPlayer(UUID) (optional/info)

		// ----- Geyser cached refs -----
		private volatile boolean geyserAvailable = false;
		private Class<?> geyserApiClass;
		private Object geyserApi; // GeyserApi.api()
		private Method gzIsBedrockPlayer; // boolean isBedrockPlayer(UUID)
		// Optional alternative:
		// private Method gzConnectionByUuid; // Optional<Connection>
		// connectionByUuid(UUID)

		/** Call once during plugin enable. */
		public void load() {
			loadFloodgate();
			loadGeyser();
		}

		private void loadFloodgate() {
			try {
				floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
				Method getInstance = floodgateApiClass.getMethod("getInstance");
				floodgateApi = getInstance.invoke(null);
				fgIsFloodgatePlayer = floodgateApi.getClass().getMethod("isFloodgatePlayer", UUID.class);

				// Optional player accessor (may not exist on very old versions)
				try {
					fgGetPlayer = floodgateApi.getClass().getMethod("getPlayer", UUID.class);
				} catch (NoSuchMethodException ignored) {
				}

				floodgateAvailable = true;
			} catch (Throwable t) {
				// Any reflection/linkage issue -> mark unavailable
				floodgateAvailable = false;
			}
		}

		private void loadGeyser() {
			try {
				Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
				Method apiMethod = apiClass.getMethod("api"); // static
				geyserApi = apiMethod.invoke(null);

				// Geyser 2.x: boolean isBedrockPlayer(UUID)
				gzIsBedrockPlayer = geyserApi.getClass().getMethod("isBedrockPlayer", UUID.class);

				// Optional alternative approach:
				// gzConnectionByUuid = geyserApi.getClass().getMethod("connectionByUuid",
				// UUID.class);

				geyserAvailable = true;
			} catch (Throwable t) {
				geyserAvailable = false;
			}
		}

		/**
		 * Returns true if the UUID is a Bedrock player (Floodgate or Geyser). Safe to
		 * call even if neither plugin is installed.
		 */
		public boolean isBedrock(UUID uuid) {
			if (uuid == null)
				return false;

			// 1) Floodgate (most authoritative)
			if (floodgateAvailable) {
				try {
					Object v = fgIsFloodgatePlayer.invoke(floodgateApi, uuid);
					if (v instanceof Boolean && (Boolean) v) {
						return true;
					}
				} catch (Throwable t) {
					floodgateAvailable = false; // stop trying on subsequent calls
				}
			}

			// 2) Geyser fallback
			if (geyserAvailable) {
				try {
					Object v = gzIsBedrockPlayer.invoke(geyserApi, uuid);
					if (v instanceof Boolean)
						return (Boolean) v;

					// If you prefer the Optional<Connection> approach:
					// Object opt = gzConnectionByUuid.invoke(geyserApi, uuid);
					// return (opt instanceof java.util.Optional) && ((java.util.Optional<?>)
					// opt).isPresent();
				} catch (Throwable t) {
					geyserAvailable = false;
				}
			}

			return false;
		}

		/**
		 * Optional: get a Floodgate player object for more info (XUID, linked Java,
		 * etc.). Returns null if Floodgate is unavailable or player not Bedrock.
		 */
		public Object getFloodgatePlayer(UUID uuid) {
			if (!floodgateAvailable || fgGetPlayer == null || uuid == null)
				return null;
			try {
				return fgGetPlayer.invoke(floodgateApi, uuid); // FloodgatePlayer or null
			} catch (Throwable t) {
				floodgateAvailable = false;
				return null;
			}
		}

		public boolean isFloodgateAvailable() {
			return floodgateAvailable;
		}

		public boolean isGeyserAvailable() {
			return geyserAvailable;
		}
	}
}
