package com.bencodez.advancedcore.api.placeholder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

public abstract class PlaceHolder<T> {
	@Getter
	private String description;
	@Getter
	private String identifier;
	@Getter
	private boolean useStartsWith = false;

	@Getter
	private String noValueReturn = "0";

	@Getter
	private ConcurrentHashMap<String, ConcurrentHashMap<UUID, String>> cache;

	@Getter
	private String updateDataKey = "";

	public PlaceHolder<T> updateDataKey(String key) {
		this.updateDataKey = key;
		return this;
	}

	@Getter
	private boolean usesCache = false;

	public PlaceHolder(String identifier) {
		this.identifier = identifier;
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

	public PlaceHolder(String identifier, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
	}

	public boolean hasDescription() {
		return description != null;
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

	public PlaceHolder<T> useStartsWith() {
		useStartsWith = true;
		return this;
	}

	public PlaceHolder<T> withDescription(String desc) {
		description = desc;
		return this;
	}

	public PlaceHolder<T> setUseCache(boolean usesCache, String identifier) {
		this.usesCache = usesCache;
		if (cache == null) {
			cache = new ConcurrentHashMap<String, ConcurrentHashMap<UUID, String>>();
		}
		cache.put(identifier, new ConcurrentHashMap<UUID, String>());
		return this;
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

	public void clearCachePlayer(UUID javaUUID) {
		for (String ident : cache.keySet()) {
			if (cache.get(ident).containsKey(javaUUID)) {
				cache.get(ident).remove(javaUUID);
			}
			
		}
	}

}
