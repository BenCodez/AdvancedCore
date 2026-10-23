package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

	@Test
	void atomicVisibleFinalMutationSurvivesDirectoryForceFailureAndLaterSaves() throws Exception {
		verifyVisibleFinalMutationSurvivesFailure(false, 1);
	}

	@Test
	void fallbackVisibleFinalMutationSurvivesDirectoryForceFailureAndLaterSaves() throws Exception {
		verifyVisibleFinalMutationSurvivesFailure(true, 3);
	}

	@Test
	void fallbackVisibleFinalMutationSurvivesMarkerCleanupForceFailureAndLaterSaves() throws Exception {
		verifyVisibleFinalMutationSurvivesFailure(true, 4);
	}

	@Test
	void fallbackVisibleFinalMutationSurvivesBackupCleanupForceFailureAndLaterSaves() throws Exception {
		verifyVisibleFinalMutationSurvivesFailure(true, 5);
	}

	private void verifyVisibleFinalMutationSurvivesFailure(boolean fallback, int failingForce) throws Exception {
		PublicationFailureServerData data = new PublicationFailureServerData(plugin(), fallback);
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		var live = data.getData();
		data.directoryForces = 0;
		data.failingForce = failingForce;
		var result = data.mutateFinalData(yaml -> {
			yaml.set("FullInventory", null);
			yaml.set("FullInventory.committed.Time", 456L);
			yaml.set("FullInventory.committed.Items.0", "stored-item");
		}).toCompletableFuture();
		result.join();
		assertSame(live, data.getData());
		assertEquals(456L, live.getLong("FullInventory.committed.Time"));
		assertFalse(live.contains("FullInventory.previous"));
		assertEquals("stored-item", YamlConfiguration.loadConfiguration(data.getdFile())
				.getString("FullInventory.committed.Items.0"));

		data.failingForce = 0;
		data.setLastUpdated();
		data.setData("TimedPermissions.example", java.util.List.of("example.use%line%1234"));
		data.mutateFinalData(yaml -> yaml.set("IgnoreTime", true)).toCompletableFuture().join();
		assertFalse(result.isCompletedExceptionally(), "the original stage confirms its own settled publication");
		TestServerData restarted = serverData();
		restarted.setup();
		assertEquals(456L, restarted.getData().getLong("FullInventory.committed.Time"));
		assertEquals("stored-item", restarted.getData().getString("FullInventory.committed.Items.0"));
		assertFalse(restarted.getData().contains("FullInventory.previous"));
		assertTrue(restarted.getLastUpdated() > 0L);
		assertEquals(java.util.List.of("example.use%line%1234"),
				restarted.getData().getStringList("TimedPermissions.example"));
		assertTrue(restarted.isIgnoreTime());
		Path target = data.getdFile().toPath();
		assertFalse(Files.exists(target.resolveSibling("ServerData.yml.replacement-pending")));
		assertFalse(Files.exists(target.resolveSibling("ServerData.yml.backup")));
	}

	@Test
	void failedFallbackCleanupQuarantinesOwnerAfterOneSettlementRetry() throws Exception {
		PublicationFailureServerData data = new PublicationFailureServerData(plugin(), true);
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		data.directoryForces = 0;
		data.failFromForce = 3;
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(
				yaml -> yaml.set("FullInventory.committed.Time", 456L)).toCompletableFuture().join());
		byte[] visibleSnapshot = Files.readAllBytes(data.getdFile().toPath());
		assertEquals(4, data.directoryForces, "two preparation forces, failed target force and one retry");
		data.failFromForce = 0;
		assertThrows(IllegalStateException.class, data::setLastUpdated);
		assertThrows(IllegalStateException.class, data::saveData);
		assertThrows(IllegalStateException.class, data::saveDataChecked);
		assertThrows(IllegalStateException.class, data::reloadData);
		assertThrows(IllegalStateException.class, data::setup);
		assertArrayEquals(visibleSnapshot, Files.readAllBytes(data.getdFile().toPath()));
	}

	@Test
	void failedFallbackBeforeMoveDiscardsCandidateAndAllowsLaterSave() throws Exception {
		PublicationFailureServerData data = new PublicationFailureServerData(plugin(), true);
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		data.directoryForces = 0;
		data.failingForce = 2;
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(
				yaml -> yaml.set("FullInventory.unpublished.Time", 456L)).toCompletableFuture().join());
		assertFalse(data.getData().contains("FullInventory.unpublished"));
		data.failingForce = 0;
		data.setLastUpdated();
		TestServerData restarted = serverData();
		restarted.setup();
		assertEquals(123L, restarted.getData().getLong("FullInventory.previous.Time"));
		assertFalse(restarted.getData().contains("FullInventory.unpublished"));
		assertTrue(restarted.getLastUpdated() > 0L);
	}

	@Test
	void moveThatThrowsAfterReplacingSettlesAndAcknowledgesTheProvenCandidate() throws Exception {
		MoveFailureServerData data = new MoveFailureServerData(plugin());
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		data.moveFailure = 1;
		data.mutateFinalData(yaml -> yaml.set("FullInventory.committed.Time", 456L)).toCompletableFuture().join();
		assertEquals(456L, data.getData().getLong("FullInventory.committed.Time"));
		data.moveFailure = 0;
		data.setLastUpdated();
		assertEquals(456L, YamlConfiguration.loadConfiguration(data.getdFile()).getLong("FullInventory.committed.Time"));
	}

	@Test
	void unchangedCandidateCannotAcknowledgeMoveFailureBeforeReplacement() throws Exception {
		verifyUnchangedCandidateMoveFailure(2);
	}

	@Test
	void unchangedCandidateCannotInferReplacementFromEqualHashes() throws Exception {
		verifyUnchangedCandidateMoveFailure(1);
	}

	private void verifyUnchangedCandidateMoveFailure(int moveFailure) throws Exception {
		MoveFailureServerData data = new MoveFailureServerData(plugin());
		data.setup();
		data.getData().set("Stored", 123L);
		// An ordinary visible file is not evidence that its contents were forced.
		Files.writeString(data.getdFile().toPath(), data.getData().saveToString());
		byte[] original = Files.readAllBytes(data.getdFile().toPath());
		data.moveFailure = moveFailure;
		assertThrows(java.util.concurrent.CompletionException.class,
				() -> data.mutateFinalData(yaml -> { }).toCompletableFuture().join());
		assertArrayEquals(original, Files.readAllBytes(data.getdFile().toPath()));
		// A subsequent confirmed replacement still succeeds; no no-op failure
		// can manufacture a visibility transition or poison ordinary recovery.
		data.moveFailure = 0;
		data.mutateFinalData(yaml -> { }).toCompletableFuture().join();
		assertEquals(123L, YamlConfiguration.loadConfiguration(data.getdFile()).getLong("Stored"));
	}

	@Test
	void moveThatFailsBeforeReplacingDiscardsCandidateAndAllowsLaterSave() throws Exception {
		MoveFailureServerData data = new MoveFailureServerData(plugin());
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		data.moveFailure = 2;
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(
				yaml -> yaml.set("FullInventory.unpublished.Time", 456L)).toCompletableFuture().join());
		assertFalse(data.getData().contains("FullInventory.unpublished"));
		data.moveFailure = 0;
		data.setLastUpdated();
		var saved = YamlConfiguration.loadConfiguration(data.getdFile());
		assertEquals(123L, saved.getLong("FullInventory.previous.Time"));
		assertFalse(saved.contains("FullInventory.unpublished"));
	}

	@Test
	void ambiguousMoveOutcomeCannotBeOverwrittenByLaterWholeFileSaves() throws Exception {
		MoveFailureServerData data = new MoveFailureServerData(plugin());
		data.setup();
		data.getData().set("FullInventory.previous.Time", 123L);
		data.saveDataChecked();
		data.moveFailure = 3;
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(
				yaml -> yaml.set("FullInventory.unpublished.Time", 456L)).toCompletableFuture().join());
		byte[] unknownSnapshot = Files.readAllBytes(data.getdFile().toPath());
		data.moveFailure = 0;
		var live = data.getData();
		String liveBefore = live.saveToString();
		var callbackCalled = new java.util.concurrent.atomic.AtomicBoolean();
		assertThrows(IllegalStateException.class, () -> data.mutateOrdinaryData(yaml -> {
			callbackCalled.set(true);
			yaml.set("Ordinary.Unconfirmed", 27);
		}));
		assertFalse(callbackCalled.get(), "unknown replacement must reject before invoking a live callback");
		assertThrows(IllegalStateException.class, data::setLastUpdated);
		assertThrows(IllegalStateException.class, () -> data.setData("Ordinary.Value", 27));
		assertThrows(IllegalStateException.class, () -> data.setValue("Ordinary.Value", 27));
		assertThrows(IllegalStateException.class, () -> data.createSection("Ordinary.Section"));
		var bulk = new YamlConfiguration();
		bulk.set("Ordinary.Bulk", 27);
		assertThrows(IllegalStateException.class, () -> data.setData(bulk));
		assertThrows(IllegalStateException.class, data::reloadData);
		assertThrows(IllegalStateException.class, data::setup);
		assertThrows(IllegalStateException.class, data::saveData);
		assertThrows(IllegalStateException.class, data::saveDataChecked);
		assertThrows(java.util.concurrent.CompletionException.class, () -> data.mutateFinalData(
				yaml -> yaml.set("IgnoreTime", true)).toCompletableFuture().join());
		assertArrayEquals(unknownSnapshot, Files.readAllBytes(data.getdFile().toPath()));
		assertSame(live, data.getData());
		assertEquals(liveBefore, live.saveToString(), "rejected operations must not change live YAML");
	}

	@Test
	void visibleTransitionInitializationIsRetainedAfterDirectoryForceFailure() throws Exception {
		PublicationFailureServerData data = new PublicationFailureServerData(plugin(), false);
		data.setup();
		data.failingForce = 1;
		assertThrows(UncheckedIOException.class,
				() -> data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21"));
		assertTrue(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
		data.failEveryForce = true;
		int forcesBeforeRead = data.directoryForces;
		assertEquals("DAY:2026-09-21", data.getPendingTimeChangeTransition(TimeType.DAY).id());
		assertEquals(forcesBeforeRead, data.directoryForces, "pending observation remains memory-only after a save failure");
		assertThrows(UncheckedIOException.class,
				() -> data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21"));
		data.failEveryForce = false;
		data.failingForce = 0;
		assertEquals("DAY:2026-09-21", data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21").id());
		assertEquals("DAY:2026-09-21", data.getPendingTimeChangeTransition(TimeType.DAY).id());
		data.setLastUpdated();
		TestServerData restarted = serverData();
		restarted.setup();
		assertEquals("DAY:2026-09-21", restarted.getPendingTimeChangeTransition(TimeType.DAY).id());
	}

	@Test
	void visibleTransitionCompletionIsRetainedAfterDirectoryForceFailure() throws Exception {
		PublicationFailureServerData data = new PublicationFailureServerData(plugin(), false);
		data.setup();
		data.getData().set("PrevDay", 20);
		var transition = data.beginTimeChangeTransition(TimeType.DAY, "2026-09-21", "21");
		data.directoryForces = 0;
		data.failingForce = 1;
		assertThrows(UncheckedIOException.class, () -> data.completeTimeChangeTransition(transition));
		assertEquals(21, data.getPrevDay());
		assertFalse(data.getData().getBoolean("TimeTransitions.DAY.Pending"));
		data.failingForce = 0;
		data.setLastUpdated();
		TestServerData restarted = serverData();
		restarted.setup();
		assertEquals(21, restarted.getPrevDay());
		assertNull(restarted.getPendingTimeChangeTransition(TimeType.DAY));
	}

	@Test
	void calendarWorkerRetriesVisiblePendingPublicationBeforeDispatchWithoutAnUnrelatedSave() throws Exception {
		try (CalendarRetryFixture fixture = new CalendarRetryFixture()) {
			fixture.data.failingForce = 1;
			assertThrows(UncheckedIOException.class,
					() -> fixture.data.beginTimeChangeTransition(TimeType.DAY, "2025-01-02", "2"));
			fixture.data.failEveryForce = true;
			fixture.requestCheck();
			assertTrue(fixture.dayIds.isEmpty(), "failed settlement cannot dispatch the retained pending record");
			assertFalse(fixture.checker.isActiveProcessing(), "failed admission must release its drain reservation");
			assertEquals("DAY:2025-01-02", fixture.data.getPendingTimeChangeTransition(TimeType.DAY).id());
			fixture.data.failEveryForce = false;
			fixture.data.failingForce = 0;
			fixture.requestCheck();
			assertEquals(java.util.List.of("DAY:2025-01-02"), fixture.dayIds);
			assertEquals(java.util.List.of(3), fixture.dayPublicationForces,
					"the worker settles the failed force before dispatching the original identity");
			assertNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
			assertEquals(2, fixture.data.getPrevDay());
		}
	}

	@Test
	void calendarWorkerSettlesFailedVisibleCompletionBeforeDispatchingTheNextPeriod() throws Exception {
		try (CalendarRetryFixture fixture = new CalendarRetryFixture()) {
			fixture.data.failingForce = 2;
			fixture.requestCheck();
			assertEquals(java.util.List.of("DAY:2025-01-02"), fixture.dayIds);
			assertEquals(2, fixture.data.getPrevDay());
			assertNull(fixture.data.getPendingTimeChangeTransition(TimeType.DAY));
			assertFalse(YamlConfiguration.loadConfiguration(fixture.data.getdFile())
					.getBoolean("TimeTransitions.DAY.Pending"));
			fixture.now.set(java.time.Instant.parse("2025-01-03T12:00:00Z"));
			fixture.data.failEveryForce = true;
			fixture.requestCheck();
			assertEquals(java.util.List.of("DAY:2025-01-02"), fixture.dayIds,
					"the next period cannot dispatch while the prior completion remains unsettled");
			assertEquals("DAY:2025-01-02", fixture.data.getData().getString("TimeTransitions.DAY.Id"));
			fixture.data.failEveryForce = false;
			fixture.data.failingForce = 0;
			fixture.requestCheck();
			assertEquals(java.util.List.of("DAY:2025-01-02", "DAY:2025-01-03"), fixture.dayIds,
					"recovery must not replay the already completed period");
			assertEquals(java.util.List.of(1, 5), fixture.dayPublicationForces);
			assertEquals(3, YamlConfiguration.loadConfiguration(fixture.data.getdFile()).getInt("PrevDay"));
		}
	}

	private final class CalendarRetryFixture implements AutoCloseable {
		private final java.util.concurrent.atomic.AtomicReference<Thread> calendarThread =
				new java.util.concurrent.atomic.AtomicReference<>();
		private final java.util.concurrent.atomic.AtomicReference<java.time.Instant> now =
				new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2025-01-02T12:00:00Z"));
		private final java.util.concurrent.ScheduledExecutorService calendar =
				java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
					Thread worker = com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime
							.daemonThreads("calendar-publication-retry-test").newThread(task);
					calendarThread.set(worker);
					return worker;
				});
		private final PublicationFailureServerData data;
		private final com.bencodez.advancedcore.api.time.TimeChecker checker;
		private final java.util.List<String> dayIds = new java.util.ArrayList<>();
		private final java.util.List<Integer> dayPublicationForces = new java.util.ArrayList<>();

		private CalendarRetryFixture() {
			AdvancedCorePlugin plugin = plugin();
			var options = mock(com.bencodez.advancedcore.AdvancedCoreConfigOptions.class);
			when(options.getTimeZone()).thenReturn("UTC");
			when(options.isTimeChangeFailSafeBypass()).thenReturn(true);
			when(plugin.getOptions()).thenReturn(options);
			when(plugin.isEnabled()).thenReturn(true);
			var eventManager = mock(org.bukkit.plugin.PluginManager.class);
			when(plugin.getServer().getPluginManager()).thenReturn(eventManager);
			data = new PublicationFailureServerData(plugin, false);
			data.setup();
			when(plugin.getServerDataFile()).thenReturn(data);
			data.getData().set("Month", "JANUARY");
			data.getData().set("PrevWeek", com.bencodez.advancedcore.api.time.TimeCalculation.weekNumber(
					java.time.LocalDateTime.of(2025, 1, 2, 12, 0), 0, java.util.Locale.getDefault()));
			data.getData().set("PrevDay", 1);
			data.saveDataChecked();
			data.directoryForces = 0;
			org.mockito.Mockito.doAnswer(call -> {
				org.bukkit.event.Event event = call.getArgument(0);
				assertTrue(event.isAsynchronous());
				assertSame(calendarThread.get(), Thread.currentThread());
				if (event instanceof com.bencodez.advancedcore.api.time.events.DayChangeEvent day) {
					dayIds.add(day.getTransition().getId());
					dayPublicationForces.add(data.directoryForces);
				}
				return null;
			}).when(eventManager).callEvent(org.mockito.ArgumentMatchers.any(org.bukkit.event.Event.class));
			checker = new com.bencodez.advancedcore.api.time.TimeChecker(plugin, new java.time.Clock() {
				@Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
				@Override public java.time.Clock withZone(java.time.ZoneId zone) { return java.time.Clock.fixed(now.get(), zone); }
				@Override public java.time.Instant instant() { return now.get(); }
			});
			checker.setTimer(calendar);
		}

		private void requestCheck() throws Exception {
			checker.requestUpdate();
			calendar.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
		}

		@Override public void close() throws Exception {
			try { checker.beginShutdown().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS); }
			finally {
				calendar.shutdownNow();
				assertTrue(calendar.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
			}
		}
	}

	private static final class LegacySaveOwner extends ServerData {
		private int saves;
		private LegacySaveOwner(AdvancedCorePlugin plugin) { super(plugin); }
		@Override public void saveData() { saves++; }
	}

	private static final class PublicationFailureServerData extends ServerData {
		private final boolean fallback;
		private final IOException forceFailure = new IOException("synthetic directory force failure");
		private int directoryForces;
		private int failingForce;
		private boolean failEveryForce;
		private int failFromForce;

		private PublicationFailureServerData(AdvancedCorePlugin plugin, boolean fallback) {
			super(plugin);
			this.fallback = fallback;
		}

		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			if (fallback) throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test provider");
			super.moveAtomically(source, target);
		}

		@Override protected void forceDirectory(Path directory) throws IOException {
			if (++directoryForces == failingForce || failEveryForce
					|| failFromForce > 0 && directoryForces >= failFromForce) throw forceFailure;
			super.forceDirectory(directory);
		}
	}

	private static final class MoveFailureServerData extends ServerData {
		private int moveFailure;
		private MoveFailureServerData(AdvancedCorePlugin plugin) { super(plugin); }
		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			if (moveFailure == 0 || moveFailure == 1) super.moveAtomically(source, target);
			if (moveFailure == 3) Files.writeString(target, "Unknown: outcome\n");
			if (moveFailure != 0) throw new IOException("synthetic move failure");
		}
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
