package com.bencodez.advancedcore.api.user.usercache.keys;

public class UserDataKeyInt extends UserDataKey {
	public UserDataKeyInt(String key) {
		super(key);
		setColumnType("INT DEFAULT '0'");
	}
}
