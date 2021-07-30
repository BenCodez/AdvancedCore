package com.bencodez.advancedcore.api.user.usercache.value;

import com.bencodez.advancedcore.api.user.userstorage.DataType;

public interface DataValue {
	public String getString();
	public int getInt();
	public boolean isInt();
	public boolean isString();
	public String getTypeName();
	public boolean isBoolean();
	public boolean getBoolean();
	public DataType getType();
}
