package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;

import lombok.Getter;

public class UserDataChangeString extends UserDataChange {
	@Getter
	private String value;

	public UserDataChangeString(String key, String value) {
		super(key);
		this.value = value;
	}

	@Override
	public DataValue toUserDataValue() {
		return new DataValueString(value);
	}
}
