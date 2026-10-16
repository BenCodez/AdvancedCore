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
	// Selected only while the checked publisher owns this monitor. Publication
	// visibility and its durable acknowledgement are separate outcomes.
	private org.bukkit.configuration.file.FileConfiguration finalDataSnapshot;
	private long visiblePublication;
	private boolean publicationSettlementPending;
	private boolean replacementCleanupPending;
	private boolean replacementUncertain;
	private boolean finalPublicationIndeterminate;
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
	public synchronized void saveData() {
		prepareSnapshotPublication();
		super.saveData();
	}

	@Override
	public synchronized void setup() {
		requireFinalPublicationSettled();
		Path target = getdFile().toPath().toAbsolutePath();
		Path backup = backupPath(target);
		Path replacementMarker = replacementMarkerPath(target);
		if (Files.exists(replacementMarker)) {
			recoverInterruptedReplacement(replacementMarker, backup, target);
		}
		super.setup();
	}

	/** Writes transition state as one forced snapshot and reports failures. */
	protected synchronized void saveTimeChangeTransitionData() {
		prepareSnapshotPublication();
		Path target = getdFile().toPath().toAbsolutePath();
		Path parent = target.getParent();
		Path temporary = null;
		try {
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
			(finalDataSnapshot == null ? getData() : finalDataSnapshot).save(temporary.toFile());
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

	/** Shutdown persistence must report I/O failure rather than acknowledge a failed save. */
	public synchronized void saveDataChecked() {
		saveTimeChangeTransitionData();
	}

	/**
	 * Stages a final YAML mutation on the shutdown persistence caller and confirms
	 * publication by this file's actual owner. Adapters with another persistence
	 * owner must override this method and return that owner's publication stage.
	 * No caller may wait for the stage while holding a YAML owner's monitor.
	 * Legacy saveData overrides still receive the mutation on their live YAML and
	 * are invoked once, but their void return cannot prove durable publication and
	 * therefore produces an exceptional stage.
	 */
	public java.util.concurrent.CompletionStage<Void> mutateFinalData(
			java.util.function.Consumer<org.bukkit.configuration.file.FileConfiguration> mutation) {
		try {
			synchronized (this) {
				requireFinalPublicationSettled();
				java.util.Objects.requireNonNull(mutation, "mutation");
				Class<?> saveOwner = getClass().getMethod("saveData").getDeclaringClass();
				if (saveOwner != YMLFile.class && saveOwner != ServerData.class) {
					// Preserve the legacy mutation-and-save contract without treating
					// its void return as confirmation of durable publication.
					mutation.accept(getData());
					saveData();
					return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException(
							"Legacy ServerData owner does not confirm final publication"));
				}
				var live = getData();
				var candidate = new org.bukkit.configuration.file.YamlConfiguration();
				var before = new java.util.LinkedHashMap<String, Object>();
				for (String path : live.getKeys(false)) {
					before.put(path, finalValue(live.get(path)));
					setFinalValue(candidate, path, before.get(path));
				}
				mutation.accept(candidate);
				finalDataSnapshot = candidate;
				long previousPublication = visiblePublication;
				try { saveDataChecked(); }
				catch (Throwable failure) {
					// A failed directory force/cleanup does not undo a visible move.
					if (visiblePublication != previousPublication) {
						try { reconcileFinalData(live, candidate, before); }
						catch (Throwable reconciliationFailure) {
							replacementUncertain = true;
							if (failure != reconciliationFailure) failure.addSuppressed(reconciliationFailure);
						}
						// Retry only the retained commit's durability/cleanup, never its
						// mutation or file replacement. A confirmed commit must settle
						// this original stage before a later save can publish its values.
						if (!replacementUncertain && (publicationSettlementPending || replacementCleanupPending)) {
							try { prepareSnapshotPublication(); }
							catch (Throwable settlementFailure) {
								finalPublicationIndeterminate = true;
								if (failure != settlementFailure) failure.addSuppressed(settlementFailure);
								throw failure;
							}
						} else {
							finalPublicationIndeterminate = true;
							throw failure;
						}
					} else {
						throw failure;
					}
				}
				finally { finalDataSnapshot = null; }
				reconcileFinalData(live, candidate, before);
			}
			return java.util.concurrent.CompletableFuture.completedFuture(null);
		} catch (Throwable failure) {
			return java.util.concurrent.CompletableFuture.failedFuture(failure);
		}
	}

	private void reconcileFinalData(org.bukkit.configuration.file.FileConfiguration live,
			org.bukkit.configuration.file.FileConfiguration candidate, java.util.Map<String, Object> before) {
		// Retain the live YAML identity and any concurrent direct live updates.
		try {
			var paths = new java.util.HashSet<>(before.keySet());
			paths.addAll(candidate.getKeys(false));
			for (String path : paths) {
				reconcileFinalValue(live, path, before.get(path), finalValue(candidate.get(path)));
			}
		} catch (RuntimeException | Error failure) {
			replacementUncertain = true;
			throw failure;
		}
	}

	private static void reconcileFinalValue(org.bukkit.configuration.ConfigurationSection live, String path,
			Object before, Object next) {
		if (java.util.Objects.equals(before, next)) return;
		Object current = live.get(path);
		if ((current instanceof org.bukkit.configuration.ConfigurationSection || current instanceof java.util.Map<?, ?>)
				&& (before == null || before instanceof java.util.Map<?, ?>)
				&& (next == null || next instanceof java.util.Map<?, ?>)) {
			// Sections are containers, not indivisible CAS values. A change to one
			// child must not discard a published change to its independent sibling.
			if (current instanceof java.util.Map<?, ?>) setFinalValue(live, path, finalValue(current));
			var section = live.getConfigurationSection(path);
			var previousChildren = finalSectionChildren(before);
			var nextChildren = finalSectionChildren(next);
			var paths = new java.util.HashSet<>(previousChildren.keySet());
			paths.addAll(nextChildren.keySet());
			for (String child : paths) {
				reconcileFinalValue(section, child, previousChildren.get(child), nextChildren.get(child));
			}
			// Removing a container may remove unchanged children, but must retain
			// concurrent child changes/additions rather than delete their parent.
			if (next == null && section.getKeys(false).isEmpty()) live.set(path, null);
		} else if (java.util.Objects.equals(before, finalValue(current))) {
			// Scalars, lists and incompatible type changes still compete at this
			// exact field; a newer live value wins over the published candidate.
			setFinalValue(live, path, next);
		}
	}

	private static java.util.Map<String, Object> finalSectionChildren(Object value) {
		// Only section child names use string paths. Values remain untouched so
		// maps inside lists retain their original key types and distinct entries.
		var children = new java.util.LinkedHashMap<String, Object>();
		if (value instanceof java.util.Map<?, ?> map)
			map.forEach((key, child) -> children.put(key.toString(), child));
		return children;
	}

	private static Object finalValue(Object value) {
		if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
			var copy = new java.util.LinkedHashMap<String, Object>();
			for (String key : section.getKeys(false)) copy.put(key, finalValue(section.get(key)));
			return copy;
		}
		if (value instanceof java.util.Map<?, ?> map) {
			var copy = new java.util.LinkedHashMap<Object, Object>();
			map.forEach((key, child) -> copy.put(key, finalValue(child)));
			return copy;
		}
		if (value instanceof java.util.List<?> list) return list.stream().map(ServerData::finalValue).collect(
				java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		return value;
	}

	private static void setFinalValue(org.bukkit.configuration.ConfigurationSection target, String path, Object value) {
		target.set(path, null);
		if (value instanceof java.util.Map<?, ?> map) {
			var section = target.createSection(path);
			map.forEach((key, child) -> setFinalValue(section, key.toString(), child));
		} else if (value != null) target.set(path, value);
	}

	protected void moveAtomically(Path source, Path target) throws IOException {
		Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	protected void forceDirectory(Path directory) throws IOException {
		DurableFiles.forceDirectory(directory);
	}

	private void replaceSnapshot(Path temporary, Path target) throws IOException {
		String intendedHash = snapshotHash(temporary);
		String previousHash = Files.isRegularFile(target) ? snapshotHash(target) : null;
		boolean previouslyAbsent = Files.notExists(target);
		try {
			moveAtomically(temporary, target);
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
			// Even a failed marker write can leave recovery state that must be
			// removed before a later save changes the target's hash.
			replacementCleanupPending = true;
			Files.writeString(replacementMarker, intendedHash, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			try (FileChannel channel = FileChannel.open(replacementMarker, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			forceDirectory(target.getParent());
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException | Error replacementFailure) {
				inspectFailedMove(target, intendedHash, previousHash, previouslyAbsent, replacementFailure);
				throw replacementFailure;
			}
			visiblePublication++;
			publicationSettlementPending = true;
			// Keep the recovery marker until the replacement's directory entry is
			// durable. If this force fails, startup can identify the committed target
			// by its recorded hash instead of rolling it back as an incomplete move.
			forceDirectory(target.getParent());
			publicationSettlementPending = false;
			clearReplacementState(replacementMarker, backup);
			replacementCleanupPending = false;
			return;
		} catch (IOException | RuntimeException | Error replacementFailure) {
			inspectFailedMove(target, intendedHash, previousHash, previouslyAbsent, replacementFailure);
			throw replacementFailure;
		}
		visiblePublication++;
		publicationSettlementPending = true;
		forceDirectory(target.getParent());
		publicationSettlementPending = false;
	}

	private void inspectFailedMove(Path target, String intendedHash, String previousHash,
			boolean previouslyAbsent, Throwable failure) {
		try {
			if (Files.isRegularFile(target)) {
				String actualHash = snapshotHash(target);
				// Equal predecessor/candidate bytes cannot prove that the forced
				// temporary file replaced an older, potentially unforced target.
				if (actualHash.equals(previousHash)) return;
				if (intendedHash.equals(actualHash)) {
					visiblePublication++;
					publicationSettlementPending = true;
					return;
				}
			} else if (previouslyAbsent && Files.notExists(target)) return;
		} catch (IOException | RuntimeException inspectionFailure) {
			if (failure != inspectionFailure) failure.addSuppressed(inspectionFailure);
		}
		// A throwing move may already have changed its target. Never overwrite
		// an unknown outcome with stale live YAML or a predecessor backup.
		replacementUncertain = true;
	}

	private void prepareSnapshotPublication() {
		requireFinalPublicationSettled();
		if (replacementUncertain)
			throw new IllegalStateException("ServerData replacement outcome is unknown; restart required before saving");
		if (!publicationSettlementPending && !replacementCleanupPending) return;
		Path target = getdFile().toPath().toAbsolutePath();
		try { forceDirectory(target.getParent()); }
		catch (IOException failure) { throw new UncheckedIOException("Failed to settle ServerData replacement", failure); }
		publicationSettlementPending = false;
		if (replacementCleanupPending) {
			clearReplacementState(replacementMarkerPath(target), backupPath(target));
			replacementCleanupPending = false;
		}
	}

	private void requireFinalPublicationSettled() {
		if (finalPublicationIndeterminate)
			throw new IllegalStateException("Final ServerData publication is unconfirmed; restart required before reuse");
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
				clearReplacementState(marker, backup);
				return;
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("Failed to inspect interrupted replacement of " + target.getFileName(), failure);
		}
		if (!Files.exists(backup)) {
			throw new IllegalStateException("Cannot recover interrupted replacement of " + target.getFileName());
		}
		restoreBackup(backup, target);
		clearReplacementState(marker, backup);
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

	private void clearReplacementState(Path marker, Path backup) {
		try {
			// The target is settled before cleanup. Remove recovery admission
			// first; an orphan backup is safe, a marker without its backup is not.
			Files.deleteIfExists(marker);
			forceDirectory(marker.getParent());
			Files.deleteIfExists(backup);
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

	private void withNativePublicationOrdering(Runnable operation) {
		try {
			Class<?> owner = getClass().getMethod("saveData").getDeclaringClass();
			if (owner == ServerData.class || owner == YMLFile.class) {
				synchronized (this) { requireFinalPublicationSettled(); operation.run(); }
			} else {
				// Existing adapters retain their own nonblocking mutation/publication model.
				operation.run();
			}
		} catch (NoSuchMethodException impossible) {
			throw new IllegalStateException("ServerData publisher unavailable", impossible);
		}
	}

	private void mutateOrdinaryData(java.util.function.Consumer<org.bukkit.configuration.file.FileConfiguration> mutation) {
		withNativePublicationOrdering(() -> { mutation.accept(getData()); saveData(); });
	}

	/** Reload must not replace the live YAML identity during checked native publication. */
	@Override
	public void reloadData() {
		withNativePublicationOrdering(super::reloadData);
	}

	/** Bulk updates share native publication ordering and retain their no-save semantics. */
	@Override
	public void setData(org.bukkit.configuration.file.FileConfiguration data) {
		withNativePublicationOrdering(() -> super.setData(data));
	}

	/**
	 * Keeps the inherited SimpleAPI mutators inside the native publication
	 * monitor. Their default implementations mutate the live configuration
	 * before entering {@link #saveData()}, which allows a final publication to
	 * observe and later overwrite an uncommitted ordinary change.
	 */
	@Override
	public void setValue(String path, Object value) {
		mutateOrdinaryData(data -> data.set(path, value));
	}

	/** See {@link #setValue(String, Object)} for the publication ordering rule. */
	@Override
	public void createSection(String path) {
		mutateOrdinaryData(data -> data.createSection(path));
	}

	public void setData(String path, Object value) {
		mutateOrdinaryData(data -> data.set(path, value));
	}

	public void setIgnoreTime(boolean value) {
		mutateOrdinaryData(data -> data.set("IgnoreTime", value));
	}

	public void setLastUpdated() {
		mutateOrdinaryData(data -> data.set("LastUpdated", System.currentTimeMillis()));
	}

	/**
	 * Sets the plugin version.
	 *
	 * @param plugin the new plugin version
	 */
	public void setPluginVersion(Plugin plugin) {
		mutateOrdinaryData(data -> data.set("PluginVersions." + plugin.getName(), plugin.getDescription().getVersion()));
	}

	/**
	 * Sets the prev day.
	 *
	 * @param day the new prev day
	 */
	public void setPrevDay(int day) {
		mutateOrdinaryData(data -> data.set("PrevDay", day));
	}

	/**
	 * Sets the prev month.
	 *
	 * @param month the new prev month
	 */
	public void setPrevMonth(String month) {
		mutateOrdinaryData(data -> data.set("Month", month));
	}

	/**
	 * Sets the prev week day.
	 *
	 * @param week the new prev week day
	 */
	public void setPrevWeekDay(int week) {
		mutateOrdinaryData(data -> data.set("PrevWeek", week));
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
			prepareSnapshotPublication();
			return new TimeChangeTransitionState(type, existingId, existingPeriod,
					getData().getString(path + ".Marker", markerValue), true);
		}

		String id = type.name() + ":" + periodKey;
		Object previousId = getData().get(path + ".Id");
		Object previousPeriod = getData().get(path + ".Period");
		Object previousMarker = getData().get(path + ".Marker");
		Object previousPending = getData().get(path + ".Pending");
		long previousPublication = visiblePublication;
		try {
			getData().set(path + ".Id", id);
			getData().set(path + ".Period", periodKey);
			getData().set(path + ".Marker", markerValue);
			getData().set(path + ".Pending", true);
			saveTimeChangeTransitionData();
		} catch (RuntimeException | Error failure) {
			if (visiblePublication == previousPublication) {
				getData().set(path + ".Id", previousId);
				getData().set(path + ".Period", previousPeriod);
				getData().set(path + ".Marker", previousMarker);
				getData().set(path + ".Pending", previousPending);
			}
			throw failure;
		}
		return new TimeChangeTransitionState(type, id, periodKey, markerValue, true);
	}

	/**
	 * Returns an unfinished transition even when the current clock has moved on.
	 * This observes known visible state without confirming durable publication;
	 * checked transition admission must settle it before event dispatch.
	 */
	public synchronized TimeChangeTransitionState getPendingTimeChangeTransition(TimeType type) {
		String path = transitionPath(type);
		if (!getData().getBoolean(path + ".Pending", false)) return null;
		if (replacementUncertain)
			throw new IllegalStateException("ServerData replacement outcome is unknown; restart required before processing a pending transition");
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
		long previousPublication = visiblePublication;
		try {
			switch (transition.type()) {
			case DAY, WEEK -> getData().set(markerPath, Integer.parseInt(transition.markerValue()));
			case MONTH -> getData().set(markerPath, transition.markerValue());
			}
			getData().set(pendingPath, false);
			saveTimeChangeTransitionData();
		} catch (RuntimeException | Error failure) {
			if (visiblePublication == previousPublication) {
				getData().set(markerPath, previousMarker);
				getData().set(pendingPath, true);
			}
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
