package com.bencodez.advancedcore.api.user.usercache.keys;

import lombok.Getter;

public class UserDataKey {
	@Getter
	private String columnType;

	@Getter
	private String key;

	public UserDataKey(String key) {
		this.key = key;
	}

	public UserDataKey setColumnType(String columnType) {
		this.columnType = columnType;
		return this;
	}
}
