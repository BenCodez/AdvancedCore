package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

public class UserDataKeyInt extends UserDataKey {
	public UserDataKeyInt(String key) {
		super(key);
		setColumnType("INT DEFAULT '0'");
	}

	public DataValue getDefault() {
		return new DataValueInt(0);
	}
}
