package com.bencodez.advancedcore.api.placeholder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

public abstract class CalculatingPlaceholder<T> extends PlaceHolder<T>{

	public CalculatingPlaceholder(String identifier) {
		super(identifier);
	}
	
	public CalculatingPlaceholder(String identifier, String noValueReturn) {
		super(identifier, noValueReturn);
	}

	public CalculatingPlaceholder(String identifier, String noValueReturn, boolean useStartsWith) {
		super(identifier, noValueReturn, useStartsWith);
	}

	public CalculatingPlaceholder(String identifier, boolean useStartsWith) {
		super(identifier, useStartsWith);
	}
	
	@Getter
	private ConcurrentHashMap<UUID, String> cacheData = new ConcurrentHashMap<UUID, String>();
	
	public abstract String placeholderDataRequest(T user, String identifier);
	
	@Override
	public void clearCachePlayer(UUID javaUUID) {
		for (String ident : getCache().keySet()) {
			if (getCache().get(ident).containsKey(javaUUID)) {
				getCache().get(ident).remove(javaUUID);
			}
		}
		cacheData.remove(javaUUID);
	}
	
}
