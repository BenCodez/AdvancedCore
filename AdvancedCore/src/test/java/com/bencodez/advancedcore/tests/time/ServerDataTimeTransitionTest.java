package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
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
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		Server server = mock(Server.class);
		when(plugin.getDataFolder()).thenReturn(dataFolder);
		when(plugin.getServer()).thenReturn(server);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("ServerDataTimeTransitionTest"));
		when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
		ServerData data = new ServerData(plugin);
		data.setup();
		data.getData().set("TimeTransitions.DAY.Pending", true);
		data.getData().set("TimeTransitions.DAY.Id", "DAY:2026-09-21");
		data.getData().set("TimeTransitions.DAY.Period", "2026-09-21");
		data.getData().set("TimeTransitions.DAY.Marker", "invalid");

		assertNull(data.getPendingTimeChangeTransition(TimeType.DAY));
		assertFalse(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
	}
}
