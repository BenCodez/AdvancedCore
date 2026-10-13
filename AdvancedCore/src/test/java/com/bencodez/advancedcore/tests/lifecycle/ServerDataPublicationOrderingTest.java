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

    @Test void heartbeatCannotOverwriteAcknowledgedFinalPublication() throws Exception {
        verifyPublicationOrder(true);
    }

    @Test void directNativeSaveCannotOverwriteAcknowledgedFinalPublication() throws Exception {
        verifyPublicationOrder(false);
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
                    data.setPluginVersion(source); written.complete(null);
                } catch (Throwable failure) { written.completeExceptionally(failure); }
            }, "custom-ordinary-mutation");
            try {
                holder.start(); assertTrue(ownerHeld.await(2, TimeUnit.SECONDS)); writer.start();
                written.get(2, TimeUnit.SECONDS);
                assertEquals(7, data.saved.get());
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
}
