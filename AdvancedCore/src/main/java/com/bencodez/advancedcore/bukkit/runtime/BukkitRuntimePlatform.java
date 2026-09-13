package com.bencodez.advancedcore.bukkit.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;

/** Bukkit services for the shared executor lifecycle; no duplicate owners are created. */
public final class BukkitRuntimePlatform implements RuntimePlatform {
    private final AdvancedCorePlugin plugin;

    public BukkitRuntimePlatform(AdvancedCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override public ScheduledExecutorService getTimer() { return plugin.getTimer(); }
    @Override public ScheduledExecutorService getLoginTimer() { return plugin.getLoginTimer(); }
    @Override public ScheduledExecutorService getInventoryTimer() { return plugin.getInventoryTimer(); }
    @Override public ScheduledExecutorService getTimeTimer() {
        TimeChecker checker = plugin.getTimeChecker();
        return checker == null ? null : checker.getTimer();
    }

    @Override public List<Cleanup> beforeExecutorShutdown() {
        return List.of(
                new Cleanup("full inventory handler", () -> {
                    FullInventoryHandler handler = plugin.getFullInventoryHandler();
                    if (handler != null) handler.shutdown();
                }),
                new Cleanup("Javascript engine", () -> {
                    if (plugin.getOptions() != null && plugin.getOptions().isJavascriptEngineEnabled()) {
                        plugin.getLogger().info("Shutting down Javascript engine");
                        JavascriptEngineHandler.getInstance().clearCachedEngine();
                    }
                }),
                new Cleanup("server data timestamp", () -> {
                    if (plugin.getServerDataFile() != null) plugin.getServerDataFile().setLastUpdated();
                }));
    }

    @Override public List<Cleanup> afterExecutorGrace() {
        return List.of(new Cleanup("reward handler", () -> {
            if (plugin.getRewardHandler() != null) plugin.getRewardHandler().shutdown();
        }));
    }

    @Override public List<Cleanup> afterExecutorShutdown() {
        return List.of(
                new Cleanup("MySQL", () -> {
                    if (plugin.isLoadUserData() && plugin.getOptions() != null
                            && UserStorage.MYSQL.equals(plugin.getOptions().getStorageType()) && plugin.getMysql() != null) {
                        ScheduledExecutorService timer = plugin.getTimer();
                        if (timer != null && !timer.isTerminated()) {
                            plugin.getLogger().warning("Leaving MySQL open because reward checkpoint tasks did not terminate");
                            return;
                        }
                        plugin.getMysql().close();
                    }
                }),
                new Cleanup("plugin unload hook", plugin::onUnLoad),
                new Cleanup("skull cache", () -> {
                    if (plugin.getSkullCacheHandler() != null) plugin.getSkullCacheHandler().close();
                }),
                new Cleanup("hologram handler", () -> {
                    if (plugin.getHologramHandler() != null) plugin.getHologramHandler().onShutDown();
                }),
                new Cleanup("permission handler", () -> {
                    if (plugin.getPermissionHandler() != null) plugin.getPermissionHandler().shutDown();
                }),
                new Cleanup("dialog service", () -> {
                    if (plugin.getDialogService() != null) plugin.getDialogService().unregister();
                }));
    }

    @Override public void info(String message) { plugin.getLogger().info(message); }
    @Override public void cleanupFailed(String component, Throwable failure) {
        plugin.getLogger().warning("Failed to shut down " + component + ": " + failure.getMessage());
        plugin.debug(failure);
    }
}
