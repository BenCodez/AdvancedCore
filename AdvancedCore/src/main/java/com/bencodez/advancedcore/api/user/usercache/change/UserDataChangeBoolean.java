package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;

import lombok.Getter;

public class UserDataChangeBoolean extends UserDataChange {
	@Getter
	private boolean value;

	public UserDataChangeBoolean(String key, boolean value) {
		super(key);
		this.value = value;
	}

	@Override
	public void dump() {
		setKey(null);
	}

	@Override
	public DataValue toUserDataValue() {
		return new DataValueBoolean(value);
	}
}
