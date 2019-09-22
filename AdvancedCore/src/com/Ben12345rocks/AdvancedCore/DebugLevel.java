package com.Ben12345rocks.AdvancedCore;

public enum DebugLevel {
	NONE, INFO, EXTRA;

	public static DebugLevel getDebug(String str) {
		for (DebugLevel d : values()) {
			if (d.toString().equalsIgnoreCase(str)) {
				return d;
			}
		}
		return NONE;
	}

	public boolean isDebug() {
		return this == INFO || this == EXTRA;
	}
}
