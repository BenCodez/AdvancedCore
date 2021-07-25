package com.bencodez.advancedcore.api.user.usercache.value;

import lombok.Getter;

public class UserDataValueInt implements UserDataValue {
	@Getter
	private int value;

	public UserDataValueInt(int value) {
		super();
		this.value = value;
	}

	@Override
	public String getString() {
		return null;
	}

	@Override
	public int getInt() {
		return value;
	}

	@Override
	public boolean isInt() {
		return true;
	}

	@Override
	public boolean isString() {
		return false;
	}

	@Override
	public String getTypeName() {
		return "Int";
	}
}
