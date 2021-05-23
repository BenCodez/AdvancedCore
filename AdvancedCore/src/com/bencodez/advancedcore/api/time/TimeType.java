package com.bencodez.advancedcore.api.time;

public enum TimeType {
	DAY, MONTH, WEEK;

	public static TimeType getTimeType(String str) {
		for (TimeType time : TimeType.values()) {
			if (time.toString().equalsIgnoreCase(str)) {
				return time;
			}
		}
		return null;
	}
}
