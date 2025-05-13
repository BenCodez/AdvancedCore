package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.simpleapi.sql.data.DataValue;

import lombok.Getter;

public abstract class UserDataKey {
	@Getter
	private String columnType;

	@Getter
	private String key;

	public UserDataKey(String key) {
		this.key = key;
	}

	public abstract DataValue getDefault();

	public UserDataKey setColumnType(String columnType) {
		this.columnType = columnType;
		return this;
	}
}
