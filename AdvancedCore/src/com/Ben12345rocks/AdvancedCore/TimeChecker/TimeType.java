package com.Ben12345rocks.AdvancedCore.TimeChecker;

public enum TimeType {
	MONTH,
	WEEK,
	DAY;

	public static TimeType getTimeType(String str) {
		for (TimeType time : TimeType.values()) {
			if (time.toString().equalsIgnoreCase(str)) {
				return time;
			}
		}
		return null;
	}
}
