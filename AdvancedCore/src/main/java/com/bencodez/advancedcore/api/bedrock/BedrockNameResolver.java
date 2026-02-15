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

		// 2) If name supplied, try the usual name resolution chain
		if (name != null && !name.isEmpty()) {
			// Online (authoritative) - match exact and (prefix-stripped) forms
			Player match = findOnlineByNameOrStripped(name);
			if (match != null) {
				boolean online = bedrockDetect.isBedrock(match.getUniqueId());
				plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): " + online + " via online UUID match");
				return online;
			}

			// Cache (case-insensitive) - try incoming name, and prefixed variant if
			// applicable
			Boolean cached = getCachedCaseInsensitive(name);
			if (cached != null) {
				plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): " + cached + " via cache");
				return cached;
			}
			String prefixed = buildPrefixedVariant(name);
			if (prefixed != null) {
				Boolean cachedPrefixed = getCachedCaseInsensitive(prefixed);
				if (cachedPrefixed != null) {
					plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): " + cachedPrefixed
							+ " via cache (prefixed-variant)");
					return cachedPrefixed;
				}
			}

			// DB flag (NOTE: may trigger UUID lookups depending on your UserManager
			// implementation)
			try {
				AdvancedCoreUser user = userManager.getUser(name);
				if (user == null) {
					String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
					if (canonical != null)
						user = userManager.getUser(canonical);
				}
				if (user != null && user.isBedrockUser()) {
					plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): TRUE via DB flag");
					return true;
				}

				// Also try DB under prefixed variant if incoming is unprefixed
				if (prefixed != null) {
					AdvancedCoreUser u2 = userManager.getUser(prefixed);
					if (u2 != null && u2.isBedrockUser()) {
						plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): TRUE via DB flag (prefixed-variant)");
						return true;
					}
				}
			} catch (Throwable ignored) {
			}

			// Prefix fallback (only if prefix is non-empty)
			if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && name.startsWith(bedrockPrefix)) {
				plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): TRUE via prefix fallback");
				return true;
			}
		}

		plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): FALSE (no signals)");
		return false;
	}

	// ------------ EXISTING METHODS (unchanged behavior) ------------

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
	 */
	public boolean isBedrockName(String name) {
		if (name == null || name.isEmpty())
			return false;

		// Online - match exact and (prefix-stripped) forms
		Player match = findOnlineByNameOrStripped(name);
		if (match != null) {
			return bedrockDetect.isBedrock(match.getUniqueId());
		}

		// Cache (case-insensitive) on incoming name
		Boolean cached = getCachedCaseInsensitive(name);
		if (cached != null)
			return cached;

		// Cache on prefixed variant (if incoming was unprefixed)
		String prefixed = buildPrefixedVariant(name);
		if (prefixed != null) {
			Boolean cachedPrefixed = getCachedCaseInsensitive(prefixed);
			if (cachedPrefixed != null)
				return cachedPrefixed;
		}

		// DB flag (NOTE: may trigger UUID lookups depending on your UserManager
		// implementation)
		try {
			AdvancedCoreUser user = userManager.getUser(name);
			if (user == null) {
				String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
				if (canonical != null)
					user = userManager.getUser(canonical);
			}
			if (user != null && user.isBedrockUser())
				return true;

			// Also try DB under prefixed variant if incoming is unprefixed
			if (prefixed != null) {
				AdvancedCoreUser u2 = userManager.getUser(prefixed);
				if (u2 != null && u2.isBedrockUser())
					return true;
			}
		} catch (Throwable ignored) {
		}

		return bedrockPrefix != null && !bedrockPrefix.isEmpty() && name.startsWith(bedrockPrefix);
	}

	public Result resolve(String incomingName) {
		if (incomingName == null || incomingName.isEmpty())
			return new Result(incomingName, false, "empty-name");

		// Online - match exact and (prefix-stripped) forms
		Player match = findOnlineByNameOrStripped(incomingName);
		if (match != null) {
			boolean bedrock = bedrockDetect.isBedrock(match.getUniqueId());

			// Canonical name: if bedrock and online name is prefixed, use the online name
			String finalName = match.getName();
			if (!bedrock) {
				// if java, keep whatever was supplied (or the online exact, doesn't matter)
				finalName = incomingName;
			} else {
				// ensure prefix if online name isn't prefixed for some reason
				finalName = addPrefixIfNeeded(finalName, true);
			}
			return new Result(finalName, bedrock, bedrock ? "online-uuid-bedrock" : "online-uuid-java");
		}

		// Cache on incoming name
		Boolean cached = getCachedCaseInsensitive(incomingName);
		if (cached != null) {
			boolean bedrock = cached;
			String finalName = addPrefixIfNeeded(incomingName, bedrock);
			return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java"));
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

		// DB flag (NOTE: may trigger UUID lookups depending on your UserManager
		// implementation)
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

			// Also try DB under prefixed variant
			if (prefixed != null) {
				AdvancedCoreUser u2 = userManager.getUser(prefixed);
				if (u2 != null) {
					boolean bedrock = u2.isBedrockUser();
					// if the prefixed record is bedrock, credit prefixed
					String finalName = bedrock ? prefixed : incomingName;
					return new Result(finalName, bedrock, "db-" + (bedrock ? "bedrock" : "java") + "-prefixed-variant");
				}
			}
		} catch (Throwable ignored) {
		}

		if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && incomingName.startsWith(bedrockPrefix)) {
			return new Result(incomingName, true, "prefixed");
		}

		return new Result(incomingName, false, "unknown-default-java");
	}

	public String getPrefixedIfBedrock(String name) {
		return addPrefixIfNeeded(name, isBedrockName(name));
	}

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

	/**
	 * Find an online player matching the provided name in either:
	 * <ul>
	 * <li>Exact form (case-insensitive)</li>
	 * <li>Prefixed variant (case-insensitive)</li>
	 * <li>Prefix-stripped comparison (e.g. ".DarkAshley" matches "darkashley")</li>
	 * </ul>
	 *
	 * <p>
	 * IMPORTANT: If both a Java and Bedrock player effectively match the same
	 * unprefixed input (e.g. "Name" and ".Name" are both online), this method will
	 * deterministically prefer the Java player to avoid incorrectly forcing Bedrock
	 * resolution due to non-deterministic iteration order.
	 * </p>
	 *
	 * @param name incoming player name
	 * @return matching online {@link Player} or null
	 */
	private Player findOnlineByNameOrStripped(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}

		final String lower = name.toLowerCase(Locale.ROOT);
		final String prefixed = buildPrefixedVariant(name);

		// 1) Exact match first (deterministic preference)
		for (Player p : Bukkit.getOnlinePlayers()) {
			final String pn = p.getName();
			if (pn != null && pn.equalsIgnoreCase(name)) {
				return p;
			}
		}

		// 2) Prefixed variant match next (if incoming was unprefixed)
		if (prefixed != null) {
			for (Player p : Bukkit.getOnlinePlayers()) {
				final String pn = p.getName();
				if (pn != null && pn.equalsIgnoreCase(prefixed)) {
					return p;
				}
			}
		}

		// 3) Prefix-stripped match last
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

	// ====================== Embedded BedrockDetect with DEBUG
	// ======================

	public static class BedrockDetect {
		private volatile boolean floodgateAvailable = false;
		private volatile boolean geyserAvailable = false;

		private Object floodgateApi;
		private Method fgIsFloodgatePlayer;
		private Method fgGetPlayer;

		private Object geyserApi;
		private Method gzIsBedrockPlayer;

		private final Consumer<String> debug;

		public BedrockDetect() {
			this(s -> {
			});
		}

		public BedrockDetect(Consumer<String> debug) {
			this.debug = (debug != null) ? debug : (s -> {
			});
		}

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

		public boolean isFloodgateAvailable() {
			return floodgateAvailable;
		}

		public boolean isGeyserAvailable() {
			return geyserAvailable;
		}
	}
}
