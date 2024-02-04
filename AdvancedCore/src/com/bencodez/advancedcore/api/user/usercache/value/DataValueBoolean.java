package com.bencodez.advancedcore.api.user.usercache.value;

import com.bencodez.advancedcore.api.user.userstorage.DataType;

public class DataValueBoolean implements DataValue {
	private boolean value;

	public DataValueBoolean(boolean value) {
		super();
		this.value = value;
	}

	@Override
	public boolean getBoolean() {
		return value;
	}

	@Override
	public int getInt() {
		return 0;
	}

	@Override
	public String getString() {
		return "" + getBoolean();
	}

	@Override
	public DataType getType() {
		return DataType.BOOLEAN;
	}

	@Override
	public String getTypeName() {
		return "Boolean";
	}

	@Override
	public boolean isBoolean() {
		return true;
	}

	@Override
	public boolean isInt() {
		return false;
	}

	@Override
	public boolean isString() {
		return false;
	}

	@Override
	public String toString() {
		return "" + value;
	}
}
