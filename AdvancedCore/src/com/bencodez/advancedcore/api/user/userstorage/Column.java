package com.bencodez.advancedcore.api.user.userstorage;

import com.bencodez.advancedcore.api.user.usercache.value.UserDataValue;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueString;

public class Column {

	public DataType dataType;
	public int limit = 0;
	public String name;
	private Object value;

	public Column(String name, DataType dataType) {
		this.name = name;
		this.dataType = dataType;
		limit = 0;
	}

	public Column(String name, DataType dataType, int limit) {
		this.name = name;
		this.dataType = dataType;
		this.limit = limit;
	}

	public Column(String name, Object value, DataType dataType) {
		this.name = name;
		this.dataType = dataType;
		limit = 0;
		this.value = value;
	}

	public DataType getDataType() {
		return dataType;
	}

	public int getLimit() {
		return limit;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public UserDataValue toUserData() {
		if (dataType.equals(DataType.INTEGER)) {
			int num = 0;
			if (value instanceof Integer) {
				try {
					num = (int) value;
				} catch (ClassCastException | NullPointerException ex) {
				}
			} else if (value instanceof String) {
				try {
					num = Integer.parseInt((String) value);
				} catch (Exception e) {
				}
			}
			return new UserDataValueInt(num);
		} else {
			String str = "";
			if (getValue() != null) {
				str = getValue().toString();
			}
			return new UserDataValueString(str);
		}
	}

}
