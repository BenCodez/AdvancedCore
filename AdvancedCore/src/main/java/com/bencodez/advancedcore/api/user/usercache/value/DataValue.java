package com.bencodez.advancedcore.api.user.usercache.value;

import com.bencodez.advancedcore.api.user.userstorage.DataType;

public interface DataValue {
	public boolean getBoolean();

	public int getInt();

	public String getString();

	public DataType getType();

	public String getTypeName();

	public boolean isBoolean();

	public boolean isInt();

	public boolean isString();
}
