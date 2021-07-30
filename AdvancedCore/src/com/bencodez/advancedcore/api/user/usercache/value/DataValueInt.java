package com.bencodez.advancedcore.api.user.usercache.value;

import com.bencodez.advancedcore.api.user.userstorage.DataType;

public class DataValueInt implements DataValue {
	private int value;

	public DataValueInt(int value) {
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
		return DataType.INTEGER;
	}
	
	@Override
	public String toString() {
		return "" + value;
	}
}
