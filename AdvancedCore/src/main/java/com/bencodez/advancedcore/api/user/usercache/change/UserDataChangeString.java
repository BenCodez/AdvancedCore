package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

import lombok.Getter;

public class UserDataChangeString extends UserDataChange {
	@Getter
	private String value;

	public UserDataChangeString(String key, String value) {
		super(key);
		this.value = value;
	}

	@Override
	public void dump() {
		setKey(null);
		value = null;
	}

	@Override
	public DataValue toUserDataValue() {
		return new DataValueString(value);
	}
}
