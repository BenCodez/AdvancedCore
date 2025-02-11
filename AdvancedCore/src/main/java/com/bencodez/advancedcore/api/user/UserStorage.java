package com.bencodez.advancedcore.api.user;

public enum UserStorage {

	MYSQL, SQLITE, @Deprecated
	FLAT;

	public static UserStorage value(String str) {
		// if (str.equalsIgnoreCase("FLAT")) {
		// return SQLITE;
		// }
		for (UserStorage s : values()) {
			if (s.toString().equalsIgnoreCase(str)) {
				return s;
			}
		}
		return null;
	}
}
