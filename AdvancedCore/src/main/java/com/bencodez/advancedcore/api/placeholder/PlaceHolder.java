package com.bencodez.advancedcore.api.placeholder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

/**
 * Abstract class for placeholders.
 *
 * @param <T> the user type
 */
public abstract class PlaceHolder<T> {
	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@Getter
	private String description;
	/**
	 * Gets the identifier.
	 *
	 * @return the identifier
	 */
	@Getter
	private String identifier;
	/**
	 * Gets use starts with flag.
	 *
	 * @return true if uses starts with
	 */
	@Getter
	private boolean useStartsWith = false;

	/**
	 * Gets the no value return string.
	 *
	 * @return the no value return string
	 */
	@Getter
	private String noValueReturn = "0";

	/**
	 * Gets the cache.
	 *
	 * @return the cache map
	 */
	@Getter
	private ConcurrentHashMap<String, ConcurrentHashMap<UUID, String>> cache;

	/**
	 * Gets the update data key.
	 *
	 * @return the update data key
	 */
	@Getter
	private String updateDataKey = "";

	/**
	 * Gets uses cache flag.
	 *
	 * @return true if uses cache
	 */
	@Getter
	private boolean usesCache = false;

	public PlaceHolder(String identifier) {
		this.identifier = identifier;
	}

	public PlaceHolder(String identifier, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
	}

	public PlaceHolder(String identifier, String noValueReturn) {
		this.identifier = identifier;
		this.noValueReturn = noValueReturn;
	}

	public PlaceHolder(String identifier, String noValueReturn, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
		this.noValueReturn = noValueReturn;
	}

	/**
	 * Clears cache for a player.
	 *
	 * @param javaUUID the player UUID
	 */
	public void clearCachePlayer(UUID javaUUID) {
		for (String ident : cache.keySet()) {
			if (cache.get(ident).containsKey(javaUUID)) {
				cache.get(ident).remove(javaUUID);
			}

		}
	}

	public boolean hasDescription() {
		return description != null;
	}

	public boolean isCached(String identifier) {
		return cache.containsKey(identifier);
	}

	public boolean isCached(String identifier, UUID uuid) {
		if (isCached(identifier)) {
			if (cache.get(identifier).containsKey(uuid)) {
				return true;
			}
		}
		return false;
	}

	public boolean matches(String identifier) {
		if (isUseStartsWith()) {
			if (MessageAPI.startsWithIgnoreCase(identifier, getIdentifier())) {
				return true;
			}
		} else {
			if (getIdentifier().equalsIgnoreCase(identifier)) {
				return true;
			}
		}
		return false;
	}

	public abstract String placeholderRequest(T user, String identifier);

	public PlaceHolder<T> setUseCache(boolean usesCache, String identifier) {
		this.usesCache = usesCache;
		if (cache == null) {
			cache = new ConcurrentHashMap<>();
		}
		cache.put(identifier, new ConcurrentHashMap<>());
		return this;
	}

	public PlaceHolder<T> updateDataKey(String key) {
		this.updateDataKey = key;
		return this;
	}

	public PlaceHolder<T> useStartsWith() {
		useStartsWith = true;
		return this;
	}

	public PlaceHolder<T> withDescription(String desc) {
		description = desc;
		return this;
	}

}
