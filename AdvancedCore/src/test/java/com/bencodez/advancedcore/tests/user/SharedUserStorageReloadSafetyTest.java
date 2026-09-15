package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.data.DataValue;

class SharedUserStorageReloadSafetyTest {
	@Test
	void sharedStorageReloadExposesAnIncompleteCompletionStageUntilReplacementFinishes() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = mock(UserDataManager.class);
		when(manager.hasSharedRuntime()).thenReturn(true);
		when(users.getDataManager()).thenReturn(manager);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);

		CompletableFuture<Void> completion = plugin.reloadAdvancedCoreAsync(true).toCompletableFuture();

		verify(scheduler).runTask(eq(plugin), any(Runnable.class));
		verify(plugin, never()).getServerDataFile();
		assertFalse(completion.isDone());
	}

	@Test
	void publicStorageTypeRemainsPinnedToTheActiveSharedBackendAfterConfigReload() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(options.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getOptions()).thenReturn(options);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = new UserDataManager(plugin);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(manager);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedSqlBackend(backend, (uuid, operation) -> operation.run());
		try {
			assertEquals(UserStorage.MYSQL, plugin.getStorageType());
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

	@Test
	void bulkApisUseTheNativeOwnerCapturedWithTheSharedRoute() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL replacementOwner = mock(MySQL.class);
		SqlUserBackend replacement = mock(SqlUserBackend.class);
		when(replacement.storageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getNativeUserStorageOwner()).thenReturn(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, replacementOwner, null));
		// Simulate the interval in a cross-type replacement where a caller could
		// otherwise observe a new route type and an unrelated legacy field.
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(replacementOwner.getColumns()).thenReturn(List.of("uuid", "PlayerName"));
		AdvancedCorePlugin.setInstance(plugin);
		UserManager users = new UserManager(plugin);
		try {
			users.getDataManager().bindSharedSqlBackend(replacement, (uuid, operation) -> operation.run());

			assertEquals(List.of("uuid", "PlayerName"), users.getAllColumns());
			verify(plugin, never()).getSQLiteUserTable();
		} finally {
			users.getDataManager().getTimer().shutdownNow();
			AdvancedCorePlugin.setInstance(null);
		}
	}

	@Test
	void bulkOperationHoldsLifecycleAdmissionUntilTheOldProviderIsNoLongerInUse() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL oldMysql = mock(MySQL.class);
		MySQL replacementMysql = mock(MySQL.class);
		SqlUserBackend oldBackend = mock(SqlUserBackend.class);
		SqlUserBackend replacementBackend = mock(SqlUserBackend.class);
		when(oldBackend.storageType()).thenReturn(UserStorage.MYSQL);
		when(oldBackend.isOpen()).thenReturn(true);
		when(replacementBackend.storageType()).thenReturn(UserStorage.MYSQL);
		when(replacementBackend.isOpen()).thenReturn(true);
		AtomicReference<AdvancedCorePlugin.UserStorageOwner> owner = new AtomicReference<>(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, oldMysql, null));
		when(plugin.getNativeUserStorageOwner()).thenAnswer(ignored -> owner.get());
		CountDownLatch bulkEntered = new CountDownLatch(1);
		CountDownLatch releaseBulk = new CountDownLatch(1);
		CountDownLatch oldClosed = new CountDownLatch(1);
		when(oldMysql.getColumns()).thenAnswer(ignored -> {
			bulkEntered.countDown();
			assertTrue(releaseBulk.await(5, TimeUnit.SECONDS));
			return List.of("uuid");
		});
		doAnswer(ignored -> { oldClosed.countDown(); return null; }).when(oldBackend).close();

		AdvancedCorePlugin.setInstance(plugin);
		UserManager users = new UserManager(plugin);
		RoutingCacheOwner cacheOwner = new RoutingCacheOwner(users.getDataManager());
		SharedUserDataRuntime runtime = new SharedUserDataRuntime(oldBackend, cacheOwner);
		users.getDataManager().bindSharedRuntime(runtime);
		AtomicReference<List<String>> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread bulk = new Thread(() -> {
			try { result.set(users.getAllColumns()); }
			catch (Throwable thrown) { failure.set(thrown); }
		}, "bulk-owner-admission");
		Thread replacement = new Thread(() -> runtime.replaceBackend(replacementBackend,
				() -> owner.set(new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, replacementMysql, null))),
				"replace-owner-admission");
		try {
			bulk.start();
			assertTrue(bulkEntered.await(5, TimeUnit.SECONDS));
			replacement.start();
			assertFalse(oldClosed.await(200, TimeUnit.MILLISECONDS));
			releaseBulk.countDown();
			bulk.join(5000);
			replacement.join(5000);
			assertFalse(bulk.isAlive());
			assertFalse(replacement.isAlive());
			assertEquals(List.of("uuid"), result.get());
			assertEquals(null, failure.get());
			assertTrue(oldClosed.await(1, TimeUnit.SECONDS));
		} finally {
			releaseBulk.countDown();
			users.getDataManager().getTimer().shutdownNow();
			AdvancedCorePlugin.setInstance(null);
		}
	}

	@Test
	void sharedBackendReplacementCompletesOnTheManagerWorkerWithoutBlockingTheCaller() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
		SqlUserBackend replacement = mock(SqlUserBackend.class);
		when(runtime.isClosed()).thenReturn(false);
		manager.bindSharedRuntime(runtime);
		try {
			manager.replaceSharedSqlBackendAsync(replacement).toCompletableFuture().get(5, TimeUnit.SECONDS);
			verify(runtime).replaceBackend(eq(replacement), any(Runnable.class));
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

    @Test
    void directNativeStorageMutationRejectsWhileSharedRuntimeOwnsStorage() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        when(runtime.isClosed()).thenReturn(false);
        manager.bindSharedRuntime(runtime);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        try {
            assertThrows(IllegalStateException.class, () -> plugin.loadUserAPI(UserStorage.MYSQL));
            verify(plugin, never()).getOptions();
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void retirementRemainsAnUnsafeReloadWindowUntilBlockingCloseCompletes() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        CompletableFuture<Void> retirement = new CompletableFuture<>();
        CountDownLatch afterRetirement = new CountDownLatch(1);
        when(runtime.isClosed()).thenReturn(false);
        when(runtime.closeAsync(any())).thenReturn(retirement);
        manager.bindSharedRuntime(runtime);
        try {
            assertTrue(manager.closeSharedRuntimeAsync(afterRetirement::countDown));
            assertTrue(manager.hasSharedRuntimeLifecycle());
            assertTrue(manager.closeSharedRuntimeAsync(() -> {
                throw new AssertionError("A second caller must join the existing retirement");
            }));
            retirement.complete(null);
            assertTrue(afterRetirement.await(5, TimeUnit.SECONDS));
            assertFalse(manager.hasSharedRuntimeLifecycle());
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void conversionRunsBehindTheRuntimeMaintenanceBarrierInsteadOfTheReloadGuard() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(users.getAllKeys(UserStorage.SQLITE)).thenReturn(new HashMap<>());
        doNothing().when(plugin).loadUserAPI(any(UserStorage.class));
        doNothing().when(plugin).debug(any(String.class));
        doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(runtime).runStorageMaintenance(any(Runnable.class));
        manager.bindSharedRuntime(runtime);
        try {
            plugin.convertDataStorage(UserStorage.SQLITE, UserStorage.MYSQL);

            verify(runtime).runStorageMaintenance(any(Runnable.class));
            verify(plugin).loadUserAPI(UserStorage.SQLITE);
            verify(plugin).loadUserAPI(UserStorage.MYSQL);
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

	private static final class RoutingCacheOwner implements UserCacheOwner {
		private final UserDataManager manager;
		private Consumer<Runnable> lifecycleGate;
		private BiConsumer<UUID, Runnable> userGate;
		private BiConsumer<UUID, Runnable> exclusiveUserGate;

		private RoutingCacheOwner(UserDataManager manager) { this.manager = manager; }

		@Override public void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate,
				BiConsumer<UUID, Runnable> perUserGate, BiConsumer<UUID, Runnable> perUserExclusiveGate) {
			lifecycleGate = gate;
			userGate = perUserGate;
			exclusiveUserGate = perUserExclusiveGate;
			manager.bindSharedSqlBackend(backend, gate, perUserGate, perUserExclusiveGate);
		}

		@Override public void bindBackend(SqlUserBackend backend) {
			manager.bindSharedSqlBackend(backend, lifecycleGate, userGate, exclusiveUserGate);
		}

		@Override public boolean isCached(UUID uuid) { return false; }
		@Override public DataValue getIfPresent(UUID uuid, String key) { return null; }
		@Override public void populate(UUID uuid, HashMap<String, DataValue> values) {}
		@Override public void queueChange(UUID uuid, String key, DataValue value) {}
		@Override public void flush(UUID uuid, SqlUserStorage storage) {}
		@Override public Set<UUID> cachedUsers() { return Set.of(); }
		@Override public void remove(UUID uuid) {}
		@Override public void clearAfterFlush() {}
		@Override public void shutdown() {}
	}
}
