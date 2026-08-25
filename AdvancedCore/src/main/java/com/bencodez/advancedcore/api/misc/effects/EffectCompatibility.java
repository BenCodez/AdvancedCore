package com.bencodez.advancedcore.api.misc.effects;

import java.util.Locale;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

/**
 * Compatibility parsing shared by player-facing effects.
 */
public final class EffectCompatibility {

	private EffectCompatibility() {
	}

	public static BarColor parseBarColor(String value) {
		return BarColor.valueOf(normalizeEnum(value, "BLUE"));
	}

	public static BarStyle parseBarStyle(String value) {
		return BarStyle.valueOf(normalizeEnum(value, "SOLID"));
	}

	public static double clampProgress(double progress) {
		return Math.max(0D, Math.min(1D, progress));
	}

	private static String normalizeEnum(String value, String defaultValue) {
		String effective = value == null || value.trim().isEmpty() ? defaultValue : value.trim();
		return effective.toUpperCase(Locale.ROOT);
	}
}
