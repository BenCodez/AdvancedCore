package com.bencodez.advancedcore.api.user.usercache.keys;

public class UserDataKeyString extends UserDataKey {
	public UserDataKeyString(String key) {
		super(key);
		setColumnType("TEXT");
	}
}
