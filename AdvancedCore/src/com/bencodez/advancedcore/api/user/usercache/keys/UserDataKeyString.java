package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueBoolean;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;

public class UserDataKeyString extends UserDataKey {
	public UserDataKeyString(String key) {
		super(key);
		setColumnType("TEXT");
	}
	
	public DataValue getDefault() {
		if (getColumnType().equalsIgnoreCase("VARCHAR(5)")) {
			return new DataValueBoolean(false);
		}
		return new DataValueString("");
	}
}
