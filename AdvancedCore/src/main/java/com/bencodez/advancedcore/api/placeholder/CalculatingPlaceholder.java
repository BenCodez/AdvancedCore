package com.bencodez.advancedcore.api.placeholder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

public abstract class CalculatingPlaceholder<T> extends PlaceHolder<T> {

	@Getter
	private ConcurrentHashMap<UUID, String> cacheData = new ConcurrentHashMap<>();

	public CalculatingPlaceholder(String identifier) {
		super(identifier);
	}

	public CalculatingPlaceholder(String identifier, boolean useStartsWith) {
		super(identifier, useStartsWith);
	}

	public CalculatingPlaceholder(String identifier, String noValueReturn) {
		super(identifier, noValueReturn);
	}

	public CalculatingPlaceholder(String identifier, String noValueReturn, boolean useStartsWith) {
		super(identifier, noValueReturn, useStartsWith);
	}

	@Override
	public void clearCachePlayer(UUID javaUUID) {
		for (String ident : getCache().keySet()) {
			if (getCache().get(ident).containsKey(javaUUID)) {
				getCache().get(ident).remove(javaUUID);
			}
		}
		cacheData.remove(javaUUID);
	}

	public abstract String placeholderDataRequest(T user, String identifier);

}
