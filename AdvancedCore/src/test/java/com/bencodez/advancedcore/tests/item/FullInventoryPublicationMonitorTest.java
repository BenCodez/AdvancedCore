package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.data.ServerData;

class FullInventoryPublicationMonitorTest {
    @TempDir Path directory;

    @Test void ordinaryNativeOwnerCannotPublishAnIncompleteOverflowSection() throws Exception {
        verifyPublication(false, false);
    }

    @Test void ordinaryFacadeOwnerCannotPublishAnIncompleteOverflowSection() throws Exception {
        verifyPublication(true, false);
    }

    @Test void finalNativeOwnerCannotPublishAnIncompleteOverflowSection() throws Exception {
        verifyPublication(false, true);
    }

    @Test void finalFacadeOwnerCannotPublishAnIncompleteOverflowSection() throws Exception {
        verifyPublication(true, true);
    }

    private void verifyPublication(boolean wrapped, boolean finalPublication) throws Exception {
        ConfigurationSerialization.registerClass(FullInventoryFinalPublicationTest.PersistedItem.class,
                "ACFinalOverflowTestItem");
        var plugin = mock(AdvancedCorePlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var owner = new ServerData(plugin);
        var yaml = new GatedYaml();
        owner.setData(yaml);
        owner.saveDataChecked();
        ServerData exposed = wrapped ? new Facade(plugin, owner) : owner;
        when(plugin.getServerDataFile()).thenReturn(exposed);
        var handler = new FullInventoryHandler(plugin);
        UUID player = UUID.randomUUID();
        handler.add(player, new FullInventoryFinalPublicationTest.PersistedItem());
        assertTrue(handler.saveDurably());
        assertNotNull(YamlConfiguration.loadConfiguration(owner.getdFile())
                .getItemStack("FullInventory." + player + ".Items.0"),
                "the regression starts with real durable recovery data");
        var writer = Executors.newSingleThreadExecutor();
        var checkpoint = Executors.newSingleThreadExecutor();
        var writerThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var checkpointThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        yaml.armed.set(true);
        Future<Boolean> inventory = writer.submit(() -> {
            writerThread.set(Thread.currentThread());
            return handler.saveDurably();
        });
        try {
            assertTrue(yaml.cleared.await(3, TimeUnit.SECONDS));
            Future<?> other = checkpoint.submit(() -> {
                checkpointThread.set(Thread.currentThread());
                if (finalPublication) owner.mutateFinalData(data -> data.set("VoteParty.Votes", 7))
                        .toCompletableFuture().join();
                else owner.setValue("VoteParty.Votes", 7);
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean blockedOnActualOwner = false;
            while (!other.isDone() && System.nanoTime() < deadline) {
                Thread thread = checkpointThread.get();
                var info = thread == null ? null : ManagementFactory.getThreadMXBean().getThreadInfo(thread.threadId());
                if (info != null && info.getThreadState() == Thread.State.BLOCKED
                        && info.getLockOwnerId() == writerThread.get().threadId()
                        && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(owner)) {
                    blockedOnActualOwner = true;
                    break;
                }
                Thread.yield();
            }
            if (other.isDone()) {
                other.get(3, TimeUnit.SECONDS);
                assertTrue(YamlConfiguration.loadConfiguration(owner.getdFile()).contains("FullInventory"),
                        "another real whole-file publication persisted the temporary section removal");
            }
            assertTrue(blockedOnActualOwner, "publication must wait for the actual YAML owner's transaction");
            yaml.release.countDown();
            assertTrue(inventory.get(3, TimeUnit.SECONDS));
            other.get(3, TimeUnit.SECONDS);
            var stored = YamlConfiguration.loadConfiguration(owner.getdFile());
            assertNotNull(stored.getItemStack("FullInventory." + player + ".Items.0"));
            assertEquals(7, stored.getInt("VoteParty.Votes"));
        } finally {
            yaml.release.countDown();
            writer.shutdownNow();
            checkpoint.shutdownNow();
            handler.getTimer().shutdownNow();
            assertTrue(writer.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(checkpoint.awaitTermination(5, TimeUnit.SECONDS));
            ConfigurationSerialization.unregisterClass(FullInventoryFinalPublicationTest.PersistedItem.class);
        }
    }

    private static final class GatedYaml extends YamlConfiguration {
        final CountDownLatch cleared = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean armed = new AtomicBoolean();

        @Override public java.util.Set<String> getKeys(boolean deep) {
            if (!deep && armed.compareAndSet(true, false)) {
                cleared.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            }
            return super.getKeys(deep);
        }
    }

    private static final class Facade extends ServerData {
        private final ServerData owner;
        Facade(AdvancedCorePlugin plugin, ServerData owner) { super(plugin); this.owner = owner; }
        @Override public Object publicationMonitor() { return owner.publicationMonitor(); }
        @Override public FileConfiguration getData() { return owner.getData(); }
        @Override public void saveData() { owner.saveData(); }
        @Override public CompletionStage<Void> mutateFinalData(java.util.function.Consumer<FileConfiguration> mutation) {
            return owner.mutateFinalData(mutation);
        }
    }
}
