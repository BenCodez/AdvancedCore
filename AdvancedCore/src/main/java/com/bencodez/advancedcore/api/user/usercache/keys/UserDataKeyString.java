package com.bencodez.advancedcore.api.user.usercache.keys;

import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueString;

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
