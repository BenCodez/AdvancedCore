package com.bencodez.advancedcore.api.user;

public enum UserStorage {

	MYSQL, SQLITE;

	public static UserStorage value(String str) {
		if (str != null && "FLAT".equalsIgnoreCase(str.trim())) {
			throw new IllegalArgumentException("FLAT user storage has been removed. Convert existing data to "
					+ "SQLITE or MYSQL using the previous version before upgrading; existing Data files are not modified.");
		}
		for (UserStorage s : values()) {
			if (s.toString().equalsIgnoreCase(str)) {
				return s;
			}
		}
		return null;
	}
}
