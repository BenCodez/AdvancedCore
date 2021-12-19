package com.bencodez.advancedcore.api.placeholder;

import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.advancedcore.api.messages.StringParser;

import lombok.Getter;

public abstract class NonPlaceHolder<T> {
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

	public NonPlaceHolder(String identifier) {
		this.identifier = identifier;
	}

	public NonPlaceHolder(String identifier, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
	}

	public boolean hasDescription() {
		return description != null;
	}

	public boolean matches(String identifier) {
		if (isUseStartsWith()) {
			if (StringParser.getInstance().startsWithIgnoreCase(identifier, getIdentifier())) {
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

	public NonPlaceHolder<T> useStartsWith() {
		useStartsWith = true;
		return this;
	}

	public NonPlaceHolder<T> withDescription(String desc) {
		description = desc;
		return this;
	}

	public NonPlaceHolder<T> setUseCache(boolean usesCache, String identifier) {
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
