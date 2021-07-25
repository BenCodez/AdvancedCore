package com.bencodez.advancedcore.api.user.usercache.value;

import lombok.Getter;

public class UserDataValueString implements UserDataValue {
	@Getter
	private String value;

	public UserDataValueString(String value) {
		super();
		this.value = value;
	}

	@Override
	public String getString() {
		return value;
	}

	@Override
	public int getInt() {
		return 0;
	}

	@Override
	public boolean isInt() {
		return false;
	}

	@Override
	public boolean isString() {
		return true;
	}

	@Override
	public String getTypeName() {
		return "String";
	}
}
