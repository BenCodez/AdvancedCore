package com.bencodez.advancedcore.api.user.usercache.keys;

import lombok.Getter;
import lombok.Setter;

public class UserDataKey {
	@Getter
	private String key;

	@Getter
	@Setter
	private boolean cacheChanges = true;

	@Getter
	private String columnType;

	public UserDataKey setColumnType(String columnType) {
		this.columnType = columnType;
		return this;
	}

	public UserDataKey(String key) {
		this.key = key;
	}
}
