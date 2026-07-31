package com.bencodez.advancedcore.api.bedrock;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;

/**
 * Resolves Bedrock player names and detects Bedrock players.
 */
public final class BedrockNameResolver {

	private final BedrockDetect bedrockDetect;
	private final UserManager userManager;
	private final String bedrockPrefix;
	private final AdvancedCorePlugin plugin;

	private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
	private final Map<String, String> ciIndex = new ConcurrentHashMap<>();

	/**
	 * Creates a new Bedrock name resolver.
	 * 
	 * @param plugin the plugin instance
	 */
	public BedrockNameResolver(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		this.bedrockDetect = new BedrockDetect(plugin::debug);
		this.bedrockDetect.load();

		// summary log
		plugin.debug("[BedrockNameResolver] Floodgate loaded=" + bedrockDetect.isFloodgateAvailable()
				+ ", Geyser loaded=" + bedrockDetect.isGeyserAvailable());

		this.userManager = plugin.getUserManager();
		this.bedrockPrefix = plugin.getOptions().getBedrockPlayerPrefix();

		plugin.addUserStartup(new UserStartup() {
			@Override
			public void onStart() {
				clearCache();
				plugin.debug("[BedrockNameResolver] startup: cleared in-memory cache/index");
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

	/**
	 * Checks if a player is a Bedrock player by name.
	 * 
	 * @param name the player name
	 * @return true if the player is a Bedrock player
	 */
	public boolean isBedrock(String name) {
		return isBedrockName(name);
	}

	/**
	 * Checks if a player is a Bedrock player by UUID and name.
	 * 
	 * @param uuid the player UUID
	 * @param name the player name
	 * @return true if the player is a Bedrock player
	 */
	public boolean isBedrock(UUID uuid, String name) {
		// 1) UUID is authoritative if present
		if (uuid != null) {
			try {
				boolean viaUuid = bedrockDetect.isBedrock(uuid);
				if (viaUuid) {
					plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): TRUE via UUID");
					return true;
				}
			} catch (Throwable ignored) {
			}
		}

		// 2) Resolve the name with the same exact-before-prefixed policy used by
		// vote and user lookups.
		Result resolved = resolve(name);
		plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): " + resolved.isBedrock + " via "
				+ resolved.rationale);
		return resolved.isBedrock;
	}

	// ------------ Learning and name resolution ------------

	/**
	 * Learns whether a user is a Bedrock player.
	 * 
	 * @param user the user to learn from
	 */
	public void learn(AdvancedCoreUser user) {
		if (user == null)
			return;
		String name = user.getPlayerName();
		if (name == null || name.isEmpty())
			return;
		putLearned(name, user.isBedrockUser());
	}

	/**
	 * Learns whether a player is a Bedrock player.
	 * 
	 * @param player the player to learn from
	 */
	public void learn(Player player) {
		if (player == null)
			return;
		final UUID uuid = player.getUniqueId();
		final boolean isBedrock = bedrockDetect.isBedrock(uuid);
		final String originalName = player.getName();
		if (isBedrock) {
			plugin.debug("Learned Bedrock player: " + originalName + " (" + uuid + ")");
			cache.put(originalName, true);
			ciIndex.put(originalName.toLowerCase(Locale.ROOT), originalName);
			AdvancedCoreUser user = userManager.getUser(player);
			if (user != null)
				user.setBedrockUser(true);
		}
	}

	/**
	 * Detect whether a name corresponds to a Bedrock player.
	 * 
	 * IMPORTANT: This method does NOT add prefixes. It only returns a boolean. Use
	 * {@link #resolve(String)} if you want canonical (possibly prefixed) names.
	 * 
	 * @param name the player name
	 * @return true if the player is a Bedrock player
	 */
	public boolean isBedrockName(String name) {
		return resolve(name).isBedrock;
	}

	/**
	 * Resolve a name to its canonical form and determine if it's a Bedrock player.
	 * 
	 * @param incomingName the player name to resolve
	 * @return the result containing the canonical name and Bedrock status
	 */
	public Result resolve(String incomingName) {
		if (incomingName == null || incomingName.isEmpty())
			return new Result(incomingName, false, "empty-name");

		// An exact online identity is authoritative.
		Player match = findOnlineExact(incomingName);
		if (match != null) {
			return resultFromOnlineMatch(incomingName, match);
		}

		// A known exact identity must win before an online prefixed variant. This
		// prevents an online Bedrock account such as ".Name" from taking a vote for
		// an offline Java account named "Name".
		Boolean cached = getCachedCaseInsensitive(incomingName);
		if (cached != null) {
			boolean bedrock = cached;
			String finalName = addPrefixIfNeeded(incomingName, bedrock);
			return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java"));
		}

		try {
			if (userManager.userExistStored(incomingName)) {
				AdvancedCoreUser user = userManager.getUser(incomingName);
				boolean bedrock = user.isBedrockUser();
				String finalName = addPrefixIfNeeded(incomingName, bedrock);
				return new Result(finalName, bedrock, "db-" + (bedrock ? "bedrock" : "java"));
			}
		} catch (Throwable ignored) {
		}

		// Only when no exact identity is known may an online prefixed/stripped
		// Bedrock identity act as a fallback.
		match = findOnlinePrefixedOrStripped(incomingName);
		if (match != null) {
			return resultFromOnlineMatch(incomingName, match);
		}

		// Cache on prefixed variant
		String prefixed = buildPrefixedVariant(incomingName);
		if (prefixed != null) {
			Boolean cachedPrefixed = getCachedCaseInsensitive(prefixed);
			if (cachedPrefixed != null) {
				boolean bedrock = cachedPrefixed;
				// if the prefixed variant is known bedrock, credit the prefixed name
				String finalName = bedrock ? prefixed : incomingName;
				return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java") + "-prefixed-variant");
			}
		}

		// DB flag on the prefixed variant. Check storage existence before creating a
		// user wrapper; getUser(String) itself never returns null for a missing name.
		try {
			if (prefixed != null && userManager.userExistStored(prefixed)) {
				AdvancedCoreUser u2 = userManager.getUser(prefixed);
				boolean bedrock = u2.isBedrockUser();
				String finalName = bedrock ? prefixed : incomingName;
				return new Result(finalName, bedrock,
						"db-" + (bedrock ? "bedrock" : "java") + "-prefixed-variant");
			}
		} catch (Throwable ignored) {
		}

		if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && incomingName.startsWith(bedrockPrefix)) {
			return new Result(incomingName, true, "prefixed");
		}

		return new Result(incomingName, false, "unknown-default-java");
	}

	public Result resolveWithoutDb(String incomingName) {
		if (incomingName == null || incomingName.isEmpty()) {
			return new Result(incomingName, false, "empty-name");
		}

		// Exact online identity first.
		Player match = findOnlineExact(incomingName);
		if (match != null) {
			return resultFromOnlineMatch(incomingName, match);
		}

		// Cache on incoming name
		Boolean cached = getCachedCaseInsensitive(incomingName);
		if (cached != null) {
			boolean bedrock = cached;
			String finalName = addPrefixIfNeeded(incomingName, bedrock);
			return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java"));
		}

		// Only use an online prefixed/stripped fallback when no exact cached identity
		// exists.
		match = findOnlinePrefixedOrStripped(incomingName);
		if (match != null) {
			return resultFromOnlineMatch(incomingName, match);
		}

		// Cache on prefixed variant
		String prefixed = buildPrefixedVariant(incomingName);
		if (prefixed != null) {
			Boolean cachedPrefixed = getCachedCaseInsensitive(prefixed);
			if (cachedPrefixed != null) {
				boolean bedrock = cachedPrefixed;
				String finalName = bedrock ? prefixed : incomingName;
				return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java") + "-prefixed-variant");
			}
		}

		// Prefix-only fallback
		if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && incomingName.startsWith(bedrockPrefix)) {
			return new Result(incomingName, true, "prefixed-only");
		}

		return new Result(incomingName, false, "unknown-no-db");
	}

	/**
	 * Get the prefixed name if the player is a Bedrock player.
	 * 
	 * @param name the player name
	 * @return the prefixed name if Bedrock, otherwise the original name
	 */
	public String getPrefixedIfBedrock(String name) {
		return addPrefixIfNeeded(name, isBedrockName(name));
	}

	/**
	 * Clear the cache and case-insensitive index.
	 */
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

	private Boolean getCachedCaseInsensitive(String name) {
		Boolean exact = cache.get(name);
		if (exact != null)
			return exact;
		String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
		return (canonical != null) ? cache.get(canonical) : null;
	}

	private String addPrefixIfNeeded(String name, boolean bedrock) {
		if (!bedrock)
			return name;
		if (bedrockPrefix == null || bedrockPrefix.isEmpty())
			return name;
		if (name.startsWith(bedrockPrefix))
			return name;
		return bedrockPrefix + name;
	}

	/**
	 * Build a prefixed variant if a prefix exists and the incoming name does not
	 * already start with it.
	 */
	private String buildPrefixedVariant(String name) {
		if (name == null || name.isEmpty())
			return null;
		if (bedrockPrefix == null || bedrockPrefix.isEmpty())
			return null;
		if (name.startsWith(bedrockPrefix))
			return null;
		return bedrockPrefix + name;
	}

	/**
	 * Strip the bedrock prefix if present.
	 */
	private String stripPrefixIfPresent(String n) {
		if (n == null)
			return "";
		if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && n.startsWith(bedrockPrefix)) {
			return n.substring(bedrockPrefix.length());
		}
		return n;
	}

	private Result resultFromOnlineMatch(String incomingName, Player match) {
		boolean bedrock = bedrockDetect.isBedrock(match.getUniqueId());
		String finalName = bedrock ? addPrefixIfNeeded(match.getName(), true) : incomingName;
		return new Result(finalName, bedrock, bedrock ? "online-uuid-bedrock" : "online-uuid-java");
	}

	/**
	 * Find an exact online player without considering prefixed or stripped aliases.
	 *
	 * @param name incoming player name
	 * @return exact online player or null
	 */
	private Player findOnlineExact(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}

		for (Player p : Bukkit.getOnlinePlayers()) {
			final String pn = p.getName();
			if (pn != null && pn.equalsIgnoreCase(name)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Find an online prefixed or prefix-stripped fallback. Callers must first rule
	 * out exact online, cached, and stored identities.
	 *
	 * @param name incoming player name
	 * @return fallback online player or null
	 */
	private Player findOnlinePrefixedOrStripped(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}

		final String lower = name.toLowerCase(Locale.ROOT);
		final String prefixed = buildPrefixedVariant(name);

		if (prefixed != null) {
			for (Player p : Bukkit.getOnlinePlayers()) {
				final String pn = p.getName();
				if (pn != null && pn.equalsIgnoreCase(prefixed)) {
					return p;
				}
			}
		}

		// If multiple players match after stripping, prefer Java over Bedrock.
		Player bedrockCandidate = null;
		Player javaCandidate = null;

		for (Player p : Bukkit.getOnlinePlayers()) {
			final String pn = p.getName();
			if (pn == null) {
				continue;
			}

			final String strippedLower = stripPrefixIfPresent(pn).toLowerCase(Locale.ROOT);
			if (!strippedLower.equals(lower)) {
				continue;
			}

			// Decide preference using UUID-based bedrock detection (authoritative online)
			boolean isBedrock = false;
			try {
				isBedrock = bedrockDetect.isBedrock(p.getUniqueId());
			} catch (Throwable ignored) {
				// If detection fails, treat as unknown; prefer as java to avoid
				// incorrectly forcing bedrock.
				isBedrock = false;
			}

			if (isBedrock) {
				if (bedrockCandidate == null) {
					bedrockCandidate = p;
				}
			} else {
				// Java (or unknown) wins immediately
				javaCandidate = p;
				break;
			}
		}

		return (javaCandidate != null) ? javaCandidate : bedrockCandidate;
	}

	/**
	 * Result of name resolution containing the final name, Bedrock status, and
	 * rationale.
	 */
	public static final class Result {
		/** The final resolved name. */
		public final String finalName;
		/** Whether the player is a Bedrock player. */
		public final boolean isBedrock;
		/** The rationale for the resolution decision. */
		public final String rationale;

		/**
		 * Instantiates a new result.
		 * 
		 * @param finalName the final resolved name
		 * @param isBedrock whether the player is Bedrock
		 * @param rationale the rationale for the decision
		 */
		public Result(String finalName, boolean isBedrock, String rationale) {
			this.finalName = finalName;
			this.isBedrock = isBedrock;
			this.rationale = rationale;
		}
	}

	// ====================== Embedded BedrockDetect with DEBUG
	// ======================

	/**
	 * Bedrock detection using Floodgate and Geyser APIs.
	 */
	public static class BedrockDetect {
		private volatile boolean floodgateAvailable = false;
		private volatile boolean geyserAvailable = false;

		private Object floodgateApi;
		private Method fgIsFloodgatePlayer;
		private Method fgGetPlayer;

		private Object geyserApi;
		private Method gzIsBedrockPlayer;

		private final Consumer<String> debug;

		/**
		 * Instantiates a new Bedrock detector with no debug output.
		 */
		public BedrockDetect() {
			this(s -> {
			});
		}

		/**
		 * Instantiates a new Bedrock detector with debug output.
		 * 
		 * @param debug the debug consumer
		 */
		public BedrockDetect(Consumer<String> debug) {
			this.debug = (debug != null) ? debug : (s -> {
			});
		}

		/**
		 * Load Floodgate and Geyser APIs.
		 */
		public void load() {
			loadFloodgate();
			loadGeyser();
			debug.accept("[BedrockDetect] Loaded. Floodgate=" + floodgateAvailable + ", Geyser=" + geyserAvailable);
		}

		private void loadFloodgate() {
			try {
				Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
				Method getInstance = api.getMethod("getInstance");
				floodgateApi = getInstance.invoke(null);
				fgIsFloodgatePlayer = floodgateApi.getClass().getMethod("isFloodgatePlayer", UUID.class);
				try {
					fgGetPlayer = floodgateApi.getClass().getMethod("getPlayer", UUID.class);
				} catch (NoSuchMethodException ignored) {
				}
				floodgateAvailable = true;
				debug.accept("[BedrockDetect] Floodgate API: LOADED");
			} catch (Throwable t) {
				floodgateAvailable = false;
				debug.accept("[BedrockDetect] Floodgate API: NOT FOUND (" + t.getClass().getSimpleName() + ": "
						+ t.getMessage() + ")");
			}
		}

		private void loadGeyser() {
			try {
				Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
				Method apiMethod = apiClass.getMethod("api");
				geyserApi = apiMethod.invoke(null);
				gzIsBedrockPlayer = geyserApi.getClass().getMethod("isBedrockPlayer", UUID.class);
				geyserAvailable = true;
				debug.accept("[BedrockDetect] Geyser API: LOADED");
			} catch (Throwable t) {
				geyserAvailable = false;
				debug.accept("[BedrockDetect] Geyser API: NOT FOUND (" + t.getClass().getSimpleName() + ": "
						+ t.getMessage() + ")");
			}
		}

		/**
		 * Check if a player is a Bedrock player by UUID.
		 * 
		 * @param uuid the player UUID
		 * @return true if the player is a Bedrock player
		 */
		public boolean isBedrock(UUID uuid) {
			if (uuid == null)
				return false;

			if (floodgateAvailable) {
				try {
					Object v = fgIsFloodgatePlayer.invoke(floodgateApi, uuid);
					if (v instanceof Boolean && (Boolean) v)
						return true;
				} catch (Throwable t) {
					floodgateAvailable = false;
					debug.accept("[BedrockDetect] Floodgate call failed, disabling: " + t.getClass().getSimpleName());
				}
			}

			if (geyserAvailable) {
				try {
					Object v = gzIsBedrockPlayer.invoke(geyserApi, uuid);
					if (v instanceof Boolean)
						return (Boolean) v;
				} catch (Throwable t) {
					geyserAvailable = false;
					debug.accept("[BedrockDetect] Geyser call failed, disabling: " + t.getClass().getSimpleName());
				}
			}

			return false;
		}

		/**
		 * Get the Floodgate player object.
		 * 
		 * @param uuid the player UUID
		 * @return the Floodgate player object or null
		 */
		public Object getFloodgatePlayer(UUID uuid) {
			if (!floodgateAvailable || fgGetPlayer == null || uuid == null)
				return null;
			try {
				return fgGetPlayer.invoke(floodgateApi, uuid);
			} catch (Throwable t) {
				floodgateAvailable = false;
				return null;
			}
		}

		/**
		 * Check if Floodgate API is available.
		 * 
		 * @return true if Floodgate is available
		 */
		public boolean isFloodgateAvailable() {
			return floodgateAvailable;
		}

		/**
		 * Check if Geyser API is available.
		 * 
		 * @return true if Geyser is available
		 */
		public boolean isGeyserAvailable() {
			return geyserAvailable;
		}
	}
}
