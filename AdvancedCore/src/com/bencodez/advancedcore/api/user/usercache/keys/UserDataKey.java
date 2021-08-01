package com.bencodez.advancedcore.api.user.usercache.keys;

import lombok.Getter;

public class UserDataKey {
	@Getter
	private String key;

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
