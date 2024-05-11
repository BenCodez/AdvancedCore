package com.bencodez.advancedcore.api.placeholder;

import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

public abstract class NonPlayerPlaceHolder<T> {
	@Getter
	private String description;
	@Getter
	private String identifier;
	@Getter
	private boolean useStartsWith = false;

	@Getter
	private ConcurrentHashMap<String, String> cache;

	@Getter
	private boolean usesCache = false;

	@Getter
	private String noValueReturn = "0";

	public NonPlayerPlaceHolder(String identifier) {
		this.identifier = identifier;
	}

	public NonPlayerPlaceHolder(String identifier, String noValueReturn) {
		this.identifier = identifier;
		this.noValueReturn = noValueReturn;
	}

	public NonPlayerPlaceHolder(String identifier, String noValueReturn, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
		this.noValueReturn = noValueReturn;
	}

	public NonPlayerPlaceHolder(String identifier, boolean useStartsWith) {
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

	public abstract String placeholderRequest(String identifier);

	public NonPlayerPlaceHolder<T> useStartsWith() {
		useStartsWith = true;
		return this;
	}

	public NonPlayerPlaceHolder<T> withDescription(String desc) {
		description = desc;
		return this;
	}

	public NonPlayerPlaceHolder<T> setUseCache(boolean usesCache, String identifier) {
		this.usesCache = usesCache;
		if (cache == null) {
			cache = new ConcurrentHashMap<String, String>();
		}
		cache.put(identifier, "");
		return this;
	}

	public boolean isCached(String identifier) {
		return cache.containsKey(identifier);
	}

}
