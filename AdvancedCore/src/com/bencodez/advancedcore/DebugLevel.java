package com.bencodez.advancedcore;

public enum DebugLevel {
	NONE, INFO, EXTRA, DEV;

	public static DebugLevel getDebug(String str) {
		for (DebugLevel d : values()) {
			if (d.toString().equalsIgnoreCase(str)) {
				return d;
			}
		}
		return NONE;
	}

	public boolean isDebug() {
		return this == INFO || this == EXTRA || this == DEV;
	}
}