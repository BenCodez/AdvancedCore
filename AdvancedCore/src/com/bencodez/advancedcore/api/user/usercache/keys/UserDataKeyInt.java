package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;

public class UserDataKeyInt extends UserDataKey {
	public UserDataKeyInt(String key) {
		super(key);
		setColumnType("INT DEFAULT '0'");
	}
	
	public DataValue getDefault() {
		return new DataValueInt(0);
	}
}
