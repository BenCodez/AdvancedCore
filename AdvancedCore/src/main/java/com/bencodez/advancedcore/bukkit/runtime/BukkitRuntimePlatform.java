package com.bencodez.advancedcore.bukkit.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.Bukkit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;

/** Bukkit services for the shared executor lifecycle; no duplicate owners are created. */
public final class BukkitRuntimePlatform implements RuntimePlatform {
    private final AdvancedCorePlugin plugin;
    private volatile CompletionStage<Void> userStorageRetirement = CompletableFuture.completedFuture(null);

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
                new Cleanup("user storage", this::closeUserStorageAfterSharedRetirement),
                new Cleanup("server data timestamp", () -> {
                    if (plugin.getServerDataFile() != null) plugin.getServerDataFile().setLastUpdated();
                }));
    }

	@Override public CompletionStage<Void> beforeExecutorShutdownCompletion() { return userStorageRetirement; }
	@Override public boolean canBlockForPreExecutorShutdown() {
		return Bukkit.getServer() == null || !Bukkit.isPrimaryThread();
	}

    @Override public List<Cleanup> afterExecutorGrace() {
        return List.of(new Cleanup("reward handler", () -> {
            if (plugin.getRewardHandler() != null) plugin.getRewardHandler().shutdown();
        }));
    }

    @Override public List<Cleanup> afterExecutorShutdown() {
        return List.of(
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

	private void closeUserStorageAfterSharedRetirement() {
        if (!plugin.isLoadUserData()) {
            userStorageRetirement = CompletableFuture.completedFuture(null);
            return;
        }
		UserManager users = plugin.getLoadedUserManager();
		var mysql = plugin.getMysql();
		boolean ownsMysql;
		if (users != null && users.getDataManager().hasSharedSqlBackend()) {
			ownsMysql = users.getDataManager().usesSharedSqlStorage(UserStorage.MYSQL);
		} else {
			ownsMysql = plugin.getOptions() != null
					&& UserStorage.MYSQL.equals(plugin.getOptions().getStorageType());
		}
		Runnable closeMysql = () -> {
			try { if (ownsMysql && mysql != null) mysql.close(); }
			finally { plugin.closePendingNativeUserStorageOwners(); }
		};
        if (users == null) {
            closeMysql.run();
            userStorageRetirement = CompletableFuture.completedFuture(null);
            return;
        }
        CompletionStage<Void> retirement = users.getDataManager().closeSharedRuntimeAsyncCompletion(closeMysql);
        if (retirement == null) {
            closeMysql.run();
            userStorageRetirement = CompletableFuture.completedFuture(null);
        } else userStorageRetirement = retirement;
    }
}
