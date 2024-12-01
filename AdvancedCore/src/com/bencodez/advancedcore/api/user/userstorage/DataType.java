package com.bencodez.advancedcore.api.user.userstorage;

public enum DataType {

	BOOLEAN, INTEGER, STRING;

	public String getNoValue() {
		switch (this) {
		case INTEGER:
			return "0";
		default:
			return "NULL";

		}
	}

}
