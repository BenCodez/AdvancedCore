package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import org.bukkit.Server;
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
}
