package com.bencodez.advancedcore.api.user;

public enum UserDataFetchMode {

	/**
	 * Current default behavior:
	 * - allow temp cache
	 * - allow UserDataCache
	 * - allow DB/flat lookup
	 * - wait for cache as needed
	 */
	DEFAULT(true, true, true, true),

	/**
	 * Don't use the UserDataCache at all (but still allow temp cache + DB/flat).
	 */
	NO_CACHE(true, false, true, true),

	/**
	 * Don't hit DB/flat storage (still allow temp cache + UserDataCache).
	 * If not found in caches, return default.
	 */
	NO_DB_LOOKUP(true, true, false, true),

	/**
	 * Only allow temp cache (no UserDataCache, no DB/flat).
	 */
	TEMP_ONLY(true, false, false, false),

	/**
	 * Only allow UserDataCache (no temp cache, no DB/flat).
	 */
	CACHE_ONLY(false, true, false, true),

	/**
	 * Use caches, but never wait for cache population (fast/non-blocking path).
	 */
	NO_WAIT(true, true, true, false);

	private final boolean allowTempCache;
	private final boolean allowUserCache;
	private final boolean allowStorageLookup;
	private final boolean waitForCache;

	UserDataFetchMode(boolean allowTempCache, boolean allowUserCache, boolean allowStorageLookup, boolean waitForCache) {
		this.allowTempCache = allowTempCache;
		this.allowUserCache = allowUserCache;
		this.allowStorageLookup = allowStorageLookup;
		this.waitForCache = waitForCache;
	}

	public boolean allowTempCache() {
		return allowTempCache;
	}

	public boolean allowUserCache() {
		return allowUserCache;
	}

	public boolean allowStorageLookup() {
		return allowStorageLookup;
	}

	public boolean waitForCache() {
		return waitForCache;
	}

	public static UserDataFetchMode fromBooleans(boolean useCache, boolean waitForCache) {
		if (useCache && waitForCache) {
			return DEFAULT;
		}
		if (useCache) {
			return NO_WAIT;
		}
		// no cache
		return waitForCache ? NO_CACHE : NO_CACHE; // wait doesn't matter if cache isn't used
	}
}
