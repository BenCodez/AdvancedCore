package com.bencodez.advancedcore.api.user.usercache.value;

import com.bencodez.advancedcore.api.user.userstorage.DataType;

public class DataValueString implements DataValue {
	private String value;

	public DataValueString(String value) {
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

	@Override
	public boolean isBoolean() {
		return false;
	}

	@Override
	public boolean getBoolean() {
		return false;
	}

	@Override
	public DataType getType() {
		return DataType.STRING;
	}

	@Override
	public String toString() {
		return value;
	}
}
