package com.bencodez.advancedcore.data;

import java.io.File;
import java.time.Month;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.simpleapi.file.YMLFile;

// TODO: Auto-generated Javadoc
/**
 * The Class ServerData.
 */
public class ServerData extends YMLFile {
	/**
	 * Durable state for one locally detected time transition. The identifier is
	 * deliberately stable while a transition is pending so consumers can store
	 * their own idempotency receipt before acknowledging asynchronous work.
	 */
	public record TimeChangeTransitionState(TimeType type, String id, String periodKey, String markerValue,
			boolean pending) {
	}

	public ServerData(AdvancedCorePlugin plugin) {
		super(plugin, new File(plugin.getDataFolder(), "ServerData.yml"));
	}

	public long getLastUpdated() {
		return getData().getLong("LastUpdated", -1);
	}

	/**
	 * Gets the plugin version.
	 *
	 * @param plugin the plugin
	 * @return the plugin version
	 */
	public String getPluginVersion(Plugin plugin) {
		return getData().getString("PluginVersions." + plugin.getName(), "");
	}

	/**
	 * Gets the prev day.
	 *
	 * @return the prev day
	 */
	public int getPrevDay() {
		return getData().getInt("PrevDay", -1);
	}

	/**
	 * Gets the prev month.
	 *
	 * @return the prev month
	 */
	public String getPrevMonth() {
		return getData().getString("Month", "");
	}

	/**
	 * Gets the prev week day.
	 *
	 * @return the prev week day
	 */
	public int getPrevWeekDay() {
		return getData().getInt("PrevWeek", -1);
	}

	public boolean isIgnoreTime() {
		return getData().getBoolean("IgnoreTime", false);
	}

	@Override
	public void onFileCreation() {
	}

	public void setData(String path, Object value) {
		getData().set(path, value);
		saveData();
	}

	public void setIgnoreTime(boolean value) {
		getData().set("IgnoreTime", value);
		saveData();
	}

	public void setLastUpdated() {
		getData().set("LastUpdated", System.currentTimeMillis());
		saveData();
	}

	/**
	 * Sets the plugin version.
	 *
	 * @param plugin the new plugin version
	 */
	public void setPluginVersion(Plugin plugin) {
		getData().set("PluginVersions." + plugin.getName(), plugin.getDescription().getVersion());
		saveData();
	}

	/**
	 * Sets the prev day.
	 *
	 * @param day the new prev day
	 */
	public void setPrevDay(int day) {
		getData().set("PrevDay", day);
		saveData();
	}

	/**
	 * Sets the prev month.
	 *
	 * @param month the new prev month
	 */
	public void setPrevMonth(String month) {
		getData().set("Month", month);
		saveData();
	}

	/**
	 * Sets the prev week day.
	 *
	 * @param week the new prev week day
	 */
	public void setPrevWeekDay(int week) {
		getData().set("PrevWeek", week);
		saveData();
	}

	/**
	 * Starts (or recovers) a transition. This writes the pending record before
	 * event listeners can observe its identifier.
	 */
	public synchronized TimeChangeTransitionState beginTimeChangeTransition(TimeType type, String periodKey,
			String markerValue) {
		String path = transitionPath(type);
		String existingPeriod = getData().getString(path + ".Period", "");
		String existingId = getData().getString(path + ".Id", "");
		boolean pending = getData().getBoolean(path + ".Pending", false);
		if (pending && periodKey.equals(existingPeriod) && !existingId.isEmpty()) {
			return new TimeChangeTransitionState(type, existingId, existingPeriod,
					getData().getString(path + ".Marker", markerValue), true);
		}

		String id = type.name() + ":" + periodKey;
		getData().set(path + ".Id", id);
		getData().set(path + ".Period", periodKey);
		getData().set(path + ".Marker", markerValue);
		getData().set(path + ".Pending", true);
		saveData();
		return new TimeChangeTransitionState(type, id, periodKey, markerValue, true);
	}

	/** Returns an unfinished transition even when the current clock has moved on. */
	public synchronized TimeChangeTransitionState getPendingTimeChangeTransition(TimeType type) {
		String path = transitionPath(type);
		if (!getData().getBoolean(path + ".Pending", false)) return null;
		String id = getData().getString(path + ".Id", "");
		String period = getData().getString(path + ".Period", "");
		String marker = getData().getString(path + ".Marker", "");
		if (id.isEmpty() || period.isEmpty() || !isValidMarker(type, marker)) {
			getData().set(path + ".Pending", false);
			saveData();
			return null;
		}
		return new TimeChangeTransitionState(type, id, period, marker, true);
	}

	private boolean isValidMarker(TimeType type, String marker) {
		try {
			return switch (type) {
			case DAY -> {
				int value = Integer.parseInt(marker);
				yield value >= 1 && value <= 31;
			}
			case WEEK -> {
				int value = Integer.parseInt(marker);
				yield value >= 1 && value <= 53;
			}
			case MONTH -> {
				Month.valueOf(marker);
				yield true;
			}
			};
		} catch (IllegalArgumentException invalid) {
			return false;
		}
	}

	/**
	 * Atomically records successful completion with the legacy marker. A crash
	 * before this save leaves the pending record intact for a recoverable retry.
	 */
	public synchronized void completeTimeChangeTransition(TimeChangeTransitionState transition) {
		if (!matchesPendingTransition(transition)) return;
		String markerPath = switch (transition.type()) {
		case DAY -> "PrevDay";
		case WEEK -> "PrevWeek";
		case MONTH -> "Month";
		};
		Object previousMarker = getData().get(markerPath);
		String pendingPath = transitionPath(transition.type()) + ".Pending";
		try {
			switch (transition.type()) {
			case DAY, WEEK -> getData().set(markerPath, Integer.parseInt(transition.markerValue()));
			case MONTH -> getData().set(markerPath, transition.markerValue());
			}
			getData().set(pendingPath, false);
			saveData();
		} catch (RuntimeException | Error failure) {
			getData().set(markerPath, previousMarker);
			getData().set(pendingPath, true);
			throw failure;
		}
	}

	/** Leaves a durable pending record available for the next checker instance. */
	public synchronized void failTimeChangeTransition(TimeChangeTransitionState transition) {
		if (!matchesPendingTransition(transition)) return;
		getData().set(transitionPath(transition.type()) + ".Pending", true);
		saveData();
	}

	private boolean matchesPendingTransition(TimeChangeTransitionState transition) {
		if (transition == null) return false;
		String path = transitionPath(transition.type());
		return getData().getBoolean(path + ".Pending", false)
				&& transition.id().equals(getData().getString(path + ".Id", ""))
				&& transition.periodKey().equals(getData().getString(path + ".Period", ""));
	}

	private String transitionPath(TimeType type) {
		return "TimeTransitions." + type.name();
	}
}
