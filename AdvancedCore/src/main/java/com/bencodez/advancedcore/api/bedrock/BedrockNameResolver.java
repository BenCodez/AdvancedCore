package com.bencodez.advancedcore.api.bedrock;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer; // <-- NEW

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStartup;

public final class BedrockNameResolver {

	private final BedrockDetect bedrockDetect;
	private final UserManager userManager;
	private final String bedrockPrefix;
	private final AdvancedCorePlugin plugin;

	private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
	private final Map<String, String> ciIndex = new ConcurrentHashMap<>();

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

	public boolean isBedrock(String name) {
		return isBedrockName(name);
	}

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
			// Online (authoritative)
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p.getName().equalsIgnoreCase(name)) {
					boolean online = bedrockDetect.isBedrock(p.getUniqueId());
					if (online) {
						plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): TRUE via online UUID match");
						return true;
					} else {
						plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): FALSE via online UUID match");
						return false;
					}
				}
			}

			// Cache (case-insensitive)
			Boolean cached = getCachedCaseInsensitive(name);
			if (cached != null) {
				plugin.debug("[BedrockNameResolver] isBedrock(uuid,name): " + cached + " via cache");
				return cached;
			}

			// DB flag
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

	public void learn(AdvancedCoreUser user) {
		if (user == null)
			return;
		String name = user.getPlayerName();
		if (name == null || name.isEmpty())
			return;
		putLearned(name, user.isBedrockUser());
	}

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

	public boolean isBedrockName(String name) {
		if (name == null || name.isEmpty())
			return false;
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getName().equalsIgnoreCase(name)) {
				return bedrockDetect.isBedrock(p.getUniqueId());
			}
		}
		Boolean cached = getCachedCaseInsensitive(name);
		if (cached != null)
			return cached;

		try {
			AdvancedCoreUser user = userManager.getUser(name);
			if (user == null) {
				String canonical = ciIndex.get(name.toLowerCase(Locale.ROOT));
				if (canonical != null)
					user = userManager.getUser(canonical);
			}
			if (user != null && user.isBedrockUser())
				return true;
		} catch (Throwable ignored) {
		}

		return bedrockPrefix != null && !bedrockPrefix.isEmpty() && name.startsWith(bedrockPrefix);
	}

	public Result resolve(String incomingName) {
		if (incomingName == null || incomingName.isEmpty())
			return new Result(incomingName, false, "empty-name");

		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getName().equalsIgnoreCase(incomingName)) {
				boolean bedrock = bedrockDetect.isBedrock(p.getUniqueId());
				String finalName = addPrefixIfNeeded(incomingName, bedrock);
				return new Result(finalName, bedrock, bedrock ? "online-uuid-bedrock" : "online-uuid-java");
			}
		}

		Boolean cached = getCachedCaseInsensitive(incomingName);
		if (cached != null) {
			boolean bedrock = cached;
			String finalName = addPrefixIfNeeded(incomingName, bedrock);
			return new Result(finalName, bedrock, "cache-" + (bedrock ? "bedrock" : "java"));
		}

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

	public static final class BedrockDetect {
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
