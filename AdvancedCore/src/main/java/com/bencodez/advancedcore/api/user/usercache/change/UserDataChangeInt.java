package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;

import lombok.Getter;

public class UserDataChangeInt extends UserDataChange {
	@Getter
	private int value;

	public UserDataChangeInt(String key, int value) {
		super(key);
		this.value = value;
	}

	@Override
	public void dump() {
		setKey(null);
	}

	@Override
	public DataValue toUserDataValue() {
		return new DataValueInt(value);
	}
}
