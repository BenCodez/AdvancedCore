package com.bencodez.advancedcore.api.placeholder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

/**
 * Abstract class for calculating placeholders.
 *
 * @param <T> the user type
 */
public abstract class CalculatingPlaceholder<T> extends PlaceHolder<T> {

	/**
	 * Gets the cache data.
	 *
	 * @return the cache data map
	 */
	@Getter
	private ConcurrentHashMap<UUID, String> cacheData = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new calculating placeholder.
	 *
	 * @param identifier the identifier
	 */
	public CalculatingPlaceholder(String identifier) {
		super(identifier);
	}

	/**
	 * Instantiates a new calculating placeholder.
	 *
	 * @param identifier the identifier
	 * @param useStartsWith whether to use starts with matching
	 */
	public CalculatingPlaceholder(String identifier, boolean useStartsWith) {
		super(identifier, useStartsWith);
	}

	/**
	 * Instantiates a new calculating placeholder.
	 *
	 * @param identifier the identifier
	 * @param noValueReturn the no value return string
	 */
	public CalculatingPlaceholder(String identifier, String noValueReturn) {
		super(identifier, noValueReturn);
	}

	/**
	 * Instantiates a new calculating placeholder.
	 *
	 * @param identifier the identifier
	 * @param noValueReturn the no value return string
	 * @param useStartsWith whether to use starts with matching
	 */
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

	/**
	 * Placeholder data request.
	 *
	 * @param user the user
	 * @param identifier the identifier
	 * @return the result string
	 */
	public abstract String placeholderDataRequest(T user, String identifier);

}
