package com.Ben12345rocks.AdvancedCore.Objects;

public enum UserStorage {
	FLAT,
	SQLITE,
	MYSQL;

	public static UserStorage value(String str) {
		for (UserStorage s : values()) {
			if (s.toString().equalsIgnoreCase(str)) {
				return s;
			}
		}
		return null;
	}
}
