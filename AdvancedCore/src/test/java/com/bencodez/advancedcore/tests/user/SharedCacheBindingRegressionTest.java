package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

@Timeout(15)
class SharedCacheBindingRegressionTest {
	@Test void bukkitPostgresUsesConservativeSchemaIdentity() {
		var plugin = mock(AdvancedCorePlugin.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		when(table.caseInsensitiveColumnNames()).thenReturn(false);
		var backend = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend(plugin, UserStorage.MYSQL, table, null);
		assertNull(backend.caseInsensitiveColumnNames());
	}
	@Test void mariaNativeScalarRejectsOlderAliasAfterNewerCommit() throws Exception { nativeAliasOrdering(com.bencodez.simpleapi.sql.mysql.DbType.MARIADB, false, 10); }
	@Test void mariaNativeBulkRejectsOlderAliasAfterNewerCommit() throws Exception { nativeAliasOrdering(com.bencodez.simpleapi.sql.mysql.DbType.MARIADB, true, 10); }
	@Test void postgresNativeScalarKeepsDistinctAliases() throws Exception { nativeAliasOrdering(com.bencodez.simpleapi.sql.mysql.DbType.POSTGRESQL, false, 2); }
	@Test void postgresNativeBulkKeepsDistinctAliases() throws Exception { nativeAliasOrdering(com.bencodez.simpleapi.sql.mysql.DbType.POSTGRESQL, true, 2); }
	@Test void unknownDialectDoesNotFoldAliases() throws Exception { nativeAliasOrdering(null, false, 2); }
	private void nativeAliasOrdering(com.bencodez.simpleapi.sql.mysql.DbType dialect, boolean bulk, int expected) throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class, CALLS_REAL_METHODS);
		Field type = com.bencodez.simpleapi.sql.mysql.AbstractSqlTable.class.getDeclaredField("dbType"); type.setAccessible(true); type.set(table, dialect);
		Field lock = com.bencodez.simpleapi.sql.mysql.AbstractSqlTable.class.getDeclaredField("updateLock"); lock.setAccessible(true); lock.set(table, new Object());
		Field owner = table.getClass().getDeclaredField("plugin"); owner.setAccessible(true); owner.set(table, plugin);
		doNothing().when(table).checkColumn(anyString(), any());
		doReturn(Set.of()).when(table).getUuids(); doReturn(false).when(table).containsKeyQuery(anyString());
		var value = new java.util.concurrent.atomic.AtomicInteger();
		doAnswer(call -> { value.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).insert(anyString(), anyString(), any(DataValue.class));
		doAnswer(call -> { value.set(call.<List<Column>>getArgument(1).get(0).getValue().getInt()); return null; }).when(table).insertQuery(anyString(), anyList());
		var older = manager.beginLegacyDirectWrite(uuid, "MYSQL:points"); var newer = manager.beginLegacyDirectWrite(uuid, "MYSQL:Points");
		try {
			UserDataCache.runWithLegacyDirectWrite(manager, newer, null, () -> table.update(uuid.toString(), "Points", new DataValueInt(10)));
			UserDataCache.runWithLegacyDirectWrite(manager, older, null, () -> {
				if (bulk) table.update(uuid.toString(), List.of(new Column("points", new DataValueInt(2))), false);
				else table.update(uuid.toString(), "points", new DataValueInt(2));
			});
			assertEquals(expected, value.get());
		} finally { manager.endLegacyDirectWrite(newer); manager.endLegacyDirectWrite(older); manager.getTimer().shutdownNow(); }
	}
	@Test void confirmedNestedSameKeyWriteCanDrainWithoutReplayingOlderValue() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new HashMap<String, Integer>(); var scalarWrites = new java.util.concurrent.atomic.AtomicInteger();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { String key = call.getArgument(1); stored.put(key, ((DataValue) call.getArgument(2)).getInt());
			scalarWrites.incrementAndGet(); UserDataCache.recordLegacyProviderCommit(id.toString(), List.of(key), UserStorage.MYSQL, true); return null; })
				.when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { var admitted = UserDataCache.filterConfirmedLegacyBatchColumns(id.toString(), call.getArgument(1));
			UserDataCache.rejectStaleLegacyBatch(id.toString());
			for (Column column : admitted) stored.put(column.getName(), column.getValue().getInt()); return null; })
				.when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("X", 7, false); super.setValues(values); }
		};
		when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			cache.addChange(new UserDataChangeInt("Z", 30), true);
			cache.processChanges();
			assertEquals(7, stored.get("X")); assertEquals(7, cache.getIfPresent("X").getInt());
			assertEquals(30, stored.get("Z")); assertEquals(30, cache.getIfPresent("Z").getInt());
			assertEquals(1, scalarWrites.get()); assertFalse(cache.hasChangesToProcess());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyIntegerMutationRejectsRetainedCacheFromReplacedProvider() {
		var plugin = mock(AdvancedCorePlugin.class);
		var original = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var replacement = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var oldOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, original);
		var newOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, replacement);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE); when(plugin.getNativeUserStorageOwner()).thenReturn(oldOwner);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(plugin.getNativeUserStorageOwner()).thenReturn(newOwner);
		try {
			assertNull(manager.tryMutateLegacyIntegers(id, Map.of("X", 1), UserDataCache.IntegerMutation.ADD, null));
			assertEquals(10, cache.getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyFlushRejectsRetainedCacheFromReplacedProvider() {
		var plugin = mock(AdvancedCorePlugin.class);
		var original = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var replacement = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var oldOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, original);
		var newOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, replacement);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE); when(plugin.getNativeUserStorageOwner()).thenReturn(oldOwner);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache); cache.addChange(new UserDataChangeInt("X", 11), true);
		when(plugin.getNativeUserStorageOwner()).thenReturn(newOwner);
		try {
			assertThrows(IllegalStateException.class, cache::processChanges);
			assertTrue(cache.hasChangesToProcess()); verify(replacement, never()).update(any(Column.class), anyList());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyReadUsesCapturedNativeProviderAcrossTemporaryReplacement() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var original = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var replacement = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var owner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, original);
		var live = new java.util.concurrent.atomic.AtomicReference<>(original);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(owner);
		when(plugin.getSQLiteUserTable()).thenAnswer(call -> live.get());
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id.toString());
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		when(users.getUser(id, false)).thenAnswer(call -> { live.set(replacement); return user; });
		when(original.getExactStrict(any())).thenAnswer(call -> { live.set(original); return new ArrayList<>(List.of(new Column("X", new DataValueInt(10)))); });
		when(replacement.getExactStrict(any())).thenAnswer(call -> { live.set(original); return new ArrayList<>(List.of(new Column("X", new DataValueInt(100)))); });
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		try {
			assertTrue(manager.populateCompleteLegacyCache(id));
			assertEquals(10, cache.getIfPresent("X").getInt());
			verify(original).getExactStrict(any()); verify(replacement, never()).getExactStrict(any());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadAcceptsMatchingNativeProvider() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var owner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(owner); when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getUser(id, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("X", new DataValueInt(12))));
		try {
			assertTrue(manager.populateCompleteLegacyCache(id));
			assertEquals(12, manager.getPublishedCache(id).getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyMysqlDefaultsUseCapturedProviderRules() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID id = UUID.randomUUID();
		var original = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var replacement = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var live = new java.util.concurrent.atomic.AtomicReference<>(original);
		var owner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, original, null);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getNativeUserStorageOwner()).thenReturn(owner);
		when(plugin.getMysql()).thenAnswer(call -> live.get()); when(plugin.getUserManager()).thenReturn(users);
		when(users.getUser(id, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenAnswer(call -> {
			live.set(replacement); return new HashMap<>(Map.of("activations", new DataValueInt(7)));
		});
		when(original.caseInsensitiveColumnNames()).thenAnswer(call -> { live.set(original); return true; });
		when(replacement.caseInsensitiveColumnNames()).thenAnswer(call -> { live.set(original); return false; });
		var manager = new UserDataManager(plugin);
		manager.addKey(new com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt("Activations"));
		try {
			assertTrue(manager.populateCompleteLegacyCache(id));
			var snapshot = manager.getPublishedCache(id).snapshotIfPublished();
			assertEquals(7, snapshot.get("activations").getInt());
			assertFalse(snapshot.containsKey("Activations"), "a case alias must not invent a zero balance");
			verify(replacement, never()).caseInsensitiveColumnNames();
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadRejectsProviderFieldChangedBeforeOwnerPublication() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class);
		var oldTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var newTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var owner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, oldTable);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(owner);
		when(plugin.getSQLiteUserTable()).thenReturn(oldTable);
		when(plugin.getUserManager()).thenReturn(users);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(users.getUser(id, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("X", new DataValueInt(100))));
		when(plugin.getSQLiteUserTable()).thenReturn(newTable);
		try {
			assertFalse(manager.populateCompleteLegacyCache(id));
			assertEquals(10, cache.getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void asyncNestedWriteCannotHideAnIndependentWriteBeforeBatchAdmission() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new ConcurrentHashMap<String, Integer>(); var tasks = new ArrayList<Runnable>();
		var dataRef = new java.util.concurrent.atomic.AtomicReference<UserData>();
		var first = new java.util.concurrent.atomic.AtomicBoolean(true);
		var worker = Executors.newSingleThreadExecutor();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getTimer()).thenReturn(timer);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		doAnswer(call -> { stored.put(call.getArgument(1), ((DataValue) call.getArgument(2)).getInt()); return null; })
				.when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> {
			if (first.compareAndSet(true, false)) worker.submit(() -> dataRef.get().setInt("X", 99, false)).get(5, TimeUnit.SECONDS);
			UserDataCache.rejectStaleLegacyBatch(id.toString());
			for (Column column : call.<List<Column>>getArgument(1)) stored.put(column.getName(), column.getValue().getInt()); return null;
		}).when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("Y", 7, false, true); super.setValues(values); }
		};
		dataRef.set(data); when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			assertThrows(IllegalStateException.class, cache::processChanges);
			assertEquals(99, stored.get("X")); assertTrue(cache.hasChangesToProcess());
		} finally { for (Runnable task : tasks) task.run(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void asyncDisjointNestedWriteDoesNotPreventEnclosingBatchFromDraining() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new ConcurrentHashMap<String, Integer>();
		var task = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getTimer()).thenReturn(timer);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		doAnswer(call -> { stored.put(call.getArgument(1), ((DataValue) call.getArgument(2)).getInt()); return null; })
				.when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { UserDataCache.rejectStaleLegacyBatch(id.toString());
			for (Column column : call.<List<Column>>getArgument(1)) stored.put(column.getName(), column.getValue().getInt()); return null; })
				.when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("Y", 7, false, true); super.setValues(values); }
		};
		when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			cache.processChanges();
			assertEquals(20, stored.get("X")); assertFalse(cache.hasChangesToProcess());
			assertNotNull(task.get()); task.get().run(); assertEquals(7, stored.get("Y"));
		} finally { if (task.get() != null && !stored.containsKey("Y")) task.get().run(); manager.getTimer().shutdownNow(); }
	}
	@Test void nestedDisjointWriteCannotAbsorbAnInterveningIndependentWriter() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new ConcurrentHashMap<String, Integer>();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		var blockedOnce = new java.util.concurrent.atomic.AtomicBoolean();
		var rejected = new java.util.concurrent.atomic.AtomicInteger();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> {
			String key = call.getArgument(1);
			if (key.equals("Y") && blockedOnce.compareAndSet(false, true)) {
				entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			}
			stored.put(key, ((DataValue) call.getArgument(2)).getInt()); return null;
		}).when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> {
			try { UserDataCache.rejectStaleLegacyBatch(id.toString()); }
			catch (IllegalStateException stale) { rejected.incrementAndGet(); throw stale; }
			for (Column column : call.<List<Column>>getArgument(1)) stored.put(column.getName(), column.getValue().getInt()); return null;
		}).when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("Y", 7, false); super.setValues(values); }
		};
		when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var worker = Executors.newSingleThreadExecutor();
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			var flush = worker.submit(cache::processChanges);
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			data.setInt("X", 99, false);
			release.countDown(); flush.get(5, TimeUnit.SECONDS);
			assertTrue(rejected.get() >= 1, "the intervening write must stale the first batch");
			assertEquals(99, stored.get("X"));
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void nestedSameKeyWriteIsNotOverwrittenByAnOlderBatch() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new HashMap<String, Integer>();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { stored.put(call.getArgument(1), ((DataValue) call.getArgument(2)).getInt()); return null; })
				.when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { UserDataCache.rejectStaleLegacyBatch(id.toString());
			for (Column column : call.<List<Column>>getArgument(1)) stored.put(column.getName(), column.getValue().getInt()); return null; })
				.when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("X", 7, false); super.setValues(values); }
		};
		when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			assertThrows(IllegalStateException.class, cache::processChanges);
			assertEquals(7, stored.get("X")); assertTrue(cache.hasChangesToProcess());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void nestedSynchronousDirectWriteDoesNotPermanentlyStaleItsOwnBatch() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		var stored = new HashMap<String, Integer>();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(id, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id.toString()); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { stored.put(call.getArgument(1), ((DataValue) call.getArgument(2)).getInt()); return null; })
				.when(table).update(eq(id.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { UserDataCache.rejectStaleLegacyBatch(id.toString());
			for (Column column : call.<List<Column>>getArgument(1)) stored.put(column.getName(), column.getValue().getInt()); return null; })
				.when(table).update(eq(id.toString()), anyList(), eq(false));
		var data = new UserData(user) {
			@Override public void setValues(HashMap<String, DataValue> values) { setInt("Y", 7, false); super.setValues(values); }
		};
		when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			cache.addChange(new UserDataChangeInt("X", 20), true);
			cache.processChanges();
			assertEquals(20, stored.get("X")); assertEquals(7, stored.get("Y"));
			assertFalse(cache.hasChangesToProcess());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadRejectsDifferentStorageFromRetainedCache() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var data = mock(UserData.class);
		var manager = new UserDataManager(plugin);
		var id = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(users.getUser(id, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("X", new DataValueInt(100))));
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		try {
			assertFalse(manager.populateCompleteLegacyCache(id));
			assertEquals(10, cache.getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadRejectsStorageChangeDuringRead() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var data = mock(UserData.class);
		var manager = new UserDataManager(plugin);
		var id = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(users.getUser(id, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenAnswer(call -> {
			when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
			return new HashMap<>(Map.of("X", new DataValueInt(100)));
		});
		try {
			assertFalse(manager.populateCompleteLegacyCache(id));
			assertEquals(10, cache.getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadRejectsReplacementOfSameStorageProvider() {
		var plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var data = mock(UserData.class);
		var first = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var second = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var original = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, first);
		var replacement = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, second);
		var manager = new UserDataManager(plugin); var id = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(original);
		when(plugin.getUserManager()).thenReturn(users);
		var cache = new UserDataCache(manager, id);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
		manager.getUserDataCache().put(id, cache);
		when(users.getUser(id, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("X", new DataValueInt(100))));
		when(plugin.getNativeUserStorageOwner()).thenReturn(replacement);
		try {
			assertFalse(manager.populateCompleteLegacyCache(id));
			assertEquals(10, cache.getIfPresent("X").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void completedUsersDoNotAccumulateWhileAnotherDirectWriteIsPending() throws Exception {
		var manager = new UserDataManager(mock(AdvancedCorePlugin.class));
		var hold = manager.beginLegacyDirectWrite(UUID.randomUUID(), "SQLITE:X");
		try {
			for (int i = 0; i < 200; i++) {
				var write = manager.beginLegacyDirectWrite(UUID.randomUUID(), "SQLITE:X");
				manager.markLegacyDirectWriteSucceeded(write); manager.endLegacyDirectWrite(write);
			}
			var field = UserDataManager.class.getDeclaredField("legacyDirectSuccesses"); field.setAccessible(true);
			assertTrue(((Map<?, ?>) field.get(manager)).isEmpty(), "completed users must not remain tracked behind an unrelated pending write");
		} finally { manager.endLegacyDirectWrite(hold); manager.getTimer().shutdownNow(); }
	}
	@Test void uncachedFailedDirectRetrySupersedesEarlierBulkOverride() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		doThrow(new IllegalStateException("failed scalar")).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			when(user.isCached()).thenReturn(false); assertThrows(IllegalStateException.class, () -> data.setInt("Activations", 20, false));
			cache.processChanges(); assertEquals(20, stored.get());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void uncachedDirectWriteSupersedesBulkOverrideInRetainedCache() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			when(user.isCached()).thenReturn(false); data.setInt("Activations", 20, false);
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(21, cache.getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void successfulBulkSupersedesStagedChangeAcrossCheckpoint() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var pool = Executors.newSingleThreadExecutor();
		try {
			var checkpoint = pool.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); cache.updateCache(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }));
			assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.addChange(new UserDataChangeInt("Activations", 7), true);
			data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			release.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
			assertEquals(10, cache.getIfPresent("Activations").getInt()); assertTrue(cache.hasChangesToProcess());
		} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void failedExternalBulkKeepsEarlierQueuedChange() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenReturn(new ArrayList<>(List.of(new Column("Activations", new DataValueInt(1)))));
		doThrow(new IllegalStateException("failed bulk")).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertThrows(IllegalStateException.class, () -> data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10)))));
			assertTrue(cache.hasChangesToProcess()); assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(3, cache.getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void newerQueuedChangeSurvivesInFlightExternalBulkWrite() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var worker = Executors.newSingleThreadExecutor();
		try {
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			var bulk = worker.submit(() -> data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10)))));
			assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.addChange(new UserDataChangeInt("Activations", 20), true);
			release.countDown(); bulk.get(5, TimeUnit.SECONDS); assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(21, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void externalBulkWriteInvalidatesArithmeticUntilFreshRead() {
		externalBulkWriteInvalidatesArithmeticUntilFreshRead(false);
	}
	@Test void externalBulkWriteSupersedesEarlierQueuedArithmetic() {
		externalBulkWriteInvalidatesArithmeticUntilFreshRead(true);
	}
	private void externalBulkWriteInvalidatesArithmeticUntilFreshRead(boolean earlierQueued) {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		try {
			if (earlierQueued) assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, cache.getIfPresent("Activations").getInt());
			cache.processChanges(); assertEquals(11, stored.get());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void cacheRetirementCannotAcknowledgeADroppedAsyncDirectWrite() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = spy(new UserDataCache(manager, uuid));
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		var removalEntered = new CountDownLatch(1); var removalRelease = new CountDownLatch(1);
		doAnswer(call -> { removalEntered.countDown(); if (!removalRelease.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); }).when(cache).beginRemoval();
		try {
			data.setInt("Activations", 10, false, true); assertNotNull(pending.get());
			var cancelledObserver = manager.clearCacheAsyncCompletion().toCompletableFuture();
			var retirement = manager.clearCacheAsyncCompletion().toCompletableFuture();
			assertTrue(cancelledObserver.cancel(false));
			assertFalse(retirement.isDone(), "retirement must wait for the accepted write");
			assertThrows(IllegalStateException.class, () -> data.setInt("Activations", 12, false, true));
			var writer = java.util.concurrent.CompletableFuture.runAsync(pending.get());
			assertTrue(removalEntered.await(5, TimeUnit.SECONDS));
			assertDoesNotThrow(() -> writer.get(1, TimeUnit.SECONDS), "writer must not wait for the retirement flush");
			assertFalse(retirement.isDone());
			removalRelease.countDown();
			assertDoesNotThrow(retirement::join);
			assertEquals(10, stored.get());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
		} finally { removalRelease.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void failedAcceptedDirectWriteCannotBeAcknowledgedByRetirement() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doThrow(new IllegalStateException("synthetic write failure")).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var persisted = new java.util.concurrent.atomic.AtomicInteger(1);
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) persisted.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), anyBoolean());
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			var retirement = manager.clearCacheAsyncCompletion().toCompletableFuture();
			assertThrows(IllegalStateException.class, pending.get()::run);
			assertThrows(java.util.concurrent.CompletionException.class, retirement::join, "failed direct write cannot be reported as durable retirement");
			assertSame(cache, manager.getUserDataCache().get(uuid));
			assertTrue(cache.hasChangesToProcess(), "failed accepted write must remain retryable");
			assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			assertEquals(10, persisted.get(), "retry must persist the accepted cached value");
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void retryOfFailedDirectWriteMustNotOverwriteANewerCachedValue() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doThrow(new IllegalStateException("synthetic write failure")).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var persisted = new java.util.concurrent.atomic.AtomicInteger(1);
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) persisted.set(column.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), anyBoolean());
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			var retirement = manager.clearCacheAsyncCompletion().toCompletableFuture();
			assertThrows(IllegalStateException.class, pending.get()::run);
			assertThrows(java.util.concurrent.CompletionException.class, retirement::join, "failed direct write cannot be reported as durable retirement");
			assertSame(cache, manager.getUserDataCache().get(uuid));
			assertTrue(cache.hasChangesToProcess(), "failed accepted write must remain retryable");
			cache.addChange(new UserDataChangeInt("Activations", 20), true);
			assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			assertEquals(20, persisted.get(), "retry must preserve the newer accepted value");
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void uncachedFailedDirectWriteCannotBeAcknowledgedByRetirement() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var sqlite = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class); when(plugin.getSQLiteUserTable()).thenReturn(sqlite);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doThrow(new IllegalStateException("synthetic write failure")).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(users.getUser(uuid, false)).thenReturn(user); when(user.getUserData()).thenReturn(data); var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			assertThrows(IllegalStateException.class, pending.get()::run);
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join(), "uncached failed write cannot be acknowledged after its task exits");
			var cache = new UserDataCache(manager, uuid);
			cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
			manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			data.setInt("Activations", 11, true, true);
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join(), "queued cache update cannot acknowledge the failed direct write");
			data.setInt(UserStorage.SQLITE, "Activations", 10, false, false);
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join(), "another storage cannot acknowledge the failed write");
			doNothing().when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
			data.setInt("Activations", 10, false, false);
			assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void olderUncachedFailureDoesNotEraseNewerSuccessfulWrite() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doAnswer(call -> { if (((DataValue) call.getArgument(2)).getInt() == 10) throw new IllegalStateException("older write failed"); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			data.setInt("Activations", 20, false, false);
			assertThrows(IllegalStateException.class, pending.get()::run);
			assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test void olderUncachedSuccessCannotAcknowledgeANewerFailedWrite() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		doAnswer(call -> { if (((DataValue) call.getArgument(2)).getInt() == 20) throw new IllegalStateException("newer write failed"); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			assertThrows(IllegalStateException.class, () -> data.setInt("Activations", 20, false, false));
			pending.get().run();
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test void failedUncachedBulkWriteRequiresSuccessfulRetryBeforeRetirement() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		doThrow(new IllegalStateException("synthetic bulk failure")).when(table).update(eq(uuid.toString()), anyList(), anyBoolean());
		var data = new UserData(user); var values = new HashMap<String, DataValue>(Map.of("Activations", new DataValueInt(10), "Other", new DataValueInt(3)));
		try {
			assertThrows(IllegalStateException.class, () -> data.setValues(UserStorage.MYSQL, values));
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			doNothing().when(table).update(eq(uuid.toString()), anyList(), anyBoolean());
			data.setValues(UserStorage.MYSQL, new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join(), "partial retry cannot acknowledge another failed key");
			data.setValues(UserStorage.MYSQL, new HashMap<>(Map.of("Other", new DataValueInt(3))));
			assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void asyncDirectSetterKeepsAdmissionUntilExecutionAndReleasesOnRejection() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		var data = new UserData(user); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var task = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
		try {
			data.setInt("Activations", 10, false, true);
			assertNotNull(task.get()); assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			task.get().run();
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, cache.getIfPresent("Activations").getInt());
			doThrow(new java.util.concurrent.RejectedExecutionException("synthetic rejection")).when(timer).execute(any(Runnable.class));
			assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> data.setInt("Activations", 12, false, true));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedOlderMysqlAliasCannotOverwriteNewerCommittedValue() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		var first = new java.util.concurrent.atomic.AtomicBoolean(true);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		when(table.caseInsensitiveColumnNames()).thenReturn(true);
		when(user.isCached()).thenAnswer(call -> { if (first.compareAndSet(true, false)) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return true; });
		doThrow(new java.util.concurrent.RejectedExecutionException("synthetic rejection")).when(timer).execute(any(Runnable.class));
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { for (Column col : call.<List<Column>>getArgument(1)) if (col.getName().equalsIgnoreCase("Activations")) stored.set(col.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache); when(user.getCache()).thenReturn(cache);
		var worker = Executors.newSingleThreadExecutor();
		try {
			var older = worker.submit(() -> assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> data.setInt("activations", 2, false, true)));
			assertTrue(entered.await(5, TimeUnit.SECONDS)); data.setInt("Activations", 10, false); release.countDown(); older.get(5, TimeUnit.SECONDS);
			assertEquals(10, cache.getIfPresent("Activations").getInt()); cache.processChanges(); assertEquals(10, stored.get());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedOlderCaseSensitiveKeyRemainsIndependent() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.ConcurrentHashMap<String, Integer>(); stored.put("Activations", 1); stored.put("activations", 1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new java.util.concurrent.atomic.AtomicBoolean(true);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer);
		when(table.caseInsensitiveColumnNames()).thenReturn(false);
		when(user.isCached()).thenAnswer(call -> { if (first.compareAndSet(true, false)) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return true; });
		doThrow(new java.util.concurrent.RejectedExecutionException("synthetic rejection")).when(timer).execute(any(Runnable.class));
		doAnswer(call -> { stored.put(call.getArgument(1), ((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), anyString(), any(DataValue.class));
		doAnswer(call -> { for (Column col : call.<List<Column>>getArgument(1)) stored.put(col.getName(), col.getValue().getInt()); return null; }).when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1), "activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache); when(user.getCache()).thenReturn(cache);
		var worker = Executors.newSingleThreadExecutor();
		try {
			var older = worker.submit(() -> assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> data.setInt("activations", 2, false, true)));
			assertTrue(entered.await(5, TimeUnit.SECONDS)); data.setInt("Activations", 10, false); release.countDown(); older.get(5, TimeUnit.SECONDS);
			cache.processChanges(); assertEquals(10, stored.get("Activations")); assertEquals(2, stored.get("activations"));
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedUncachedOlderMysqlAliasDoesNotBlockRetirement() throws Exception { uncachedAliasFailure(true); }
	@Test void rejectedUncachedCaseSensitiveKeyStillBlocksRetirement() throws Exception { uncachedAliasFailure(false); }
	private void uncachedAliasFailure(boolean folds) throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var timer = mock(ScheduledExecutorService.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new java.util.concurrent.atomic.AtomicBoolean(true);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(plugin.getTimer()).thenReturn(timer); when(table.caseInsensitiveColumnNames()).thenReturn(folds);
		when(user.isCached()).thenAnswer(call -> { if (first.compareAndSet(true, false)) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return false; });
		doThrow(new java.util.concurrent.RejectedExecutionException("synthetic rejection")).when(timer).execute(any(Runnable.class));
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); String key = call.getArgument(1); UserDataCache.recordLegacyProviderCommit(uuid.toString(), List.of(key), UserStorage.MYSQL, folds); return null; }).when(table).update(eq(uuid.toString()), anyString(), any(DataValue.class));
		var data = new UserData(user); var worker = Executors.newSingleThreadExecutor();
		try {
			var older = worker.submit(() -> assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> data.setInt("activations", 2, false, true)));
			assertTrue(entered.await(5, TimeUnit.SECONDS)); data.setInt("Activations", 10, false); release.countDown(); older.get(5, TimeUnit.SECONDS);
			assertEquals(10, stored.get());
			if (folds) assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, TimeUnit.SECONDS));
			else assertThrows(java.util.concurrent.ExecutionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, TimeUnit.SECONDS));
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void completedMysqlAliasReconcilesOlderSnapshot() { mysqlSnapshotAliases(true); }
	@Test void foldedMysqlStoredKeyPreventsConflictingRegisteredDefault() { mysqlStoredKeyAndDefault(true); }
	@Test void caseSensitiveMysqlStoredKeyRetainsDistinctRegisteredDefault() { mysqlStoredKeyAndDefault(false); }
	@Test void mariaDbStoredAliasSupportsWarmLegacyArithmetic() throws Exception {
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		Field type = com.bencodez.simpleapi.sql.mysql.AbstractSqlTable.class.getDeclaredField("dbType");
		type.setAccessible(true);
		type.set(table, com.bencodez.simpleapi.sql.mysql.DbType.MARIADB);
		doCallRealMethod().when(table).caseInsensitiveColumnNames();
		assertEquals(Boolean.TRUE, table.caseInsensitiveColumnNames());
		mysqlStoredKeyAndDefault(true, table);
		type.set(table, com.bencodez.simpleapi.sql.mysql.DbType.POSTGRESQL);
		assertEquals(Boolean.FALSE, table.caseInsensitiveColumnNames());
		type.set(table, null);
		assertNull(table.caseInsensitiveColumnNames());
	}
	private void mysqlStoredKeyAndDefault(boolean folds) {
		mysqlStoredKeyAndDefault(folds, null);
	}
	private void mysqlStoredKeyAndDefault(boolean folds, com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL actualTable) {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = actualTable == null ? mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class) : actualTable; var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); if (actualTable == null) when(table.caseInsensitiveColumnNames()).thenReturn(folds);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		when(table.getExactStrict(uuid.toString())).thenReturn(new ArrayList<>(List.of(new Column("activations", new DataValueInt(9)))));
		manager.addKey(new com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt("Activations"));
		try {
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			var result = manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null);
			assertNotNull(result); assertTrue(result.applied());
			assertEquals(folds ? 10 : 9, manager.getPublishedCache(uuid).getIfPresent("activations").getInt());
			if (!folds) assertEquals(1, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void completedCaseSensitiveKeyDoesNotRewriteOtherSnapshotKey() { mysqlSnapshotAliases(false); }
	private void mysqlSnapshotAliases(boolean folds) {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table); when(table.caseInsensitiveColumnNames()).thenReturn(folds);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = new UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		try {
			cache.addChange(new UserDataChangeInt("x", 2), true); cache.addChange(new UserDataChangeInt("Other", 3), true); long snapshot = cache.getSharedSnapshotVersion();
			cache.processChanges(); assertTrue(cache.tryPublishCompleteLegacySnapshot(new HashMap<>(Map.of("X", new DataValueInt(1))), snapshot));
			assertEquals(folds ? 2 : 1, cache.getIfPresent("X").getInt()); assertEquals(2, cache.getIfPresent("x").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void directCacheUpdateDoesNotWaitForSlowLegacyCheckpoint() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin); var cache = new UserDataCache(manager, UUID.randomUUID());
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
		try {
			var checkpoint = pool.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); cache.updateCache(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			cache.addChange(new UserDataChangeInt("Activations", 7), true);
			var writer = pool.submit(() -> cache.addChange(new UserDataChangeInt("Activations", 10), false));
			assertDoesNotThrow(() -> writer.get(1, TimeUnit.SECONDS), "cache publication must not wait on checkpoint storage");
			assertEquals(10, cache.getIfPresent("Activations").getInt());
			release.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
			assertEquals(10, cache.getIfPresent("Activations").getInt(), "earlier staged value cannot overwrite the later direct update");
			assertTrue(cache.hasChangesToProcess());
		} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void directCachedSetterCannotLoseAnInterveningCreditFlush() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString());
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = spy(new UserDataCache(manager, uuid));
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		doAnswer(call -> { call.callRealMethod(); entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return null; })
				.when(cache).addChange(any(com.bencodez.advancedcore.api.user.usercache.change.UserDataChange.class), eq(false));
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; })
				.when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var worker = Executors.newSingleThreadExecutor();
		try {
			var setter = worker.submit(() -> data.setInt("Activations", 10, false)); assertTrue(entered.await(5, TimeUnit.SECONDS));
			var during = manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null);
			if (during != null) cache.processChanges();
			release.countDown(); setter.get(5, TimeUnit.SECONDS);
			if (during == null) {
				assertTrue(manager.populateCompleteLegacyCache(uuid));
				assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
				cache.processChanges();
			}
			assertEquals(cache.getIfPresent("Activations").getInt(), stored.get());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void directLegacyStringWriteFencesAnOlderStrictRead() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1); var reads = new java.util.concurrent.atomic.AtomicInteger();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> { int value = stored.get();
			if (reads.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
			return new ArrayList<>(List.of(new Column("Activations", new DataValueInt(value)))); });
		doAnswer(call -> { stored.set(Integer.parseInt(((DataValue) call.getArgument(2)).getString())); return null; })
				.when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			var load = worker.submit(() -> manager.populateCompleteLegacyCache(uuid)); assertTrue(entered.await(5, TimeUnit.SECONDS));
			data.setString("Activations", "10");
			release.countDown(); assertFalse(load.get(5, TimeUnit.SECONDS));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void legacyMutationNotificationCanReenterManagerForSameUser() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		UserDataManager manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		try {
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD,
					keys -> assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied())).applied());
			assertEquals(3, cache.getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void internalLegacyBatchDoesNotFenceLaterCachedArithmetic() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1); var calls = new java.util.concurrent.atomic.AtomicInteger();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { if (calls.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
			for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; })
				.when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			UserDataCache cache = manager.getPublishedCache(uuid);
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			var flush = worker.submit(cache::processChanges); assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			release.countDown(); flush.get(5, TimeUnit.SECONDS);
			cache.processChanges(); assertEquals(3, stored.get());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void externalLegacyBulkWriteFencesAnOlderStrictRead() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var reads = new java.util.concurrent.atomic.AtomicInteger();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> { int value = stored.get();
			if (reads.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
			return new ArrayList<>(List.of(new Column("Activations", new DataValueInt(value)))); });
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; })
				.when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			var load = worker.submit(() -> manager.populateCompleteLegacyCache(uuid));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			data.setValues(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
			release.countDown(); assertFalse(load.get(5, TimeUnit.SECONDS));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void ordinaryLegacyPopulationCannotPublishAcrossRetiredDirectWrite() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExact(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			manager.getUserDataCache().put(uuid, new UserDataCache(manager, uuid));
			var setter = worker.submit(() -> data.setInt("Activations", 10));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			manager.removeCache(uuid, null);
			manager.cacheUser(uuid, null);
			release.countDown(); setter.get(5, TimeUnit.SECONDS);
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void directLegacyWriteCannotRetireIntoAStaleReplacement() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			manager.getUserDataCache().put(uuid, new UserDataCache(manager, uuid));
			var setter = worker.submit(() -> data.setInt("Activations", 10));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			manager.removeCache(uuid, null);
			boolean populated = manager.populateCompleteLegacyCache(uuid);
			release.countDown(); setter.get(5, TimeUnit.SECONDS);
			if (!populated) assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadCannotPublishDuringDirectWrite() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			var setter = worker.submit(() -> data.setInt("Activations", 10));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertFalse(manager.populateCompleteLegacyCache(uuid));
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			release.countDown(); setter.get(5, TimeUnit.SECONDS);
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void concurrentLegacyPopulationsCannotReplaceTheNewerSnapshot() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserData data = plugin.getUserManager().getUser(uuid, false).getUserData();
		when(data.getKeys()).thenReturn(new ArrayList<>());
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(data.getValues()).thenAnswer(call -> {
			if (calls.getAndIncrement() == 0) {
				entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
				return new HashMap<>(Map.of("Activations", new DataValueInt(1)));
			}
			return new HashMap<>(Map.of("Activations", new DataValueInt(8)));
		});
		var worker = Executors.newSingleThreadExecutor();
		try {
			var oldLoad = worker.submit(() -> manager.cacheUser(uuid, null));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			UserDataCache generation = manager.getUserDataCache().get(uuid);
			manager.cacheUser(uuid, null);
			release.countDown(); oldLoad.get(5, TimeUnit.SECONDS);
			assertSame(generation, manager.getUserDataCache().get(uuid));
			assertEquals(8, generation.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void customLegacyRowReaderRemainsAvailableWithoutClaimingCompleteAuthority() {
		var user = mock(AdvancedCoreUser.class);
		var data = new UserData(user) {
			@Override public List<Column> getMySqlRow() {
				return List.of(new Column("Activations", new DataValueInt(7)));
			}
		};
		var plugin = mock(AdvancedCorePlugin.class);
		when(user.getPlugin()).thenReturn(plugin); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		assertFalse(data.supportsStrictCompleteValues());
		assertNull(data.getValuesIfStrictComplete());
		assertEquals(7, data.getValues().get("Activations").getInt());
	}
	@Test void nativeStrictCapabilityDoesNotReadStorage() {
		var user = mock(AdvancedCoreUser.class); var plugin = mock(AdvancedCorePlugin.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(user.getPlugin()).thenReturn(plugin); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		assertTrue(new UserData(user).supportsStrictCompleteValues());
		verifyNoInteractions(table);
		verify(plugin, never()).getSQLiteUserTable();
	}
	@Test void customReaderMayExplicitlyOptIntoStrictValues() {
		var user = mock(AdvancedCoreUser.class);
		var data = new UserData(user) {
			@Override public boolean supportsStrictCompleteValues() { return true; }
			@Override public HashMap<String, DataValue> getValuesIfStrictComplete() {
				return new HashMap<>(Map.of("Activations", new DataValueInt(7)));
			}
		};
		assertTrue(data.supportsStrictCompleteValues());
		assertEquals(7, data.getValuesIfStrictComplete().get("Activations").getInt());
	}
	@Test void legacyPopulationReportsUnsupportedReaderWithoutPublishingPlaceholder() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); UUID uuid = UUID.randomUUID();
		var data = new UserData(user) { @Override public List<Column> getMySqlRow() { return List.of(); } };
		when(user.getPlugin()).thenReturn(plugin); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getUserManager()).thenReturn(users); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(data);
		var manager = new UserDataManager(plugin);
		try {
			assertEquals(UserDataManager.LegacyPopulationResult.UNSUPPORTED, manager.populateCompleteLegacyCacheIfSupported(uuid));
			assertFalse(manager.getUserDataCache().containsKey(uuid));
			verify(plugin, never()).getMysql();
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyPopulationPropagatesSupportedStrictReaderFailure() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); UUID uuid = UUID.randomUUID();
		var data = new UserData(user) {
			@Override public boolean supportsStrictCompleteValues() { return true; }
			@Override public HashMap<String, DataValue> getValuesIfStrictComplete() { throw new IllegalStateException("strict read failed"); }
		};
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getUserManager()).thenReturn(users); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(data);
		var manager = new UserDataManager(plugin);
		try { assertThrows(IllegalStateException.class, () -> manager.populateCompleteLegacyCacheIfSupported(uuid)); }
		finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyPopulationReportsActiveWriterAsRetryWithoutReading() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var admission = manager.beginLegacyDirectWrite(uuid);
		try {
			assertEquals(UserDataManager.LegacyPopulationResult.RETRY, manager.populateCompleteLegacyCacheIfSupported(uuid));
			verify(users, never()).getUser(eq(uuid), anyBoolean());
		} finally { manager.endLegacyDirectWrite(admission); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingLegacyAwardRetainsOriginalOperationUntilDirectWriterCompletes() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = mock(AdvancedCoreUser.class); var data = mock(UserData.class);
		when(users.getUser(uuid, false)).thenReturn(stored); when(stored.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var admission = manager.beginLegacyDirectWrite(uuid); var notices = new java.util.concurrent.atomic.AtomicInteger();
		try {
			var completion = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1),
					UserDataCache.IntegerMutation.ADD, keys -> notices.incrementAndGet()).toCompletableFuture();
			var second = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1),
					UserDataCache.IntegerMutation.ADD, keys -> notices.incrementAndGet()).toCompletableFuture();
			assertFalse(completion.isDone()); assertEquals(1, cache.getIfPresent("Activations").getInt());
			assertFalse(second.isDone());
			assertThrows(IllegalStateException.class, () -> manager.withLegacyIntegerOwnerReplacement(() -> fail("owner must remain open")));
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 9), UserDataCache.IntegerMutation.SET, null),
					"a synchronous operation must not overtake the retained operations");
			assertThrows(IllegalStateException.class, manager::clearCache);
			assertThrows(IllegalStateException.class, () -> manager.removeCache(uuid, null));
			assertFalse(completion.isDone(), "retirement refusal must retain the admitted operation");
			manager.endLegacyDirectWrite(admission); admission = null;
			assertTrue(completion.get(5, TimeUnit.SECONDS).applied());
			assertTrue(second.get(5, TimeUnit.SECONDS).applied());
			assertEquals(3, cache.getIfPresent("Activations").getInt()); assertEquals(2, notices.get());
		} finally { if (admission != null) manager.endLegacyDirectWrite(admission); manager.getTimer().shutdownNow(); }
	}
	@Test void legacyPlatformMutationDoesNotWaitForContendedStripe() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var blocked = new java.util.concurrent.locks.ReentrantLock() {
			@Override public boolean tryLock() { return false; }
			@Override public boolean tryLock(long timeout, TimeUnit unit) { calls.incrementAndGet(); return false; }
		};
		var field = UserDataManager.class.getDeclaredField("legacyMutationStripes"); field.setAccessible(true);
		var stripes = (java.util.concurrent.locks.ReentrantLock[]) field.get(manager);
		stripes[(uuid.hashCode() & Integer.MAX_VALUE) % stripes.length] = blocked;
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class)); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null));
			assertEquals(0, calls.get(), "platform threads must not wait on the contended stripe");
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void pendingAwardAdmissionRejectsActiveOwnerReplacementWithoutWaiting() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var worker = Executors.newSingleThreadExecutor();
		try {
			var replacing = worker.submit(() -> manager.withLegacyIntegerOwnerReplacement(() -> {
				entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
			}));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertThrows(IllegalStateException.class, () -> manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			release.countDown(); replacing.get(5, TimeUnit.SECONDS);
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void warmLegacyMutationPinsOwnerUntilItsApplicationCompletes() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(plugin.getMysql()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, table, null));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		when(table.caseInsensitiveColumnNames()).thenAnswer(call -> {
			entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return false;
		});
		var worker = Executors.newSingleThreadExecutor();
		try {
			var mutation = worker.submit(() -> manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertThrows(IllegalStateException.class, () -> manager.withLegacyIntegerOwnerReplacement(() -> fail("owner must stay open")));
			release.countDown(); assertTrue(mutation.get(5, TimeUnit.SECONDS).applied());
			assertEquals(2, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingLegacyAwardFailsBeforeMutationWhenOwnerChanges() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var first = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var second = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var firstOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, first);
		var secondOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, second);
		var owner = new java.util.concurrent.atomic.AtomicReference<>(firstOwner);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenAnswer(call -> owner.get());
		when(plugin.getSQLiteUserTable()).thenAnswer(call -> owner.get().table());
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var completion = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1),
					UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			owner.set(secondOwner); release.countDown();
			assertThrows(java.util.concurrent.ExecutionException.class, () -> completion.get(5, TimeUnit.SECONDS));
			assertEquals(1, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingLegacyMutationsPreserveAddSetAddOrder() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var stored = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(users.getUser(uuid, false)).thenReturn(stored); when(stored.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		var manager = new UserDataManager(plugin); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var first = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var second = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 10), UserDataCache.IntegerMutation.SET, null).toCompletableFuture();
			var third = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 2), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertFalse(first.isDone()); assertFalse(second.isDone()); assertFalse(third.isDone());
			manager.endLegacyDirectWrite(direct); direct = null;
			first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS); third.get(5, TimeUnit.SECONDS);
			assertEquals(12, cache.getIfPresent("Activations").getInt());
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void terminalPendingTimeoutDoesNotApplyAfterWriterEnds() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var pending = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var drained = manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture();
			assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(8, TimeUnit.SECONDS));
			assertThrows(java.util.concurrent.ExecutionException.class, () -> drained.get(5, TimeUnit.SECONDS));
			manager.endLegacyDirectWrite(direct); direct = null;
			assertEquals(1, cache.getIfPresent("Activations").getInt());
			assertTrue(manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture().isDone());
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void asyncRetirementWaitsForPendingAwardAndItsFlush() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		var manager = new UserDataManager(plugin); when(users.getDataManager()).thenReturn(manager);
		var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var award = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var retirement = manager.clearCacheAsyncCompletion().toCompletableFuture();
			assertFalse(award.isDone()); assertFalse(retirement.isDone());
			manager.endLegacyDirectWrite(direct); direct = null;
			assertTrue(award.get(5, TimeUnit.SECONDS).applied());
			retirement.get(5, TimeUnit.SECONDS);
			verify(data).setValues(any(HashMap.class));
			assertFalse(manager.getUserDataCache().containsKey(uuid));
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void acceptedPendingAwardCannotReportCancellationAndThenApply() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var storedUser = mock(AdvancedCoreUser.class); var storedData = mock(UserData.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		when(users.getUser(uuid, false)).thenReturn(storedUser); when(storedUser.getUserData()).thenReturn(storedData);
		when(storedData.supportsStrictCompleteValues()).thenReturn(true);
		when(storedData.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var result = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertFalse(result.cancel(false), "an accepted ticket must remain observable until its actual outcome");
			assertFalse(result.complete(null), "an observer cannot forge a successful result");
			assertFalse(result.completeExceptionally(new IllegalStateException("forged")), "an observer cannot forge failure");
			assertThrows(UnsupportedOperationException.class, () -> result.orTimeout(1, TimeUnit.MILLISECONDS));
			var observer = result.thenApply(value -> value).toCompletableFuture();
			assertTrue(observer.cancel(false));
			assertFalse(result.isDone(), "cancelling a derived observer does not settle the actual receipt");
			manager.endLegacyDirectWrite(direct); direct = null;
			assertTrue(result.get(5, TimeUnit.SECONDS).applied());
			assertEquals(2, cache.getIfPresent("Activations").getInt());
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void legacyCompletionCannotBlockTheOnlyWorkerWaitingForLaterReceipt() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Points", new DataValueInt(1))));
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var successor = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<?>>();
			var notified = new java.util.concurrent.CompletableFuture<Void>();
			var first = manager.submitLegacyIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, keys -> {
				try { java.util.concurrent.CompletableFuture.allOf(successor.get()).get(3, TimeUnit.SECONDS); notified.complete(null); }
				catch (Exception e) { notified.completeExceptionally(e); }
			}).toCompletableFuture();
			var second = manager.submitLegacyIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			successor.set(second);
			var observer = first.whenComplete((value, failure) -> {
				assertTrue(second.thenApply(result -> result).join().applied());
				assertTrue(second.minimalCompletionStage().toCompletableFuture().join().applied());
				assertThrows(IllegalStateException.class, () -> manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture().join());
				assertThrows(java.util.concurrent.CompletionException.class, () -> manager.shutdownLegacyIntegerMutations().toCompletableFuture().join());
			});
			manager.endLegacyDirectWrite(direct); direct = null;
			notified.get(5, TimeUnit.SECONDS); observer.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
			assertEquals(3, cache.getIfPresent("Points").getInt());
			assertTrue(manager.submitLegacyIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture().get(5, TimeUnit.SECONDS).applied());
			assertEquals(4, cache.getIfPresent("Points").getInt());
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedAwardReceiptDrainsBeforeRuntimeOwnerCloses() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		var runtime = new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner);
		manager.bindSharedRuntime(runtime); UUID uuid = UUID.randomUUID();
		var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var award = manager.submitSharedIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertFalse(award.cancel(false));
			assertThrows(IllegalStateException.class, () -> manager.withLegacyIntegerOwnerReplacement(() -> fail("owner must not change")));
			var shutdown = manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
			assertFalse(shutdown.isDone()); verify(backend, never()).close();
			release.countDown(); assertTrue(award.get(5, TimeUnit.SECONDS).applied());
			shutdown.get(5, TimeUnit.SECONDS); assertEquals(2, cache.getIfPresent("Activations").getInt()); verify(backend).close();
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedCompletionCannotBlockTheOnlyWorkerWaitingForLaterReceipt() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var finalized = manager.afterSharedMutationFinalization(first).whenComplete((value, failure) ->
					assertTrue(second.join().applied())).toCompletableFuture();
			var observer = first.whenComplete((value, failure) -> {
				assertTrue(second.thenApply(result -> result).join().applied());
				assertTrue(second.minimalCompletionStage().toCompletableFuture().join().applied());
				assertThrows(IllegalStateException.class, () -> manager.pendingSharedIntegerMutations(uuid).toCompletableFuture().join());
				assertThrows(IllegalStateException.class, () -> manager.afterSharedMutationFinalization(first).toCompletableFuture().join());
			});
			release.countDown(); observer.get(5, TimeUnit.SECONDS); finalized.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
			assertEquals(3, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedReplayRetryRemainsUncertainAndDoesNotInventAnOutcome() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.supportsAtomicUserTransactions()).thenReturn(true);
		var runtime = mock(SharedUserDataRuntime.class); when(runtime.backend()).thenReturn(backend);
		when(runtime.transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any()))
				.thenThrow(new IllegalStateException("synthetic uncertain commit"));
		manager.bindSharedRuntime(runtime);
		var original = manager.getTimer(); var executor = mock(ScheduledExecutorService.class);
		var task = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
		doThrow(new java.util.concurrent.RejectedExecutionException("synthetic retry rejection"))
				.when(executor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		Field timer = UserDataManager.class.getDeclaredField("timer"); timer.setAccessible(true); timer.set(manager, executor);
		try {
			UUID uuid = UUID.randomUUID(); String identity = "award:diagnostic";
			manager.acquireSharedReplayLease(uuid, identity);
			var receipt = manager.submitSharedReplayTransaction(uuid, UserStorage.SQLITE, identity, Map.of(), scope -> null).toCompletableFuture();
			var callback = new java.util.concurrent.atomic.AtomicBoolean(); receipt.whenComplete((value, failure) -> callback.set(true));
			assertNotNull(task.get()); task.get().run();
			assertFalse(receipt.isDone(), "an unknown commit is not a definite failure or success");
			assertFalse(callback.get(), "diagnosis must not trigger lease settlement callbacks");
			assertEquals(java.util.List.of(new UserDataManager.IndeterminateReplay(uuid, identity, false)), manager.indeterminateSharedReplays());
			UUID other = UUID.randomUUID(); String checkpoint = "award:checkpoint";
			manager.acquireSharedReplayCheckpointLease(other, checkpoint);
			var cleanup = manager.submitSharedReplayCheckpoint(other, UserStorage.SQLITE, checkpoint, Map.of(), scope -> null).toCompletableFuture();
			task.get().run(); assertFalse(cleanup.isDone());
			assertTrue(manager.indeterminateSharedReplays().contains(new UserDataManager.IndeterminateReplay(other, checkpoint, true)));
			assertDoesNotThrow(() -> manager.requireSharedReplayAwardActive(other, checkpoint), "uncertain cleanup must not mark its lease checkpointed");
			assertFalse(manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().isDone());
			verify(runtime, times(2)).transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any());
			verify(runtime, never()).closeAsync(any());
		} finally { timer.set(manager, original); original.shutdownNow(); }
	}
	@Test void rejectedCheckpointDoesNotFenceAwardAsCleanedUp() throws Exception {
		var manager = new UserDataManager(mock(AdvancedCorePlugin.class));
		var backend = mock(SqlUserBackend.class); when(backend.supportsAtomicUserTransactions()).thenReturn(true);
		var runtime = mock(SharedUserDataRuntime.class); when(runtime.backend()).thenReturn(backend);
		when(runtime.transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any()))
				.thenThrow(new com.bencodez.advancedcore.core.user.storage.SqlUserStorage.ReplayPreconditionException("checkpoint rejected"));
		manager.bindSharedRuntime(runtime); UUID uuid = UUID.randomUUID(); String identity = "checkpoint-rejection";
		try {
			manager.acquireSharedReplayLease(uuid, identity);
			manager.acquireSharedReplayCheckpointLease(uuid, identity);
			var receipt = manager.submitSharedReplayCheckpoint(uuid, UserStorage.SQLITE, identity, Map.of(), scope -> null).toCompletableFuture();
			assertThrows(java.util.concurrent.ExecutionException.class, () -> receipt.get(5, TimeUnit.SECONDS));
			assertDoesNotThrow(() -> manager.requireSharedReplayAwardActive(uuid, identity));
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void checkpointCannotConsumeAnAwardAttemptWithoutItsOwnReservation() {
		var manager = new UserDataManager(mock(AdvancedCorePlugin.class));
		var backend = mock(SqlUserBackend.class); when(backend.supportsAtomicUserTransactions()).thenReturn(true);
		var runtime = mock(SharedUserDataRuntime.class); when(runtime.backend()).thenReturn(backend);
		manager.bindSharedRuntime(runtime); UUID uuid = UUID.randomUUID(); String identity = "paired-checkpoint";
		try {
			manager.acquireSharedReplayLease(uuid, identity);
			assertThrows(IllegalStateException.class, () -> manager.submitSharedReplayCheckpoint(uuid, UserStorage.SQLITE, identity, Map.of(), scope -> null));
			assertDoesNotThrow(() -> manager.requireSharedReplayAwardActive(uuid, identity));
			manager.acquireSharedReplayCheckpointLease(uuid, identity);
			manager.cancelSharedReplayCheckpointReservation(uuid, identity);
			assertDoesNotThrow(() -> manager.requireSharedReplayAwardActive(uuid, identity));
			verify(runtime, never()).transaction(any(UUID.class), any(), anyMap(), any());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void sharedAwardShutdownFlushesToActualBackendBeforeClose() throws Exception {
		try (Fixture fixture = new Fixture()) {
			var runtime = fixture.runtime(); fixture.manager.bindSharedRuntime(runtime);
			var cache = fixture.manager.getCache(fixture.uuid);
			cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1))));
			var receipt = fixture.manager.submitSharedIntegerMutation(fixture.uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var shutdown = fixture.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
			assertFalse(shutdown.isDone()); fixture.tasks.remove(0).run();
			assertTrue(receipt.get(5, TimeUnit.SECONDS).applied()); assertFalse(shutdown.isDone());
			fixture.manager.afterSharedMutationFinalization(receipt).toCompletableFuture().get(5, TimeUnit.SECONDS);
			fixture.tasks.remove(fixture.tasks.size() - 1).run();
			assertFalse(shutdown.isDone(), "retirement must wait for the separately scheduled final flush");
			fixture.tasks.remove(fixture.tasks.size() - 1).run(); shutdown.get(5, TimeUnit.SECONDS);
			assertEquals(2, fixture.first.rows.get(fixture.uuid).get("Points").getInt()); assertFalse(fixture.first.isOpen());
		}
	}
	@Test void sharedSynchronousWriterCannotOvertakeAcceptedQueuedAward() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertNull(cache.tryMutateIntegers(Map.of("Points", 2), UserDataCache.IntegerMutation.ADD, null));
			assertEquals(1, cache.getIfPresent("Points").getInt());
			release.countDown(); assertTrue(first.get(5, TimeUnit.SECONDS).applied());
			assertTrue(cache.tryMutateIntegers(Map.of("Points", 2), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(4, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedAwardWaitsForPriorSynchronousWriterAndKeepsOrder() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var begin = UserDataManager.class.getDeclaredMethod("beginSharedSynchronousIntegerMutation", UUID.class); begin.setAccessible(true);
		var end = UserDataManager.class.getDeclaredMethod("endSharedSynchronousIntegerMutation", UUID.class); end.setAccessible(true);
		assertEquals(true, begin.invoke(manager, uuid));
		try {
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 5), UserDataCache.IntegerMutation.SET, null).toCompletableFuture();
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 2), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertFalse(first.isDone()); assertFalse(second.isDone());
			end.invoke(manager, uuid);
			assertTrue(first.get(5, TimeUnit.SECONDS).applied()); assertTrue(second.get(5, TimeUnit.SECONDS).applied());
			assertEquals(7, cache.getIfPresent("Points").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void synchronousWriterEndingDuringCompletionCannotScheduleTheNextAwardTwice() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var begin = UserDataManager.class.getDeclaredMethod("beginSharedSynchronousIntegerMutation", UUID.class); begin.setAccessible(true);
		var end = UserDataManager.class.getDeclaredMethod("endSharedSynchronousIntegerMutation", UUID.class); end.setAccessible(true);
		var workerBlocked = new java.util.concurrent.CountDownLatch(1); var startWorker = new java.util.concurrent.CountDownLatch(1);
		var callbackEntered = new java.util.concurrent.CountDownLatch(1); var finishCallback = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { workerBlocked.countDown(); try { startWorker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		boolean admitted = false;
		try {
			assertTrue(workerBlocked.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null);
			first.whenComplete((value, failure) -> { callbackEntered.countDown(); try { finishCallback.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
			startWorker.countDown(); assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
			assertEquals(true, begin.invoke(manager, uuid)); admitted = true;
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null);
			end.invoke(manager, uuid); admitted = false;
			finishCallback.countDown(); manager.afterSharedMutationFinalization(first).toCompletableFuture().get(5, TimeUnit.SECONDS);
			assertTrue(second.toCompletableFuture().get(5, TimeUnit.SECONDS).applied());
			manager.getTimer().submit(() -> {}).get(5, TimeUnit.SECONDS);
			assertEquals(3, cache.getIfPresent("Points").getInt(), "the queued award must run exactly once");
		} finally { if (admitted) end.invoke(manager, uuid); startWorker.countDown(); finishCallback.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingSharedObserverWaitsForCapturedOperationsAndCannotCancelThem() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null);
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null);
			var observer = manager.pendingSharedIntegerMutations(uuid).toCompletableFuture();
			var cancelled = manager.pendingSharedIntegerMutations(uuid).toCompletableFuture();
			assertFalse(observer.isDone()); assertTrue(cancelled.cancel(false)); assertFalse(first.toCompletableFuture().isDone());
			release.countDown(); observer.get(5, TimeUnit.SECONDS);
			assertTrue(first.toCompletableFuture().get(5, TimeUnit.SECONDS).applied()); assertTrue(second.toCompletableFuture().get(5, TimeUnit.SECONDS).applied());
			assertEquals(3, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedCompletionCanAwaitSameUserSuccessorThroughAllOf() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var workerEntered = new java.util.concurrent.CountDownLatch(1); var releaseWorker = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { workerEntered.countDown(); try { releaseWorker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(workerEntered.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var callback = first.thenApply(value -> { try { java.util.concurrent.CompletableFuture.allOf(second).get(2, TimeUnit.SECONDS); return value; } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); } });
			releaseWorker.countDown();
			assertTrue(callback.get(5, TimeUnit.SECONDS).applied());
			assertEquals(3, cache.getIfPresent("Points").getInt());
		} finally { releaseWorker.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedMutationListenerCanAwaitQueuedSuccessor() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		var successor = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<?>>();
		var notified = new java.util.concurrent.CompletableFuture<Void>();
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, keys -> {
				try { java.util.concurrent.CompletableFuture.allOf(successor.get()).get(3, TimeUnit.SECONDS); notified.complete(null); }
				catch (Exception e) { notified.completeExceptionally(e); }
			});
			successor.set(manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture());
			release.countDown(); first.toCompletableFuture().get(5, TimeUnit.SECONDS); notified.get(5, TimeUnit.SECONDS);
			assertEquals(3, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedMutationListenerReentersAfterAdmissionAndRefusedCloseKeepsNotifications() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var firstNotification = new java.util.concurrent.CompletableFuture<Void>(); var secondNotification = new java.util.concurrent.CompletableFuture<Void>();
		try {
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, keys -> {
				try {
					assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).applied());
					assertThrows(java.util.concurrent.CompletionException.class, () -> manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().join());
					firstNotification.complete(null);
				} catch (Throwable failure) { firstNotification.completeExceptionally(failure); }
			});
			first.toCompletableFuture().get(5, TimeUnit.SECONDS); firstNotification.get(5, TimeUnit.SECONDS);
			manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD,
					keys -> secondNotification.complete(null)).toCompletableFuture().get(5, TimeUnit.SECONDS);
			secondNotification.get(5, TimeUnit.SECONDS); assertEquals(4, cache.getIfPresent("Points").getInt()); verify(backend, never()).close();
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void directSharedMutationReleasesAdmissionBeforeNotificationSubmission() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = spy(new UserDataManager(plugin));
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		Field active = UserDataManager.class.getDeclaredField("sharedIntegerActiveUsers"); active.setAccessible(true);
		var observed = new java.util.concurrent.atomic.AtomicBoolean();
		doAnswer(call -> { synchronized (manager) { observed.set(!((Map<?, ?>) active.get(manager)).containsKey(uuid)); } return call.callRealMethod(); })
				.when(manager).dispatchSharedMutationNotification(any(Runnable.class));
		try {
			assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, keys -> {}).applied());
			assertTrue(observed.get(), "notification must be submitted after the synchronous admission ends");
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void sharedObserverAndShutdownWaitForPreviouslyQueuedCallback() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var workerEntered = new CountDownLatch(1); var workerRelease = new CountDownLatch(1);
		var callbackEntered = new CountDownLatch(1); var callbackRelease = new CountDownLatch(1);
		manager.getTimer().execute(() -> { workerEntered.countDown(); try { workerRelease.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(workerEntered.await(5, TimeUnit.SECONDS));
			var receipt = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			receipt.whenComplete((value, failure) -> { callbackEntered.countDown(); try { callbackRelease.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
			var observer = manager.pendingSharedIntegerMutations(uuid).toCompletableFuture();
			var shutdown = manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
			workerRelease.countDown(); assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
			assertFalse(observer.isDone()); assertFalse(shutdown.isDone()); verify(backend, never()).close();
			callbackRelease.countDown(); observer.get(5, TimeUnit.SECONDS); shutdown.get(5, TimeUnit.SECONDS); verify(backend).close();
		} finally { workerRelease.countDown(); callbackRelease.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedSharedCallbackDeliveryRetainsAdmissionWithoutInlineFallback() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		Field field = UserDataManager.class.getDeclaredField("mutationCallbacks"); field.setAccessible(true);
		var lane = (java.util.concurrent.ThreadPoolExecutor) field.get(manager);
		var entered = new java.util.concurrent.CountDownLatch(4); var release = new java.util.concurrent.CountDownLatch(1);
		for (int i = 0; i < 4; i++) lane.execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		for (int i = 0; i < 768; i++) lane.execute(() -> {});
		try {
			var receipt = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var callback = new java.util.concurrent.atomic.AtomicBoolean(); receipt.whenComplete((value, failure) -> callback.set(true));
			manager.getTimer().submit(() -> {}).get(5, TimeUnit.SECONDS);
			assertEquals(2, cache.getIfPresent("Points").getInt()); assertFalse(receipt.isDone()); assertFalse(callback.get());
			assertTrue(manager.hasUnsettledLegacyIntegerMutations());
			assertFalse(manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().isDone());
			assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, manager.getLastDeferredStorageFailure());
		} finally { release.countDown(); lane.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void lateReceiptCallbackCanRunOnRegisteringThread() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new SharedUserDataRuntime(backend, mock(UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		try {
			var receipt = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			receipt.get(5, TimeUnit.SECONDS); Thread registering = Thread.currentThread();
			assertSame(registering, receipt.thenApply(value -> Thread.currentThread()).get(5, TimeUnit.SECONDS));
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void pendingSharedObserverPropagatesAnAdmittedOperationFailure() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class)));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(Integer.MAX_VALUE)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var award = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null);
			var observer = manager.pendingSharedIntegerMutations(uuid).toCompletableFuture(); assertFalse(observer.isDone());
			release.countDown(); assertThrows(java.util.concurrent.ExecutionException.class, () -> award.toCompletableFuture().get(5, TimeUnit.SECONDS));
			assertThrows(java.util.concurrent.ExecutionException.class, () -> observer.get(5, TimeUnit.SECONDS));
			assertEquals(Integer.MAX_VALUE, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void deferredSharedAwardRejectionDoesNotCallObserversUnderManagerLock() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID();
		var begin = UserDataManager.class.getDeclaredMethod("beginSharedSynchronousIntegerMutation", UUID.class); begin.setAccessible(true);
		var end = UserDataManager.class.getDeclaredMethod("endSharedSynchronousIntegerMutation", UUID.class); end.setAccessible(true);
		assertEquals(true, begin.invoke(manager, uuid));
		try {
			var receipt = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var observer = receipt.handle((value, failure) -> { assertNotNull(failure); assertFalse(Thread.holdsLock(manager)); return null; });
			manager.getTimer().shutdownNow(); end.invoke(manager, uuid); observer.get(5, TimeUnit.SECONDS);
			manager.afterSharedMutationFinalization(receipt).toCompletableFuture().get(5, TimeUnit.SECONDS);
			assertFalse(manager.hasUnsettledLegacyIntegerMutations());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void uncertainSharedAwardBlocksLaterAwardAndRetirement() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = spy(new UserDataCache(manager, uuid)); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var attempted = new java.util.concurrent.CountDownLatch(1);
		doAnswer(call -> { attempted.countDown(); throw new UserDataCache.SharedIntegerMutationUncertainException(new IllegalStateException("uncertain")); })
				.doCallRealMethod().when(cache).tryMutateIntegers(anyMap(), any(), any());
		try {
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 2), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			assertTrue(attempted.await(5, TimeUnit.SECONDS));
			assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
			assertFalse(first.isDone()); assertEquals(1, cache.getIfPresent("Points").getInt());
			assertFalse(manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().isDone()); verify(backend, never()).close();
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void unsupportedReplayProviderRejectsBeforeAdmission() {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		try {
			assertThrows(UnsupportedOperationException.class, () -> manager.submitSharedReplayTransaction(UUID.randomUUID(), UserStorage.SQLITE, "fixture-operation", Map.of(), scope -> null));
			assertFalse(manager.hasUnsettledLegacyIntegerMutations()); verify(backend, never()).user(any());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedSharedAwardHasNoLaterMutation() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		manager.getTimer().shutdownNow();
		var receipt = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
		assertThrows(java.util.concurrent.ExecutionException.class, () -> receipt.get(5, TimeUnit.SECONDS));
		assertEquals(1, cache.getIfPresent("Points").getInt());
		assertFalse(manager.hasUnsettledLegacyIntegerMutations());
	}
	@Test void sharedAwardCompletionCannotAwaitItsOwnRetirement() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = new UserDataManager(plugin);
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var award = manager.submitSharedIntegerMutation(uuid, Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var callback = award.thenRun(() -> assertThrows(java.util.concurrent.CompletionException.class,
					() -> manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().join()));
			release.countDown(); callback.get(5, TimeUnit.SECONDS);
			manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().get(5, TimeUnit.SECONDS);
			assertEquals(2, cache.getIfPresent("Points").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void sharedAwardReceiptsPreserveAdmissionOrderAndRejectRetirement() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var manager = spy(new UserDataManager(plugin));
		doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(manager).dispatchSharedUserDataNotification(any(Runnable.class));
		var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.isOpen()).thenReturn(true);
		var owner = mock(com.bencodez.advancedcore.core.user.runtime.UserCacheOwner.class);
		manager.bindSharedRuntime(new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner));
		UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid); cache.configureSharedStorage(values -> {}, Runnable::run);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		manager.getTimer().execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var callbacks = new java.util.concurrent.atomic.AtomicInteger(); var overtook = new java.util.concurrent.atomic.AtomicBoolean();
			var first = manager.submitSharedIntegerMutation(uuid, Map.of("Activations", 2), UserDataCache.IntegerMutation.ADD,
					keys -> { callbacks.incrementAndGet(); overtook.set(cache.tryMutateIntegers(Map.of("Activations", 99), UserDataCache.IntegerMutation.ADD, null) != null); }).toCompletableFuture();
			var second = manager.submitSharedIntegerMutation(uuid, Map.of("Activations", 10), UserDataCache.IntegerMutation.SET, null).toCompletableFuture();
			var third = manager.submitSharedIntegerMutation(uuid, Map.of("Activations", 4), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var callback = first.thenRun(() -> assertNull(cache.tryMutateIntegers(Map.of("Activations", 99), UserDataCache.IntegerMutation.ADD, null)));
			var retirement = manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
			assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> manager.submitSharedIntegerMutation(uuid, Map.of("Activations", 99), UserDataCache.IntegerMutation.ADD, null));
			release.countDown(); first.get(5, TimeUnit.SECONDS); callback.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS); third.get(5, TimeUnit.SECONDS);
			retirement.get(5, TimeUnit.SECONDS); assertEquals(1, callbacks.get()); assertFalse(overtook.get());
			assertEquals(14, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void rejectedPendingAwardIsTerminalAndDoesNotApply() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); manager.getTimer().shutdownNow();
		var result = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
		assertThrows(java.util.concurrent.ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
		assertTrue(manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture().isDone());
		assertEquals(1, cache.getIfPresent("Activations").getInt());
	}
	@Test void rejectedSuccessorDoesNotReplayCompletedAward() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		var timer = mock(ScheduledExecutorService.class); var task = new java.util.concurrent.atomic.AtomicReference<Runnable>();
		var schedules = new java.util.concurrent.atomic.AtomicInteger();
		doAnswer(call -> { if (schedules.getAndIncrement() == 0) { task.set(call.getArgument(0)); return null; } throw new java.util.concurrent.RejectedExecutionException("successor rejected"); })
				.when(timer).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		Field timerField = UserDataManager.class.getDeclaredField("timer"); timerField.setAccessible(true); var original = manager.getTimer(); timerField.set(manager, timer);
		try {
			var first = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var second = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 2), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var drained = manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture();
			assertNotNull(task.get()); task.get().run();
			assertTrue(first.get(5, TimeUnit.SECONDS).applied());
			assertThrows(java.util.concurrent.ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
			assertThrows(java.util.concurrent.ExecutionException.class, () -> drained.get(5, TimeUnit.SECONDS));
			assertEquals(2, cache.getIfPresent("Activations").getInt());
			assertTrue(manager.pendingLegacyIntegerMutations(uuid).toCompletableFuture().isDone());
		} finally { timerField.set(manager, original); original.shutdownNow(); }
	}
	@Test void runtimeShutdownKeepsStorageOwnerUntilPendingAwardFlushes() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(plugin.isLoadUserData()).thenReturn(true); when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getLoadedUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		var manager = new UserDataManager(plugin); when(users.getDataManager()).thenReturn(manager);
		var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var award = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var platform = new com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform(plugin);
			var method = platform.getClass().getDeclaredMethod("closeUserStorageAfterSharedRetirement"); method.setAccessible(true);
			var shutdown = ((java.util.concurrent.CompletionStage<?>) method.invoke(platform)).toCompletableFuture();
			assertFalse(shutdown.isDone()); verify(plugin, never()).closePendingNativeUserStorageOwners();
			manager.endLegacyDirectWrite(direct); direct = null;
			assertTrue(award.get(5, TimeUnit.SECONDS).applied()); shutdown.get(5, TimeUnit.SECONDS);
			verify(data).setValues(any(HashMap.class)); verify(plugin).closePendingNativeUserStorageOwners();
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void shutdownWorkerReportsUnexpectedRetirementFailure() throws Exception {
		var manager = spy(new UserDataManager(mock(AdvancedCorePlugin.class)));
		doThrow(new IllegalStateException("retirement failed")).when(manager).clearCacheAsyncCompletion();
		try {
			var shutdown = manager.shutdownLegacyIntegerMutations().toCompletableFuture();
			assertThrows(java.util.concurrent.ExecutionException.class, () -> shutdown.get(5, TimeUnit.SECONDS));
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void shutdownLegacyFlushDoesNotBlockCallingThread() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var user = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getUserData()).thenReturn(data);
		var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
		doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; }).when(data).setValues(any(HashMap.class));
		var manager = new UserDataManager(plugin); when(users.getDataManager()).thenReturn(manager);
		var cache = new UserDataCache(manager, uuid); manager.getUserDataCache().put(uuid, cache);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		cache.addChange(new UserDataChangeInt("Activations", 2), true);
		try {
			var shutdown = manager.shutdownLegacyIntegerMutations().toCompletableFuture();
			assertTrue(entered.await(5, TimeUnit.SECONDS)); assertFalse(shutdown.isDone());
			release.countDown(); shutdown.get(5, TimeUnit.SECONDS);
		} finally { release.countDown(); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingLegacyAwardLoadsStrictColdBalanceBeforeApplying() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var stored = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		when(users.getUser(uuid, false)).thenReturn(stored); when(stored.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(7))));
		var manager = new UserDataManager(plugin);
		try {
			var result = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1),
					UserDataCache.IntegerMutation.ADD, null).toCompletableFuture().get(5, TimeUnit.SECONDS);
			assertEquals(7, result.previous().get("Activations"));
			assertEquals(8, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void pendingAwardUsesValuePublishedByCompetingDirectSetter() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var stored = mock(AdvancedCoreUser.class); var data = mock(UserData.class); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		when(users.getUser(uuid, false)).thenReturn(stored); when(stored.getUserData()).thenReturn(data);
		when(data.supportsStrictCompleteValues()).thenReturn(true);
		when(data.getValuesIfStrictComplete()).thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(10))));
		var manager = new UserDataManager(plugin); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var direct = manager.beginLegacyDirectWrite(uuid);
		try {
			var award = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
			var accepted = direct;
			UserDataCache.runWithLegacyDirectPublication(manager, accepted, cache,
					() -> cache.addChange(new UserDataChangeInt("Activations", 10), false));
			manager.markLegacyDirectWriteSucceeded(direct); manager.endLegacyDirectWrite(direct); direct = null;
			award.get(5, TimeUnit.SECONDS);
			assertEquals(11, cache.getIfPresent("Activations").getInt());
		} finally { if (direct != null) manager.endLegacyDirectWrite(direct); manager.getTimer().shutdownNow(); }
	}
	@Test void pendingLegacyAwardDoesNotRepeatAfterNotificationFailure() throws Exception {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getUserManager()).thenReturn(users); when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var notices = new java.util.concurrent.atomic.AtomicInteger();
		var retirementRejected = new java.util.concurrent.atomic.AtomicBoolean();
		try {
			var result = manager.submitLegacyIntegerMutation(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD,
					keys -> {
						notices.incrementAndGet();
						retirementRejected.set(manager.clearCacheAsyncCompletion().toCompletableFuture().isCompletedExceptionally());
						throw new IllegalStateException("notification failed");
					})
					.toCompletableFuture().get(5, TimeUnit.SECONDS);
			assertTrue(result.applied()); assertEquals(2, cache.getIfPresent("Activations").getInt());
			assertEquals(1, notices.get()); assertTrue(retirementRejected.get()); assertNotNull(manager.getLastDeferredStorageFailure());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void synchronousLegacyAwardIsNotReportedFailedAfterListenerFailure() {
		var plugin = mock(AdvancedCorePlugin.class); var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID(); var cache = new UserDataCache(manager, uuid);
		cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache); var notices = new java.util.concurrent.atomic.AtomicInteger();
		try {
			var result = manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD,
					keys -> { notices.incrementAndGet(); throw new IllegalStateException("notification failed"); });
			assertTrue(result.applied()); assertEquals(2, cache.getIfPresent("Activations").getInt());
			assertEquals(1, notices.get()); assertNotNull(manager.getLastDeferredStorageFailure());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyCapabilityChangeDuringInspectionRequiresRetry() {
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class); UUID uuid = UUID.randomUUID();
		var first = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		var second = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(first);
		when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, first));
		var data = new UserData(user) {
			@Override public boolean supportsStrictCompleteValues() {
				when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, second));
				return false;
			}
		};
		when(plugin.getUserManager()).thenReturn(users); when(users.getUser(uuid, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(data);
		var manager = new UserDataManager(plugin);
		try {
			assertEquals(UserDataManager.LegacyPopulationResult.RETRY, manager.populateCompleteLegacyCacheIfSupported(uuid));
			assertFalse(manager.getUserDataCache().containsKey(uuid));
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void directLegacySetterSurvivesAnOlderInFlightCreditFlush() throws Exception { directSetterWithProviderParticipation(true); }
	@Test void opaqueLegacyProviderDoesNotGainAtomicOrdering() throws Exception { directSetterWithProviderParticipation(false); }
	private void directSetterWithProviderParticipation(boolean participates) throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		var providerAdmission = new Object();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { synchronized (providerAdmission) { stored.set(((DataValue) call.getArgument(2)).getInt()); } return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		doAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			synchronized (providerAdmission) {
				if (participates) UserDataCache.rejectStaleLegacyBatch(uuid.toString());
				for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt());
			} return null; })
				.when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		var worker = Executors.newSingleThreadExecutor();
		try {
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			UserDataCache cache = manager.getPublishedCache(uuid);
			when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			var flush = worker.submit(cache::processChanges); assertTrue(entered.await(5, TimeUnit.SECONDS));
			data.setInt("Activations", 10, false);
			assertEquals(10, stored.get());
			release.countDown(); flush.get(5, TimeUnit.SECONDS);
			assertEquals(participates ? 10 : 2, stored.get());
			if (participates) assertEquals(10, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void directLegacySetterSurvivesOlderQueuedCreditFlush() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> new ArrayList<>(List.of(new Column("Activations", new DataValueInt(stored.get())))));
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		doAnswer(call -> { for (Column column : call.<List<Column>>getArgument(1)) if (column.getName().equals("Activations")) stored.set(column.getValue().getInt()); return null; })
				.when(table).update(eq(uuid.toString()), anyList(), eq(false));
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		try {
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			UserDataCache cache = manager.getPublishedCache(uuid);
			when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			data.setInt("Activations", 10, false);
			cache.processChanges();
			assertEquals(10, cache.getIfPresent("Activations").getInt());
			assertEquals(10, stored.get());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void directLegacySetterCannotBeLostBehindAnOlderStrictRead() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(AdvancedCoreUser.class);
		var table = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class);
		var manager = new UserDataManager(plugin); UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(uuid, false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString()); when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(table);
		var data = new UserData(user); when(user.getUserData()).thenReturn(data);
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		var stored = new java.util.concurrent.atomic.AtomicInteger(1);
		var reads = new java.util.concurrent.atomic.AtomicInteger();
		doAnswer(call -> { stored.set(((DataValue) call.getArgument(2)).getInt()); return null; }).when(table).update(eq(uuid.toString()), eq("Activations"), any(DataValue.class));
		when(table.getExactStrict(uuid.toString())).thenAnswer(call -> {
			int value = stored.get();
			if (reads.getAndIncrement() == 0) {
				entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			}
			return new ArrayList<>(List.of(new Column("Activations", new DataValueInt(value))));
		});
		var worker = Executors.newSingleThreadExecutor();
		try {
			var load = worker.submit(() -> manager.populateCompleteLegacyCache(uuid));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			data.setInt("Activations", 10);
			release.countDown(); assertFalse(load.get(5, TimeUnit.SECONDS));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("Activations", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(11, manager.getPublishedCache(uuid).getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyLoadRejectsRetiredGeneration() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(null);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserData data = plugin.getUserManager().getUser(uuid, false).getUserData();
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		when(data.getValuesIfStrictComplete()).thenAnswer(call -> {
			entered.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			return new HashMap<>(Map.of("Activations", new DataValueInt(1)));
		});
		var worker = Executors.newSingleThreadExecutor();
		try {
			var load = worker.submit(() -> manager.populateCompleteLegacyCache(uuid));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			UserDataCache generation = manager.getUserDataCache().get(uuid);
			generation.beginRemoval();
			assertTrue(manager.retireSharedCache(uuid, generation));
			UserDataCache replacement = new UserDataCache(manager, uuid);
			replacement.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(9))));
			assertNull(manager.getUserDataCache().putIfAbsent(uuid, replacement));
			release.countDown();
			assertFalse(load.get(5, TimeUnit.SECONDS));
			assertNull(generation.getIfPresent("Activations"));
			assertSame(replacement, manager.getUserDataCache().get(uuid));
			assertEquals(9, replacement.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void strictLegacyPopulationDoesNotInventBalanceAfterFailureAndCanRetry() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getNativeUserStorageOwner()).thenReturn(null);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserData data = plugin.getUserManager().getUser(uuid, false).getUserData();
		when(data.getValuesIfStrictComplete()).thenThrow(new IllegalStateException("synthetic read failure"))
				.thenReturn(null).thenReturn(new HashMap<>());
		try {
			assertThrows(IllegalStateException.class, () -> manager.populateCompleteLegacyCache(uuid));
			assertThrows(IllegalStateException.class, () -> manager.populateCompleteLegacyCache(uuid));
			assertNull(manager.tryMutateLegacyIntegers(uuid, Map.of("missing", 1), UserDataCache.IntegerMutation.ADD, null));
			assertTrue(manager.populateCompleteLegacyCache(uuid));
			assertTrue(manager.tryMutateLegacyIntegers(uuid, Map.of("missing", 1), UserDataCache.IntegerMutation.ADD, null).applied());
			assertEquals(1, manager.getPublishedCache(uuid).getIfPresent("missing").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void failedLegacyColdLoadCanRetryOnTheSameGeneration() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserData data = plugin.getUserManager().getUser(uuid, false).getUserData();
		when(data.getKeys()).thenReturn(new ArrayList<>());
		when(data.getValues()).thenThrow(new IllegalStateException("synthetic read failure"))
				.thenReturn(new HashMap<>(Map.of("Activations", new DataValueInt(4))));
		try {
			assertThrows(IllegalStateException.class, () -> manager.cacheUser(uuid, null));
			UserDataCache generation = manager.getUserDataCache().get(uuid);
			assertNotNull(generation);
			assertSame(generation, manager.getCache(uuid));
			assertEquals(4, generation.getIfPresent("Activations").getInt());
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void legacyColdLoadCannotReplaceAnAcceptedGeneration() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserData data = plugin.getUserManager().getUser(uuid, false).getUserData();
		when(data.getKeys()).thenReturn(new ArrayList<>());
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		when(data.getValues()).thenAnswer(call -> {
			entered.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
			return new HashMap<>(Map.of("Activations", new DataValueInt(1)));
		});
		var worker = Executors.newSingleThreadExecutor();
		try {
			var load = worker.submit(() -> manager.cacheUser(uuid, null));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			UserDataCache cache = manager.getUserDataCache().get(uuid);
			assertNotNull(cache, "cold load must publish its canonical generation before reading");
			cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Activations", new DataValueInt(1))));
			assertTrue(cache.tryMutateLegacyIntegers(Map.of("Activations", 1),
					UserDataCache.IntegerMutation.ADD, null).applied());
			release.countDown();
			load.get(5, TimeUnit.SECONDS);
			assertSame(cache, manager.getUserDataCache().get(uuid));
			assertEquals(2, cache.getIfPresent("Activations").getInt());
		} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
	}
	@Test void storageSpecificValuesOverrideRemainsInUse() throws Exception {
		try (Fixture fixture = new Fixture()) {
			var user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			var custom = new UserData(user) {
				@Override public ArrayList<String> getKeys() { return new ArrayList<>(List.of("custom")); }
				@Override public HashMap<String, DataValue> getValues(UserStorage storage) {
					return new HashMap<>(Map.of("custom", new DataValueInt(7)));
				}
			};
			when(user.getUserData()).thenReturn(custom);
			SharedUserDataRuntime runtime = fixture.runtime();
			try {
				UserDataCache cache = fixture.manager.getCache(fixture.uuid);
				assertEquals(7, cache.getIfPresent("custom").getInt());
				assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("missing"));
			} finally { runtime.close(); }
		}
	}
	@Test void sharedPopulationRetainsPersistedDynamicColumns() throws Exception {
		try (Fixture fixture = new Fixture()) {
			var data = fixture.plugin.getUserManager().getUser(fixture.uuid, false).getUserData();
			when(data.getKeys()).thenReturn(new ArrayList<>(List.of("VoteShopLimitDaily")));
			when(data.getValuesIfComplete()).thenReturn(new HashMap<>(Map.of("VoteShopLimitDaily", new DataValueInt(4))));
			SharedUserDataRuntime runtime = fixture.runtime();

			UserDataCache cache = fixture.manager.getCache(fixture.uuid);

			assertEquals(4, cache.snapshot().get("VoteShopLimitDaily").getInt());
			runtime.close();
		}
	}

    @Test void customValuesReaderIsRetainedWithoutCertifyingMissingKeys() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            var custom = new UserData(user) {
                @Override public ArrayList<String> getKeys() { return new ArrayList<>(List.of("custom")); }
                @Override public HashMap<String, DataValue> getValues() { return new HashMap<>(Map.of("custom", new DataValueInt(7))); }
            };
            when(user.getUserData()).thenReturn(custom);
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            assertEquals(7, cache.getIfPresent("custom").getInt());
            assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("missing"));
            runtime.close();
        }
    }

    @Test void noDatabaseLookupMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.NO_DB_LOOKUP); }
    @Test void cacheOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.CACHE_ONLY); }
    @Test void temporaryOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.TEMP_ONLY); }

	@Test void userDataRemovalUsesTheSharedRuntimeExclusiveDeletePath() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserDataManager manager = new UserDataManager(plugin);
		SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString());
		when(runtime.isClosed()).thenReturn(false);
		when(runtime.backend()).thenReturn(backend);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedRuntime(runtime);
		try {
			new UserData(user).remove();
			verify(runtime).remove(uuid);
			verify(user, never()).clearCache();
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

	@Test void explicitAlternateStoreReadsCannotUseTheSharedCache() throws Exception {
		try (Fixture fixture = new Fixture()) {
			SharedUserDataRuntime runtime = fixture.runtime();
			fixture.manager.bindSharedRuntime(runtime);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			UserData data = new UserData(user);

			assertThrows(IllegalStateException.class,
					() -> data.getInt(UserStorage.SQLITE, "Points", 0, UserDataFetchMode.CACHE_ONLY));
			assertThrows(IllegalStateException.class,
					() -> data.getString(UserStorage.SQLITE, "PlayerName", UserDataFetchMode.CACHE_ONLY));
			verify(user, never()).getCache();
			runtime.close();
		}
	}

    @Test void legacyCachePopulationPublishesOnlyAfterSharedAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        var data = plugin.getUserManager().getUser(uuid, false).getUserData();
        when(data.getKeys()).thenReturn(new ArrayList<>());
        when(data.getValues()).thenReturn(new HashMap<>());
        SqlUserBackend backend = mock(SqlUserBackend.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        manager.bindSharedSqlBackend(backend, (id, operation) -> {
            entered.countDown();
            try {
                await(release);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            operation.run();
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            var population = worker.submit(() -> manager.cacheUser(uuid, null));
            await(entered);
            assertFalse(manager.containsKey(uuid), "a detached snapshot must not be published before admission");
            release.countDown();
            population.get(5, TimeUnit.SECONDS);
            assertTrue(manager.containsKey(uuid));
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void preBindingCachePopulationBlocksSharedTransitionAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
        var data = plugin.getUserManager().getUser(uuid, false).getUserData();
        when(data.getKeys()).thenReturn(new ArrayList<>());
        when(data.getValues()).thenAnswer(call -> {
            loading.countDown();
            await(release);
            return new HashMap<String, DataValue>();
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            var population = worker.submit(() -> manager.cacheUser(uuid, null));
            await(loading);
            assertThrows(IllegalStateException.class, manager::beginSharedBindingTransition);
            release.countDown();
            population.get(5, TimeUnit.SECONDS);
            assertDoesNotThrow(manager::beginSharedBindingTransition);
            manager.endSharedBindingTransition();
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void cachePopulationCannotPublishAfterBackendReplacementStarts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
            var data = fixture.plugin.getUserManager().getUser(fixture.uuid, false).getUserData();
            doAnswer(call -> {
                loading.countDown();
                try {
                    await(release);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return new HashMap<String, DataValue>();
            }).when(data).getValuesIfComplete();
            MemoryBackend replacement = new MemoryBackend(UserStorage.SQLITE);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var population = workers.submit(() -> fixture.manager.cacheUser(fixture.uuid, null));
                await(loading);
                var replacementTask = workers.submit(() -> runtime.replaceBackend(replacement));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> replacementTask.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                population.get(5, TimeUnit.SECONDS);
                replacementTask.get(5, TimeUnit.SECONDS);
                assertFalse(fixture.manager.containsKey(fixture.uuid));
                assertFalse(fixture.first.isOpen());
            } finally {
                release.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void managerClearDoesNotDeadlockWithSameUserPopulation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writing = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            cache.setSharedStorageWriter(values -> {
                writing.countDown();
                try {
                    await(releaseWrite);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            });
            cache.addChange(new UserDataChangeInt("Points", 1), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clear = workers.submit(fixture.manager::clearCache);
                await(writing);
                var population = workers.submit(() -> fixture.manager.cacheUser(fixture.uuid, null));
                releaseWrite.countDown();
                clear.get(5, TimeUnit.SECONDS);
                population.get(5, TimeUnit.SECONDS);
                assertTrue(fixture.manager.containsKey(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    private void cacheMiss(UserDataFetchMode mode) {
        SqlUserBackend backend = mock(SqlUserBackend.class);
        UserCacheOwner owner = mock(UserCacheOwner.class);
        when(backend.isOpen()).thenReturn(true);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        UUID uuid = UUID.randomUUID();
        DataValue fallback = new DataValueInt(17);
        assertSame(fallback, runtime.read(uuid, "Points", mode, null, fallback));
        assertSame(fallback, runtime.read(uuid, "Points", mode, new HashMap<>(), fallback));
        verify(backend, never()).user(any(UUID.class));
        verify(owner, never()).populate(any(UUID.class), any());
        verify(owner, never()).queueChange(any(), any(), any());
        verify(owner, never()).flush(any(), any(UserStorage.class), any());
    }

    @Test void noDatabaseModesRetainTemporaryAndUserCachePrecedence() {
        SqlUserBackend backend = mock(SqlUserBackend.class);
        UserCacheOwner owner = mock(UserCacheOwner.class);
        when(backend.isOpen()).thenReturn(true);
        UUID uuid = UUID.randomUUID();
        DataValue cached = new DataValueInt(2), temporary = new DataValueInt(3);
        when(owner.getIfPresent(uuid, "Points")).thenReturn(cached);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        HashMap<String, DataValue> temp = new HashMap<>(Map.of("Points", temporary));
        assertSame(temporary, runtime.read(uuid, "Points", UserDataFetchMode.NO_DB_LOOKUP, temp, null));
        assertSame(cached, runtime.read(uuid, "Points", UserDataFetchMode.CACHE_ONLY, temp, null));
        assertSame(temporary, runtime.read(uuid, "Points", UserDataFetchMode.TEMP_ONLY, temp, null));
        verify(backend, never()).user(any(UUID.class));
    }

    @Test void managerGetCacheAfterRuntimeStartupUsesTheSelectedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            fixture.tasks.get(0).run();
            assertEquals(7, fixture.first.points(fixture.uuid));
            assertFalse(cache.hasChangesToProcess());
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void completedBatchDoesNotOverwriteANewerQueuedCacheValue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);
                cache.addChange(new UserDataChangeInt("Points", 9), true);
                assertEquals(9, cache.getCache().get("Points").getInt());
                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);
                assertEquals(9, cache.getCache().get("Points").getInt(),
                        "the older completed batch must not replace the newer queued value");
                fixture.first.blockWrites(null, null);
                fixture.tasks.get(fixture.tasks.size() - 1).run();
                assertEquals(9, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void immediateWriteIsPersistedAfterAnOlderQueuedBatch() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var queued = workers.submit(fixture.tasks.get(0));
                await(writeStarted);
                var immediate = workers.submit(() -> data.setInt(UserStorage.MYSQL, "Points", 9, false, false));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (cache.getCache().get("Points").getInt() != 9 && System.nanoTime() < deadline) {
                    Thread.yield();
                }
                assertEquals(9, cache.getCache().get("Points").getInt());
                releaseWrite.countDown();
                queued.get(5, TimeUnit.SECONDS);
                immediate.get(5, TimeUnit.SECONDS);
                assertEquals(9, fixture.first.points(fixture.uuid),
                        "the immediate write must reach storage after the older queued batch");
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void immediateSharedWriteReportsOneChangeAfterPersistence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            var userManager = fixture.plugin.getUserManager();
            clearInvocations(userManager);

            new UserData(user).setInt(UserStorage.MYSQL, "Points", 9, false, false);

            assertEquals(9, fixture.first.points(fixture.uuid));
            fixture.tasks.get(fixture.tasks.size() - 1).run();
            verify(userManager, times(1)).onChange(eq(user), any(String[].class));
            runtime.close();
        }
    }

    @Test void primaryThreadImmediateSharedWriteDefersWithoutLosingOrdering() throws Exception {
        try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            var userManager = fixture.plugin.getUserManager();
            clearInvocations(userManager);
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, false);

			UserData data = new UserData(user);
			assertDoesNotThrow(() -> data.setInt(UserStorage.MYSQL, "Points", 9, false, false));
			assertFalse(fixture.first.rows.containsKey(fixture.uuid));
			assertEquals(9, cache.snapshot().get("Points").getInt(),
					"the setter must publish read-after-write state before returning");
			data.setInt(UserStorage.MYSQL, "Points",
					data.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.DEFAULT) + 1, false, false);
			assertEquals(10, cache.snapshot().get("Points").getInt(),
					"same-tick read-modify-write must observe the preceding setter");
			assertEquals(10, fixture.first.points(fixture.uuid));
			verify(fixture.plugin.getBukkitScheduler(), never()).runTask(eq(fixture.plugin), any(Runnable.class));
			verify(userManager, never()).onChange(any(), any(String[].class));
			fixture.tasks.get(fixture.tasks.size() - 1).run();
            verify(userManager, times(1)).onChange(eq(user), any(String[].class));
            runtime.close();
        }
    }

    @Test void immediateWriteToAnotherStoreDoesNotUseTheSharedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);

            assertThrows(IllegalStateException.class,
                    () -> new UserData(user).setInt(UserStorage.SQLITE, "Points", 9, false, false));
            assertFalse(fixture.first.rows.containsKey(fixture.uuid),
                    "an explicit alternate-store write must not be redirected to the shared backend");
            assertFalse(cache.getCache().containsKey("Points"),
                    "a rejected cross-store write must not leave an unpersisted shared-cache value behind");

            assertThrows(IllegalStateException.class,
                    () -> new UserData(user).setInt(UserStorage.SQLITE, "Queued", 11, true, false));
            assertFalse(cache.hasChangesToProcess(),
                    "a queued cross-store write must be rejected before it reaches the active shared writer");
            runtime.close();
        }
    }

	@Test void primaryThreadPendingPopulationDefersMutationIntoTheBoundGeneration() throws Exception {
        try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
            SharedUserDataRuntime runtime = fixture.runtime();
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenAnswer(ignored -> fixture.manager.isCached(fixture.uuid));
            when(user.getCache()).thenAnswer(ignored -> fixture.manager.getCache(fixture.uuid));
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, true, true, false);

			assertDoesNotThrow(() -> new UserData(user).setInt(UserStorage.MYSQL, "Points", 9, true, false));
			assertFalse(fixture.first.rows.containsKey(fixture.uuid), "the primary thread must not write SQL");
			assertEquals(2, fixture.tasks.size(), "population must be queued before the mutation");
			assertFalse(fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().containsKey("Points"),
					"an unbound placeholder must not accept a gate-bypassing mutation");

			fixture.tasks.get(0).run();
			assertFalse(fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().containsKey("Points"));
			fixture.tasks.get(1).run();
			assertEquals(9, fixture.manager.getUserDataCache().get(fixture.uuid).getCache().get("Points").getInt(),
					"the admitted worker mutation must publish into the populated cache");
			for (int index = 2; !fixture.first.rows.containsKey(fixture.uuid) && index < fixture.tasks.size(); index++) {
				fixture.tasks.get(index).run();
			}
			assertEquals(9, fixture.first.points(fixture.uuid));
			fixture.assertNoLegacyWrites();
			runtime.close();
		}
	}

	@Test void implicitFacadeUsesTheRuntimeStorageAfterOptionsReload() throws Exception {
		try (Fixture fixture = new Fixture()) {
			SharedUserDataRuntime runtime = fixture.runtime();
			UserDataCache cache = fixture.manager.getCache(fixture.uuid);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenReturn(cache);

			UserData data = new UserData(user);
			data.setInt("Points", 14, false);

			assertEquals(UserStorage.MYSQL,
					fixture.manager.effectiveStorageType(fixture.plugin.getStorageType()));
			assertEquals(14, fixture.first.points(fixture.uuid));
			assertEquals(14, data.getInt("Points", UserDataFetchMode.DEFAULT));
			assertEquals(14, data.getValues().get("Points").getInt());
			runtime.close();
		}
	}

	@Test void primaryThreadBulkSetterSnapshotsAndPublishesBeforeReturning() throws Exception {
		try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
			SharedUserDataRuntime runtime = fixture.runtime();
			UserDataCache cache = fixture.manager.getCache(fixture.uuid);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenReturn(cache);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false);
			HashMap<String, DataValue> submitted = new HashMap<>(Map.of("Points", new DataValueInt(18)));

			new UserData(user).setValues(submitted);

			assertEquals(18, cache.snapshot().get("Points").getInt());
			submitted.clear();
			fixture.tasks.get(fixture.tasks.size() - 1).run();
			assertEquals(18, fixture.first.points(fixture.uuid));
			runtime.close();
		}
	}

	@Test void workerMutationCannotBypassAPendingPrimaryThreadPopulation() throws Exception {
		try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
			SharedUserDataRuntime runtime = fixture.runtime();
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenAnswer(ignored -> fixture.manager.getCache(fixture.uuid));
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, false);

			fixture.manager.getCache(fixture.uuid);
			new UserData(user).setInt(UserStorage.MYSQL, "Points", 12, true, false);

			assertFalse(fixture.first.rows.containsKey(fixture.uuid));
			fixture.tasks.get(0).run();
			assertEquals(12, fixture.first.points(fixture.uuid));
			assertEquals(12, fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().get("Points").getInt());
			runtime.close();
		}
	}

    @Test void completedBatchReconcilesATemporaryCacheSnapshotWithoutPersistingTheSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            data.updateTempCacheWithColumns(new ArrayList<>(List.of(new Column("Points", new DataValueInt(3)))));
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);

                data.updateCacheWithTemp();
                assertEquals(3, cache.getCache().get("Points").getInt(),
                        "the temporary storage snapshot is visible while the earlier write is in flight");

                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);

                assertEquals(7, fixture.first.points(fixture.uuid));
                assertEquals(7, cache.getCache().get("Points").getInt(),
                        "the completed queued write must reconcile the stale temporary snapshot");
                assertFalse(cache.hasChangesToProcess(),
                        "a read snapshot must not become an automatic persistence request");
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void temporarySnapshotCannotEraseANewerQueuedMutationFence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            data.updateTempCacheWithColumns(new ArrayList<>(List.of(new Column("Points", new DataValueInt(3)))));
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);
                cache.addChange(new UserDataChangeInt("Points", 9), true);
                data.updateCacheWithTemp();
                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);

                assertEquals(9, cache.getCache().get("Points").getInt(),
                        "the queued mutation must remain visible after the older batch completes");
                fixture.first.blockWrites(null, null);
                fixture.tasks.get(fixture.tasks.size() - 1).run();
                assertEquals(9, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void joinStyleCacheCreationAfterStartupUsesTheSelectedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.cacheUser(fixture.uuid, null);
            fixture.manager.getCache(fixture.uuid).addChange(new UserDataChangeInt("Points", 8), true);
            fixture.tasks.get(0).run();
            assertEquals(8, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void directCacheInsertionAfterStartupAlsoGetsTheGateAndWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            cache.addChange(new UserDataChangeInt("Points", 9), true);
            cache.processChanges();
            assertEquals(9, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void aWriterWithoutALifecycleGateDoesNotPreventSharedAttachment() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.setSharedStorageWriter(values -> fail("ungated writer was reused"));
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            cache.addChange(new UserDataChangeInt("Points", 16), true);
            runtime.close();
            assertEquals(16, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
        }
    }

    @Test void anAlreadyConstructedCacheCannotBypassBindingWhenInsertedLater() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 10), true);
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            fixture.tasks.get(0).run();
            assertEquals(10, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void cachesCreatedAfterReplacementDoNotReuseTheOldWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache old = fixture.manager.getCache(fixture.uuid);
            old.addChange(new UserDataChangeInt("Points", 11), true);
            MemoryBackend replacement = new MemoryBackend(UserStorage.SQLITE);
            runtime.replaceBackend(replacement);
            assertEquals(11, fixture.first.points(fixture.uuid));
            UserDataCache fresh = fixture.manager.getCache(fixture.uuid);
            assertNotSame(old, fresh);
            fresh.addChange(new UserDataChangeInt("Points", 12), true);
            fresh.processChanges();
            assertEquals(12, replacement.points(fixture.uuid));
            assertEquals(11, fixture.first.points(fixture.uuid));
            assertThrows(IllegalStateException.class, () -> old.addChange(new UserDataChangeInt("Points", 99), true));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void newlyInsertedCacheCannotQueueAfterRetirementStarts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            List<Runnable> worker = new ArrayList<>();
            CompletableFuture<Void> closing = runtime.closeAsync(worker::add).toCompletableFuture();
            UserDataCache late = new UserDataCache(fixture.manager, fixture.uuid);
            fixture.manager.getUserDataCache().put(fixture.uuid, late);
            assertThrows(IllegalStateException.class, () -> late.addChange(new UserDataChangeInt("Points", 99), true));
            worker.get(0).run();
            closing.join();
            assertThrows(IllegalStateException.class, () -> late.addChange(new UserDataChangeInt("Points", 99), true));
        }
    }

    @Test void legacyMonitorHeldDuringLazyAttachmentFailsWithoutDeadlocking() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 15), true);
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            synchronized (cache) { assertThrows(IllegalStateException.class, cache::processChanges); }
            assertTrue(cache.hasChangesToProcess());
            runtime.close();
            assertEquals(15, fixture.first.points(fixture.uuid));
        }
    }

    @Test void aFailedAttachmentLeavesTheSameOwnerReusable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException unavailable = new IllegalStateException("registration unavailable");
            doThrow(unavailable).doCallRealMethod().when(fixture.manager).bindSharedCacheInitializer(any());
            assertSame(unavailable, assertThrows(IllegalStateException.class, fixture::runtime));
            SharedUserDataRuntime retry = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 13), true);
            retry.close();
            assertEquals(13, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
        }
    }

    @Test void activeLegacyBatchRejectsAttachmentWithoutPublishingAndRetrySucceeds() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 1), true);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var legacyData = cache.getUser().getUserData();
            doAnswer(call -> { entered.countDown(); await(release); return null; }).when(legacyData).setValues(any(HashMap.class));
            var legacyWorker = Executors.newSingleThreadExecutor();
            try {
                var legacy = legacyWorker.submit(cache::processChanges);
                await(entered);
                assertThrows(IllegalStateException.class, fixture::runtime);
                release.countDown();
                legacy.get(5, TimeUnit.SECONDS);
                SharedUserDataRuntime runtime = fixture.runtime();
                cache.addChange(new UserDataChangeInt("Points", 14), true);
                runtime.close();
                assertEquals(14, fixture.first.points(fixture.uuid));
            } finally {
                release.countDown();
                legacyWorker.shutdownNow();
                assertTrue(legacyWorker.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void absentCacheRemovalDoesNotPopulateUserData() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin) {
            @Override
            public UserDataCache getCache(UUID requested) {
                throw new AssertionError("cache eviction must not populate an absent user");
            }
        };
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        SqlUserBackend backend = mock(SqlUserBackend.class);
        manager.bindSharedSqlBackend(backend,
                (id, operation) -> operation.run(),
                (id, operation) -> operation.run());

        assertDoesNotThrow(() -> manager.removeCache(uuid, null));

        assertFalse(manager.containsKey(uuid));
    }

    @Test void legacyRemovalUsesTheExclusiveSharedAdmission() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        UserDataCache cache = new UserDataCache(manager, uuid);
        cache.updateCache(new HashMap<>(Map.of("Points", new DataValueInt(1))));
        manager.getUserDataCache().put(uuid, cache);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        int[] exclusiveCalls = { 0 };
        manager.bindSharedSqlBackend(backend,
                (id, operation) -> fail("legacy removal must not use shared read admission"),
                (id, operation) -> { exclusiveCalls[0]++; operation.run(); });

        manager.removeCache(uuid, null);

        assertEquals(1, exclusiveCalls[0]);
        assertFalse(manager.containsKey(uuid));
    }

    @Test void legacyRemovalRetiresTheSharedCacheBeforeDetachingIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);

            fixture.manager.removeCache(fixture.uuid, null);

            assertFalse(fixture.manager.containsKey(fixture.uuid));
            assertThrows(IllegalStateException.class,
                    () -> cache.addChange(new UserDataChangeInt("Points", 2), true));
            runtime.close();
        }
    }

    @Test void managerDrivenRemovalEvictsTheOwnersPerUserGate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getCache(fixture.uuid);
            Field gatesField = BukkitUserCacheOwner.class.getDeclaredField("cacheGates");
            gatesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, ?> gates = (Map<UUID, ?>) gatesField.get(fixture.owner);
            assertTrue(gates.containsKey(fixture.uuid));

            fixture.manager.removeCache(fixture.uuid, null);

            assertFalse(gates.containsKey(fixture.uuid));
            runtime.close();
        }
    }

    @Test void managerWideClearDoesNotHoldMapAdmissionWhileSharedFlushWaits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 18), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clearing = workers.submit(fixture.manager::clearCache);
                await(writeStarted);
                // A shared cache flush is in progress. A population already admitted by
                // the runtime must still be able to take the cache-map read admission.
                assertTrue(workers.submit(() -> fixture.manager.withCacheMapReadAdmission(() -> Boolean.TRUE))
                        .get(200, TimeUnit.MILLISECONDS));
                releaseWrite.countDown();
                clearing.get(5, TimeUnit.SECONDS);
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void managerWideClearExcludesQueuedWritesUntilTheCacheIsDetached() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 18), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clearing = workers.submit(fixture.manager::clearCache);
                await(writeStarted);
                var concurrentWrite = workers.submit(() ->
                        cache.addChange(new UserDataChangeInt("Points", 19), true));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> concurrentWrite.get(200, TimeUnit.MILLISECONDS),
                        "a write must wait for the exclusive retirement instead of being silently dropped");
                releaseWrite.countDown();
                clearing.get(5, TimeUnit.SECONDS);
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> concurrentWrite.get(5, TimeUnit.SECONDS));
                assertFalse(fixture.manager.containsKey(fixture.uuid));
                assertEquals(18, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void managerWideClearWaitsForAnAlreadyAdmittedSetterBeforeMarkingRemoval() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var workers = Executors.newFixedThreadPool(2);
            var access = SharedUserDataRuntime.class.getDeclaredMethod("userAccess", UUID.class, java.util.function.Supplier.class); access.setAccessible(true);
            Field cellsField = SharedUserDataRuntime.class.getDeclaredField("userLocks"); cellsField.setAccessible(true);
            try {
                var writer = workers.submit(() -> access.invoke(runtime, fixture.uuid, (java.util.function.Supplier<Void>) () -> {
	                    entered.countDown();
	                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
	                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
	                    cache.addChange(new UserDataChangeInt("Points", 42), true); return null;
                }));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                Object cell = ((Map<?, ?>) cellsField.get(runtime)).get(fixture.uuid);
                Field lockField = cell.getClass().getDeclaredField("lock"); lockField.setAccessible(true);
                var lock = (java.util.concurrent.locks.ReentrantReadWriteLock) lockField.get(cell);
                var clear = workers.submit(fixture.manager::clearCache);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!lock.hasQueuedThreads() && System.nanoTime() < deadline) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                assertTrue(lock.hasQueuedThreads(), "retirement must be waiting for the admitted setter");
                release.countDown(); writer.get(5, TimeUnit.SECONDS); clear.get(5, TimeUnit.SECONDS);
                assertEquals(42, fixture.first.points(fixture.uuid));
                assertThrows(IllegalStateException.class, () -> cache.addChange(new UserDataChangeInt("Points", 99), true));
            } finally { release.countDown(); workers.shutdownNow(); workers.awaitTermination(5, TimeUnit.SECONDS); }
            runtime.close();
        }
    }

    @Test void removalListenerFailureCannotReopenDetachedSharedCache() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            Field listener = UserDataManager.class.getDeclaredField("sharedCacheRemovalListener"); listener.setAccessible(true);
            Object original = listener.get(fixture.manager);
            listener.set(fixture.manager, (java.util.function.Consumer<UUID>) id -> { throw new IllegalStateException("listener failed"); });
            try {
                assertThrows(IllegalStateException.class, fixture.manager::clearCache);
                assertFalse(fixture.manager.containsKey(fixture.uuid));
                assertThrows(IllegalStateException.class, () -> cache.addChange(new UserDataChangeInt("Points", 99), true));
            } finally { listener.set(fixture.manager, original); }
            runtime.close();
        }
    }

    @Test void failedManagerWideClearReopensEveryStillMappedCache() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        try {
            UserDataCache first = failingCache(manager, UUID.randomUUID());
            UserDataCache second = failingCache(manager, UUID.randomUUID());
            manager.getUserDataCache().put(first.getUuid(), first);
            manager.getUserDataCache().put(second.getUuid(), second);
            manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (uuid, operation) -> operation.run());

            assertThrows(IllegalStateException.class, manager::clearCache);

            first.setSharedStorageWriter(values -> {});
            second.setSharedStorageWriter(values -> {});
            first.addChange(new UserDataChangeInt("Retry", 1), true);
            second.addChange(new UserDataChangeInt("Retry", 1), true);
            assertTrue(first.getCache().containsKey("Retry"));
            assertTrue(second.getCache().containsKey("Retry"));
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void failedPerUserRemovalReopensTheMappedCache() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        try {
            UserDataCache cache = failingCache(manager, UUID.randomUUID());
            manager.getUserDataCache().put(cache.getUuid(), cache);
            manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (uuid, operation) -> operation.run());

            assertThrows(IllegalStateException.class, () -> manager.removeCache(cache.getUuid(), null));

            cache.setSharedStorageWriter(values -> {});
            cache.addChange(new UserDataChangeInt("Retry", 1), true);
            assertTrue(cache.getCache().containsKey("Retry"));
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void refreshFlushCallbackCanRemoveTheSameUserAfterSharedAdmission() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 21), true);
            var userManager = fixture.plugin.getUserManager();
            doAnswer(call -> {
                fixture.manager.removeCache(fixture.uuid, null);
                return null;
            }).when(userManager).onChange(any(AdvancedCoreUser.class), any(String[].class));

            assertDoesNotThrow(() -> fixture.manager.cacheUser(fixture.uuid, null));
            fixture.tasks.get(fixture.tasks.size() - 1).run();

            assertEquals(21, fixture.first.points(fixture.uuid));
            assertFalse(fixture.manager.containsKey(fixture.uuid));
            Field completedField = UserDataManager.class.getDeclaredField("completedSharedCachePopulations");
            completedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> completed = (Set<UUID>) completedField.get(fixture.manager);
            assertFalse(completed.contains(fixture.uuid),
                    "a callback-driven removal must not be followed by a stale completed-population marker");
            runtime.close();
        }
    }

    private UserDataCache failingCache(UserDataManager manager, UUID uuid) {
        UserDataCache cache = new UserDataCache(manager, uuid);
        cache.updateCache(new HashMap<>(Map.of("Points", new DataValueInt(1))));
        cache.setSharedStorageWriter(values -> { throw new IllegalStateException("write failed"); });
        cache.addChange(new UserDataChangeInt("Points", 2), true);
        return cache;
    }

    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(5, TimeUnit.SECONDS)); }

    private static final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        final UserDataManager manager;
        final BukkitUserCacheOwner owner;
        final MemoryBackend first = new MemoryBackend(UserStorage.MYSQL);
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);

        Fixture() throws Exception {
			when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getNativeUserStorageOwner()).thenReturn(null);
            when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
            manager = spy(new UserDataManager(plugin));
            manager.getTimer().shutdownNow();
            Field field = UserDataManager.class.getDeclaredField("timer");
            field.setAccessible(true);
            field.set(manager, timer);
            when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return mock(ScheduledFuture.class);
            });
            doAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return null;
            }).when(timer).execute(any(Runnable.class));
            when(plugin.getTimer()).thenReturn(timer);
            var data = plugin.getUserManager().getUser(uuid, false).getUserData();
            when(data.getKeys()).thenAnswer(ignored -> new ArrayList<String>());
            when(data.getValues()).thenAnswer(ignored -> new HashMap<String, DataValue>());
            owner = new BukkitUserCacheOwner(manager);
        }

        SharedUserDataRuntime runtime() { return new SharedUserDataRuntime(first, owner); }
        void assertNoLegacyWrites() { verify(plugin.getUserManager().getUser(uuid, false).getUserData(), never()).setValues(any(HashMap.class)); }
        public void close() { manager.getTimer().shutdownNow(); }
    }

    private static final class MemoryBackend implements SqlUserBackend {
        final UserStorage type;
        final Map<UUID, HashMap<String, DataValue>> rows = new ConcurrentHashMap<>();
        volatile CountDownLatch writeStarted;
        volatile CountDownLatch releaseWrite;
        boolean open = true;
        MemoryBackend(UserStorage type) { this.type = type; }
        void blockWrites(CountDownLatch started, CountDownLatch release) {
            writeStarted = started;
            releaseWrite = release;
        }
        int points(UUID uuid) { return rows.get(uuid).get("Points").getInt(); }
        public UserStorage storageType() { return type; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public List<UUID> enumerateUsers() { return new ArrayList<>(rows.keySet()); }
        public SqlUserStorage user(UUID uuid) {
            if (!open) throw new IllegalStateException("closed backend");
            return new SqlUserStorage() {
                public List<Column> readRow(UserStorage requested) {
                    List<Column> columns = new ArrayList<>();
                    rows.getOrDefault(uuid, new HashMap<>()).forEach((key, value) -> columns.add(new Column(key, value)));
                    return columns;
                }
                public boolean contains(UserStorage requested) { return rows.containsKey(uuid); }
                public void delete(UserStorage requested) { rows.remove(uuid); }
                public void write(UserStorage requested, String key, DataValue value) { writeValues(requested, new HashMap<>(Map.of(key, value))); }
                public void writeValues(UserStorage requested, HashMap<String, DataValue> values) {
                    assertEquals(type, requested);
                    if (!open) throw new IllegalStateException("closed backend");
                    CountDownLatch started = writeStarted;
                    CountDownLatch release = releaseWrite;
                    if (started != null && release != null) {
                        started.countDown();
                        try { await(release); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    rows.computeIfAbsent(uuid, ignored -> new HashMap<>()).putAll(values);
                }
            };
        }
    }
}
