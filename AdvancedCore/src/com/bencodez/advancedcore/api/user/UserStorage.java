package com.bencodez.advancedcore.api.user;

public enum UserStorage {
	FLAT, SQLITE, MYSQL;

	public static UserStorage value(String str) {
		for (UserStorage s : values()) {
			if (s.toString().equalsIgnoreCase(str)) {
				return s;
			}
		}
		return null;
	}
}
