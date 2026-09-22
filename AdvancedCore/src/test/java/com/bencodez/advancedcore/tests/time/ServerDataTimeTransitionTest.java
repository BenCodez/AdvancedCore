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
		ServerData data = serverData();
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
	void atomicServerDataSavePropagatesReplacementFailure() throws Exception {
		ServerData data = serverData();
		data.setup();
		File target = data.getdFile();
		assertTrue(target.delete());
		assertTrue(target.mkdir());
		Files.writeString(target.toPath().resolve("blocks-replacement"), "occupied");
		data.getData().set("PrevDay", 21);

		assertThrows(UncheckedIOException.class, data::saveData);
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
		data.saveData();
		data.getData().set("PrevDay", 21);
		data.saveData();

		Path target = data.getdFile().toPath();
		Path backup = target.resolveSibling(target.getFileName().toString() + ".backup");
		assertTrue(Files.isRegularFile(backup));
		assertEquals(20, YamlConfiguration.loadConfiguration(backup.toFile()).getInt("PrevDay"));
		assertEquals(21, YamlConfiguration.loadConfiguration(target.toFile()).getInt("PrevDay"));

		Files.writeString(target.resolveSibling(target.getFileName().toString() + ".replacement-pending"), "");
		Files.writeString(target, "incomplete: [");
		UnsupportedAtomicMoveServerData recovered = new UnsupportedAtomicMoveServerData(plugin());
		recovered.setup();
		assertEquals(20, recovered.getPrevDay());
		assertFalse(Files.exists(target.resolveSibling(target.getFileName().toString() + ".replacement-pending")));
	}

	@Test
	void completedFallbackReplacementIsKeptWhenMarkerCleanupWasInterrupted() throws Exception {
		UnsupportedAtomicMoveServerData data = new UnsupportedAtomicMoveServerData(plugin());
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveData();
		data.getData().set("PrevDay", 21);
		data.saveData();

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

		data.saveData();

		assertEquals(1, data.directoryForces);
	}

	@Test
	void fallbackReplacementForcesEveryPublishedDirectoryChange() {
		DirectoryTrackingServerData data = new DirectoryTrackingServerData(plugin(), true);
		data.setup();
		data.getData().set("PrevDay", 20);
		data.saveData();
		data.directoryForces = 0;
		data.getData().set("PrevDay", 21);

		data.saveData();

		assertEquals(4, data.directoryForces);
	}

	private ServerData serverData() {
		return new ServerData(plugin());
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

		@Override public void saveData() {
			if (failSaves) throw new IllegalStateException("simulated save failure");
			super.saveData();
		}
	}

	private static final class UnsupportedAtomicMoveServerData extends ServerData {
		private UnsupportedAtomicMoveServerData(AdvancedCorePlugin plugin) {
			super(plugin);
		}

		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test provider");
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
	}
}
