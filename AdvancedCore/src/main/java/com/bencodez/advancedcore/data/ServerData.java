package com.bencodez.advancedcore.data;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Month;
import java.util.HexFormat;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.simpleapi.file.DurableFiles;
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

	@Override
	public synchronized void setup() {
		Path target = getdFile().toPath().toAbsolutePath();
		Path backup = backupPath(target);
		Path replacementMarker = replacementMarkerPath(target);
		if (Files.exists(replacementMarker)) {
			recoverInterruptedReplacement(replacementMarker, backup, target);
		}
		if (!Files.exists(target) && Files.exists(backup)) restoreBackup(backup, target);
		super.setup();
		if (isFailedToRead() && Files.exists(backup)) {
			restoreBackup(backup, target);
			super.reloadData();
			if (isFailedToRead()) throw new IllegalStateException("Failed to recover " + target.getFileName());
		}
	}

	/** Writes transition state as one forced snapshot and reports failures. */
	protected synchronized void saveTimeChangeTransitionData() {
		Path target = getdFile().toPath().toAbsolutePath();
		Path parent = target.getParent();
		Path temporary = null;
		try {
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
			getData().save(temporary.toFile());
			try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			replaceSnapshot(temporary, target);
			temporary = null;
		} catch (IOException failure) {
			throw new UncheckedIOException("Failed to save " + target.getFileName(), failure);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException cleanupFailure) {
					getPlugin().getLogger().warning("Failed to remove temporary " + target.getFileName()
							+ " snapshot: " + cleanupFailure.getMessage());
				}
			}
		}
	}

	protected void moveAtomically(Path source, Path target) throws IOException {
		Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	protected void forceDirectory(Path directory) throws IOException {
		DurableFiles.forceDirectory(directory);
	}

	private void replaceSnapshot(Path temporary, Path target) throws IOException {
		try {
			moveAtomically(temporary, target);
			forceDirectory(target.getParent());
		} catch (AtomicMoveNotSupportedException unsupported) {
			Path backup = backupPath(target);
			Path replacementMarker = replacementMarkerPath(target);
			if (Files.exists(target)) {
				Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
				try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.WRITE)) {
					channel.force(true);
				}
				forceDirectory(target.getParent());
			}
			Files.writeString(replacementMarker, snapshotHash(temporary), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			try (FileChannel channel = FileChannel.open(replacementMarker, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			forceDirectory(target.getParent());
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException replacementFailure) {
				if (Files.exists(backup)) {
					Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
					DurableFiles.forceFile(target);
					forceDirectory(target.getParent());
				}
				throw replacementFailure;
			}
			// Keep the recovery marker until the replacement's directory entry is
			// durable. If this force fails, startup can identify the committed target
			// by its recorded hash instead of rolling it back as an incomplete move.
			forceDirectory(target.getParent());
			Files.delete(replacementMarker);
			forceDirectory(target.getParent());
		}
	}

	private Path backupPath(Path target) {
		return target.resolveSibling(target.getFileName().toString() + ".backup");
	}

	private Path replacementMarkerPath(Path target) {
		return target.resolveSibling(target.getFileName().toString() + ".replacement-pending");
	}

	private void recoverInterruptedReplacement(Path marker, Path backup, Path target) {
		try {
			String intendedHash = Files.readString(marker).trim();
			if (Files.isRegularFile(target) && !intendedHash.isEmpty()
					&& intendedHash.equals(snapshotHash(target))) {
				deleteReplacementMarker(marker);
				return;
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("Failed to inspect interrupted replacement of " + target.getFileName(), failure);
		}
		if (!Files.exists(backup)) {
			throw new IllegalStateException("Cannot recover interrupted replacement of " + target.getFileName());
		}
		restoreBackup(backup, target);
		deleteReplacementMarker(marker);
	}

	private String snapshotHash(Path snapshot) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(snapshot)) {
				byte[] buffer = new byte[8192];
				for (int read; (read = input.read(buffer)) >= 0;) {
					if (read > 0) digest.update(buffer, 0, read);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private void restoreBackup(Path backup, Path target) {
		try {
			Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
			DurableFiles.forceFile(target);
			forceDirectory(target.getParent());
		} catch (IOException failure) {
			throw new UncheckedIOException("Failed to recover " + target.getFileName(), failure);
		}
	}

	private void deleteReplacementMarker(Path marker) {
		try {
			Files.delete(marker);
			forceDirectory(marker.getParent());
		} catch (IOException failure) {
			throw new UncheckedIOException("Failed to clear interrupted replacement marker", failure);
		}
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
		Object previousId = getData().get(path + ".Id");
		Object previousPeriod = getData().get(path + ".Period");
		Object previousMarker = getData().get(path + ".Marker");
		Object previousPending = getData().get(path + ".Pending");
		try {
			getData().set(path + ".Id", id);
			getData().set(path + ".Period", periodKey);
			getData().set(path + ".Marker", markerValue);
			getData().set(path + ".Pending", true);
			saveTimeChangeTransitionData();
		} catch (RuntimeException | Error failure) {
			getData().set(path + ".Id", previousId);
			getData().set(path + ".Period", previousPeriod);
			getData().set(path + ".Marker", previousMarker);
			getData().set(path + ".Pending", previousPending);
			throw failure;
		}
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
			saveTimeChangeTransitionData();
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
			saveTimeChangeTransitionData();
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
		saveTimeChangeTransitionData();
	}

	/** Returns whether this exact durable transition still owns the pending slot. */
	public synchronized boolean isPendingTimeChangeTransition(TimeChangeTransitionState transition) {
		return matchesPendingTransition(transition);
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
