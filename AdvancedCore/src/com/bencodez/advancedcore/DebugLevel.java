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

	public boolean isDebug(DebugLevel cur) {
		switch (this) {
		case DEV:
			if (cur != NONE) {
				return true;
			}
			break;
		case EXTRA:
			if (cur == INFO || cur == EXTRA) {
				return true;
			}
			break;
		case INFO:
			if (cur == INFO) {
				return true;
			}
			break;
		case NONE:
			break;
		default:
			break;

		}

		return false;
	}
}
