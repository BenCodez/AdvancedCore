package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.UserDataValue;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueString;

import lombok.Getter;

public class UserDataChangeString extends UserDataChange {
	@Getter
	private String value;

	public UserDataChangeString(String key, String value) {
		super(key);
		this.value = value;
	}

	@Override
	public UserDataValue toUserDataValue() {
		return new UserDataValueString(value);
	}
}
