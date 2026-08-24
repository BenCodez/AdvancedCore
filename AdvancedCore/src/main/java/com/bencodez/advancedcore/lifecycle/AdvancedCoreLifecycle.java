package com.bencodez.advancedcore.lifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

/**
 * Owns AdvancedCore runtime executor creation and best-effort shutdown sequencing.
 * Public plugin getters remain the compatibility surface; this class only
 * centralizes lifecycle mechanics that were previously embedded in the plugin.
 */
public final class AdvancedCoreLifecycle {

	private final AdvancedCorePlugin plugin;

	public AdvancedCoreLifecycle(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	public static RuntimeExecutors createRuntimeExecutors(AdvancedCorePlugin plugin) {
		return new RuntimeExecutors(new BukkitScheduler(plugin), Executors.newSingleThreadScheduledExecutor(),
				Executors.newSingleThreadScheduledExecutor(), Executors.newSingleThreadScheduledExecutor());
	}

	public void shutdown() {
		if (plugin == null) {
			return;
		}

		runCleanup("Javascript engine", () -> {
			if (plugin.getOptions() != null && plugin.getOptions().isJavascriptEngineEnabled()) {
				plugin.getLogger().info("Shutting down Javascript engine");
				JavascriptEngineHandler.getInstance().clearCachedEngine();
			}
		});

		runCleanup("MySQL", () -> {
			if (plugin.isLoadUserData() && plugin.getOptions() != null
					&& UserStorage.MYSQL.equals(plugin.getOptions().getStorageType()) && plugin.getMysql() != null) {
				plugin.getMysql().close();
			}
		});

		runCleanup("server data timestamp", () -> {
			if (plugin.getServerDataFile() != null) {
				plugin.getServerDataFile().setLastUpdated();
			}
		});

		ScheduledExecutorService timeTimer = null;
		TimeChecker timeChecker = plugin.getTimeChecker();
		if (timeChecker != null) {
			timeTimer = timeChecker.getTimer();
		}

		shutdown(plugin.getLoginTimer());
		shutdown(plugin.getTimer());
		shutdown(timeTimer);
		shutdown(plugin.getInventoryTimer());

		plugin.getLogger().info("Allowing background tasks to finish before shutdown");
		await(plugin.getLoginTimer(), 2, TimeUnit.SECONDS);
		await(plugin.getTimer(), 2, TimeUnit.SECONDS);
		await(timeTimer, 2, TimeUnit.SECONDS);
		await(plugin.getInventoryTimer(), 1, TimeUnit.SECONDS);

		runCleanup("reward handler", () -> {
			if (plugin.getRewardHandler() != null) {
				plugin.getRewardHandler().shutdown();
			}
		});

		shutdownNow(plugin.getLoginTimer());
		shutdownNow(plugin.getTimer());
		shutdownNow(timeTimer);
		shutdownNow(plugin.getInventoryTimer());

		runCleanup("plugin unload hook", plugin::onUnLoad);
		runCleanup("skull cache", () -> {
			if (plugin.getSkullCacheHandler() != null) {
				plugin.getSkullCacheHandler().close();
			}
		});
		runCleanup("full inventory handler", () -> {
			FullInventoryHandler handler = plugin.getFullInventoryHandler();
			if (handler != null) {
				handler.shutdown();
				handler.save();
			}
		});
		runCleanup("hologram handler", () -> {
			if (plugin.getHologramHandler() != null) {
				plugin.getHologramHandler().onShutDown();
			}
		});
		runCleanup("permission handler", () -> {
			if (plugin.getPermissionHandler() != null) {
				plugin.getPermissionHandler().shutDown();
			}
		});
		runCleanup("dialog service", () -> {
			if (plugin.getDialogService() != null) {
				plugin.getDialogService().unregister();
			}
		});
	}

	private void runCleanup(String component, Runnable cleanup) {
		try {
			cleanup.run();
		} catch (Throwable e) {
			plugin.getLogger().warning("Failed to shut down " + component + ": " + e.getMessage());
			plugin.debug(e);
		}
	}

	static void shutdown(ScheduledExecutorService executor) {
		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
		}
	}

	static void shutdownNow(ScheduledExecutorService executor) {
		if (executor != null && !executor.isTerminated()) {
			executor.shutdownNow();
		}
	}

	static void await(ScheduledExecutorService executor, long timeout, TimeUnit unit) {
		if (executor == null) {
			return;
		}
		try {
			executor.awaitTermination(timeout, unit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static final class RuntimeExecutors {
		private final BukkitScheduler bukkitScheduler;
		private final ScheduledExecutorService timer;
		private final ScheduledExecutorService loginTimer;
		private final ScheduledExecutorService inventoryTimer;

		RuntimeExecutors(BukkitScheduler bukkitScheduler, ScheduledExecutorService timer,
				ScheduledExecutorService loginTimer, ScheduledExecutorService inventoryTimer) {
			this.bukkitScheduler = bukkitScheduler;
			this.timer = timer;
			this.loginTimer = loginTimer;
			this.inventoryTimer = inventoryTimer;
		}

		public BukkitScheduler getBukkitScheduler() {
			return bukkitScheduler;
		}

		public ScheduledExecutorService getTimer() {
			return timer;
		}

		public ScheduledExecutorService getLoginTimer() {
			return loginTimer;
		}

		public ScheduledExecutorService getInventoryTimer() {
			return inventoryTimer;
		}
	}
}
