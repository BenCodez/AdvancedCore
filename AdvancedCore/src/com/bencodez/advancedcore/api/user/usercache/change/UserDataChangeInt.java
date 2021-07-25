package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.UserDataValue;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueInt;

import lombok.Getter;

public class UserDataChangeInt extends UserDataChange {
	@Getter
	private int value;

	public UserDataChangeInt(String key, int value) {
		super(key);
		this.value = value;
	}

	@Override
	public UserDataValue toUserDataValue() {
		return new UserDataValueInt(value);
	}
}
