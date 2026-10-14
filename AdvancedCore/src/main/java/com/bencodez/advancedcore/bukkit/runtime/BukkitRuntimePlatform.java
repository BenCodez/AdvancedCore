package com.bencodez.advancedcore.bukkit.runtime;

import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.data.ServerData;

/** Bukkit services for the shared executor lifecycle; no duplicate owners are created. */
public final class BukkitRuntimePlatform implements RuntimePlatform {
    private final AdvancedCorePlugin plugin;
    private final RewardHandler rewardHandler;
    private volatile LongSupplier shutdownRemainingNanos;
    private final CompletableFuture<Void> persistenceWorkerTermination = new CompletableFuture<>();
    // The hook can be admitted later, after a retained time transition drains.
    private final CompletableFuture<Void> consumerPreparationCompletion = new CompletableFuture<>();
    private final CompletableFuture<Void> unloadHookCompletion = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownHookCompletion = new CompletableFuture<>();
    private final List<ScheduledExecutorService> nativeExecutorOwners;
    // Exactly three existing final saves, in order, with no server-thread I/O.
    private final ThreadPoolExecutor persistenceWorker = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(3),
            AdvancedCoreRuntime.daemonThreads("AdvancedCore-Shutdown-Persistence")) {
        @Override protected void terminated() { persistenceWorkerTermination.complete(null); }
    };
    private volatile CompletionStage<Void> inventoryPersistence = CompletableFuture.completedFuture(null);
    private volatile CompletionStage<Void> timestampPersistence = CompletableFuture.completedFuture(null);
    private volatile CompletionStage<Void> permissionPersistence = CompletableFuture.completedFuture(null);
    private volatile CompletionStage<Void> userStorageRetirement = CompletableFuture.completedFuture(null);
	private CompletionStage<Void> startedUserStorageRetirement;
	private volatile CompletionStage<Void> admittedUserStorageRetirement;
	private volatile CompletionStage<Void> delayedRewardRetirement = CompletableFuture.completedFuture(null);
	private CompletionStage<Void> preStorageShutdownHook;
	private CompletableFuture<Void> storageRetirementPreparation = CompletableFuture.completedFuture(null);
	private volatile CompletionStage<Void> timeChangeRetirement = CompletableFuture.completedFuture(null);
	private final Object userStorageRetirementLock = new Object();
    private record UserStorageAdmission(boolean enabled, UserManager users, UserDataManager manager) { }
    private volatile UserStorageAdmission userStorageAdmission;
    private final AtomicBoolean userStorageOwnerClosed = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> asynchronousOwnerClose =
            new java.util.concurrent.atomic.AtomicReference<>();

    public BukkitRuntimePlatform(AdvancedCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        rewardHandler = plugin.getRewardHandler();
        // Capture identities before consumer unload changes handler/manager fields.
        var owners = new IdentityHashMap<ScheduledExecutorService, Boolean>();
        addExecutor(owners, plugin.getTimer());
        addExecutor(owners, plugin.getLoginTimer());
        addExecutor(owners, plugin.getInventoryTimer());
        TimeChecker checker = plugin.getTimeChecker();
        if (checker != null) addExecutor(owners, checker.getTimer());
        FullInventoryHandler inventory = plugin.getFullInventoryHandler();
        if (inventory != null) addExecutor(owners, inventory.getTimer());
        if (plugin.getPermissionHandler() != null) addExecutor(owners, plugin.getPermissionHandler().getTimer());
        if (rewardHandler != null) addExecutor(owners, rewardHandler.getDelayedTimer());
        UserManager users = plugin.getLoadedUserManager();
        if (users != null && users.getDataManager() != null) addExecutor(owners, users.getDataManager().getTimer());
        // Keep the same non-creating snapshot for retirement that was used to
        // discover the native executor owners.  Shutdown may clear the plugin's
        // manager reference before the storage cleanup action runs; looking it
        // up again would both lose the original owner and invoke a lifecycle
        // callback twice.
        userStorageAdmission = new UserStorageAdmission(plugin.isLoadUserData(), users,
                users == null ? null : users.getDataManager());
        nativeExecutorOwners = List.copyOf(owners.keySet());
    }

    private static void addExecutor(IdentityHashMap<ScheduledExecutorService, Boolean> owners,
            ScheduledExecutorService executor) {
        if (executor != null) owners.put(executor, Boolean.TRUE);
    }

    @Override public void beginShutdown(LongSupplier remainingNanos) {
        shutdownRemainingNanos = Objects.requireNonNull(remainingNanos, "remainingNanos");
    }

    @Override public ScheduledExecutorService getTimer() { return plugin.getTimer(); }
	@Override public ScheduledExecutorService getUserStorageTimer() {
		UserStorageAdmission admission = userStorageAdmission;
		if (admission != null) return admission.manager() == null ? null : admission.manager().getTimer();
		UserManager users = plugin.getLoadedUserManager();
		return users == null || users.getDataManager() == null ? null : users.getDataManager().getTimer();
	}
    @Override public ScheduledExecutorService getLoginTimer() { return plugin.getLoginTimer(); }
    @Override public ScheduledExecutorService getInventoryTimer() { return plugin.getInventoryTimer(); }
    @Override public ScheduledExecutorService getTimeTimer() {
        TimeChecker checker = plugin.getTimeChecker();
        return checker == null ? null : checker.getTimer();
    }

    @Override public List<Cleanup> beforeExecutorShutdown() {
        return List.of(
                new Cleanup("full inventory handler", () -> {
                    inventoryPersistence = finalPersistence("full inventory snapshot", () -> {
                        FullInventoryHandler handler = plugin.getFullInventoryHandler();
                        return handler == null ? CompletableFuture.completedFuture(null)
                                : Objects.requireNonNull(handler.shutdownAsync(persistenceWorker), "inventory persistence");
                    });
                }),
                new Cleanup("Javascript engine", () -> {
                    if (plugin.getOptions() != null && plugin.getOptions().isJavascriptEngineEnabled()) {
                        plugin.getLogger().info("Shutting down Javascript engine");
                        JavascriptEngineHandler.getInstance().clearCachedEngine();
                    }
                }),
				new Cleanup("delayed reward admission", this::closeDelayedRewardAdmission),
				new Cleanup("time change admission", this::closeTimeChangeAdmission),
				new Cleanup("user storage", this::closeUserStorageAfterTimeChangeRetirement),
                new Cleanup("server data timestamp", () -> {
                    timestampPersistence = finalPersistence("server data timestamp", this::submitTimestampPersistence);
                }));
    }

	@Override public CompletionStage<Void> beforeExecutorShutdownCompletion() { return userStorageRetirement; }
	@Override public CompletionStage<Void> admittedStorageRetirementCompletion() {
		return admittedUserStorageRetirement;
	}
	@Override public CompletionStage<Void> storageRetirementPreparationCompletion() {
		synchronized (userStorageRetirementLock) {
			return storageRetirementPreparation;
		}
	}
	@Override public boolean holdTimeTimerUntilPreExecutorShutdownCompletion() {
		return timeChangeRetirement != null && !timeChangeRetirement.toCompletableFuture().isDone();
	}
	@Override public void beforeForcedTimeTimerShutdown() {
		TimeChecker checker = plugin.getTimeChecker();
		if (checker != null) checker.abortActiveTransitions();
		ensureUserStorageRetirementStarted();
	}
	@Override public void beforeDeferredPlatformCleanup() {
		TimeChecker checker = plugin.getTimeChecker();
		if (checker != null) {
			checker.cancelActiveTransitionsAndClosePersistence();
			checker.cancelQueuedUpdate();
		}
	}
	@Override public boolean canBlockForPreExecutorShutdown() {
		return Bukkit.getServer() == null || !Bukkit.isPrimaryThread();
	}

    @Override public List<Cleanup> afterExecutorGrace() {
        return List.of(new Cleanup("reward handler", () -> {
            if (rewardHandler != null) {
                LongSupplier budget = shutdownRemainingNanos;
                if (budget == null) rewardHandler.shutdown();
                else rewardHandler.shutdown(Math.min(TimeUnit.SECONDS.toNanos(10),
                        Math.max(0L, budget.getAsLong())), TimeUnit.NANOSECONDS);
            }
        }));
    }

	@Override public List<Cleanup> afterStorageExecutorShutdown() {
		return List.of(new Cleanup("terminal user storage", () -> {
			CompletionStage<Void> retirement = userStorageRetirement;
			if (retirement != null && (!retirement.toCompletableFuture().isDone()
					|| retirement.toCompletableFuture().isCompletedExceptionally())) {
				observePersistence("terminal user storage owner", closeUserStorageOwnerAsync(captureUserStorageAdmission()));
			}
		}));
	}

    @Override public List<Cleanup> afterExecutorShutdown() {
        return List.of(
                new Cleanup("plugin unload hook", () -> {
                    try {
                        plugin.onUnLoad();
                        unloadHookCompletion.complete(null);
                    } catch (Throwable failure) {
                        unloadHookCompletion.completeExceptionally(failure);
                        throw failure;
                    }
                }),
                new Cleanup("skull cache", () -> {
                    if (plugin.getSkullCacheHandler() != null) plugin.getSkullCacheHandler().close();
                }),
                new Cleanup("hologram handler", () -> {
                    if (plugin.getHologramHandler() != null) plugin.getHologramHandler().onShutDown();
                }),
                new Cleanup("permission handler", () -> {
                    permissionPersistence = finalPersistence("timed permission snapshot", () ->
                            plugin.getPermissionHandler() == null ? CompletableFuture.completedFuture(null)
                                    : Objects.requireNonNull(plugin.getPermissionHandler().shutDownAsync(persistenceWorker),
                                            "permission persistence"));
                }),
                new Cleanup("dialog service", () -> {
                    if (plugin.getDialogService() != null) plugin.getDialogService().unregister();
                }),
                new Cleanup("shutdown persistence worker", this::finishPersistenceSubmission),
                new Cleanup("plugin shutdown completion hook", () -> {
                    try {
                        plugin.onShutdownComplete(finalFilePersistence());
                        shutdownHookCompletion.complete(null);
                    } catch (Throwable failure) {
                        shutdownHookCompletion.completeExceptionally(failure);
                        throw failure;
                    } finally {
                        releaseNativeOwnership();
                    }
                }));
    }

    @Override public void info(String message) { plugin.getLogger().info(message); }
    @Override public void cleanupFailed(String component, Throwable failure) {
        plugin.getLogger().warning("Failed to shut down " + component + ": " + failure.getMessage());
        plugin.debug(failure);
    }

	private void closeTimeChangeAdmission() {
		try {
			TimeChecker checker = plugin.getTimeChecker();
			timeChangeRetirement = checker == null ? CompletableFuture.completedFuture(null)
                    : Objects.requireNonNull(checker.beginShutdown(), "time retirement");
        } catch (Throwable failure) { timeChangeRetirement = CompletableFuture.failedFuture(failure); }
	}

	private void closeDelayedRewardAdmission() {
		if (rewardHandler == null) return;
		CompletionStage<Void> retirement = rewardHandler.beginShutdown();
		delayedRewardRetirement = retirement == null ? CompletableFuture.completedFuture(null) : retirement;
	}

	private void closeUserStorageAfterTimeChangeRetirement() {
		// Deferred platform unload may change configuration or manager references.
		// Storage already admitted for this shutdown retains its own retirement identity.
		captureUserStorageAdmission();
		CompletionStage<Void> timeRetirement = timeChangeRetirement;
		if (timeRetirement.toCompletableFuture().isDone()) {
			try {
				timeRetirement.toCompletableFuture().join();
				userStorageRetirement = ensureUserStorageRetirementStarted();
			} catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
				userStorageRetirement = preservePreparationFailure(ensureUserStorageRetirementStarted(), failure);
			}
			return;
		}
		userStorageRetirement = timeRetirement.handle((ignored, failure) -> failure).thenCompose(failure -> {
			CompletionStage<Void> retirement = ensureUserStorageRetirementStarted();
			return failure == null ? retirement : preservePreparationFailure(retirement, failure);
		});
	}

	private CompletionStage<Void> ensureUserStorageRetirementStarted() {
		synchronized (userStorageRetirementLock) {
			if (startedUserStorageRetirement == null) {
				CompletionStage<Void> hook = ensurePreStorageShutdownHook();
				if (hook.toCompletableFuture().isDone()) {
					try { hook.toCompletableFuture().join(); }
					catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
						startedUserStorageRetirement = rewardHandler == null
                                ? retireAfterPreparationFailure(failure) : deferUserStorageRetirement(failure);
                        if (captureUserStorageAdmission().enabled()) admittedUserStorageRetirement = startedUserStorageRetirement;
                        return startedUserStorageRetirement;
					}
                    // A delayed check may have returned after deferring itself to
                    // the manager. Its worker barrier must precede the replay-tail
                    // snapshot even when the consumer hook completed immediately.
                    startedUserStorageRetirement = rewardHandler == null
                            ? closeUserStorageAfterSharedRetirement() : deferUserStorageRetirement(null);
                    if (captureUserStorageAdmission().enabled()) admittedUserStorageRetirement = startedUserStorageRetirement;
				} else {
					var preparation = new CompletableFuture<Void>();
					storageRetirementPreparation = preparation;
					startedUserStorageRetirement = hook.handle((ignored, failure) -> failure)
							.thenCompose(failure -> {
								try { return deferUserStorageRetirement(failure); }
								finally {
									// Future completion can precede its callbacks. Signal only
									// after the final retirement task has been admitted.
									preparation.complete(null);
								}
							});
				}
			}
			return startedUserStorageRetirement;
		}
	}

    /** A completing consumer callback may still hold shared storage read admission. */
    private CompletionStage<Void> deferUserStorageRetirement(Throwable preparationFailure) {
        UserStorageAdmission admission = captureUserStorageAdmission();
        if (!admission.enabled() || admission.users() == null) {
            CompletionStage<Void> retirement = preparationFailure == null ? closeUserStorageAfterSharedRetirement()
                    : retireAfterPreparationFailure(preparationFailure);
            if (admission.enabled()) admittedUserStorageRetirement = retirement;
            return retirement;
        }
        var result = new CompletableFuture<Void>();
        // Publish the admission before handing the task to the worker.  The
        // lifecycle watchdog can run re-entrantly from an executor's execute
        // implementation (and can otherwise race the return from execute).
        // Reserving the completion first ensures it retains the worker and
        // native owner even while the task is being accepted.  If acceptance
        // is rejected, the same stage is completed exceptionally below, so a
        // rejection cannot masquerade as a successful retirement.
        admittedUserStorageRetirement = result;
        try {
            Objects.requireNonNull(getUserStorageTimer(), "user storage timer").execute(() -> {
                try {
                    CompletionStage<Void> replay = AdvancedCoreUser.persistedReplayCompletion(plugin);
                    if (replay.toCompletableFuture().isDone()) {
                        replay.toCompletableFuture().join();
                        completeDeferredUserStorageRetirement(result, preparationFailure);
                    } else {
                        // Queue after acknowledgement/restore finishes. The
                        // completing callback may still hold shared read admission.
                        replay.whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                result.completeExceptionally(preparationAndStorageFailure(preparationFailure, failure));
                                return;
                            }
                            try {
                                getUserStorageTimer().execute(() -> completeDeferredUserStorageRetirement(result,
                                        preparationFailure));
                            } catch (RuntimeException | Error admissionFailure) {
                                result.completeExceptionally(preparationAndStorageFailure(preparationFailure,
                                        admissionFailure));
                            }
                        });
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(preparationAndStorageFailure(preparationFailure, failure));
                }
            });
        } catch (RuntimeException | Error failure) {
            // Do not detach the shared runtime or pretend rejected work flushed storage.
            result.completeExceptionally(preparationAndStorageFailure(preparationFailure, failure));
        }
        return result;
    }

    private void completeDeferredUserStorageRetirement(CompletableFuture<Void> result, Throwable preparationFailure) {
        try {
            CompletionStage<Void> retirement = preparationFailure == null
                    ? closeUserStorageAfterSharedRetirement() : retireAfterPreparationFailure(preparationFailure);
            retirement.whenComplete((ignored, failure) -> {
                if (failure == null) result.complete(null);
                else result.completeExceptionally(failure);
            });
        } catch (Throwable failure) {
            result.completeExceptionally(preparationAndStorageFailure(preparationFailure, failure));
        }
    }

    private static Throwable preparationAndStorageFailure(Throwable preparationFailure, Throwable storageFailure) {
        if (preparationFailure == null) return storageFailure;
        Throwable cause = preparationFailure instanceof java.util.concurrent.CompletionException
                && preparationFailure.getCause() != null ? preparationFailure.getCause() : preparationFailure;
        var reported = new java.util.concurrent.CompletionException(cause);
        if (storageFailure != null && storageFailure != cause) reported.addSuppressed(storageFailure);
        return reported;
    }

    /** A failed consumer preparation still has accepted cache writes to retire. */
    private CompletionStage<Void> retireAfterPreparationFailure(Throwable preparationFailure) {
        try { return preservePreparationFailure(closeUserStorageAfterSharedRetirement(), preparationFailure); }
        catch (Throwable storageFailure) {
            return CompletableFuture.failedFuture(preparationAndStorageFailure(preparationFailure, storageFailure));
        }
    }

    private CompletionStage<Void> preservePreparationFailure(CompletionStage<Void> retirement, Throwable preparationFailure) {
        Throwable cause = preparationFailure instanceof java.util.concurrent.CompletionException
                && preparationFailure.getCause() != null ? preparationFailure.getCause() : preparationFailure;
        var reported = new java.util.concurrent.CompletionException(cause);
        var result = new CompletableFuture<Void>();
        try {
            retirement.whenComplete((ignored, storageFailure) -> {
                if (storageFailure != null && storageFailure != cause) reported.addSuppressed(storageFailure);
                result.completeExceptionally(reported);
            });
        } catch (Throwable storageFailure) {
            if (storageFailure != cause) reported.addSuppressed(storageFailure);
            result.completeExceptionally(reported);
        }
        return result;
    }

	private CompletionStage<Void> ensurePreStorageShutdownHook() {
		synchronized (userStorageRetirementLock) {
			if (preStorageShutdownHook != null) return preStorageShutdownHook;
			try {
				CompletionStage<Void> hook = plugin.onBeforeStorageShutdown();
				preStorageShutdownHook = hook == null ? CompletableFuture.completedFuture(null) : hook;
			} catch (Throwable failure) {
				preStorageShutdownHook = CompletableFuture.failedFuture(failure);
			}
			CompletableFuture<Void> inventory = inventoryPersistence.toCompletableFuture();
			if (!inventory.isDone() || inventory.isCompletedExceptionally()) {
				preStorageShutdownHook = CompletableFuture.allOf(inventory,
						preStorageShutdownHook.toCompletableFuture());
			}
			CompletableFuture<Void> rewards = delayedRewardRetirement.toCompletableFuture();
			if (!rewards.isDone() || rewards.isCompletedExceptionally()) {
				preStorageShutdownHook = CompletableFuture.allOf(rewards,
						preStorageShutdownHook.toCompletableFuture());
			}
			preStorageShutdownHook.whenComplete((ignored, failure) -> {
				if (failure == null) consumerPreparationCompletion.complete(null);
				else consumerPreparationCompletion.completeExceptionally(failure);
			});
			return preStorageShutdownHook;
		}
	}

    private CompletionStage<Void> observePersistence(String component, CompletionStage<Void> completion) {
        completion.whenComplete((ignored, failure) -> {
            if (failure != null) cleanupFailed(component, failure);
        });
        return completion;
    }

    private CompletionStage<Void> finalPersistence(String component, Supplier<CompletionStage<Void>> submission) {
        CompletionStage<Void> completion;
        try { completion = submission.get(); }
        catch (Throwable failure) { completion = CompletableFuture.failedFuture(failure); }
        return observePersistence(component, completion);
    }

    private CompletionStage<Void> submitTimestampPersistence() {
        ServerData owner = plugin.getServerDataFile();
        if (owner == null) return CompletableFuture.completedFuture(null);
        long lastUpdated = System.currentTimeMillis();
        var completion = new CompletableFuture<Void>();
        try {
            persistenceWorker.execute(() -> {
                try {
                    owner.mutateFinalData(data -> data.set("LastUpdated", lastUpdated))
                            .whenComplete((ignored, failure) -> {
                                if (failure == null) completion.complete(null);
                                else completion.completeExceptionally(failure);
                            });
                } catch (Throwable failure) { completion.completeExceptionally(failure); }
            });
        } catch (Throwable failure) { completion.completeExceptionally(failure); }
        return completion;
    }

    private void releaseNativeOwnership() {
        if (!plugin.hasServerDataOwnership()) return;
        CompletableFuture<Void> settled = CompletableFuture.allOf(finalFilePersistence(), consumerPreparationCompletion,
                userStorageRetirement.toCompletableFuture(), persistenceWorkerTermination, unloadHookCompletion,
                shutdownHookCompletion);
        // A cancelled/failed publication or preparation can settle while another
        // writer is still active. It cannot prove drain, even after eventual timer
        // termination; retain the opaque admission token until JVM restart.
        plugin.releaseServerDataOwnershipAfter(settled.thenCompose(ignored -> nativeExecutorTermination()));
    }

    private CompletionStage<Void> nativeExecutorTermination() {
        List<ScheduledExecutorService> pending = new ArrayList<>();
        for (ScheduledExecutorService owner : nativeExecutorOwners) {
            if (!owner.isTerminated()) pending.add(owner);
        }
        if (pending.isEmpty()) return CompletableFuture.completedFuture(null);
        var completion = new CompletableFuture<Void>();
        // Cancellation and lifecycle-drain acknowledgements are not executor
        // termination. This daemon waits outside the server thread and never
        // refreshes the caller's shared 15-second wait budget.
        Thread waiter = AdvancedCoreRuntime.daemonThreads("AdvancedCore-ServerData-Drain").newThread(() -> {
            try {
                for (ScheduledExecutorService owner : pending) {
                    while (!owner.isTerminated()) owner.awaitTermination(1, TimeUnit.SECONDS);
                }
                completion.complete(null);
            } catch (Throwable failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                completion.completeExceptionally(failure);
            }
        });
        try { waiter.start(); }
        catch (Throwable failure) { completion.completeExceptionally(failure); }
        return completion;
    }

    private CompletableFuture<Void> finalFilePersistence() {
        return CompletableFuture.allOf(inventoryPersistence.toCompletableFuture(),
                timestampPersistence.toCompletableFuture(), permissionPersistence.toCompletableFuture());
    }

    private void finishPersistenceSubmission() {
        // Do not discard accepted snapshots on timeout or close the native owner
        // while inventory delivery can still advance its durable replay receipt.
        persistenceWorker.shutdown();
        CompletableFuture<Void> completion = finalFilePersistence();
        if (completion.isDone()) return;
        LongSupplier budget = shutdownRemainingNanos;
        long remaining = budget == null ? TimeUnit.SECONDS.toNanos(15) : Math.max(0L, budget.getAsLong());
        CompletableFuture.delayedExecutor(remaining, TimeUnit.NANOSECONDS).execute(() -> {
            if (!completion.isDone()) cleanupFailed("shutdown persistence", new java.util.concurrent.TimeoutException(
                    "Shutdown persistence is still pending after the shared 15-second deadline"));
        });
    }

	private CompletionStage<Void> closeUserStorageAfterSharedRetirement() {
        UserStorageAdmission admission = captureUserStorageAdmission();
        if (!admission.enabled()) {
			return CompletableFuture.completedFuture(null);
        }
		// Resolve this only after shared retirement. A successful replacement may
		// install a new owner while shutdown is waiting for its worker, and closing
		// the owner observed before that wait leaks the installed MySQL provider.
		Runnable closeMysql = () -> closeCurrentUserStorageOwner(admission);
        if (admission.users() == null) {
			return closeUserStorageOwnerAsync(admission);
        }
		CompletionStage<Void> retirement = admission.manager().closeSharedRuntimeAsyncCompletion(closeMysql);
        if (retirement == null) {
			return closeUserStorageOwnerAsync(admission);
		}
		return retirement;
	}

    /** A provider close can itself block in JDBC even when no manager worker remains. */
    private CompletionStage<Void> closeUserStorageOwnerAsync(UserStorageAdmission admission) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        while (true) {
            CompletableFuture<Void> active = asynchronousOwnerClose.get();
            if (active != null) return active;
            if (asynchronousOwnerClose.compareAndSet(null, completion)) break;
        }
        Thread worker = AdvancedCoreRuntime.daemonThreads("AdvancedCore-Storage-Owner-Close").newThread(() -> {
            try {
                // Without a manager there is no worker barrier to run the
                // snapshot. This existing daemon owner-close worker can drain
                // callbacks without waiting on the lifecycle caller.
                AdvancedCoreUser.persistedReplayCompletion(plugin).toCompletableFuture().join();
                closeCurrentUserStorageOwner(admission);
                completion.complete(null);
            }
            catch (Throwable failure) {
                asynchronousOwnerClose.compareAndSet(completion, null);
                completion.completeExceptionally(failure);
            }
        });
        try { worker.start(); }
        catch (Throwable failure) {
            asynchronousOwnerClose.compareAndSet(completion, null);
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    private UserStorageAdmission captureUserStorageAdmission() {
        synchronized (userStorageRetirementLock) {
            if (userStorageAdmission == null) {
                boolean enabled = plugin.isLoadUserData();
                // Even disabled user loading can have an existing worker to retire.
                UserManager users = plugin.getLoadedUserManager();
                userStorageAdmission = new UserStorageAdmission(enabled, users,
                        users == null ? null : users.getDataManager());
            }
            return userStorageAdmission;
        }
    }

	private void closeCurrentUserStorageOwner(UserStorageAdmission admission) {
		if (!userStorageOwnerClosed.compareAndSet(false, true)) return;
		try {
			AdvancedCorePlugin.UserStorageOwner owner = plugin.getNativeUserStorageOwner();
			if (owner != null) {
				if (owner.storageType() == UserStorage.MYSQL && owner.mysql() != null) owner.mysql().close();
				else if (owner.storageType() == UserStorage.SQLITE && owner.table() != null
						&& owner.table().getSqLite() != null) owner.table().getSqLite().closeConnection();
				return;
			}
			boolean ownsMysql = admission.users() != null && admission.manager().hasSharedSqlBackend()
					? admission.manager().usesSharedSqlStorage(UserStorage.MYSQL)
					: plugin.getOptions() != null && UserStorage.MYSQL.equals(plugin.getOptions().getStorageType());
			if (ownsMysql && plugin.getMysql() != null) plugin.getMysql().close();
		} catch (RuntimeException | Error failure) {
			userStorageOwnerClosed.set(false);
			throw failure;
		} finally { plugin.closePendingNativeUserStorageOwners(); }
	}
}
