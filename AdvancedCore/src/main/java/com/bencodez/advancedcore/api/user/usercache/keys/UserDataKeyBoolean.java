package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;

public class UserDataKeyBoolean extends UserDataKey {
	public UserDataKeyBoolean(String key) {
		super(key);
		setColumnType("VARCHAR(5)");
	}

	public DataValue getDefault() {
		return new DataValueBoolean(false);
	}
}
