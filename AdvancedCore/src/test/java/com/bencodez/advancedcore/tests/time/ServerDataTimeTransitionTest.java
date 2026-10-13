package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.data.ServerData;

class ServerDataTimeTransitionTest {
	@TempDir
	File dataFolder;

	@Test
	void malformedNumericMarkerIsDiscardedInsteadOfRetriedForever() {
		TestServerData data = serverData();
		data.setup();
		data.getData().set("TimeTransitions.DAY.Pending", true);
		data.getData().set("TimeTransitions.DAY.Id", "DAY:2026-09-21");
		data.getData().set("TimeTransitions.DAY.Period", "2026-09-21");
		data.getData().set("TimeTransitions.DAY.Marker", "invalid");

		assertNull(data.getPendingTimeChangeTransition(TimeType.DAY));
		assertFalse(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
	}

	@Test
	void failedCompletionSaveRestoresPendingStateAndPreviousMarker() {
		FailingServerData data = new FailingServerData(plugin());
		data.setup();
		data.getData().set("PrevDay", 20);
		data.getData().set("TimeTransitions.DAY.Pending", true);
		data.getData().set("TimeTransitions.DAY.Id", "DAY:2026-09-21");
		data.getData().set("TimeTransitions.DAY.Period", "2026-09-21");
		data.getData().set("TimeTransitions.DAY.Marker", "21");
		data.failSaves = true;

		assertThrows(IllegalStateException.class, () -> data.completeTimeChangeTransition(
				new ServerData.TimeChangeTransitionState(TimeType.DAY, "DAY:2026-09-21", "2026-09-21", "21", true)));
		assertEquals(20, data.getData().getInt("PrevDay"));
		assertTrue(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
	}

	@Test
	void checkedShutdownSnapshotPersistsAndPropagatesActualReplacementFailure() throws Exception {
		TestServerData data = serverData();
		data.setup();
		data.getData().set("TimedPermissions.example", java.util.List.of("example.use%line%1234"));
		data.saveDataChecked();
		assertEquals(java.util.List.of("example.use%line%1234"),
				YamlConfiguration.loadConfiguration(data.getdFile()).getStringList("TimedPermissions.example"));
		File target = data.getdFile();
		assertTrue(target.delete());
		assertTrue(target.mkdir());
		Files.writeString(target.toPath().resolve("blocks-replacement"), "occupied");
		assertThrows(UncheckedIOException.class, data::saveDataChecked);
	}

	@Test
	void finalMutationUsesNativeCheckedPublicationAndReportsFailure() throws Exception {
		TestServerData data = serverData();
		data.setup();
		data.mutateFinalData(yaml -> yaml.set("LastUpdated", 1234L)).toCompletableFuture().join();
		assertEquals(1234L, YamlConfiguration.loadConfiguration(data.getdFile()).getLong("LastUpdated"));
		File target = data.getdFile();
		assertTrue(target.delete()); assertTrue(target.mkdir());
		Files.writeString(target.toPath().resolve("blocks-replacement"), "occupied");
		var failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> data.mutateFinalData(yaml -> yaml.set("LastUpdated", 5678L)).toCompletableFuture().join());
		assertTrue(failure.getCause() instanceof UncheckedIOException);
		assertEquals(1234L, data.getData().getLong("LastUpdated"));
	}

	@Test
	void legacyVoidSaveOwnerIsInvokedWithoutClaimingConfirmedPublication() {
		var owner = new LegacySaveOwner(plugin()); owner.setup();
		var failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> owner.mutateFinalData(yaml -> yaml.set("LastUpdated", 1234L)).toCompletableFuture().join());
		assertTrue(failure.getCause() instanceof UnsupportedOperationException);
		assertEquals(1, owner.saves);
		assertEquals(1234L, owner.getData().getLong("LastUpdated"), "legacy owners retain the established live mutation before their void save");
	}

	@Test
	void failedFinalMutationCannotLeakPartialChangesIntoTheNextWholeFileSave() {
		TestServerData data = serverData(); data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(yaml -> {
			yaml.set("FullInventory", null);
			yaml.set("FullInventory.unacknowledged.Time", 456L);
			throw new IllegalStateException("mutation failed after editing its candidate");
		}).toCompletableFuture().join());
		data.mutateFinalData(yaml -> yaml.set("LastUpdated", 789L)).toCompletableFuture().join();
		var restarted = YamlConfiguration.loadConfiguration(data.getdFile());
		assertEquals(123L, restarted.getLong("FullInventory.previous.Time"));
		assertFalse(restarted.contains("FullInventory.unacknowledged"));
		assertEquals(789L, restarted.getLong("LastUpdated"));
	}

	private static final class LegacySaveOwner extends ServerData {
		private int saves;
		private LegacySaveOwner(AdvancedCorePlugin plugin) { super(plugin); }
		@Override public void saveData() { saves++; }
	}

	@Test
	void atomicServerDataSavePropagatesReplacementFailure() throws Exception {
		TestServerData data = serverData();
		data.setup();
		File target = data.getdFile();
		assertTrue(target.delete());
		assertTrue(target.mkdir());
		Files.writeString(target.toPath().resolve("blocks-replacement"), "occupied");
		data.getData().set("PrevDay", 21);

		assertThrows(UncheckedIOException.class, data::saveTransitionState);
		assertTrue(target.isDirectory());
	}

	@Test
	void failedTransitionInitializationIsRolledBackAndRetried() {
		FailingServerData data = new FailingServerData(plugin());
		data.setup();
		data.failSaves = true;

		assertThrows(IllegalStateException.class,
				() -> data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21"));
		assertFalse(data.getData().getBoolean("TimeTransitions.DAY.Pending"));

		data.failSaves = false;
		ServerData.TimeChangeTransitionState transition =
				data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21");
		assertEquals("DAY:2026-09-21", transition.id());
		assertTrue(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
	}

	@Test
	void unsupportedAtomicMoveKeepsRecoverablePreviousSnapshot() throws Exception {
		UnsupportedAtomicMoveServerData data = new UnsupportedAtomicMoveServerData(plugin());
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveTransitionState();
		byte[] previousSnapshot = Files.readAllBytes(data.getdFile().toPath());
		data.getData().set("PrevDay", 21);
		data.saveTransitionState();

		Path target = data.getdFile().toPath();
		Path backup = target.resolveSibling(target.getFileName().toString() + ".backup");
		assertFalse(Files.exists(backup), "a committed replacement must not leave a stale rollback snapshot");
		assertEquals(21, YamlConfiguration.loadConfiguration(target.toFile()).getInt("PrevDay"));

		Files.write(backup, previousSnapshot);
		Files.writeString(target.resolveSibling(target.getFileName().toString() + ".replacement-pending"), "");
		Files.writeString(target, "incomplete: [");
		UnsupportedAtomicMoveServerData recovered = new UnsupportedAtomicMoveServerData(plugin());
		recovered.setup();
		assertEquals(20, recovered.getPrevDay());
		assertFalse(Files.exists(target.resolveSibling(target.getFileName().toString() + ".replacement-pending")));
	}

	@Test
	void orphanedBackupIsNotRestoredWithoutAReplacementMarker() throws Exception {
		UnsupportedAtomicMoveServerData data = new UnsupportedAtomicMoveServerData(plugin());
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveTransitionState();
		Path target = data.getdFile().toPath();
		Path backup = target.resolveSibling(target.getFileName().toString() + ".backup");
		Files.copy(target, backup);
		Files.delete(target);

		UnsupportedAtomicMoveServerData recovered = new UnsupportedAtomicMoveServerData(plugin());
		recovered.setup();

		assertEquals(-1, recovered.getPrevDay());
	}

	@Test
	void completedFallbackReplacementIsKeptWhenMarkerCleanupWasInterrupted() throws Exception {
		UnsupportedAtomicMoveServerData data = new UnsupportedAtomicMoveServerData(plugin());
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveTransitionState();
		data.getData().set("PrevDay", 21);
		data.saveTransitionState();

		Path target = data.getdFile().toPath();
		String targetHash = HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)));
		Path marker = target.resolveSibling(target.getFileName().toString() + ".replacement-pending");
		Files.writeString(marker, targetHash);

		UnsupportedAtomicMoveServerData recovered = new UnsupportedAtomicMoveServerData(plugin());
		recovered.setup();

		assertEquals(21, recovered.getPrevDay());
		assertFalse(Files.exists(marker));
	}

	@Test
	void atomicReplacementForcesContainingDirectory() {
		DirectoryTrackingServerData data = new DirectoryTrackingServerData(plugin(), false);
		data.setup();
		data.getData().set("PrevDay", 21);

		data.saveTransitionState();

		assertEquals(1, data.directoryForces);
	}

	@Test
	void fallbackReplacementForcesEveryPublishedDirectoryChange() {
		DirectoryTrackingServerData data = new DirectoryTrackingServerData(plugin(), true);
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveTransitionState();
		data.directoryForces = 0;
		data.getData().set("PrevDay", 21);

		data.saveTransitionState();

		assertEquals(5, data.directoryForces);
	}

	@Test
	void ordinaryServerDataSaveDoesNotForceTheContainingDirectory() {
		DirectoryTrackingServerData data = new DirectoryTrackingServerData(plugin(), false);
		data.setup();
		data.directoryForces = 0;

		data.setLastUpdated();

		assertEquals(0, data.directoryForces);
	}

	private TestServerData serverData() {
		return new TestServerData(plugin());
	}

	private AdvancedCorePlugin plugin() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		Server server = mock(Server.class);
		when(plugin.getDataFolder()).thenReturn(dataFolder);
		when(plugin.getServer()).thenReturn(server);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("ServerDataTimeTransitionTest"));
		when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
		return plugin;
	}

	private static final class FailingServerData extends ServerData {
		private boolean failSaves;

		private FailingServerData(AdvancedCorePlugin plugin) {
			super(plugin);
		}

		@Override protected void saveTimeChangeTransitionData() {
			if (failSaves) throw new IllegalStateException("simulated save failure");
			super.saveTimeChangeTransitionData();
		}
	}

	private static class TestServerData extends ServerData {
		private TestServerData(AdvancedCorePlugin plugin) {
			super(plugin);
		}

		private void saveTransitionState() {
			saveTimeChangeTransitionData();
		}
	}

	private static final class UnsupportedAtomicMoveServerData extends ServerData {
		private UnsupportedAtomicMoveServerData(AdvancedCorePlugin plugin) {
			super(plugin);
		}

		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test provider");
		}

		private void saveTransitionState() {
			saveTimeChangeTransitionData();
		}
	}

	private static final class DirectoryTrackingServerData extends ServerData {
		private final boolean useFallback;
		private int directoryForces;

		private DirectoryTrackingServerData(AdvancedCorePlugin plugin, boolean useFallback) {
			super(plugin);
			this.useFallback = useFallback;
		}

		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			if (useFallback) {
				throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test provider");
			}
			super.moveAtomically(source, target);
		}

		@Override protected void forceDirectory(Path directory) {
			directoryForces++;
		}

		private void saveTransitionState() {
			saveTimeChangeTransitionData();
		}
	}
}
