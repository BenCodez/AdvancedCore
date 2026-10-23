package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

/** Exercises native publication against an already serialized ordinary snapshot. */
class ServerDataPublicationOrderingTest {
    @TempDir File dataFolder;

    @Test void ordinaryCallbackPublishesLiveProjectionBeforeBlockedNativeDiskIo() throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            BarrierServerData data = new BarrierServerData(plugin);
            data.install();
            FileConfiguration live = data.getData();
            live.set("VoteParty.Total", 7);
            var display = new java.util.concurrent.atomic.AtomicInteger(7);
            var finished = new CompletableFuture<Void>();
            Thread writer = new Thread(() -> {
                try {
                    data.mutateOrdinaryData(yaml -> {
                        assertSame(live, yaml);
                        assertTrue(Thread.holdsLock(data.publicationMonitor()));
                        yaml.set("VoteParty.Total", 17);
                        display.set(yaml.getInt("VoteParty.Total"));
                    });
                    finished.complete(null);
                } catch (Throwable failure) { finished.completeExceptionally(failure); }
            }, "ordinary-live-projection");
            try {
                writer.start();
                assertTrue(data.ordinaryWriteStarted.await(2, TimeUnit.SECONDS));
                assertFalse(finished.isDone(), "disk I/O is still held");
                assertEquals(17, display.get(), "display must not wait for disk I/O");
                assertSame(live, data.getData());
            } finally {
                data.releaseOrdinaryWrite.countDown();
                writer.join(2000);
                if (writer.isAlive()) writer.interrupt();
            }
            assertFalse(writer.isAlive());
            finished.get(2, TimeUnit.SECONDS);
            assertEquals(17, YamlConfiguration.loadConfiguration(data.getdFile()).getInt("VoteParty.Total"));
        }
    }

    @Test void ordinaryCallbackFailurePreservesLiveIdentityWithoutSavingOrClaimingRollback() throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            LegacySavingOwner data = legacySavingOwner();
            FileConfiguration live = data.getData();
            var failure = new IllegalArgumentException("synthetic projection failure");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> data.mutateOrdinaryData(yaml -> {
                assertSame(live, yaml);
                yaml.set("VoteParty.Total", 17);
                throw failure;
            })));
            assertEquals(0, data.saves);
            assertSame(live, data.getData());
            assertEquals(17, live.getInt("VoteParty.Total"));
            assertFalse(data.getdFile().exists());
        }
    }

    @Test void ordinaryCallbackRetainsTheCustomOwnersSaveFailure() throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            LegacySavingOwner data = legacySavingOwner();
            data.saveFailure = new IllegalStateException("synthetic owner failure");
            var display = new java.util.concurrent.atomic.AtomicInteger();
            assertSame(data.saveFailure, assertThrows(IllegalStateException.class, () -> data.mutateOrdinaryData(yaml -> {
                yaml.set("VoteParty.Total", 17);
                display.set(yaml.getInt("VoteParty.Total"));
            })));
            assertEquals(1, data.saves);
            assertEquals(17, display.get());
            assertSame(data.getData(), data.savedData);
        }
    }

    @Test void heartbeatCannotOverwriteAcknowledgedFinalPublication() throws Exception {
        verifyPublicationOrder(true);
    }

    @Test void directNativeSaveCannotOverwriteAcknowledgedFinalPublication() throws Exception {
        verifyPublicationOrder(false);
    }

    @Test void failedDirectoryForceReconcilesCommittedFieldsWithoutOverwritingConcurrentLiveUpdates()
            throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            FailingDirectoryForceServerData data = new FailingDirectoryForceServerData(plugin);
            data.setData(new YamlConfiguration());
            data.getData().set("TimedPermissions.example", java.util.List.of("previous"));
            data.getData().set("Unrelated.Value", "previous");
            FileConfiguration live = data.getData();
            var result = new CompletableFuture<Void>();
            Thread writer = new Thread(() -> data.mutateFinalData(yaml -> {
                yaml.set("FullInventory.committed.Time", 456L);
                yaml.set("TimedPermissions.example", java.util.List.of("candidate"));
                yaml.set("Unrelated.Value", "candidate");
            }).whenComplete((ignored, failure) -> {
                if (failure == null) result.complete(null); else result.completeExceptionally(failure);
            }), "failing-final-server-data-publication");
            try {
                writer.start();
                assertTrue(data.forceStarted.await(2, TimeUnit.SECONDS));
                live.set("TimedPermissions.example", java.util.List.of("concurrent"));
                live.set("Unrelated.Value", "concurrent");
            } finally {
                data.releaseForce.countDown();
                writer.join(2000);
                if (writer.isAlive()) writer.interrupt();
            }
            assertFalse(writer.isAlive());
            ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertInstanceOf(java.io.UncheckedIOException.class, failure.getCause());
            assertSame(data.forceFailure, failure.getCause().getCause());
            assertSame(live, data.getData());
            assertEquals(456L, live.getLong("FullInventory.committed.Time"));
            assertEquals(java.util.List.of("concurrent"), live.getStringList("TimedPermissions.example"));
            assertEquals("concurrent", live.getString("Unrelated.Value"));
            assertEquals("candidate", YamlConfiguration.loadConfiguration(data.getdFile()).getString("Unrelated.Value"));
            data.failing = false;
            assertQuarantinedAfterUnconfirmedFinalPublication(data);
            var restarted = YamlConfiguration.loadConfiguration(data.getdFile());
            assertEquals(456L, restarted.getLong("FullInventory.committed.Time"));
            assertEquals(java.util.List.of("candidate"), restarted.getStringList("TimedPermissions.example"));
            assertEquals("candidate", restarted.getString("Unrelated.Value"));
        }
    }

    @Test void failedDirectoryForceMergesNestedCommittedSiblingsAndPreservesCompetingChildren() throws Exception {
        verifyNestedFinalReconciliation(true);
    }

    @Test void successfulPublicationMergesNestedCommittedSiblingsAndPreservesCompetingChildren() throws Exception {
        verifyNestedFinalReconciliation(false);
    }

    @Test void successfulPublicationPreservesTypedMapKeysInsideUnchangedLists() throws Exception {
        verifyUnchangedListPayload(true);
    }

    @Test void failedDirectoryForcePreservesTypedMapKeysInsideUnchangedLists() throws Exception {
        verifyUnchangedListPayload(false);
    }

    private void verifyUnchangedListPayload(boolean successful) throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            Files.writeString(dataFolder.toPath().resolve("ServerData.yml"), """
                    Rows:
                      - 1: first
                        '1': second
                        Nested:
                          2: nested-integer
                          '2': nested-string
                          Values:
                            - 3: list-integer
                              '3': list-string
                            - - 4: deepest-integer
                                '4': deepest-string
                    Unrelated: previous
                    """);
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            FailingDirectoryForceServerData data = new FailingDirectoryForceServerData(plugin);
            data.setup();
            FileConfiguration live = data.getData();
            java.util.List<?> expectedRows = live.getList("Rows");
            assertPreservedRows(live, expectedRows);
            data.failAfterBarrier = !successful;
            data.releaseForce.countDown();
            var result = data.mutateFinalData(yaml -> yaml.set("Unrelated", "candidate")).toCompletableFuture();
            if (successful) result.get(2, TimeUnit.SECONDS);
            else {
                CompletionException failure = assertThrows(CompletionException.class, result::join);
                assertInstanceOf(java.io.UncheckedIOException.class, failure.getCause());
                assertSame(data.forceFailure, failure.getCause().getCause());
            }
            assertSame(live, data.getData());
            assertEquals("candidate", live.getString("Unrelated"));
            assertPreservedRows(live, expectedRows);
            var published = YamlConfiguration.loadConfiguration(data.getdFile());
            assertEquals("candidate", published.getString("Unrelated"));
            assertPreservedRows(published, expectedRows);
            data.failing = false;
            if (successful) { data.setLastUpdated(); data.reloadData(); }
            else assertQuarantinedAfterUnconfirmedFinalPublication(data);
            assertPreservedRows(data.getData(), expectedRows);
            assertPreservedRows(YamlConfiguration.loadConfiguration(data.getdFile()), expectedRows);
            assertEquals(!successful, result.isCompletedExceptionally());
        }
    }

    private static void assertPreservedRows(FileConfiguration yaml, java.util.List<?> expectedRows) {
        assertNotNull(expectedRows);
        assertEquals(expectedRows, yaml.getList("Rows"));
        Map<?, ?> row = (Map<?, ?>) yaml.getList("Rows").get(0);
        assertEquals("first", row.get(1));
        assertEquals("second", row.get("1"));
        Map<?, ?> nested = (Map<?, ?>) row.get("Nested");
        assertEquals("nested-integer", nested.get(2));
        assertEquals("nested-string", nested.get("2"));
        java.util.List<?> values = (java.util.List<?>) nested.get("Values");
        Map<?, ?> listMap = (Map<?, ?>) values.get(0);
        assertEquals("list-integer", listMap.get(3));
        assertEquals("list-string", listMap.get("3"));
        Map<?, ?> deepest = (Map<?, ?>) ((java.util.List<?>) values.get(1)).get(0);
        assertEquals("deepest-integer", deepest.get(4));
        assertEquals("deepest-string", deepest.get("4"));
    }

    private void verifyNestedFinalReconciliation(boolean failForce) throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            FailingDirectoryForceServerData data = new FailingDirectoryForceServerData(plugin);
            data.failAfterBarrier = failForce;
            data.setData(new YamlConfiguration());
            FileConfiguration live = data.getData();
            live.set("TimedPermissions.existing", java.util.List.of("previous"));
            live.set("Nested.branch.existing", "previous");
            live.set("Nested.branch.removed", "previous");
            live.set("Nested.branch.competingRemoval", "previous");
            live.set("Nested.branch.competingValue", "previous");
            live.set("Nested.removedTree.unchanged", "previous");
            live.set("Nested.removedTree.competing", "previous");
            live.set("Nested.scalarToSection", "previous");
            live.set("Nested.competingScalarToSection", "previous");
            live.set("Nested.sectionToScalar.child", "previous");
            live.set("Nested.competingSectionToScalar.child", "previous");
            live.set("Nested.concurrentlyRemoved.child", "previous");
            live.set("Nested.concurrentlyReplaced.child", "previous");
            live.set("RawMap", Map.of("existing", "previous", "removed", "previous",
                    "competing", "previous", "numeric", Map.of(1, "previous", 2, "previous")));
            var result = new CompletableFuture<Void>();
            Thread writer = new Thread(() -> data.mutateFinalData(yaml -> {
                yaml.set("TimedPermissions.committed", java.util.List.of("candidate"));
                yaml.set("Nested.branch.committed", "candidate");
                yaml.set("Nested.branch.removed", null);
                yaml.set("Nested.branch.competingRemoval", null);
                yaml.set("Nested.branch.competingValue", "candidate");
                yaml.set("Nested.removedTree", null);
                yaml.set("Nested.addedTree.committed", "candidate");
                yaml.set("Nested.addedTree.competing", "candidate");
                yaml.set("Nested.scalarToSection", null);
                yaml.set("Nested.scalarToSection.child", "candidate");
                yaml.set("Nested.competingScalarToSection", null);
                yaml.set("Nested.competingScalarToSection.child", "candidate");
                yaml.set("Nested.sectionToScalar", "candidate");
                yaml.set("Nested.competingSectionToScalar", "candidate");
                yaml.set("Nested.concurrentlyRemoved.committed", "candidate");
                yaml.set("Nested.concurrentlyReplaced.committed", "candidate");
                yaml.set("RawMap.committed", "candidate");
                yaml.set("RawMap.removed", null);
                yaml.set("RawMap.competing", "candidate");
                yaml.set("RawMap.numeric.1", "candidate");
            }).whenComplete((ignored, failure) -> {
                if (failure == null) result.complete(null); else result.completeExceptionally(failure);
            }), "nested-final-server-data-publication");
            try {
                writer.start();
                assertTrue(data.forceStarted.await(2, TimeUnit.SECONDS));
                live.set("TimedPermissions.existing", java.util.List.of("concurrent"));
                live.set("Nested.branch.existing", "concurrent");
                live.set("Nested.branch.competingRemoval", "concurrent");
                live.set("Nested.branch.competingValue", "concurrent");
                live.set("Nested.removedTree.competing", "concurrent");
                live.set("Nested.removedTree.added", "concurrent");
                live.set("Nested.addedTree.added", "concurrent");
                live.set("Nested.addedTree.competing", "concurrent");
                live.set("Nested.competingScalarToSection", "concurrent");
                live.set("Nested.competingSectionToScalar.child", "concurrent");
                live.set("Nested.concurrentlyRemoved", null);
                live.set("Nested.concurrentlyReplaced", "concurrent");
                live.set("RawMap", Map.of("existing", "concurrent", "removed", "previous",
                        "competing", "concurrent", "added", "concurrent",
                        "numeric", Map.of(1, "previous", 2, "concurrent", 3, "concurrent")));
            } finally {
                data.releaseForce.countDown();
                writer.join(2000);
                if (writer.isAlive()) writer.interrupt();
            }
            assertFalse(writer.isAlive());
            if (failForce) {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> result.get(2, TimeUnit.SECONDS));
                assertInstanceOf(java.io.UncheckedIOException.class, failure.getCause());
                assertSame(data.forceFailure, failure.getCause().getCause());
            } else result.get(2, TimeUnit.SECONDS);
            assertSame(live, data.getData());
            var published = YamlConfiguration.loadConfiguration(data.getdFile());
            assertEquals(java.util.List.of("candidate"), published.getStringList("TimedPermissions.committed"));
            assertEquals(java.util.List.of("previous"), published.getStringList("TimedPermissions.existing"));
            assertEquals("candidate", published.getString("Nested.branch.committed"));
            assertNestedFinalValues(live);
            data.failing = false;
            if (failForce) assertQuarantinedAfterUnconfirmedFinalPublication(data);
            else {
                data.setLastUpdated();
                assertNestedFinalValues(YamlConfiguration.loadConfiguration(data.getdFile()));
            }
            assertEquals(failForce, result.isCompletedExceptionally());
        }
    }

    private static void assertQuarantinedAfterUnconfirmedFinalPublication(FailingDirectoryForceServerData data)
            throws Exception {
        byte[] visible = Files.readAllBytes(data.getdFile().toPath());
        assertEquals(2, data.failedForces, "only one settlement retry is allowed");
        assertThrows(IllegalStateException.class, data::setLastUpdated);
        assertThrows(IllegalStateException.class, data::saveData);
        assertThrows(IllegalStateException.class, data::saveDataChecked);
        assertThrows(IllegalStateException.class, data::reloadData);
        assertThrows(IllegalStateException.class, data::setup);
        var callbackCalled = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> data.mutateOrdinaryData(yaml -> callbackCalled.set(true)));
        assertFalse(callbackCalled.get(), "an unconfirmed final publication fences the callback itself");
        assertThrows(CompletionException.class,
                () -> data.mutateFinalData(yaml -> yaml.set("UnacknowledgedSuccessor", true)).toCompletableFuture().join());
        assertFalse(data.getData().contains("UnacknowledgedSuccessor"));
        assertArrayEquals(visible, Files.readAllBytes(data.getdFile().toPath()),
                "later operations cannot silently make an unacknowledged final candidate durable");
    }

    private static void assertNestedFinalValues(FileConfiguration yaml) {
        assertEquals(java.util.List.of("candidate"), yaml.getStringList("TimedPermissions.committed"));
        assertEquals(java.util.List.of("concurrent"), yaml.getStringList("TimedPermissions.existing"));
        assertEquals("candidate", yaml.getString("Nested.branch.committed"));
        assertEquals("concurrent", yaml.getString("Nested.branch.existing"));
        assertFalse(yaml.contains("Nested.branch.removed"));
        assertEquals("concurrent", yaml.getString("Nested.branch.competingRemoval"));
        assertEquals("concurrent", yaml.getString("Nested.branch.competingValue"));
        assertFalse(yaml.contains("Nested.removedTree.unchanged"));
        assertEquals("concurrent", yaml.getString("Nested.removedTree.competing"));
        assertEquals("concurrent", yaml.getString("Nested.removedTree.added"));
        assertEquals("candidate", yaml.getString("Nested.addedTree.committed"));
        assertEquals("concurrent", yaml.getString("Nested.addedTree.competing"));
        assertEquals("concurrent", yaml.getString("Nested.addedTree.added"));
        assertEquals("candidate", yaml.getString("Nested.scalarToSection.child"));
        assertEquals("concurrent", yaml.getString("Nested.competingScalarToSection"));
        assertEquals("candidate", yaml.getString("Nested.sectionToScalar"));
        assertEquals("concurrent", yaml.getString("Nested.competingSectionToScalar.child"));
        assertFalse(yaml.contains("Nested.concurrentlyRemoved"));
        assertEquals("concurrent", yaml.getString("Nested.concurrentlyReplaced"));
        assertEquals("candidate", yaml.getString("RawMap.committed"));
        assertEquals("concurrent", yaml.getString("RawMap.existing"));
        assertEquals("concurrent", yaml.getString("RawMap.competing"));
        assertEquals("concurrent", yaml.getString("RawMap.added"));
        assertFalse(yaml.contains("RawMap.removed"));
        assertEquals("candidate", yaml.getString("RawMap.numeric.1"));
        assertEquals("concurrent", yaml.getString("RawMap.numeric.2"));
        assertEquals("concurrent", yaml.getString("RawMap.numeric.3"));
    }

    @Test void legacyFinalMutationReachesLiveOwnerAndSavedYamlExactlyOnceWithoutClaimingDurability() {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            LegacySavingOwner data = legacySavingOwner();
            FileConfiguration live = data.getData();
            var mutations = new java.util.concurrent.atomic.AtomicInteger();
            var failure = assertThrows(CompletionException.class, () -> data.mutateFinalData(yaml -> {
                mutations.incrementAndGet();
                assertSame(live, yaml, "a legacy save override publishes its existing live YAML");
                yaml.set("FullInventory.example.Items.0", "stored-item");
                yaml.set("TimedPermissions.example", java.util.List.of("example.use%line%12345"));
                yaml.set("LastUpdated", 2468L);
            }).toCompletableFuture().join());

            assertInstanceOf(UnsupportedOperationException.class, failure.getCause(),
                    "returning from a void save does not confirm durable publication");
            assertEquals(1, mutations.get());
            assertEquals(1, data.saves);
            assertSame(live, data.getData());
            assertSame(live, data.savedData);
            assertEquals(2468L, live.getLong("LastUpdated"));
            YamlConfiguration saved = YamlConfiguration.loadConfiguration(data.getdFile());
            assertEquals("stored-item", saved.getString("FullInventory.example.Items.0"));
            assertEquals(java.util.List.of("example.use%line%12345"), saved.getStringList("TimedPermissions.example"));
            assertEquals(2468L, saved.getLong("LastUpdated"));
        }
    }

    @Test void legacyFinalMutationFailureIsObservableWithoutInvokingSave() {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            LegacySavingOwner data = legacySavingOwner();
            FileConfiguration live = data.getData();
            var mutations = new java.util.concurrent.atomic.AtomicInteger();
            var mutationFailure = new IllegalStateException("legacy mutation failed");
            var failure = assertThrows(CompletionException.class, () -> data.mutateFinalData(yaml -> {
                mutations.incrementAndGet();
                yaml.set("LastUpdated", 2468L);
                throw mutationFailure;
            }).toCompletableFuture().join());

            assertSame(mutationFailure, failure.getCause());
            assertEquals(1, mutations.get());
            assertEquals(0, data.saves, "a failed legacy mutation must not proceed to save");
            assertSame(live, data.getData());
            assertEquals(2468L, live.getLong("LastUpdated"), "legacy mutation retains its existing live-YAML semantics");
            assertFalse(data.getdFile().exists());
        }
    }

    @Test void legacyFinalSaveFailureIsObservableAfterExactlyOneMutationAndOneSave() {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            LegacySavingOwner data = legacySavingOwner();
            FileConfiguration live = data.getData();
            var mutations = new java.util.concurrent.atomic.AtomicInteger();
            data.saveFailure = new IllegalStateException("legacy save failed");
            var failure = assertThrows(CompletionException.class, () -> data.mutateFinalData(yaml -> {
                mutations.incrementAndGet();
                yaml.set("LastUpdated", 2468L);
            }).toCompletableFuture().join());

            assertSame(data.saveFailure, failure.getCause());
            assertEquals(1, mutations.get());
            assertEquals(1, data.saves);
            assertSame(live, data.getData());
            assertSame(live, data.savedData);
            assertEquals(2468L, data.savedData.getLong("LastUpdated"));
            assertFalse(data.getdFile().exists());
        }
    }


	@Test void inheritedSetValueCannotMutateLiveYamlDuringNativePublication() throws Exception {
		verifyInheritedMutatorIsPublicationOrdered(data -> data.setValue("Inherited.Value", "new"),
				"Inherited.Value");
	}

    @Test void ordinaryCallbackCannotMutateLiveYamlDuringNativeFinalPublication() throws Exception {
        verifyInheritedMutatorIsPublicationOrdered(data -> data.mutateOrdinaryData(yaml -> {
            assertTrue(Thread.holdsLock(data.publicationMonitor()));
            yaml.set("Ordinary.Value", "new");
        }), "Ordinary.Value");
    }

	@Test void inheritedCreateSectionCannotMutateLiveYamlDuringNativePublication() throws Exception {
		verifyInheritedMutatorIsPublicationOrdered(data -> data.createSection("Inherited.Section"),
				"Inherited.Section");
	}

    @Test void inheritedBulkUpdateCannotMutateLiveYamlDuringNativePublicationOrImplicitlySave() throws Exception {
        YamlConfiguration updates = new YamlConfiguration();
        updates.set("Bulk.Value", "new");
        BarrierServerData data = verifyInheritedMutatorIsPublicationOrdered(owner -> owner.setData(updates),
                "Bulk.Value");
        assertFalse(YamlConfiguration.loadConfiguration(data.getdFile()).contains("Bulk.Value"),
                "the inherited bulk update must preserve its no-automatic-save contract");
        data.saveData();
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(data.getdFile());
        assertEquals("new", saved.getString("Bulk.Value"));
        assertEquals(2L, saved.getLong("Inherited.Final"));
    }

    @Test void inheritedReloadWaitsForFinalOverflowPublicationAndLaterOrdinarySaveKeepsAcknowledgedItems()
            throws Exception {
        ConfigurationSerialization.registerClass(StoredItem.class, "ACPublicationOrderingOverflow");
        FullInventoryHandler inventoryHandler = null;
        var releaseTimer = new CountDownLatch(1);
        var persistence = Executors.newSingleThreadExecutor(AdvancedCoreRuntime.daemonThreads("reload-publication-test"));
        BarrierServerData data = null;
        Thread reload = null;
        try (var schedulerConstruction = mockConstruction(BukkitScheduler.class);
                var bukkit = mockStatic(Bukkit.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            Server server = mock(Server.class);
            AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            when(plugin.getOptions()).thenReturn(options);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(plugin.isEnabled()).thenReturn(true);
            data = new BarrierServerData(plugin);
            data.setup();
            when(plugin.getServerDataFile()).thenReturn(data);
            inventoryHandler = new FullInventoryHandler(plugin);
            var timerEntered = new CountDownLatch(1);
            inventoryHandler.getTimer().execute(() -> {
                timerEntered.countDown();
                try { releaseTimer.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(timerEntered.await(2, TimeUnit.SECONDS));

            UUID uuid = UUID.randomUUID();
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            ItemStack item = new StoredItem();
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.isOnline()).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.addItem(item)).thenReturn(new HashMap<>(Map.of(0, item)));
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            inventoryHandler.getLastMessageTime().put(uuid, System.currentTimeMillis());
            var deliveryTask = new AtomicReference<Runnable>();
            doAnswer(call -> { deliveryTask.set(call.getArgument(1)); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
            CompletableFuture<Void> delivery = inventoryHandler.giveItemAsync(player, item).toCompletableFuture();
            deliveryTask.get().run();
            assertFalse(delivery.isDone(), "reserved overflow is not acknowledged before publication");

            FileConfiguration before = data.getData();
            data.blockFinalPublication = true;
            CompletableFuture<Void> finalSave = inventoryHandler.shutdownAsync(persistence).toCompletableFuture();
            assertTrue(data.finalPublicationStarted.await(2, TimeUnit.SECONDS));
            var reloadStarted = new CountDownLatch(1);
            var reloaded = new CompletableFuture<Void>();
            BarrierServerData owner = data;
            reload = new Thread(() -> {
                reloadStarted.countDown();
                try { owner.reloadData(); reloaded.complete(null); }
                catch (Throwable failure) { reloaded.completeExceptionally(failure); }
            }, "inherited-server-data-reload");
            reload.start();
            assertTrue(reloadStarted.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (reload.getState() != Thread.State.BLOCKED && !reloaded.isDone()
                    && System.nanoTime() < deadline) Thread.yield();
            assertEquals(Thread.State.BLOCKED, reload.getState(), "reload must wait for native publication");
            assertSame(before, data.getData(), "pending publication cannot lose its live configuration identity");
            assertFalse(delivery.isDone());
            assertFalse(finalSave.isDone());

            data.releaseFinalPublication.countDown();
            finalSave.get(2, TimeUnit.SECONDS);
            delivery.get(2, TimeUnit.SECONDS);
            reloaded.get(2, TimeUnit.SECONDS);
            assertNotSame(before, data.getData(), "reload still replaces the configuration after publication");
            String path = "FullInventory." + uuid + ".Items.0";
            assertEquals(item, data.getData().getItemStack(path));
            data.setLastUpdated();
            YamlConfiguration saved = YamlConfiguration.loadConfiguration(data.getdFile());
            assertEquals(item, saved.getItemStack(path), "later whole-file save must retain acknowledged overflow");
            assertTrue(saved.getLong("LastUpdated") > 0L);
            verify(inventory, times(1)).addItem(item);
        } finally {
            if (data != null) data.releaseFinalPublication.countDown();
            releaseTimer.countDown();
            if (inventoryHandler != null) inventoryHandler.getTimer().shutdownNow();
            persistence.shutdownNow();
            if (reload != null) {
                reload.join(2000);
                if (reload.isAlive()) reload.interrupt();
            }
            try {
                assertTrue(persistence.awaitTermination(2, TimeUnit.SECONDS));
                if (inventoryHandler != null)
                    assertTrue(inventoryHandler.getTimer().awaitTermination(2, TimeUnit.SECONDS));
                if (reload != null) assertFalse(reload.isAlive());
            } finally { ConfigurationSerialization.unregisterClass(StoredItem.class); }
        }
    }

    @Test void customPublicationOwnersKeepNonblockingInheritedReloadAndBulkUpdate() throws Exception {
        try (var scheduler = mockConstruction(BukkitScheduler.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            Files.writeString(dataFolder.toPath().resolve("ServerData.yml"), "Stored: from-disk\n");
            CustomOwner data = new CustomOwner(plugin);
            data.setData(new YamlConfiguration());
            var ownerHeld = new CountDownLatch(1);
            var releaseOwner = new CountDownLatch(1);
            var updated = new CompletableFuture<Void>();
            Thread holder = new Thread(() -> {
                synchronized (data) {
                    ownerHeld.countDown();
                    try { releaseOwner.await(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }, "custom-publication-owner");
            Thread writer = new Thread(() -> {
                try {
                    YamlConfiguration bulk = new YamlConfiguration();
                    bulk.set("Bulk.Value", "new");
                    data.setData(bulk);
                    assertEquals("new", data.getData().getString("Bulk.Value"));
                    data.reloadData();
                    updated.complete(null);
                } catch (Throwable failure) { updated.completeExceptionally(failure); }
            }, "custom-inherited-configuration-update");
            try {
                holder.start();
                assertTrue(ownerHeld.await(2, TimeUnit.SECONDS));
                writer.start();
                updated.get(2, TimeUnit.SECONDS);
                assertEquals("from-disk", data.getData().getString("Stored"));
                assertEquals(0, data.saved.get(), "bulk updates and reloads must not add saves");
            } finally {
                releaseOwner.countDown();
                holder.join(2000); writer.join(2000);
                if (holder.isAlive()) holder.interrupt();
                if (writer.isAlive()) writer.interrupt();
            }
            assertFalse(holder.isAlive() || writer.isAlive());
        }
    }

    @Test void customPublicationOwnersKeepNonblockingOrdinaryMutators() throws Exception {
        try (var scheduler = mockConstruction(com.bencodez.simpleapi.scheduler.BukkitScheduler.class)) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            CustomOwner data = new CustomOwner(plugin);
            data.setData(new YamlConfiguration());
            var ownerHeld = new CountDownLatch(1);
            var releaseOwner = new CountDownLatch(1);
            var written = new CompletableFuture<Void>();
            Thread holder = new Thread(() -> {
                synchronized (data) {
                    ownerHeld.countDown();
                    try { releaseOwner.await(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }, "custom-publication-owner");
            Thread writer = new Thread(() -> {
                try {
                    data.setData("Example", "value"); data.setIgnoreTime(true); data.setLastUpdated();
                    data.setPrevDay(3); data.setPrevWeekDay(4); data.setPrevMonth("example-period");
                    var source = mock(org.bukkit.plugin.Plugin.class);
                    when(source.getName()).thenReturn("ExamplePlugin");
                    when(source.getDescription()).thenReturn(new org.bukkit.plugin.PluginDescriptionFile(
                            "ExamplePlugin", "1.0", "example.Main"));
                    data.setPluginVersion(source);
                    data.mutateOrdinaryData(yaml -> yaml.set("Custom.Callback", "value"));
                    written.complete(null);
                } catch (Throwable failure) { written.completeExceptionally(failure); }
            }, "custom-ordinary-mutation");
            try {
                holder.start(); assertTrue(ownerHeld.await(2, TimeUnit.SECONDS)); writer.start();
                written.get(2, TimeUnit.SECONDS);
                assertEquals(8, data.saved.get());
                assertEquals("value", data.getData().getString("Custom.Callback"));
                assertEquals(3, data.getData().getInt("PrevDay"));
            } finally {
                releaseOwner.countDown(); holder.join(2000); writer.join(2000);
                if (holder.isAlive()) holder.interrupt(); if (writer.isAlive()) writer.interrupt();
            }
            assertFalse(holder.isAlive() || writer.isAlive());
        }
    }

    private static final class CustomOwner extends ServerData {
        final java.util.concurrent.atomic.AtomicInteger saved = new java.util.concurrent.atomic.AtomicInteger();
        CustomOwner(AdvancedCorePlugin plugin) { super(plugin); }
        @Override public void saveData() { saved.incrementAndGet(); }
    }

    private LegacySavingOwner legacySavingOwner() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        LegacySavingOwner data = new LegacySavingOwner(plugin);
        data.setData(new YamlConfiguration());
        return data;
    }

    private static final class LegacySavingOwner extends ServerData {
        int saves;
        FileConfiguration savedData;
        RuntimeException saveFailure;
        LegacySavingOwner(AdvancedCorePlugin plugin) { super(plugin); }
        @Override public void saveData() {
            saves++;
            savedData = getData();
            if (saveFailure != null) throw saveFailure;
            try { savedData.save(getdFile()); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }

    private void verifyPublicationOrder(boolean heartbeat) throws Exception {
        try (var scheduler = mockConstruction(com.bencodez.simpleapi.scheduler.BukkitScheduler.class)) {
            verifyNativePublicationOrder(heartbeat);
        }
	    }

	private BarrierServerData verifyInheritedMutatorIsPublicationOrdered(java.util.function.Consumer<BarrierServerData> mutator,
			String path) throws Exception {
		try (var scheduler = mockConstruction(com.bencodez.simpleapi.scheduler.BukkitScheduler.class)) {
			AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
			when(plugin.getDataFolder()).thenReturn(dataFolder);
			BarrierServerData data = new BarrierServerData(plugin);
			data.install(); data.blockFinalPublication = true;
			var finalResult = new CompletableFuture<Void>();
			Thread finalWriter = new Thread(() -> data.mutateFinalData(yaml -> yaml.set("Inherited.Final", 2L))
					.whenComplete((ignored, failure) -> {
						if (failure == null) finalResult.complete(null); else finalResult.completeExceptionally(failure);
					}), "final-server-data-save");
			Thread inheritedWriter = new Thread(() -> mutator.accept(data), "inherited-server-data-mutator");
			try {
				finalWriter.start();
				assertTrue(data.finalPublicationStarted.await(2, TimeUnit.SECONDS));
				inheritedWriter.start();
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
				while (inheritedWriter.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
					Thread.yield();
				assertEquals(Thread.State.BLOCKED, inheritedWriter.getState());
				assertFalse(data.getData().contains(path), "inherited mutator changed live YAML before publication");
			} finally {
				data.releaseFinalPublication.countDown();
				data.releaseOrdinaryWrite.countDown();
				finalWriter.join(2000); inheritedWriter.join(2000);
				if (finalWriter.isAlive()) finalWriter.interrupt();
				if (inheritedWriter.isAlive()) inheritedWriter.interrupt();
			}
			assertFalse(finalWriter.isAlive() || inheritedWriter.isAlive());
			finalResult.get(2, TimeUnit.SECONDS);
			assertTrue(data.getData().contains(path));
			assertEquals(2L, data.getData().getLong("Inherited.Final"));
			assertEquals(2L, YamlConfiguration.loadConfiguration(data.getdFile()).getLong("Inherited.Final"));
			return data;
		}
	}
    private void verifyNativePublicationOrder(boolean heartbeat) throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        BarrierServerData data = new BarrierServerData(plugin);
        data.install(); data.getData().set("LastUpdated", 1L);
        var ordinaryResult = new CompletableFuture<Void>();
        var finalResult = new CompletableFuture<Void>();
        var finalAttempted = new CountDownLatch(1);
        Thread ordinary = new Thread(() -> {
            try {
                if (heartbeat) data.setLastUpdated(); else data.saveData();
                ordinaryResult.complete(null);
            } catch (Throwable failure) { ordinaryResult.completeExceptionally(failure); }
        }, "ordinary-server-data-save");
        Thread finalWriter = new Thread(() -> {
            finalAttempted.countDown();
            data.mutateFinalData(yaml -> yaml.set("LastUpdated", 2L)).whenComplete((ignored, failure) -> {
                if (failure == null) finalResult.complete(null); else finalResult.completeExceptionally(failure);
            });
        }, "final-server-data-save");
        try {
            ordinary.start();
            assertTrue(data.ordinaryWriteStarted.await(2, TimeUnit.SECONDS));
            finalWriter.start();
            assertTrue(finalAttempted.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (finalWriter.getState() != Thread.State.BLOCKED && !finalResult.isDone()
                    && System.nanoTime() < deadline) Thread.yield();
            assertFalse(finalResult.isDone(), "a final receipt cannot settle ahead of an older native publication");
            assertEquals(Thread.State.BLOCKED, finalWriter.getState());
        } finally {
            data.releaseOrdinaryWrite.countDown();
            ordinary.join(2000); finalWriter.join(2000);
            if (ordinary.isAlive()) ordinary.interrupt();
            if (finalWriter.isAlive()) finalWriter.interrupt();
        }
        assertFalse(ordinary.isAlive() || finalWriter.isAlive());
        ordinaryResult.get(2, TimeUnit.SECONDS); finalResult.get(2, TimeUnit.SECONDS);
        assertEquals(2L, YamlConfiguration.loadConfiguration(data.getdFile()).getLong("LastUpdated"));
    }

    @SerializableAs("ACPublicationOrderingOverflow")
    public static final class StoredItem extends ItemStack {
        public StoredItem() { super(Material.STONE, 1); }
        @Override public Map<String, Object> serialize() { return Map.of("amount", 1); }
        public static StoredItem deserialize(Map<String, Object> values) { return new StoredItem(); }
        @Override public boolean equals(Object value) { return value instanceof StoredItem; }
        @Override public int hashCode() { return 1; }
    }

    private static final class BarrierServerData extends ServerData {
        final CountDownLatch ordinaryWriteStarted = new CountDownLatch(1);
        final CountDownLatch releaseOrdinaryWrite = new CountDownLatch(1);
        final CountDownLatch finalPublicationStarted = new CountDownLatch(1);
        final CountDownLatch releaseFinalPublication = new CountDownLatch(1);
        BarrierServerData(AdvancedCorePlugin plugin) { super(plugin); }

        boolean blockFinalPublication;
        @Override
        protected void moveAtomically(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
            if (!blockFinalPublication) { super.moveAtomically(source, target); return; }
            finalPublicationStarted.countDown();
            try {
                if (!releaseFinalPublication.await(5, TimeUnit.SECONDS))
                    throw new java.io.IOException("synthetic final publication timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
            }
            super.moveAtomically(source, target);
        }
        void install() {
            setData(new YamlConfiguration() {
                @Override public void save(File file) throws java.io.IOException {
                    String captured = saveToString();
                    ordinaryWriteStarted.countDown();
                    try {
                        if (!releaseOrdinaryWrite.await(5, TimeUnit.SECONDS))
                            throw new java.io.IOException("synthetic ordinary publication timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
                    }
                    Files.writeString(file.toPath(), captured, StandardCharsets.UTF_8);
                }
            });
        }
    }

    private static final class FailingDirectoryForceServerData extends ServerData {
        final CountDownLatch forceStarted = new CountDownLatch(1);
        final CountDownLatch releaseForce = new CountDownLatch(1);
        final java.io.IOException forceFailure = new java.io.IOException("synthetic directory force failure");
        boolean failing = true;
        boolean failAfterBarrier = true;
        int failedForces;
        FailingDirectoryForceServerData(AdvancedCorePlugin plugin) { super(plugin); }
        @Override protected void forceDirectory(java.nio.file.Path directory) throws java.io.IOException {
            if (!failing) { super.forceDirectory(directory); return; }
            forceStarted.countDown();
            try {
                if (!releaseForce.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("force barrier timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
            }
            if (failAfterBarrier) { failedForces++; throw forceFailure; }
            super.forceDirectory(directory);
        }
    }
}
