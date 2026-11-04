package com.bencodez.advancedcore.tests.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

class UserTablePreparedStatementTest {
	@Test void bindingSharedStoragePreservesNewerLegacyBulkValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			var stored = new java.util.concurrent.atomic.AtomicInteger(f.stored());
			f.cache.configureSharedStorage(values -> stored.set(values.get("X").getInt()), Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(10, stored.get());
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 30), true);
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(30, stored.get());
		}
	}
	@Test void bindingDifferentSharedStorageCannotRouteLegacyQueue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			var writes = new java.util.concurrent.atomic.AtomicInteger();
			f.cache.configureSharedStorage(values -> writes.incrementAndGet(), Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.cache.processChanges());
			org.junit.jupiter.api.Assertions.assertEquals(0, writes.get()); org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess());
		}
	}
	@Test void scalarIntegerAfterBulkOverrideSurvivesOlderQueuedWrite() throws Exception { scalarAfterBulkOverride(false); }
	@Test void scalarStringAfterBulkOverrideSurvivesOlderQueuedWrite() throws Exception { scalarAfterBulkOverride(true); }
	@Test void laterQueuedChangeStillSupersedesScalarPublication() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var first = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (first.compareAndSet(false, true)) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.eq("X"));
			var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var direct = pool.submit(() -> f.data.setInt("X", 20, false));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				f.data.setInt("X", 30, true); release.countDown(); direct.get(5, java.util.concurrent.TimeUnit.SECONDS);
				f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(30, f.stored());
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	private void scalarAfterBulkOverride(boolean stringValue) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.data.setInt("X", 2, true);
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			if (stringValue) f.data.setString("X", "20", false); else f.data.setInt("X", 20, false);
			org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void evictionCannotDetachInflightLegacyBatch() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
			var data = spy(f.data); when(f.user.getUserData()).thenReturn(data);
			org.mockito.Mockito.doAnswer(call -> { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); }).when(data).setValues(any(java.util.HashMap.class));
			try {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
				var flush = pool.submit(f.cache::processChanges); org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
						() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().join());
				org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.manager.removeCache(f.uuid, null));
				org.junit.jupiter.api.Assertions.assertSame(f.cache, f.manager.getUserDataCache().get(f.uuid));
				release.countDown(); flush.get(5, java.util.concurrent.TimeUnit.SECONDS); f.manager.removeCache(f.uuid, null);
				org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void laterStagedSqliteAliasCannotCrossCheckpointBoundary() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var armed = new java.util.concurrent.atomic.AtomicBoolean(); var observed = new java.util.concurrent.atomic.AtomicInteger();
			var earlier = new com.bencodez.advancedcore.api.user.usercache.change.UserDataChange("X") {
				@Override public com.bencodez.simpleapi.sql.data.DataValue toUserDataValue() {
					if (armed.compareAndSet(true, false)) f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("x", 20), true);
					return new DataValueInt(10);
				}
				@Override public void dump() { setKey(null); }
			};
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(earlier, true); armed.set(true);
				f.cache.flushChangesAndRun(() -> { try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); } });
			});
			org.junit.jupiter.api.Assertions.assertEquals(10, observed.get());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void evictionWaitingForCheckpointPersistsStagedChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var evictionStarted = new java.util.concurrent.CountDownLatch(1);
			var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var checkpoint = pool.submit(() -> f.cache.flushChangesAndRun(() -> {
					f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 40), true); entered.countDown();
					try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				}));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				var eviction = pool.submit(() -> { evictionStarted.countDown(); f.manager.removeCache(f.uuid, null); });
				org.junit.jupiter.api.Assertions.assertTrue(evictionStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> eviction.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
				release.countDown(); checkpoint.get(5, java.util.concurrent.TimeUnit.SECONDS); eviction.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertEquals(40, f.stored()); org.junit.jupiter.api.Assertions.assertFalse(f.manager.getUserDataCache().containsKey(f.uuid));
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void laterStagedChangeDoesNotLeakThroughEarlierBulkOverride() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			var armed = new java.util.concurrent.atomic.AtomicBoolean(); var observed = new java.util.concurrent.atomic.AtomicInteger();
			var earlier = new com.bencodez.advancedcore.api.user.usercache.change.UserDataChange("X") {
				@Override public com.bencodez.simpleapi.sql.data.DataValue toUserDataValue() {
					if (armed.compareAndSet(true, false)) f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
					return new DataValueInt(10);
				}
				@Override public void dump() { setKey(null); }
			};
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(earlier, true); armed.set(true);
				f.cache.flushChangesAndRun(() -> { try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); } });
			});
			org.junit.jupiter.api.Assertions.assertEquals(10, observed.get());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void newerDirectBulkOverrideStillSupersedesEarlierQueuedChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var observed = new java.util.concurrent.atomic.AtomicInteger();
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
				f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(15))));
				f.cache.flushChangesAndRun(() -> { try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); } });
			});
			org.junit.jupiter.api.Assertions.assertEquals(15, observed.get());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(15, f.stored());
		}
	}
	@Test void retirementCannotAcknowledgeStagedCheckpointChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
				org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
						() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			});
			org.junit.jupiter.api.Assertions.assertSame(f.cache, f.manager.getUserDataCache().get(f.uuid));
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void dumpCannotDiscardStagedCheckpointChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
				org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.cache.dump());
			});
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void copiedBatchMapRetainsDestinationAcrossReload() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var copyingData = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
					var token = com.bencodez.advancedcore.api.user.usercache.UserDataCache.captureLegacyBatchToken(values);
					when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
					var copy = new java.util.HashMap<>(values);
					if (token == null) super.setValues(copy);
					else com.bencodez.advancedcore.api.user.usercache.UserDataCache.withLegacyBatchCopy(token, copy, () -> super.setValues(copy));
				}
			};
			when(f.user.getUserData()).thenReturn(copyingData);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
			f.cache.processChanges();
			org.mockito.Mockito.verifyNoInteractions(mysql);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void copiedBatchTokenRejectsUnrelatedAndExpiredUse() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var saved = new java.util.concurrent.atomic.AtomicReference<com.bencodez.advancedcore.api.user.usercache.UserDataCache.LegacyBatchToken>();
			var copyingData = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
					var token = com.bencodez.advancedcore.api.user.usercache.UserDataCache.captureLegacyBatchToken(values);
					org.junit.jupiter.api.Assertions.assertNotNull(token); saved.set(token);
					var independent = new java.util.HashMap<>(values);
					org.junit.jupiter.api.Assertions.assertNull(com.bencodez.advancedcore.api.user.usercache.UserDataCache.captureLegacyBatchToken(independent));
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.withLegacyBatchCopy(token, independent, () -> {
						org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> com.bencodez.advancedcore.api.user.usercache.UserDataCache.withLegacyBatchCopy(token, independent, () -> {}));
						super.setValues(independent);
					});
				}
			};
			when(f.user.getUserData()).thenReturn(copyingData);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true); f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> com.bencodez.advancedcore.api.user.usercache.UserDataCache.withLegacyBatchCopy(saved.get(), new java.util.HashMap<>(), () -> {}));
		}
	}
	@Test void independentReentrantWriteDoesNotInheritBatchDestination() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var nested = new java.util.concurrent.atomic.AtomicBoolean();
			var copyingData = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
					var token = com.bencodez.advancedcore.api.user.usercache.UserDataCache.captureLegacyBatchToken(values);
					if (nested.compareAndSet(false, true)) super.setValues(com.bencodez.advancedcore.api.user.UserStorage.MYSQL, new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(20))));
					var copy = new java.util.HashMap<>(values);
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.withLegacyBatchCopy(token, copy, () -> super.setValues(copy));
				}
			};
			when(f.user.getUserData()).thenReturn(copyingData);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true); f.cache.processChanges();
			org.mockito.Mockito.verify(mysql, org.mockito.Mockito.atLeastOnce()).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(false));
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void nestedStagingRemainsFencedUntilOuterCheckpointCompletes() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.flushChangesAndRun(() -> {
				f.cache.flushChangesAndRun(() -> f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true));
				f.cache.processChanges();
				try (var sql = f.db.createStatement()) { sql.executeUpdate("UPDATE Users SET X=5"); }
				catch (Exception e) { throw new IllegalStateException(e); }
			});
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void sqliteDirectAliasUpdatesCanonicalCacheValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.data.setInt("x", 10, false);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(10, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY));
		}
	}
	@Test void copiedBatchMapCannotHideProviderFailure() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var copyingData = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
					super.setValues(new java.util.HashMap<>(values));
				}
			};
			when(f.user.getUserData()).thenReturn(copyingData);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_update BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.cache.processChanges());
			org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess());
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_update"); }
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void callbackCheckpointSupersedesOuterSqliteDirectWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> {
				if (called.compareAndSet(false, true)) {
					f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
					f.cache.flushChangesAndRun(() -> { });
				}
				return null;
			}).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.eq("X"));
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(20, f.cache.getIfPresent("X").getInt());
		}
	}
	@Test void opaqueCallbackBatchCannotSuppressNativeDirectWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var opaque = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { }
			};
			when(f.user.getUserData()).thenReturn(opaque); var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) { f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true); f.cache.flushChangesAndRun(() -> {}); } return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.eq("X"));
			f.data.setInt("X", 10, false); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void callbackBatchDoesNotSuppressDifferentSqliteRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String upper = f.uuid.toString().toUpperCase(java.util.Locale.ROOT);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + upper + "',1)"); }
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(upper); when(other.isCached()).thenReturn(true); when(other.getCache()).thenReturn(f.cache);
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) { f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true); f.cache.flushChangesAndRun(() -> {}); } return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(other), org.mockito.ArgumentMatchers.eq("X"));
			new com.bencodez.advancedcore.api.user.UserData(other).setInt("X", 10, false);
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(20, row.getInt(1)); }
				sql.setString(1, upper); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			}
		}
	}
	@Test void callbackBatchOnDifferentStoreDoesNotSuppressSqliteWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			org.mockito.Mockito.doAnswer(call -> { com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyBatchProviderCommit(f.uuid.toString(), java.util.List.of("X"), com.bencodez.advancedcore.api.user.UserStorage.MYSQL, false); return null; }).when(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(false));
			var redirected = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { super.setValues(com.bencodez.advancedcore.api.user.UserStorage.MYSQL, values); }
			};
			when(f.user.getUserData()).thenReturn(redirected); var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) { f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true); f.cache.flushChangesAndRun(() -> {}); } return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.eq("X"));
			f.data.setInt("X", 10, false); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void movedSqliteRowInvalidatesDestinationCache() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var destination = java.util.UUID.randomUUID();
			var target = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, destination);
			target.updateCompleteCachePreservingPending(new java.util.HashMap<>()); f.manager.getUserDataCache().put(destination, target);
			f.data.setInt("X", 50, false); f.data.setString("uuid", destination.toString(), false);
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(destination, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
		}
	}
	@Test void olderDestinationWriteCannotOverwriteMovedRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String destination = java.util.UUID.randomUUID().toString(); f.data.setInt("X", 50, false);
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class); when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(destination); when(other.isCached()).thenReturn(false);
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			new com.bencodez.advancedcore.api.user.UserData(other).setInt("X", 2, false, true);
			f.data.setString("uuid", destination, false); task.get().run();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) { sql.setString(1, destination); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(50, row.getInt(1)); } }
		}
	}
	@Test void olderUppercaseDestinationWriteCannotOverwriteMovedRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String destination = java.util.UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT); f.data.setInt("X", 50, false);
			var canonical = java.util.UUID.fromString(destination); var canonicalCache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, canonical);
			canonicalCache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(77)))); f.manager.getUserDataCache().put(canonical, canonicalCache);
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class); when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(destination); when(other.isCached()).thenReturn(false);
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			new com.bencodez.advancedcore.api.user.UserData(other).setInt("X", 2, false, true);
			f.data.setString("uuid", destination, false); task.get().run();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) { sql.setString(1, destination); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(50, row.getInt(1)); } }
			org.junit.jupiter.api.Assertions.assertEquals(77, canonicalCache.getIfPresent("X").getInt());
		}
	}
	@Test void opaqueCopiedBatchCanFlushDuringRetirement() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var copying = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { super.setValues(new java.util.HashMap<>(values)); }
			};
			when(f.user.getUserData()).thenReturn(copying);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void olderSourceWriteCannotModifyRecreatedRowAfterMove() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Y INTEGER"); }
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 2, false, true); f.data.setString("uuid", java.util.UUID.randomUUID().toString(), false); f.data.setInt("Y", 7, false);
			try { task.get().run(); } catch (IllegalStateException expected) { }
			try (var sql = f.db.prepareStatement("SELECT X,Y FROM Users WHERE uuid=?")) { sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); row.getInt(1); org.junit.jupiter.api.Assertions.assertTrue(row.wasNull()); org.junit.jupiter.api.Assertions.assertEquals(7, row.getInt(2)); } }
		}
	}
	@Test void reboundPrimaryMoveInvalidatesItsDestination() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String source = java.util.UUID.randomUUID().toString(); var destination = java.util.UUID.randomUUID();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + source + "',50)"); }
			var target = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, destination); target.updateCompleteCachePreservingPending(new java.util.HashMap<>()); f.manager.getUserDataCache().put(destination, target);
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setString("uuid", destination.toString(), false, true); identity.set(source); task.get().run();
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(destination, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
		}
	}
	@Test void reboundPrimaryMoveToOriginalIdentityInvalidatesDestination() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String source = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); sql.executeUpdate("INSERT INTO Users VALUES ('" + source + "',50)"); }
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>());
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setString("uuid", f.uuid.toString(), false, true); identity.set(source); task.get().run();
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
		}
	}
	@Test void failedPrimaryAliasDoesNotEnterUnfencedRetryQueue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_move BEFORE UPDATE ON Users WHEN NEW.uuid != OLD.uuid BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("UUID", java.util.UUID.randomUUID().toString(), false);
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void nestedAsyncWriteRetainsLegacyBindingAdmission() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Y INTEGER"); }
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			var copying = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { super.setValues(values); f.data.setInt("Y", 3, false, true); }
			};
			when(f.user.getUserData()).thenReturn(copying); f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true); f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertNotNull(task.get());
			var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.storageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.manager.bindSharedSqlBackend(backend, Runnable::run));
			task.get().run();
		}
	}
	@Test void destinationArithmeticWaitsForInFlightSqliteMove() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var destination = java.util.UUID.randomUUID(); var target = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, destination);
			target.updateCompleteCachePreservingPending(new java.util.HashMap<>()); f.manager.getUserDataCache().put(destination, target);
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var table = f.plugin.getSQLiteUserTable();
			org.mockito.Mockito.doAnswer(call -> { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); }).when(table).update(any(Column.class), org.mockito.ArgumentMatchers.anyList());
			var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var move = pool.submit(() -> f.data.setString("uuid", destination.toString(), false));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(destination, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
				release.countDown(); move.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(destination, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void failedSqliteMoveLeavesDestinationSnapshotUsable() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var destination = java.util.UUID.randomUUID(); var target = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, destination);
			target.updateCompleteCachePreservingPending(new java.util.HashMap<>()); f.manager.getUserDataCache().put(destination, target);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_move BEFORE UPDATE ON Users WHEN NEW.uuid != OLD.uuid BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", destination.toString(), false);
			var result = f.manager.tryMutateLegacyIntegers(destination, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertTrue(result.applied());
		}
	}
	@Test void callbackCheckpointForOtherColumnDoesNotSuppressDirectWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Y INTEGER DEFAULT 1"); }
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> {
				if (called.compareAndSet(false, true)) {
					f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Y", 20), true);
					f.cache.flushChangesAndRun(() -> { });
				}
				return null;
			}).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.eq("X"));
			f.data.setInt("X", 10, false);
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT X,Y FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(20, row.getInt(2));
			}
		}
	}
	@Test void warmPublicCacheLookupDefersInvalidatedStorageRefresh() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.plugin.getOptions()).thenReturn(new com.bencodez.advancedcore.AdvancedCoreConfigOptions());
			var table = f.plugin.getSQLiteUserTable(); var reads = new java.util.concurrent.atomic.AtomicInteger(); Thread caller = Thread.currentThread();
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExactStrict(any(Column.class));
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExact(any(Column.class));
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasPublishedStorageSnapshot());
			org.junit.jupiter.api.Assertions.assertSame(f.cache, f.manager.getCache(f.uuid));
			f.manager.cacheUserIfNeeded(f.uuid);
			org.junit.jupiter.api.Assertions.assertEquals(0, reads.get());
		}
	}
	@Test void disappearedSqliteRowCannotAcknowledgeDirectUpdate() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			var table = f.plugin.getSQLiteUserTable(); var once = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> {
				boolean present = (boolean) call.callRealMethod();
				if (present && once.compareAndSet(false, true)) try (var statement = f.db.prepareStatement("UPDATE Users SET uuid=? WHERE uuid=?")) {
					statement.setString(1, replacement); statement.setString(2, f.uuid.toString()); statement.executeUpdate();
				}
				return present;
			}).when(table).containsKey(f.uuid.toString());
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) { sql.setString(1, replacement); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); } }
		}
	}
	@Test void legacyArithmeticRejectsOldStorageGenerationAfterReload() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var stored = new java.util.concurrent.atomic.AtomicInteger(1);
			org.mockito.Mockito.doAnswer(call -> { stored.set(call.<com.bencodez.simpleapi.sql.data.DataValue>getArgument(2).getInt()); return null; }).when(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.eq("X"), any(com.bencodez.simpleapi.sql.data.DataValue.class));
			f.data.setInt("X", 10, false); org.junit.jupiter.api.Assertions.assertEquals(10, stored.get());
			var add = com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD;
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null));
			org.junit.jupiter.api.Assertions.assertNull(f.cache.tryMutateLegacyIntegers(java.util.Map.of("X", 1), add, null));
			org.junit.jupiter.api.Assertions.assertEquals(10, stored.get());
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			org.junit.jupiter.api.Assertions.assertEquals(1, f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null).previous().get("X"));
		}
	}
	@Test void olderAsyncSqlitePrimaryUpdateCannotOverwriteNewerPrimary() throws Exception { asyncPrimaryUpdate(false); }
	@Test void newerUnrelatedSqliteColumnDoesNotSuppressPrimaryUpdate() throws Exception { asyncPrimaryUpdate(true); }
	@Test void failedNewerPrimaryUpdateDoesNotSuppressOlderSuccessfulUpdate() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setString("uuid", replacement, false, true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_update BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", f.uuid.toString(), false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_update"); }
			task.get().run();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT uuid FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(replacement, row.getString(1)); }
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void olderSqliteValueWriteCannotRecreateRowAfterNewerPrimaryChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 2, false, true); f.data.setString("uuid", replacement, false);
			try { task.get().run(); } catch (IllegalStateException expected) { /* A stale row write may be rejected for retry. */ }
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT count(*) FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
			try (var sql = f.db.prepareStatement("SELECT uuid FROM Users WHERE uuid=?")) { sql.setString(1, replacement); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); } }
		}
	}
	@Test void rejectedStaleSqliteWriteCannotRecreateRowOnRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 2, false, true); f.data.setString("uuid", replacement, false);
			try { task.get().run(); } catch (IllegalStateException expected) { }
			f.cache.processChanges();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT count(*) FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedNestedCheckpointRetainsStagedChanges() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_update BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
				f.cache.flushChangesAndRun(() -> org.junit.jupiter.api.Assertions.fail("failed write must prevent action"));
			}));
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_update"); }
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void nestedCheckpointFlushesPreviouslyStagedWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var observed = new java.util.concurrent.atomic.AtomicInteger();
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
				f.cache.flushChangesAndRun(() -> { try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); } });
			});
			org.junit.jupiter.api.Assertions.assertEquals(10, observed.get());
		}
	}
	@Test void nestedCheckpointLeavesLaterWriteForSubsequentFlush() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var observed = new java.util.concurrent.atomic.AtomicInteger();
			f.cache.flushChangesAndRun(() -> {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
				f.cache.flushChangesAndRun(() -> {
					f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
					try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); }
				});
			});
			org.junit.jupiter.api.Assertions.assertEquals(10, observed.get());
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void nestedCheckpointPreservesConcurrentLaterWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var data = spy(f.data); when(f.user.getUserData()).thenReturn(data);
			org.mockito.Mockito.doAnswer(call -> { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); })
					.when(data).setValues(any(java.util.HashMap.class));
			var observed = new java.util.concurrent.atomic.AtomicInteger(); var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var checkpoint = pool.submit(() -> f.cache.flushChangesAndRun(() -> {
					f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
					f.cache.flushChangesAndRun(() -> { try { observed.set(f.stored()); } catch (Exception e) { throw new IllegalStateException(e); } });
				}));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
				release.countDown(); checkpoint.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertEquals(10, observed.get());
				f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void retryDoesNotWriteToReloadedStore() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			f.data.setInt("X", 10, false, true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_update BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			try { task.get().run(); } catch (RuntimeException expected) { }
			try { f.cache.processChanges(); } catch (IllegalStateException expected) { }
			org.mockito.Mockito.verifyNoInteractions(mysql);
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
		}
	}
	@Test void queuedRetryWaitsForOriginalStoreAfterReload() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			f.data.setInt("X", 10, false, true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_update BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			try { task.get().run(); } catch (RuntimeException expected) { }
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_update"); }
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.cache.processChanges());
			org.mockito.Mockito.verifyNoInteractions(mysql);
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void reloadDuringDefaultBatchDispatchKeepsOriginalStore() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var data = spy(f.data); when(f.user.getUserData()).thenReturn(data);
			org.mockito.Mockito.doAnswer(call -> { when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL); return call.callRealMethod(); })
					.when(data).setValues(any(java.util.HashMap.class));
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
			f.cache.processChanges();
			org.mockito.Mockito.verifyNoInteractions(mysql);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void olderPrimaryOnlyInsertCannotRecreateRowAfterNewerPrimaryChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString(); var primary = new Column("uuid", new DataValueString(f.uuid.toString()));
			var old = f.manager.beginLegacyDirectWrite(f.uuid, java.util.Set.of(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("uuid")), f.uuid.toString());
			try {
				f.data.setString("uuid", replacement, false);
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(f.manager, old, null, () -> f.plugin.getSQLiteUserTable().insert(new java.util.ArrayList<>(java.util.List.of(primary))));
				f.manager.markLegacyDirectWriteSucceeded(old);
				try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT count(*) FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
			} finally { f.manager.endLegacyDirectWrite(old); }
		}
	}
	private void asyncPrimaryUpdate(boolean unrelated) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setString("uuid", replacement, false, true); org.junit.jupiter.api.Assertions.assertNotNull(task.get());
			if (unrelated) f.data.setInt("X", 10, false); else f.data.setString("uuid", f.uuid.toString(), false);
			task.get().run();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT uuid FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(unrelated ? replacement : f.uuid.toString(), row.getString(1));
			}
		}
	}
	@Test void explicitSqliteBulkDoesNotAlterConfiguredMysqlCacheOrFlush() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.configureLegacyCache(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var stored = new java.util.concurrent.atomic.AtomicInteger(1);
			org.mockito.Mockito.doAnswer(call -> { for (Column column : call.<java.util.List<Column>>getArgument(1)) if (column.getName().equals("X")) stored.set(column.getValue().getInt()); return null; }).when(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(false));
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setValues(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored()); org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, stored.get());
		}
	}
	@Test void explicitMysqlBulkDoesNotAlterConfiguredSqliteCacheOrFlush() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var stored = new java.util.concurrent.atomic.AtomicInteger(1);
			org.mockito.Mockito.doAnswer(call -> { for (Column column : call.<java.util.List<Column>>getArgument(1)) if (column.getName().equals("X")) stored.set(column.getValue().getInt()); return null; }).when(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(false));
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setValues(com.bencodez.advancedcore.api.user.UserStorage.MYSQL, new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(10, stored.get()); org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
		}
	}
	@Test void storageReloadDoesNotAttachNewStoreWriteToOldCacheGeneration() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			f.data.setValues(com.bencodez.advancedcore.api.user.UserStorage.MYSQL, new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
		}
	}
	@Test void storageReloadScalarDoesNotAlterOldCacheGeneration() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			f.data.setInt(com.bencodez.advancedcore.api.user.UserStorage.MYSQL, "X", 10, false);
			org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
			verify(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.eq("X"), any(com.bencodez.simpleapi.sql.data.DataValue.class));
		}
	}
	@Test void sharedSqliteRejectsDuplicateOrAmbiguousAliases() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.configureSharedStorage(values -> {}, Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> f.cache.tryMutateIntegers(java.util.Map.of("X", 1, "x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
			org.junit.jupiter.api.Assertions.assertEquals(5, f.cache.getIfPresent("X").getInt());
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5), "x", new DataValueInt(7))));
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
			org.junit.jupiter.api.Assertions.assertNull(f.cache.tryMutateIntegers(java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			f.cache.getCache().put("x", new DataValueInt(7));
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
		}
	}
	@Test void sharedSqliteAliasRetainsInFlightValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var writes = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger();
			f.cache.configureSharedStorage(values -> { if (writes.getAndIncrement() == 0) { entered.countDown(); try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } } stored.set(values.get("X").getInt()); }, Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 7), true);
			var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var flush = worker.submit(f.cache::processChangesForSharedRuntime); org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertTrue(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
				var mutation = f.cache.tryMutateIntegers(java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
				org.junit.jupiter.api.Assertions.assertEquals(7, mutation.previous().get("x")); release.countDown(); flush.get(5, java.util.concurrent.TimeUnit.SECONDS);
				f.cache.processChangesForSharedRuntime(); org.junit.jupiter.api.Assertions.assertEquals(8, stored.get());
			} finally { release.countDown(); worker.shutdownNow(); }
		}
	}
	@Test void sharedSqliteKeepsNonAsciiIdentifiersDistinct() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.configureSharedStorage(values -> {}, Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Ä", new DataValueInt(5), "ä", new DataValueInt(7))));
			var result = f.cache.tryMutateIntegers(java.util.Map.of("ä", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertEquals(7, result.previous().get("ä")); org.junit.jupiter.api.Assertions.assertEquals(5, f.cache.getIfPresent("Ä").getInt()); org.junit.jupiter.api.Assertions.assertEquals(8, f.cache.getIfPresent("ä").getInt());
		}
	}
	@Test void sharedMysqlAmbiguousAliasDoesNotSeedZero() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.configureSharedStorage(values -> {}, Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
			var result = f.cache.tryMutateIntegers(java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNull(result); org.junit.jupiter.api.Assertions.assertNull(f.cache.getIfPresent("x")); org.junit.jupiter.api.Assertions.assertEquals(5, f.cache.getIfPresent("X").getInt());
			org.junit.jupiter.api.Assertions.assertTrue(f.cache.seedMissingIntegerFromCompleteSnapshot("other"));
			org.junit.jupiter.api.Assertions.assertEquals(0, f.cache.getIfPresent("other").getInt());
		}
	}
	@Test void sharedMysqlRejectsArithmeticWhenBothAliasesAlreadyExist() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.configureSharedStorage(values -> {}, Runnable::run, Runnable::run, com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5), "x", new DataValueInt(0))));
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
			org.junit.jupiter.api.Assertions.assertNull(f.cache.tryMutateIntegers(java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
			org.junit.jupiter.api.Assertions.assertEquals(5, f.cache.getIfPresent("X").getInt());
		}
	}
	@Test void failedExplicitSqliteScalarDoesNotRetryInConfiguredMysql() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.configureLegacyCache(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var mysql = mock(com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class); when(f.plugin.getMysql()).thenReturn(mysql);
			var stored = new java.util.concurrent.atomic.AtomicInteger(1);
			org.mockito.Mockito.doAnswer(call -> { for (Column column : call.<java.util.List<Column>>getArgument(1)) if (column.getName().equals("X")) stored.set(column.getValue().getInt()); return null; }).when(mysql).update(org.mockito.ArgumentMatchers.eq(f.uuid.toString()), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(false));
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setInt(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, "X", 10, false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, stored.get());
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void explicitSqliteScalarRetainsCachedCallbackAndDoesNotAlterMysqlCache() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.configureLegacyCache(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { called.set(true); return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.<String[]>any());
			var table = f.plugin.getSQLiteUserTable();
			org.mockito.Mockito.doAnswer(call -> { org.junit.jupiter.api.Assertions.assertTrue(called.get()); return call.callRealMethod(); }).when(table).update(any(Column.class), org.mockito.ArgumentMatchers.anyList());
			f.data.setInt(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, "X", 10, false);
			org.junit.jupiter.api.Assertions.assertTrue(called.get()); org.junit.jupiter.api.Assertions.assertEquals(1, f.cache.getIfPresent("X").getInt()); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			verify(f.users, org.mockito.Mockito.times(1)).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.<String[]>any());
		}
	}
	@Test void queuedExplicitOtherStoreRetainsExistingConfiguredQueueBehavior() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.configureLegacyCache(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
			f.data.setInt(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, "X", 10, true);
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored()); org.junit.jupiter.api.Assertions.assertEquals(10, f.cache.getIfPresent("X").getInt()); org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess());
		}
	}
	@Test void sharedSqliteAliasUsesPersistedBalanceRatherThanZero(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var backend = new com.bencodez.advancedcore.core.user.storage.sql.SqliteUserBackend(directory, "Users", "Users", com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema.builder().column("X", "INTEGER", com.bencodez.simpleapi.sql.DataType.INTEGER).build(), com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger.NO_OP);
			backend.user(f.uuid).writeValues(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))));
			var runtime = new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, new com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner(f.manager));
			try {
				runtime.populate(f.uuid); org.junit.jupiter.api.Assertions.assertTrue(f.cache.seedMissingIntegerFromCompleteSnapshot("x"));
				var result = f.cache.tryMutateIntegers(java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
				org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertEquals(5, result.previous().get("x"));
				f.cache.processChangesForSharedRuntime();
				var row = backend.user(f.uuid).readRow(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); org.junit.jupiter.api.Assertions.assertEquals(6, row.stream().filter(column -> column.getName().equalsIgnoreCase("X")).findFirst().orElseThrow().getValue().getInt());
			} finally { runtime.close(); }
		}
	}
	private static final class PausingWriteLock extends java.util.concurrent.locks.ReentrantReadWriteLock {
		final java.util.concurrent.CountDownLatch enteringUnlock = new java.util.concurrent.CountDownLatch(1), releaseUnlock = new java.util.concurrent.CountDownLatch(1);
		final WriteLock write = new WriteLock(this) {
			@Override public void unlock() {
				enteringUnlock.countDown();
				try { if (!releaseUnlock.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
				super.unlock();
			}
		};
		@Override public WriteLock writeLock() { return write; }
	}
	@Test void setterAtCheckpointUnlockIsNotStranded() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var order = new PausingWriteLock(); var field = f.cache.getClass().getDeclaredField("legacyMutationOrder"); field.setAccessible(true); field.set(f.cache, order);
			var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var checkpoint = pool.submit(() -> f.cache.flushChangesAndRun(() -> {}));
				org.junit.jupiter.api.Assertions.assertTrue(order.enteringUnlock.await(5, java.util.concurrent.TimeUnit.SECONDS));
				var entered = new java.util.concurrent.CountDownLatch(1);
				var setter = pool.submit(() -> { entered.countDown(); f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true); });
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				try { setter.get(250, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (java.util.concurrent.TimeoutException expected) { }
				order.releaseUnlock.countDown(); checkpoint.get(5, java.util.concurrent.TimeUnit.SECONDS); setter.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess()); f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			} finally { order.releaseUnlock.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void reentrantSchedulingCallbackDoesNotStrandCheckpointChange() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true); return null; }).when(f.plugin).debug(org.mockito.ArgumentMatchers.eq("Schedule changes"));
			f.cache.flushChangesAndRun(() -> f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true));
			org.junit.jupiter.api.Assertions.assertTrue(called.get()); verify(f.plugin, org.mockito.Mockito.times(1)).debug("Schedule changes"); f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
		}
	}
	@Test void customProviderRowCannotCertifySharedSnapshotComplete() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var primary = new Column("uuid", new DataValueString(f.uuid.toString()));
			var table = new UserTable(f.plugin, "Users", java.util.List.of(primary), primary) {
				@Override public void checkColumn(Column column) { }
				@Override public java.util.ArrayList<Column> getExact(Column column) { var values = super.getExact(column); values.removeIf(value -> value.getName().equals("X")); return values; }
			};
			var sqlite = mock(SQLite.class); when(sqlite.getSQLConnection()).thenReturn(f.db); table.setSqLite(sqlite);
			var backend = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend(f.plugin, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, null, table);
			var owner = new com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner(f.manager);
			var runtime = new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, owner);
			try { runtime.populate(f.uuid); org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("X")); }
			finally { runtime.close(); }
		}
	}
	@Test void unknownSharedProviderDoesNotAuthorizeMissingDefaults() throws Exception { sharedProviderAuthority(false); }
	@Test void optedInSharedProviderAuthorizesMissingDefaults() throws Exception { sharedProviderAuthority(true); }
	private void sharedProviderAuthority(boolean authoritative) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var delegate = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend(f.plugin, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, null, f.plugin.getSQLiteUserTable());
			var backend = new com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend() {
				@Override public com.bencodez.advancedcore.api.user.UserStorage storageType() { return delegate.storageType(); }
				@Override public com.bencodez.advancedcore.core.user.storage.SqlUserStorage user(java.util.UUID uuid) { return delegate.user(uuid); }
				@Override public java.util.List<java.util.UUID> enumerateUsers() { return delegate.enumerateUsers(); }
				@Override public boolean isOpen() { return delegate.isOpen(); }
				@Override public void close() { delegate.close(); }
				@Override public boolean hasAuthoritativeRowReads() { return authoritative; }
			};
			var runtime = new com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime(backend, new com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner(f.manager));
			try { runtime.populate(f.uuid); org.junit.jupiter.api.Assertions.assertEquals(authoritative, f.cache.seedMissingIntegerFromCompleteSnapshot("missing")); }
			finally { runtime.close(); }
		}
	}
	@Test void rejectedPartiallySupersededInsertRetainsFailure() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Y INTEGER"); sql.executeUpdate("DELETE FROM Users"); }
			var table = f.plugin.getSQLiteUserTable(); var primary = new Column("uuid", new DataValueString(f.uuid.toString()));
			var old = f.manager.beginLegacyDirectWrite(f.uuid, java.util.Set.of(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X"), com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("Y")), f.uuid.toString());
			var newer = f.manager.beginLegacyDirectWrite(f.uuid, java.util.Set.of(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X")), f.uuid.toString());
			com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(f.manager, newer, null, () -> table.insert(new java.util.ArrayList<>(java.util.List.of(primary, new Column("X", new DataValueInt(10))))));
			f.manager.markLegacyDirectWriteSucceeded(newer); f.manager.endLegacyDirectWrite(newer);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			try {
				var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(f.manager, old, null, () -> table.insert(new java.util.ArrayList<>(java.util.List.of(primary, new Column("X", new DataValueInt(2)), new Column("Y", new DataValueInt(3)))))));
				f.manager.failLegacyDirectWrite(old, null, failure);
			} finally { f.manager.endLegacyDirectWrite(old); }
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedOrdinarySqliteLoadCannotAuthorizeDefaultArithmetic() throws Exception {
		String uri = "jdbc:sqlite:file:load-" + java.util.UUID.randomUUID() + "?mode=memory&cache=shared";
		try (var db = java.sql.DriverManager.getConnection(uri); var lock = java.sql.DriverManager.getConnection(uri); var f = new ReviewSqliteFixture(db)) {
			try (var sql = db.createStatement()) { sql.executeUpdate("PRAGMA busy_timeout=100"); }
			f.manager.addKey(new com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt("X"));
			f.manager.getUserDataCache().clear();
			try (var sql = lock.createStatement()) { sql.executeUpdate("BEGIN EXCLUSIVE"); }
			try { f.manager.cacheUser(f.uuid, null); } finally { try (var sql = lock.createStatement()) { sql.executeUpdate("ROLLBACK"); } }
			var add = com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD;
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null));
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.populateCompleteLegacyCache(f.uuid));
			var result = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null);
			org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertEquals(1, result.previous().get("X"));
		}
	}
	@Test void incompleteLegacyPopulationCannotAuthorizeArithmetic() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.updateCache(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(0))));
			var add = com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD;
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null));
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
		}
	}
	@Test void sqliteArithmeticUsesExistingAsciiAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var add = com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD;
			var first = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("x", 1), add, null);
			org.junit.jupiter.api.Assertions.assertNotNull(first); org.junit.jupiter.api.Assertions.assertEquals(1, first.previous().get("x"));
			var second = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), add, null);
			org.junit.jupiter.api.Assertions.assertNotNull(second); org.junit.jupiter.api.Assertions.assertEquals(2, second.previous().get("X"));
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(3, f.stored());
		}
	}
	@Test void retainedCacheUsesLatestUncachedSqliteAliasWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.user.isCached()).thenReturn(false); f.data.setInt("x", 20, false);
			var result = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertEquals(20, result.previous().get("X"));
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(21, f.stored());
		}
	}
	@Test void strictSqliteDefaultsDoNotShadowAnExistingAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.addKey(new com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt("x"));
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.populateCompleteLegacyCache(f.uuid));
			var result = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertEquals(1, result.previous().get("x"));
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
		}
	}
	@Test void ambiguousSqliteCacheAliasesDoNotAuthorizeArithmetic() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(1), "x", new DataValueInt(7))));
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
		}
	}
	@Test void sqliteArithmeticKeepsNonAsciiColumnsDistinct() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Ä INTEGER"); sql.executeUpdate("ALTER TABLE Users ADD COLUMN ä INTEGER"); sql.executeUpdate("UPDATE Users SET Ä=5, ä=9"); }
			f.cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Ä", new DataValueInt(5), "ä", new DataValueInt(9))));
			var result = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("Ä", 1, "ä", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNotNull(result); org.junit.jupiter.api.Assertions.assertEquals(5, result.previous().get("Ä")); org.junit.jupiter.api.Assertions.assertEquals(9, result.previous().get("ä"));
			f.cache.processChanges();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT Ä, ä FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(6, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(2)); }
		}
	}
	@Test void ignoredBulkUuidCannotBlockSuccessfulRetryRetirement() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL, 'reject'); END"); }
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("uuid", new DataValueString(f.uuid.toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void legacyBatchCapturesGenerationBeforeSelectingValues() throws Exception {
		legacyBatchGenerationBoundary(false);
	}
	@Test void legacyBatchRetriesWhenGenerationChangesAfterCapture() throws Exception {
		legacyBatchGenerationBoundary(true);
	}
	private void legacyBatchGenerationBoundary(boolean afterCapture) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			class HookMap extends java.util.concurrent.ConcurrentHashMap<java.util.UUID, Object> {
				boolean fired;
				@Override public Object compute(java.util.UUID key, java.util.function.BiFunction<? super java.util.UUID, ? super Object, ? extends Object> function) {
					if (!fired && !afterCapture) { fired = true; f.data.setInt("X", 10, false); }
					Object result = super.compute(key, function);
					if (!fired && afterCapture) { fired = true; f.data.setInt("X", 10, false); }
					return result;
				}
			}
			var field = com.bencodez.advancedcore.api.user.usercache.UserDataManager.class.getDeclaredField("legacyWriteStates"); field.setAccessible(true); field.set(f.manager, new HookMap());
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(10, f.cache.getIfPresent("X").getInt());
		}
	}
	@Test void failedSqliteDirectRetryDoesNotMergeNonAsciiColumns() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Ä INTEGER"); sql.executeUpdate("ALTER TABLE Users ADD COLUMN ä INTEGER"); }
			var timer = mock(java.util.concurrent.ScheduledExecutorService.class); when(f.plugin.getTimer()).thenReturn(timer);
			var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			org.mockito.Mockito.doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
			f.data.setInt("Ä", 10, false, true); f.data.setInt("ä", 20, true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL, 'reject'); END"); }
			pending.get().run();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.cache.processChanges();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT Ä, ä FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(20, row.getInt(2)); }
		}
	}
	@Test void failedSqliteDirectRetryKeepsNewerCaseAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var timer = mock(java.util.concurrent.ScheduledExecutorService.class); when(f.plugin.getTimer()).thenReturn(timer);
			var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			org.mockito.Mockito.doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
			f.data.setInt("x", 10, false, true); f.data.setInt("X", 20, true);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL, 'reject'); END"); }
			pending.get().run();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(20, f.cache.getIfPresent("X").getInt());
		}
	}
	private static final class ReviewSqliteFixture implements AutoCloseable {
		final Connection db;
		final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		final com.bencodez.advancedcore.api.user.UserManager users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		final com.bencodez.advancedcore.api.user.AdvancedCoreUser user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
		final java.util.UUID uuid = java.util.UUID.randomUUID();
		final com.bencodez.advancedcore.api.user.usercache.UserDataManager manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
		com.bencodez.advancedcore.api.user.usercache.UserDataCache cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, uuid);
		final com.bencodez.advancedcore.api.user.UserData data = new com.bencodez.advancedcore.api.user.UserData(user);
		ReviewSqliteFixture() throws Exception { this(java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")); }
		ReviewSqliteFixture(Connection connection) throws Exception {
			db = connection;
			try (var sql = db.createStatement()) { sql.executeUpdate("CREATE TABLE Users(uuid TEXT PRIMARY KEY, X INTEGER)"); sql.executeUpdate("INSERT INTO Users VALUES ('" + uuid + "',1)"); }
			var primary = new Column("uuid", new DataValueString(uuid.toString())); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(db); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class));
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(uuid.toString()); when(user.getUserData()).thenReturn(data);
			when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(1)))); manager.getUserDataCache().put(uuid, cache);
		}
		void configureLegacyCache(com.bencodez.advancedcore.api.user.UserStorage storage) {
			when(plugin.getStorageType()).thenReturn(storage);
			cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, uuid);
			cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(1))));
			manager.getUserDataCache().put(uuid, cache); when(user.getCache()).thenReturn(cache);
		}
		int stored() throws Exception { try (var sql = db.createStatement(); var row = sql.executeQuery("SELECT X FROM Users")) { row.next(); return row.getInt(1); } }
		@Override public void close() throws Exception { manager.getTimer().shutdownNow(); db.close(); }
	}
	@Test void sqliteAsyncNestedAliasCanDrainBeforeDeferredWrite() throws Exception { sqliteAsyncNestedAlias(false, false, false); }
	@Test void sqliteAsyncNestedAliasKeepsCapturedProviderAfterReplacement() throws Exception { sqliteAsyncNestedAlias(true, false, false); }
	@Test void sqliteAsyncNestedAliasRetainsFailedDeferredWrite() throws Exception { sqliteAsyncNestedAlias(false, true, false); }
	@Test void sqliteAsyncNestedAliasCanDrainOnMissingRow() throws Exception { sqliteAsyncNestedAlias(false, false, true); }
	@Test void sqliteConfirmedNestedInsertPreservesOtherBatchColumns() throws Exception { sqliteConfirmedNestedInsert(false); }
	@Test void sqliteNestedInsertAfterAdmissionPreservesOtherBatchColumns() throws Exception { sqliteConfirmedNestedInsert(true); }
	private void sqliteConfirmedNestedInsert(boolean afterAdmission) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Y INTEGER"); sql.executeUpdate("DELETE FROM Users"); }
			var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			var tasks = new java.util.ArrayList<Runnable>();
			when(f.plugin.getTimer()).thenReturn(timer);
			org.mockito.Mockito.doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { setInt("x", 7, false, true); super.setValues(values); }
			};
			when(f.user.getUserData()).thenReturn(data);
			var table = f.plugin.getSQLiteUserTable();
			var first = new java.util.concurrent.atomic.AtomicBoolean(true);
			if (afterAdmission) {
				org.mockito.Mockito.doAnswer(call -> {
					if (first.compareAndSet(true, false)) tasks.remove(0).run();
					return call.callRealMethod();
				}).when(table).insert(any());
			} else {
				org.mockito.Mockito.doAnswer(call -> {
					if (first.compareAndSet(true, false)) { tasks.remove(0).run(); return false; }
					return call.callRealMethod();
				}).when(table).containsKey(anyString());
			}
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Y", 30), true);
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT X, Y FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next());
				org.junit.jupiter.api.Assertions.assertEquals(7, row.getInt(1));
				org.junit.jupiter.api.Assertions.assertEquals(30, row.getInt(2));
			}
			org.junit.jupiter.api.Assertions.assertTrue(tasks.isEmpty());
		}
	}
	private void sqliteAsyncNestedAlias(boolean replaceProvider, boolean failDeferred, boolean missingRow) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			var tasks = new java.util.ArrayList<Runnable>();
			when(f.plugin.getTimer()).thenReturn(timer);
			org.mockito.Mockito.doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { setInt("x", 7, false, true); super.setValues(values); }
			};
			when(f.user.getUserData()).thenReturn(data);
			try (var otherDb = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
				if (missingRow) try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
				try (var sql = otherDb.createStatement()) { sql.executeUpdate("CREATE TABLE Users(uuid TEXT PRIMARY KEY, X INTEGER)"); sql.executeUpdate("INSERT INTO Users VALUES ('" + f.uuid + "',50)"); }
				var primary = new Column("uuid", new DataValueString(f.uuid.toString()));
				var otherTable = spy(new UserTable(f.plugin, "Users", Collections.singletonList(primary), primary));
				var sqlite = mock(SQLite.class); when(sqlite.getSQLConnection()).thenReturn(otherDb); otherTable.setSqLite(sqlite);
				doNothing().when(otherTable).checkColumn(any(Column.class));
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
				f.cache.processChanges();
				org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
				org.junit.jupiter.api.Assertions.assertEquals(20, f.stored());
				org.junit.jupiter.api.Assertions.assertEquals(1, tasks.size());
				if (replaceProvider) when(f.plugin.getSQLiteUserTable()).thenReturn(otherTable);
				if (failDeferred) try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_x BEFORE UPDATE OF X ON Users BEGIN SELECT RAISE(FAIL,'synthetic'); END"); }
				tasks.remove(0).run();
				if (failDeferred) { org.junit.jupiter.api.Assertions.assertEquals(20, f.stored()); org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess()); }
				else org.junit.jupiter.api.Assertions.assertEquals(7, f.stored());
				org.junit.jupiter.api.Assertions.assertEquals(7, f.cache.getIfPresent("X").getInt());
				try (var sql = otherDb.createStatement(); var row = sql.executeQuery("SELECT X FROM Users")) { row.next(); org.junit.jupiter.api.Assertions.assertEquals(50, row.getInt(1)); }
			} finally { while (!tasks.isEmpty()) tasks.remove(0).run(); }
		}
	}
	@Test void sqliteIndependentWriteAfterNestedConfirmationSurvivesBatchRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var first = new java.util.concurrent.atomic.AtomicBoolean(true);
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
					setInt("x", 7, false);
					if (first.compareAndSet(true, false)) {
						entered.countDown();
						try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
						catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
					}
					super.setValues(values);
				}
			};
			when(f.user.getUserData()).thenReturn(data);
			var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
				var flush = worker.submit(f.cache::processChanges);
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				f.data.setInt("X", 99, false); release.countDown();
				flush.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertEquals(99, f.stored());
				org.junit.jupiter.api.Assertions.assertEquals(99, f.cache.getIfPresent("X").getInt());
			} finally { release.countDown(); worker.shutdownNow(); }
		}
	}
	@Test void sqliteConfirmedNestedFirstRowDoesNotReplayEarlierQueuedValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { setInt("x", 7, false); super.setValues(values); }
			};
			when(f.user.getUserData()).thenReturn(data);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(7, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(7, f.cache.getIfPresent("X").getInt());
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
		}
	}
	@Test void sqliteFailedNestedAliasStaysPendingUntilSuccessfulRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_x BEFORE UPDATE OF X ON Users BEGIN SELECT RAISE(FAIL,'synthetic'); END"); }
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { setInt("x", 7, false); super.setValues(values); }
			};
			when(f.user.getUserData()).thenReturn(data);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, f.cache::processChanges);
			org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess());
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_x"); }
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
			org.junit.jupiter.api.Assertions.assertEquals(7, f.stored());
		}
	}
	@Test void sqliteConfirmedNestedAliasDrainsWithoutDuplicatePhysicalWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) {
				sql.executeUpdate("ALTER TABLE Users ADD COLUMN Z INTEGER");
				sql.executeUpdate("CREATE TABLE Audit(n INTEGER)");
				sql.executeUpdate("CREATE TRIGGER audit_x AFTER UPDATE OF X ON Users BEGIN INSERT INTO Audit VALUES(1); END");
			}
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) { setInt("x", 7, false); super.setValues(values); }
			};
			when(f.user.getUserData()).thenReturn(data);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Z", 30), true);
			f.cache.processChanges();
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT X,Z,(SELECT count(*) FROM Audit) FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next());
				org.junit.jupiter.api.Assertions.assertEquals(7, row.getInt(1));
				org.junit.jupiter.api.Assertions.assertEquals(30, row.getInt(2));
				org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(3));
			}
			org.junit.jupiter.api.Assertions.assertEquals(7, f.cache.getIfPresent("X").getInt());
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.hasChangesToProcess());
		}
	}
	@Test void sqliteBulkAliasesRespectExistingRowAssignmentOrder() throws Exception { bulkAliases(false, false); }
	@Test void sqliteAliasMutationUpdatesCanonicalCacheRead() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("x", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(2, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY));
		}
	}
	@Test void completedSqliteAliasReconcilesOlderSnapshot() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("ALTER TABLE Users ADD COLUMN Other INTEGER"); }
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("x", 2), true);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Other", 3), true);
			long snapshot = f.cache.getSharedSnapshotVersion();
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertTrue(f.cache.tryPublishCompleteLegacySnapshot(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(1))), snapshot));
			org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
		}
	}
	@Test void sqliteAliasBulkWriteUpdatesCanonicalCacheRead() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("x", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			org.junit.jupiter.api.Assertions.assertEquals(10, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY));
		}
	}
	@Test void failedSqliteBulkPrimaryAliasDoesNotPreventRetirementAfterRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(f.uuid.toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.data.setInt("X", 10, false);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void failedSqliteBulkChangingPrimaryAliasStillRequiresRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(java.util.UUID.randomUUID().toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedSqliteBulkUppercasePrimaryValueStillRequiresRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(f.uuid.toString().toUpperCase(java.util.Locale.ROOT))); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedSqliteScalarPrimaryWriteCannotBeAcknowledgedByBulkRetry() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", java.util.UUID.randomUUID().toString(), false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedPrimaryOnlyInsertCannotBeAcknowledgedAsSuccessful() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); sql.executeUpdate("CREATE TRIGGER reject_insert BEFORE INSERT ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", java.util.UUID.randomUUID().toString(), false);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void failedBulkInsertTracksActualPrimaryAliasRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); sql.executeUpdate("CREATE TRIGGER reject_insert BEFORE INSERT ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(java.util.UUID.randomUUID().toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void filteredConversionCannotCertifyACompleteLegacyRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("UPDATE Users SET X=9"); }
			var custom = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> convert(java.util.List<Column> columns) {
					var values = super.convert(columns); values.remove("X"); return values;
				}
			};
			when(f.user.getUserData()).thenReturn(custom);
			org.junit.jupiter.api.Assertions.assertNull(custom.getValuesIfStrictComplete());
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.manager.populateCompleteLegacyCache(f.uuid));
			org.junit.jupiter.api.Assertions.assertEquals(9, f.stored());
		}
	}
	@Test void filteredConversionCannotCertifyACompleteSharedRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var custom = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> convert(java.util.List<Column> columns) {
					var values = super.convert(columns); values.remove("X"); return values;
				}
			};
			when(f.user.getUserData()).thenReturn(custom);
			var backend = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend(f.plugin, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, null, f.plugin.getSQLiteUserTable());
			f.manager.bindSharedSqlBackend(backend, Runnable::run); f.cache.configureSharedStorage(values -> {}, Runnable::run);
			f.manager.cacheUser(f.uuid, null);
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("X"));
		}
	}
	@Test void filteredRowReaderCannotCertifyACompleteSharedRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var custom = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public java.util.List<Column> getSQLiteRow() { var columns = new java.util.ArrayList<>(super.getSQLiteRow()); columns.removeIf(column -> column.getName().equals("X")); return columns; }
			};
			when(f.user.getUserData()).thenReturn(custom);
			var backend = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend(f.plugin, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, null, f.plugin.getSQLiteUserTable());
			f.manager.bindSharedSqlBackend(backend, Runnable::run); f.cache.configureSharedStorage(values -> {}, Runnable::run); f.manager.cacheUser(f.uuid, null);
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.seedMissingIntegerFromCompleteSnapshot("X"));
		}
	}
	@Test void arithmeticIsNotAdmittedBehindPendingLegacyCheckpoint() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var field = f.cache.getClass().getDeclaredField("legacyMutationOrder"); field.setAccessible(true); var order = (java.util.concurrent.locks.ReentrantReadWriteLock) field.get(f.cache);
			var pendingField = f.cache.getClass().getDeclaredField("legacyCheckpointsPending"); pendingField.setAccessible(true);
			var pool = java.util.concurrent.Executors.newSingleThreadExecutor(); order.readLock().lock();
			try {
				var checkpoint = pool.submit(() -> f.cache.flushChangesAndRun(() -> f.cache.updateCache(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(5))))));
				long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5); boolean pending = false;
				while (System.nanoTime() < until) { synchronized (f.cache) { pending = pendingField.getInt(f.cache) > 0; } if (pending) break; Thread.yield(); }
				org.junit.jupiter.api.Assertions.assertTrue(pending);
				f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 10), true);
				org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
				order.readLock().unlock(); checkpoint.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertTrue(f.manager.populateCompleteLegacyCache(f.uuid));
				var first = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
				var second = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
				org.junit.jupiter.api.Assertions.assertEquals(10, first.previous().get("X")); org.junit.jupiter.api.Assertions.assertEquals(11, second.previous().get("X"));
				f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(12, f.stored());
			} finally { if (order.getReadHoldCount() > 0) order.readLock().unlock(); pool.shutdownNow(); }
		}
	}
	@Test void prepareFailureTracksEffectivePrimaryAlias() throws Exception { prepareFailurePrimaryAlias(false); }
	@Test void prepareFailureRetainsChangedEffectivePrimaryAlias() throws Exception { prepareFailurePrimaryAlias(true); }
	private void prepareFailurePrimaryAlias(boolean changed) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var wrapped = mock(Connection.class); when(wrapped.unwrap(org.sqlite.SQLiteConnection.class)).thenReturn((org.sqlite.SQLiteConnection) f.db);
			var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
			when(wrapped.prepareStatement(anyString())).thenAnswer(call -> { String sql = call.getArgument(0); if (sql.startsWith("UPDATE") && fail.getAndSet(false)) throw new java.sql.SQLException("synthetic prepare failure"); return f.db.prepareStatement(sql); });
			var sqlite = mock(SQLite.class); when(sqlite.getSQLConnection()).thenReturn(wrapped); f.plugin.getSQLiteUserTable().setSqLite(sqlite);
			String other = java.util.UUID.randomUUID().toString();
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("UUID", new DataValueString(changed ? f.uuid.toString() : other)); values.put("uUiD", new DataValueString(changed ? other : f.uuid.toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values); f.data.setInt("X", 10, false);
			if (changed) org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
			else f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void olderPrimaryOnlyInsertDoesNotReplaceNewerRowValues() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			var table = f.plugin.getSQLiteUserTable(); var primary = new Column("uuid", new DataValueString(f.uuid.toString()));
			var old = f.manager.beginLegacyDirectWrite(f.uuid, java.util.Set.of(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("uuid")), f.uuid.toString());
			var newer = f.manager.beginLegacyDirectWrite(f.uuid, java.util.Set.of(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X")), f.uuid.toString());
			try {
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(f.manager, newer, null, () -> table.insert(new java.util.ArrayList<>(java.util.List.of(primary, new Column("X", new DataValueInt(10))))));
				f.manager.markLegacyDirectWriteSucceeded(newer); f.manager.endLegacyDirectWrite(newer);
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(f.manager, old, null, () -> table.insert(new java.util.ArrayList<>(java.util.List.of(primary, new Column("uuid", new DataValueString(f.uuid.toString()))))));
				f.manager.markLegacyDirectWriteSucceeded(old);
				org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			} finally { f.manager.endLegacyDirectWrite(old); }
		}
	}
	@Test void sharedBindCannotOvertakeLegacySnapshotPublication() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var cache = spy(f.cache); f.manager.getUserDataCache().put(f.uuid, cache);
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			org.mockito.Mockito.doAnswer(call -> { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); }).when(cache).tryPublishCompleteLegacySnapshot(any(), org.mockito.ArgumentMatchers.anyLong());
			var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var load = pool.submit(() -> f.manager.populateCompleteLegacyCache(f.uuid));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class); when(backend.storageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
				org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.manager.bindSharedSqlBackend(backend, Runnable::run));
				org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, f.manager::beginSharedBindingTransition);
				release.countDown(); org.junit.jupiter.api.Assertions.assertTrue(load.get(5, java.util.concurrent.TimeUnit.SECONDS));
				f.manager.bindSharedSqlBackend(backend, Runnable::run);
			} finally { release.countDown(); pool.shutdownNow(); }
		}
	}
	@Test void reentrantNotificationCanFlushQueuedLegacyWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			var called = new java.util.concurrent.atomic.AtomicBoolean();
			org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) f.cache.processChanges(); return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.<String[]>any());
			f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertTrue(called.get()); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void reentrantNotificationDoesNotExemptAnotherDirectWriter() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			var other = f.manager.beginLegacyDirectWrite(f.uuid, com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X"));
			try {
				var called = new java.util.concurrent.atomic.AtomicBoolean();
				org.mockito.Mockito.doAnswer(call -> { if (called.compareAndSet(false, true)) f.cache.processChanges(); return null; }).when(f.users).onChange(org.mockito.ArgumentMatchers.eq(f.user), org.mockito.ArgumentMatchers.<String[]>any());
				org.junit.jupiter.api.Assertions.assertThrows(com.bencodez.advancedcore.api.user.usercache.UserDataCache.StaleLegacyBatchException.class, () -> f.data.setInt("X", 10, false));
				org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
			} finally { f.manager.endLegacyDirectWrite(other); }
		}
	}
	@Test void primaryOnlyInsertDoesNotPublishWrongAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			String other = java.util.UUID.randomUUID().toString();
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("UUID", new DataValueString(f.uuid.toString())); values.put("uUiD", new DataValueString(other));
			f.data.setValues(values);
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT uuid FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(f.uuid.toString(), row.getString(1)); }
			org.junit.jupiter.api.Assertions.assertFalse(f.cache.snapshot().values().stream().anyMatch(value -> value != null && value.isString() && other.equals(value.getString())));
		}
	}
	@Test void failedPrimaryOnlyInsertDoesNotTrackIneffectiveAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); sql.executeUpdate("CREATE TRIGGER reject_insert BEFORE INSERT ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("UUID", new DataValueString(f.uuid.toString())); values.put("uUiD", new DataValueString(java.util.UUID.randomUUID().toString()));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_insert"); }
			f.data.setInt("X", 10, false); f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void sqliteCaseDistinctRowsDoNotSupersedeEachOther() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String lower = f.uuid.toString(), upper = lower.toUpperCase(java.util.Locale.ROOT);
			org.junit.jupiter.api.Assertions.assertNotEquals(lower, upper);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + upper + "',1)"); }
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(upper); when(other.isCached()).thenReturn(false);
			var otherData = new com.bencodez.advancedcore.api.user.UserData(other);
			f.data.setInt("X", 2, false, true); org.junit.jupiter.api.Assertions.assertNotNull(task.get());
			otherData.setInt("X", 10, false); task.get().run();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, lower); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(2, row.getInt(1)); }
				sql.setString(1, upper); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			}
		}
	}
	@Test void sqliteCaseDistinctCachedRowsDoNotShareValues() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String upper = f.uuid.toString().toUpperCase(java.util.Locale.ROOT);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + upper + "',1)"); }
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(upper); when(other.isCached()).thenReturn(true); when(other.getCache()).thenReturn(f.cache);
			new com.bencodez.advancedcore.api.user.UserData(other).setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertEquals(1, f.cache.getIfPresent("X").getInt());
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
			}
		}
	}
	@Test void sqliteCaseDistinctDirectStringDoesNotReplaceQueuedCanonicalValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String upper = f.uuid.toString().toUpperCase(java.util.Locale.ROOT);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + upper + "',1)"); }
			f.data.setInt("X", 2, true);
			var other = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			when(other.getPlugin()).thenReturn(f.plugin); when(other.getUUID()).thenReturn(upper); when(other.isCached()).thenReturn(true); when(other.getCache()).thenReturn(f.cache);
			new com.bencodez.advancedcore.api.user.UserData(other).setString("X", "10", false);
			org.junit.jupiter.api.Assertions.assertEquals(2, f.cache.getIfPresent("X").getInt());
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, upper); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			}
		}
	}
	@Test void olderScalarPublicationCannotReplaceNewerCommittedValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var first = new java.util.concurrent.atomic.AtomicBoolean(true);
			when(f.user.isCached()).thenAnswer(call -> { if (first.compareAndSet(true, false)) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return true; });
			var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var older = executor.submit(() -> f.data.setInt("X", 2, false));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				f.data.setInt("X", 10, false); release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
				org.junit.jupiter.api.Assertions.assertEquals(10, f.cache.getIfPresent("X").getInt());
			} finally { release.countDown(); executor.shutdownNow(); }
		}
	}
	@Test void failedNewerScalarRemainsRetryableAfterOlderWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var first = new java.util.concurrent.atomic.AtomicBoolean(true);
			when(f.user.isCached()).thenAnswer(call -> { if (first.compareAndSet(true, false)) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return true; });
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_ten BEFORE UPDATE ON Users WHEN NEW.X=10 BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var older = executor.submit(() -> f.data.setInt("X", 2, false));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				f.data.setInt("X", 10, false); release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_ten"); }
				org.junit.jupiter.api.Assertions.assertEquals(10, f.cache.getIfPresent("X").getInt());
				f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
			} finally { release.countDown(); executor.shutdownNow(); }
		}
	}
	@Test void asyncWriteFailureTracksExecutionTimeIdentity() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); sql.executeUpdate("CREATE TRIGGER reject_other BEFORE UPDATE ON Users WHEN OLD.uuid='" + other + "' BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 10, false, true); org.junit.jupiter.api.Assertions.assertNotNull(task.get()); identity.set(other); task.get().run();
			var failed = com.bencodez.advancedcore.api.user.usercache.UserDataManager.class.getDeclaredField("legacyUnrecoveredWrites"); failed.setAccessible(true);
			org.junit.jupiter.api.Assertions.assertFalse(((java.util.Map<?, ?>) failed.get(f.manager)).isEmpty(), "execution-row failure must be retained");
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
		}
	}
	@Test void asyncIdentityRebindRemainsAdmittedDuringRetirement() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); }
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 10, false, true);
			var retirement = f.manager.clearCacheAsyncCompletion().toCompletableFuture(); org.junit.jupiter.api.Assertions.assertFalse(retirement.isDone());
			identity.set(other); task.get().run(); retirement.get(5, java.util.concurrent.TimeUnit.SECONDS);
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) { sql.setString(1, other); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); } }
		}
	}
	@Test void asyncRebindDoesNotOverwriteOriginalQueuedValue() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); }
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setInt("X", 10, false, true); identity.set(other); task.get().run(); identity.set(f.uuid.toString()); f.cache.processChanges();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(2, row.getInt(1)); }
				sql.setString(1, other); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			}
		}
	}
	@Test void queuedMutationCannotRecreateRowAfterPrimaryMove() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
			f.data.setString("uuid", other, false);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, f.cache::processChanges);
			try (var sql = f.db.prepareStatement("SELECT count(*) FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { row.next(); org.junit.jupiter.api.Assertions.assertEquals(0, row.getInt(1)); }
			}
			org.junit.jupiter.api.Assertions.assertTrue(f.cache.hasChangesToProcess());
		}
	}
	@Test void movedSqliteRowCannotSpendOldSourceBalance() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString(); f.data.setString("uuid", other, false);
			org.junit.jupiter.api.Assertions.assertNull(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
			try (var sql = f.db.prepareStatement("SELECT count(*) FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { row.next(); org.junit.jupiter.api.Assertions.assertEquals(0, row.getInt(1)); }
			}
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.populateCompleteLegacyCache(f.uuid));
			var afterRefresh = f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
			org.junit.jupiter.api.Assertions.assertNotNull(afterRefresh); org.junit.jupiter.api.Assertions.assertTrue(afterRefresh.applied());
			f.cache.processChanges();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
				sql.setString(1, other); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(1, row.getInt(1)); }
			}
		}
	}
	@Test void primaryValueOnFirstInsertDoesNotFenceQueuedRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			f.data.setString("uuid", java.util.UUID.randomUUID().toString(), false);
			f.cache.processChanges();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(2, row.getInt(1)); }
			}
		}
	}
	@Test void failedPrimaryMoveDoesNotFenceQueuedMutation() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_move BEFORE UPDATE ON Users WHEN NEW.uuid != OLD.uuid BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", java.util.UUID.randomUUID().toString(), false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_move"); }
			f.cache.processChanges(); org.junit.jupiter.api.Assertions.assertEquals(2, f.stored());
		}
	}
	@Test void asyncRebindDoesNotUndoNewerQueuedMutation() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); }
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 10, false, true);
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 3), true);
			identity.set(other); task.get().run(); identity.set(f.uuid.toString()); f.cache.processChanges();
			try (var sql = f.db.prepareStatement("SELECT X FROM Users WHERE uuid=?")) {
				sql.setString(1, f.uuid.toString()); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(3, row.getInt(1)); }
				sql.setString(1, other); try (var row = sql.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			}
		}
	}
	@Test void asyncRebindSuccessDoesNotClearOriginalRowFailure() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); sql.executeUpdate("CREATE TRIGGER reject_first BEFORE UPDATE ON Users WHEN OLD.uuid='" + f.uuid + "' BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			f.data.setInt("X", 3, false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_first"); }
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 10, false, true); identity.set(other); task.get().run();
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
			identity.set(f.uuid.toString()); f.data.setInt("X", 3, false);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void asyncReboundRowFailureRequiresRetryOnThatRow() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String other = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("INSERT INTO Users VALUES ('" + other + "',1)"); sql.executeUpdate("CREATE TRIGGER reject_other BEFORE UPDATE ON Users WHEN OLD.uuid='" + other + "' BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			var identity = new java.util.concurrent.atomic.AtomicReference<>(f.uuid.toString()); when(f.user.getUUID()).thenAnswer(call -> identity.get());
			var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
			org.mockito.Mockito.doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
			f.data.setInt("X", 10, false, true); identity.set(other); task.get().run();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_other"); }
			identity.set(f.uuid.toString()); f.data.setInt("X", 10, false);
			org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
			identity.set(other); f.data.setInt("X", 10, false);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void failedBulkWithUnchangedEffectivePrimaryCanRetryWithoutPrimaryAlias() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(java.util.UUID.randomUUID().toString())); values.put("uUiD", new DataValueString(f.uuid.toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			var retry = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); retry.put("uUiD", new DataValueString(f.uuid.toString())); retry.put("X", new DataValueInt(10));
			f.data.setValues(retry);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void failedBulkInsertUsesFirstPrimaryAliasForFailureTracking() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().clear(); when(f.user.isCached()).thenReturn(false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); sql.executeUpdate("CREATE TRIGGER reject_insert BEFORE INSERT ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("UUID", new DataValueString(f.uuid.toString())); values.put("uUiD", new DataValueString(java.util.UUID.randomUUID().toString())); values.put("X", new DataValueInt(10));
			f.data.setValues(values);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_insert"); }
			f.data.setInt("X", 10, false);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			org.junit.jupiter.api.Assertions.assertEquals(10, f.stored());
		}
	}
	@Test void failedSqliteScalarPrimaryWriteCanBeRetriedExplicitly() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			String replacement = java.util.UUID.randomUUID().toString();
			try (var sql = f.db.createStatement()) { sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL,'reject'); END"); }
			f.data.setString("uuid", replacement, false);
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
			f.data.setString("uuid", replacement, false);
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT uuid FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(replacement, row.getString(1));
			}
		}
	}
	@Test void queuedSetterAfterBulkInvalidationDoesNotReadStorageOnCaller() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.plugin.getOptions()).thenReturn(new com.bencodez.advancedcore.AdvancedCoreConfigOptions());
			when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
			var table = f.plugin.getSQLiteUserTable(); var reads = new java.util.concurrent.atomic.AtomicInteger(); Thread caller = Thread.currentThread();
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExact(any(Column.class));
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			f.data.setInt("X", 11, true);
			org.junit.jupiter.api.Assertions.assertEquals(0, reads.get());
		}
	}
	@Test void cacheOnlyReadAfterBulkWriteDoesNotQuerySqlOnCaller() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.plugin.getOptions()).thenReturn(new com.bencodez.advancedcore.AdvancedCoreConfigOptions());
			when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
			org.mockito.Mockito.doAnswer(call -> { f.manager.cacheUserIfNeeded(f.uuid); return null; }).when(f.user).cacheIfNeeded();
			var table = f.plugin.getSQLiteUserTable(); var reads = new java.util.concurrent.atomic.AtomicInteger(); Thread caller = Thread.currentThread();
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExact(any(Column.class));
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(10, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY));
			org.junit.jupiter.api.Assertions.assertEquals(0, reads.get());
		}
	}
	@Test void noWaitHotReadAfterBulkWriteDoesNotQuerySqlOnCaller() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			when(f.plugin.getOptions()).thenReturn(new com.bencodez.advancedcore.AdvancedCoreConfigOptions());
			when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
			org.mockito.Mockito.doAnswer(call -> { f.manager.cacheUserIfNeeded(f.uuid); return null; }).when(f.user).cacheIfNeeded();
			var table = f.plugin.getSQLiteUserTable(); var reads = new java.util.concurrent.atomic.AtomicInteger(); Thread caller = Thread.currentThread();
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExact(any(Column.class));
			org.mockito.Mockito.doAnswer(call -> { if (Thread.currentThread() == caller) reads.incrementAndGet(); return call.callRealMethod(); }).when(table).getExactStrict(any(Column.class));
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			org.junit.jupiter.api.Assertions.assertEquals(10, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_WAIT));
			org.junit.jupiter.api.Assertions.assertEquals(0, reads.get());
		}
	}
	@Test void invalidatedNoWaitReadDoesNotWaitForBlockedRefresh() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var table = f.plugin.getSQLiteUserTable();
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var finished = new java.util.concurrent.CountDownLatch(1);
			org.mockito.Mockito.doAnswer(call -> { try { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return call.callRealMethod(); } finally { finished.countDown(); } }).when(table).getExactStrict(any(Column.class));
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				var read = executor.submit(() -> f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_WAIT));
				org.junit.jupiter.api.Assertions.assertEquals(10, read.get(1, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
			} finally { release.countDown(); finished.await(5, java.util.concurrent.TimeUnit.SECONDS); executor.shutdownNow(); }
		}
	}
	@Test void cacheOnlyAndNoDbLookupNeverScheduleAnInvalidatedRefresh() throws Exception {
		for (var mode : java.util.List.of(com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY,
				com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_DB_LOOKUP)) {
			try (var f = new ReviewSqliteFixture()) {
				f.cache.invalidateStorageSnapshot("X");
				f.data.getInt("X", 77, mode);
				f.manager.getTimer().submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
				verify(f.plugin.getSQLiteUserTable(), org.mockito.Mockito.never()).getExactStrict(any(Column.class));
				verify(f.user, org.mockito.Mockito.never()).getCache();
			}
		}
	}
	@Test void noStorageModeDoesNotRefreshWhenInvalidationRacesCacheLookup() throws Exception {
		for (var mode : java.util.List.of(com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY,
				com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_DB_LOOKUP)) {
			try (var f = new ReviewSqliteFixture()) {
				var cache = spy(f.cache);
				f.manager.getUserDataCache().put(f.uuid, cache);
				var first = new java.util.concurrent.atomic.AtomicBoolean(true);
				org.mockito.Mockito.doAnswer(call -> {
					boolean published = (Boolean) call.callRealMethod();
					if (first.compareAndSet(true, false)) cache.invalidateStorageSnapshot("X");
					return published;
				}).when(cache).hasPublishedStorageSnapshot();
				when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
				f.data.getInt("X", 77, mode);
				f.manager.getTimer().submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
				verify(f.plugin.getSQLiteUserTable(), org.mockito.Mockito.never()).getExactStrict(any(Column.class));
				verify(f.user, org.mockito.Mockito.never()).getCache();
			}
		}
	}
	@Test void noStorageModeDoesNotCreateAMissingCache() throws Exception {
		for (var mode : java.util.List.of(com.bencodez.advancedcore.api.user.UserDataFetchMode.CACHE_ONLY,
				com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_DB_LOOKUP)) {
			try (var f = new ReviewSqliteFixture()) {
				f.manager.getUserDataCache().remove(f.uuid);
				when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
				org.junit.jupiter.api.Assertions.assertEquals(77, f.data.getInt("X", 77, mode));
				verify(f.user, org.mockito.Mockito.never()).getCache();
				verify(f.plugin.getSQLiteUserTable(), org.mockito.Mockito.never()).getExactStrict(any(Column.class));
				org.junit.jupiter.api.Assertions.assertNull(f.manager.getUserDataCache().get(f.uuid));
			}
		}
	}
	@Test void defaultReadRetriesFailedCustomColdPopulation() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.manager.getUserDataCache().remove(f.uuid);
			when(f.plugin.getOptions()).thenReturn(new com.bencodez.advancedcore.AdvancedCoreConfigOptions());
			when(f.user.getCache()).thenAnswer(call -> f.manager.getCache(f.uuid));
			var calls = new java.util.concurrent.atomic.AtomicInteger();
			var data = new com.bencodez.advancedcore.api.user.UserData(f.user) {
				@Override public java.util.ArrayList<String> getKeys() { return new java.util.ArrayList<>(); }
				@Override public java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> getValues() {
					if (calls.getAndIncrement() == 0) throw new IllegalStateException("first read failed");
					return new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(7)));
				}
			};
			when(f.user.getUserData()).thenReturn(data);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> f.manager.cacheUser(f.uuid, null));
			org.junit.jupiter.api.Assertions.assertEquals(7, data.getInt("X", 77, com.bencodez.advancedcore.api.user.UserDataFetchMode.DEFAULT));
			org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
		}
	}
	@Test void directWritesNotifyOnceWhenEmptyCacheBecomesPopulated() throws Exception {
		for (boolean text : new boolean[] { false, true }) for (boolean async : new boolean[] { false, true }) {
			try (var f = new ReviewSqliteFixture()) {
				var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, f.uuid);
				f.manager.getUserDataCache().put(f.uuid, cache);
				when(f.user.isCached()).thenAnswer(call -> f.manager.isCached(f.uuid));
				var tasks = new java.util.ArrayList<Runnable>();
				var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
				when(f.plugin.getTimer()).thenReturn(timer);
				org.mockito.Mockito.doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
				if (text) f.data.setString("X", "4", false, async); else f.data.setInt("X", 4, false, async);
				while (!tasks.isEmpty()) tasks.remove(0).run();
				verify(f.users, org.mockito.Mockito.times(1)).onChange(f.user, "X");
			}
		}
	}
	@Test void uncachedDirectWriteNotificationCanAwaitRetirement() throws Exception {
		for (boolean text : new boolean[] { false, true }) for (boolean async : new boolean[] { false, true }) {
			try (var f = new ReviewSqliteFixture()) {
				f.manager.getUserDataCache().clear();
				when(f.user.isCached()).thenReturn(false);
				var tasks = new java.util.ArrayList<Runnable>();
				var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
				when(f.plugin.getTimer()).thenReturn(timer);
				org.mockito.Mockito.doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
				org.mockito.Mockito.doAnswer(call -> {
					f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS);
					return null;
				}).when(f.users).onChange(f.user, "X");
				if (text) f.data.setString("X", "9", false, async); else f.data.setInt("X", 9, false, async);
				while (!tasks.isEmpty()) tasks.remove(0).run();
				verify(f.users, org.mockito.Mockito.times(1)).onChange(f.user, "X");
				org.junit.jupiter.api.Assertions.assertEquals(9, f.stored());
			}
		}
	}
	@Test void cachedDirectWriteNotificationRejectsItsOwnRetirementAndAllowsLaterRetry() throws Exception {
		for (boolean text : new boolean[] { false, true }) for (boolean async : new boolean[] { false, true }) {
			try (var f = new ReviewSqliteFixture()) {
				var tasks = new java.util.ArrayList<Runnable>();
				var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
				when(f.plugin.getTimer()).thenReturn(timer);
				org.mockito.Mockito.doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
				org.mockito.Mockito.doAnswer(call -> {
					var failure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
							() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS));
					org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());
					org.junit.jupiter.api.Assertions.assertEquals(1, f.stored(), "notification remains before SQL");
					return null;
				}).when(f.users).onChange(f.user, "X");
				if (text) f.data.setString("X", "9", false, async); else f.data.setInt("X", 9, false, async);
				while (!tasks.isEmpty()) tasks.remove(0).run();
				org.junit.jupiter.api.Assertions.assertEquals(9, f.stored());
				verify(f.users, org.mockito.Mockito.times(1)).onChange(f.user, "X");
				f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
			}
		}
	}
	@Test void cachedQueuedAsyncNotificationCanAwaitRetirement() throws Exception {
		for (boolean text : new boolean[] { false, true }) try (var f = new ReviewSqliteFixture()) {
			var first = new java.util.concurrent.atomic.AtomicBoolean(true);
			org.mockito.Mockito.doAnswer(call -> {
				if (first.compareAndSet(true, false)) f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS);
				return null;
			}).when(f.users).onChange(f.user, "X");
			if (text) f.data.setString("X", "9", true, true); else f.data.setInt("X", 9, true, true);
			org.junit.jupiter.api.Assertions.assertEquals(9, f.stored());
		}
	}
	@Test void nestedOtherManagerCallbackCannotHideLiveRetirementAdmission() throws Exception {
		try (var outer = new ReviewSqliteFixture(); var inner = new ReviewSqliteFixture()) {
			var outerWrite = outer.manager.beginLegacyDirectWrite(outer.uuid, com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X"));
			var innerWrite = inner.manager.beginLegacyDirectWrite(inner.uuid, com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X"));
			try {
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectCallback(outer.manager, outerWrite, () ->
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectCallback(inner.manager, innerWrite, () -> {
						var failure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
								() -> outer.manager.clearCacheAsyncCompletion().toCompletableFuture().join());
						org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());
					}));
			} finally { inner.manager.endLegacyDirectWrite(innerWrite); outer.manager.endLegacyDirectWrite(outerWrite); }
			outer.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void reentrantRejectionDoesNotCancelAnExistingRetirement() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var admission = f.manager.beginLegacyDirectWrite(f.uuid, com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey("X"));
			var observer = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
			var cancelled = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
			org.junit.jupiter.api.Assertions.assertTrue(cancelled.cancel(false));
			try {
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectCallback(f.manager, admission, () -> {
					var error = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
							() -> f.manager.clearCacheAsyncCompletion().toCompletableFuture().join());
					org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalStateException.class, error.getCause());
				});
				org.junit.jupiter.api.Assertions.assertFalse(observer.isDone());
			} finally { f.manager.endLegacyDirectWrite(admission); }
			observer.get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void uncaughtReentrantRejectionReleasesAdmissionAndRetainsFailedWrite() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			org.mockito.Mockito.doAnswer(call -> {
				f.manager.clearCacheAsyncCompletion().toCompletableFuture().join();
				return null;
			}).when(f.users).onChange(f.user, "X");
			var failure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
					() -> f.data.setInt("X", 9, false));
			org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());
			org.junit.jupiter.api.Assertions.assertEquals(1, f.stored());
			org.mockito.Mockito.doNothing().when(f.users).onChange(f.user, "X");
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(9, f.stored());
			f.manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
		}
	}
	@Test void deferredLegacyRefreshDoesNotReviveReplacedGeneration() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			f.data.setValues(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(10))));
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			f.manager.getTimer().execute(() -> { entered.countDown(); try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
			try {
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
				org.junit.jupiter.api.Assertions.assertEquals(10, f.data.getInt("X", 0, com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_WAIT));
				var replacement = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(f.manager, f.uuid);
				replacement.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new DataValueInt(77))));
				f.manager.getUserDataCache().put(f.uuid, replacement);
				release.countDown(); f.manager.getTimer().submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
				org.junit.jupiter.api.Assertions.assertSame(replacement, f.manager.getUserDataCache().get(f.uuid));
				org.junit.jupiter.api.Assertions.assertEquals(77, replacement.getIfPresent("X").getInt());
				verify(f.plugin.getSQLiteUserTable(), org.mockito.Mockito.never()).getExactStrict(any(Column.class));
			} finally { release.countDown(); }
		}
	}
	@Test void sqliteBulkAliasesRespectMissingRowAssignmentOrder() throws Exception { bulkAliases(true, false); }
	@Test void sqliteBulkAliasesRespectInsertConflictAssignmentOrder() throws Exception { bulkAliases(false, true); }
	private void bulkAliases(boolean missing, boolean conflict) throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			if (missing) try (var sql = f.db.createStatement()) { sql.executeUpdate("DELETE FROM Users"); }
			if (conflict) { var table = f.plugin.getSQLiteUserTable(); doReturn(false).doCallRealMethod().when(table).containsKey(anyString()); }
			f.cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 2), true);
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			values.put("X", new DataValueInt(10)); values.put("x", new DataValueInt(20));
			f.data.setValues(values);
			int written = f.stored();
			org.junit.jupiter.api.Assertions.assertEquals(missing || conflict ? 10 : 20, written);
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.populateCompleteLegacyCache(f.uuid));
			org.junit.jupiter.api.Assertions.assertTrue(f.manager.tryMutateLegacyIntegers(f.uuid, java.util.Map.of("X", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
			f.cache.processChanges();
			org.junit.jupiter.api.Assertions.assertEquals(written + 1, f.stored());
		}
	}
	@Test void guardedSqliteBulkInsertSupportsThousandColumns() throws Exception {
		try (var f = new ReviewSqliteFixture()) {
			var ddl = new StringBuilder("CREATE TABLE Users(uuid TEXT PRIMARY KEY");
			var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
			for (int i = 0; i < 1000; i++) { ddl.append(", c").append(i).append(" INTEGER"); values.put("c" + i, new DataValueInt(i)); }
			try (var sql = f.db.createStatement()) { sql.executeUpdate("DROP TABLE Users"); sql.executeUpdate(ddl.append(")").toString()); }
			f.data.setValues(values);
			try (var sql = f.db.createStatement(); var row = sql.executeQuery("SELECT c0,c999 FROM Users")) {
				org.junit.jupiter.api.Assertions.assertTrue(row.next());
				org.junit.jupiter.api.Assertions.assertEquals(0, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(999, row.getInt(2));
			}
		}
	}
	@Test void sqliteBulkSupersedesOlderQueuedCaseVariant() throws Exception {
		String id = java.util.UUID.randomUUID().toString(); var uuid = java.util.UUID.fromString(id);
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var sql = real.createStatement()) { sql.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); sql.executeUpdate("INSERT INTO Users VALUES ('" + id + "', 1)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class));
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
			var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("activations", new DataValueInt(1))));
			manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			try {
				var add = com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD;
				org.junit.jupiter.api.Assertions.assertTrue(manager.tryMutateLegacyIntegers(uuid, java.util.Map.of("activations", 1), add, null).applied());
				data.setValues(new java.util.HashMap<>(java.util.Map.of("Activations", new DataValueInt(10))));
				org.junit.jupiter.api.Assertions.assertTrue(manager.populateCompleteLegacyCache(uuid));
				org.junit.jupiter.api.Assertions.assertTrue(manager.tryMutateLegacyIntegers(uuid, java.util.Map.of("activations", 1), add, null).applied());
				cache.processChanges();
				try (var sql = real.createStatement(); var row = sql.executeQuery("SELECT Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(11, row.getInt(1)); }
			} finally { manager.getTimer().shutdownNow(); }
		}
	}
	@Test void olderInsertCannotOverwriteNewerSqlCommitBeforeItsCallerReturns() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var sql = real.createStatement()) { sql.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			var olderReady = new java.util.concurrent.CountDownLatch(1); var allowOlder = new java.util.concurrent.CountDownLatch(1);
			var newerCommitted = new java.util.concurrent.CountDownLatch(1); var allowNewerReturn = new java.util.concurrent.CountDownLatch(1);
			var wrapped = mock(Connection.class); when(wrapped.unwrap(org.sqlite.SQLiteConnection.class)).thenReturn((org.sqlite.SQLiteConnection) real);
			var count = new java.util.concurrent.atomic.AtomicInteger();
			when(wrapped.prepareStatement(anyString())).thenAnswer(call -> { String sql = call.getArgument(0); var prepared = real.prepareStatement(sql); if (!sql.startsWith("INSERT")) return prepared; var delayed = spy(prepared);
				org.mockito.Mockito.doAnswer(invocation -> { int ordinal = count.getAndIncrement(); if (ordinal == 0) { olderReady.countDown(); if (!allowOlder.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return invocation.callRealMethod(); }
					Object result = invocation.callRealMethod(); newerCommitted.countDown(); if (!allowNewerReturn.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return result; }).when(delayed).executeUpdate(); return delayed; });
			when(sqlite.getSQLConnection()).thenReturn(wrapped); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var older = pool.submit(() -> data.setInt("Activations", 2, false)); org.junit.jupiter.api.Assertions.assertTrue(olderReady.await(5, java.util.concurrent.TimeUnit.SECONDS));
				var newer = pool.submit(() -> data.setInt("Activations", 10, false)); org.junit.jupiter.api.Assertions.assertTrue(newerCommitted.await(5, java.util.concurrent.TimeUnit.SECONDS));
				allowOlder.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS); allowNewerReturn.countDown(); newer.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var sql = real.createStatement(); var row = sql.executeQuery("SELECT Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			} finally { allowOlder.countDown(); allowNewerReturn.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void failedDirectSqliteScalarCanRetryAndRetire() throws Exception {
		String id = java.util.UUID.randomUUID().toString(); var uuid = java.util.UUID.fromString(id);
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var sql = real.createStatement()) {
				sql.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)");
				sql.executeUpdate("INSERT INTO Users VALUES ('" + id + "', 1)");
				sql.executeUpdate("CREATE TRIGGER reject_write BEFORE UPDATE ON Users BEGIN SELECT RAISE(FAIL, 'reject'); END");
			}
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class));
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(uuid, false)).thenReturn(user);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
			var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, uuid); cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new DataValueInt(1))));
			manager.getUserDataCache().put(uuid, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			try {
				data.setInt("Activations", 10, false);
				org.junit.jupiter.api.Assertions.assertTrue(cache.hasChangesToProcess());
				try (var sql = real.createStatement()) { sql.executeUpdate("DROP TRIGGER reject_write"); }
				cache.processChanges();
				org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
				try (var sql = real.createStatement(); var row = sql.executeQuery("SELECT Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			} finally { manager.getTimer().shutdownNow(); }
		}
	}
	@Test void sqlitePrimaryLookalikeDoesNotReplaceNewerColumn() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var sql = real.createStatement()) { sql.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, \"uuİd\" INTEGER, Activations INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var wrapped = mock(Connection.class); when(wrapped.unwrap(org.sqlite.SQLiteConnection.class)).thenReturn((org.sqlite.SQLiteConnection) real);
			var count = new java.util.concurrent.atomic.AtomicInteger();
			when(wrapped.prepareStatement(anyString())).thenAnswer(call -> { String sql = call.getArgument(0); var prepared = real.prepareStatement(sql); if (!sql.startsWith("INSERT")) return prepared; var delayed = spy(prepared);
				org.mockito.Mockito.doAnswer(invocation -> { if (count.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return invocation.callRealMethod(); }).when(delayed).executeUpdate(); return delayed; });
			when(sqlite.getSQLConnection()).thenReturn(wrapped); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var older = pool.submit(() -> data.setInt("uuİd", 2, false)); org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				pool.submit(() -> data.setInt("Activations", 10, false)).get(1, java.util.concurrent.TimeUnit.SECONDS);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var sql = real.createStatement(); var row = sql.executeQuery("SELECT \"uuİd\", Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(2, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(2)); }
			} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void failedLegacySqliteBatchRetainsItsQueuedCredit() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		var primary = new Column("uuid", new DataValueString(id));
		var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
		var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
		var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); table.setSqLite(sqlite);
		when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
		when(users.getUser(java.util.UUID.fromString(id), false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(table);
		var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
		var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, java.util.UUID.fromString(id));
		cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
		manager.getUserDataCache().put(java.util.UUID.fromString(id), cache);
		doNothing().when(table).checkColumn(any(Column.class)); doReturn(true).when(table).containsKey(anyString());
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeUpdate()).thenThrow(new java.sql.SQLException("synthetic write failure"));
		try {
			org.junit.jupiter.api.Assertions.assertTrue(manager.tryMutateLegacyIntegers(java.util.UUID.fromString(id), java.util.Map.of("Activations", 1),
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, cache::processChanges);
			org.junit.jupiter.api.Assertions.assertTrue(cache.hasChangesToProcess());
			org.junit.jupiter.api.Assertions.assertEquals(2, cache.getIfPresent("Activations").getInt());
			org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> table.update(primary,
					new java.util.ArrayList<>(java.util.List.of(new Column("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(4))))));
		} finally { manager.getTimer().shutdownNow(); }
	}
	@Test void olderLegacySqliteInsertCannotReplaceNewerDirectInsert() throws Exception { legacyInsertRace(false); }
	@Test void sqliteInsertConflictAfterAdmissionDoesNotReplaceNewerDirectInsert() throws Exception { legacyInsertRace(true); }
	private void legacyInsertRace(boolean pauseBeforeSql) throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id));
			var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
			var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
			var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary));
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var realSqlite = mock(SQLite.class);
			if (pauseBeforeSql) {
				var wrapped = mock(java.sql.Connection.class);
				when(wrapped.prepareStatement(anyString())).thenAnswer(call -> {
					String sql = call.getArgument(0); var prepared = real.prepareStatement(sql);
					if (!sql.startsWith("INSERT OR IGNORE")) return prepared;
					var delayed = spy(prepared);
					org.mockito.Mockito.doAnswer(invocation -> { entered.countDown();
						if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
						return invocation.callRealMethod(); }).when(delayed).executeUpdate();
					return delayed;
				});
				when(realSqlite.getSQLConnection()).thenReturn(wrapped);
			} else when(realSqlite.getSQLConnection()).thenReturn(real);
			table.setSqLite(realSqlite);
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(users.getUser(java.util.UUID.fromString(id), false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
			when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
			var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, java.util.UUID.fromString(id));
			cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
			manager.getUserDataCache().put(java.util.UUID.fromString(id), cache);
			when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			doNothing().when(table).checkColumn(any(Column.class));
			var existsCalls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> existsCalls.getAndIncrement() >= 2).when(table).containsKey(anyString());
			var insertCalls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> { if (!pauseBeforeSql && insertCalls.getAndIncrement() == 0) { entered.countDown();
				if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
				return call.callRealMethod(); }).when(table).insert(any());
			var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				org.junit.jupiter.api.Assertions.assertTrue(manager.tryMutateLegacyIntegers(java.util.UUID.fromString(id), java.util.Map.of("Activations", 1),
						com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
				var older = worker.submit(cache::processChanges);
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				data.setInt("Activations", 10, false);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT Activations FROM Users")) {
					org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1));
				}
			} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void sqliteInsertRetainsLegacyEmptyStringForNullColumn() throws Exception {
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Label TEXT)"); }
			var primary = new Column("uuid", new DataValueString("id")); var plugin = mock(AdvancedCorePlugin.class); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); doNothing().when(table).checkColumn(any(Column.class));
			table.insert(new java.util.ArrayList<>(java.util.List.of(primary, new Column("Label", com.bencodez.simpleapi.sql.DataType.STRING))));
			try (var query = real.createStatement(); var row = query.executeQuery("SELECT Label FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals("", row.getString(1)); }
		}
	}
	@Test void olderDirectSqliteInsertCannotReplaceNewerCompletedDirectInsert() throws Exception { directInsertRace(false, false, false, false); }
	@Test void directInsertConflictAfterGuardCannotReplaceNewerCompletedInsert() throws Exception { directInsertRace(true, false, false, false); }
	@Test void failedNewerDirectSqliteInsertDoesNotSuppressOlderSuccessfulWrite() throws Exception { directInsertRace(false, true, false, false); }
	@Test void directSqliteRetirementWaitsWithoutBlockingAndSurvivesObserverCancellation() throws Exception { directInsertRace(true, false, true, false); }
	@Test void directSqliteColumnCaseVariantsShareTheSameOrdering() throws Exception { directInsertRace(true, false, false, true); }
	private void directInsertRace(boolean pauseBeforeSql, boolean failNewer, boolean retire, boolean caseVariant) throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER" + (failNewer ? " CHECK (Activations <> 10)" : "") + ")"); }
			var primary = new Column("uuid", new DataValueString(id));
			var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
			var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
			var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary));
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var realSqlite = mock(SQLite.class);
			if (pauseBeforeSql) {
				var wrapped = mock(Connection.class); when(wrapped.unwrap(org.sqlite.SQLiteConnection.class)).thenReturn((org.sqlite.SQLiteConnection) real); var executions = new java.util.concurrent.atomic.AtomicInteger();
				when(wrapped.prepareStatement(anyString())).thenAnswer(call -> {
					String sql = call.getArgument(0); var prepared = real.prepareStatement(sql);
					if (!sql.startsWith("INSERT")) return prepared;
					var delayed = spy(prepared);
					org.mockito.Mockito.doAnswer(invocation -> { if (executions.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return invocation.callRealMethod(); }).when(delayed).executeUpdate();
					return delayed;
				});
				when(realSqlite.getSQLConnection()).thenReturn(wrapped);
			} else when(realSqlite.getSQLConnection()).thenReturn(real);
			table.setSqLite(realSqlite);
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id);
			when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			var calls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> { if (!pauseBeforeSql && calls.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return call.callRealMethod(); }).when(table).insert(any());
			var data = new com.bencodez.advancedcore.api.user.UserData(user); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var older = pool.submit(() -> data.setInt("Activations", 2, false));
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				java.util.concurrent.CompletableFuture<Void> retirement = null;
				if (retire) {
					var cancelled = manager.clearCacheAsyncCompletion().toCompletableFuture(); retirement = manager.clearCacheAsyncCompletion().toCompletableFuture();
					org.junit.jupiter.api.Assertions.assertTrue(cancelled.cancel(false)); org.junit.jupiter.api.Assertions.assertFalse(retirement.isDone());
					org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> data.setInt("Activations", 10, false));
				} else pool.submit(() -> data.setInt(caseVariant ? "activations" : "Activations", 10, false)).get(1, java.util.concurrent.TimeUnit.SECONDS);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				if (retirement != null) retirement.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT Activations FROM Users")) {
					org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(failNewer || retire ? 2 : 10, row.getInt(1), "a failed newer insert must not suppress the older write");
				}
				if (failNewer) {
					org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
					data.setInt("Activations", 3, false);
					org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
				}
			} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void olderDirectSqliteExistingRowCannotOverwriteNewerCompletedWrite() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); create.executeUpdate("INSERT INTO Users VALUES ('" + id + "', 1)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
			var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			doNothing().when(table).checkColumn(any(Column.class)); var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1); var calls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> { if (calls.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return true; }).when(table).containsKey(anyString());
			var data = new com.bencodez.advancedcore.api.user.UserData(user); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var older = pool.submit(() -> data.setInt("Activations", 2, false)); org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				pool.submit(() -> data.setInt("Activations", 10, false)).get(1, java.util.concurrent.TimeUnit.SECONDS);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void olderDirectSqliteBulkInsertPreservesNewerKeyAndUnrelatedKey() throws Exception { directBulkInsertRace(false, false); }
	@Test void olderDirectSqliteBulkConflictAfterGuardPreservesBothKeys() throws Exception { directBulkInsertRace(true, false); }
	@Test void sqliteNonAsciiColumnNamesRemainDistinct() throws Exception { directBulkInsertRace(true, true); }
	private void directBulkInsertRace(boolean pauseBeforeSql, boolean nonAscii) throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate(nonAscii ? "CREATE TABLE Users (uuid TEXT PRIMARY KEY, Ä INTEGER, ä INTEGER, Other INTEGER)" : "CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER, Other INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class);
			var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
			var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			if (pauseBeforeSql) {
				var wrapped = mock(Connection.class); when(wrapped.unwrap(org.sqlite.SQLiteConnection.class)).thenReturn((org.sqlite.SQLiteConnection) real); var executions = new java.util.concurrent.atomic.AtomicInteger();
				when(wrapped.prepareStatement(anyString())).thenAnswer(call -> { String sql = call.getArgument(0); var prepared = real.prepareStatement(sql); if (!sql.startsWith("INSERT")) return prepared; var delayed = spy(prepared);
					org.mockito.Mockito.doAnswer(invocation -> { if (executions.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return invocation.callRealMethod(); }).when(delayed).executeUpdate(); return delayed; });
				when(sqlite.getSQLConnection()).thenReturn(wrapped);
			} else when(sqlite.getSQLConnection()).thenReturn(real);
			table.setSqLite(sqlite); when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table);
			doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			var calls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> { if (!pauseBeforeSql && calls.getAndIncrement() == 0) { entered.countDown(); if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return call.callRealMethod(); }).when(table).insert(any());
			var data = new com.bencodez.advancedcore.api.user.UserData(user); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
			try {
				var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put(nonAscii ? "Ä" : "Activations", new DataValueInt(2)); values.put("Other", new DataValueInt(3));
				var older = pool.submit(() -> data.setValues(com.bencodez.advancedcore.api.user.UserStorage.SQLITE, values)); org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				pool.submit(() -> data.setInt(nonAscii ? "ä" : "Activations", 10, false)).get(1, java.util.concurrent.TimeUnit.SECONDS);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery(nonAscii ? "SELECT Ä, ä, Other FROM Users" : "SELECT Activations, Other FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(nonAscii ? 2 : 10, row.getInt(1)); if (nonAscii) { org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(2)); org.junit.jupiter.api.Assertions.assertEquals(3, row.getInt(3)); } else org.junit.jupiter.api.Assertions.assertEquals(3, row.getInt(2)); }
			} finally { release.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void directSqliteGuardRegistersOnReplacementConnection() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var first = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:"); var second = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			for (var connection : java.util.List.of(first, second)) try (var create = connection.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(first); table.setSqLite(sqlite); when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id);
			when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table); doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			var data = new com.bencodez.advancedcore.api.user.UserData(user);
			try {
				data.setInt("Activations", 2, false); when(sqlite.getSQLConnection()).thenReturn(second); data.setInt("Activations", 10, false);
				try (var query = second.createStatement(); var row = query.executeQuery("SELECT Activations FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); }
			} finally { manager.getTimer().shutdownNow(); }
		}
	}
	@Test void directSqliteNotificationCanReenterWithAnotherWrite() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER, Other INTEGER)"); }
			var primary = new Column("uuid", new DataValueString(id)); var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(id);
			when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table); doNothing().when(table).checkColumn(any(Column.class));
			var data = new com.bencodez.advancedcore.api.user.UserData(user);
			org.mockito.Mockito.doAnswer(call -> { data.setInt("Other", 5, false); return null; }).when(users).onChange(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq("Activations"));
			try {
				data.setInt("Activations", 10, false);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT Activations, Other FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1)); org.junit.jupiter.api.Assertions.assertEquals(5, row.getInt(2)); }
			} finally { manager.getTimer().shutdownNow(); }
		}
	}
	@Test void concurrentFirstDirectWritesCanInitializeSqliteGuard() throws Exception {
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) { create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)"); }
			var primary = new Column("uuid", new DataValueString("placeholder")); var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin); var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary)); var sqlite = mock(SQLite.class);
			when(sqlite.getSQLConnection()).thenReturn(real); table.setSqLite(sqlite); when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE); when(plugin.getSQLiteUserTable()).thenReturn(table); doNothing().when(table).checkColumn(any(Column.class)); doReturn(false).when(table).containsKey(anyString());
			int count = 12; var ready = new java.util.concurrent.CountDownLatch(count); var start = new java.util.concurrent.CountDownLatch(1); var pool = java.util.concurrent.Executors.newFixedThreadPool(count); var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			try {
				for (int i=0; i<count; i++) { var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class); when(user.getPlugin()).thenReturn(plugin); when(user.getUUID()).thenReturn(java.util.UUID.randomUUID().toString()); var data = new com.bencodez.advancedcore.api.user.UserData(user);
					tasks.add(pool.submit(() -> { ready.countDown(); try { if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } data.setInt("Activations", 1, false); })); }
				org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
				for (var task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT COUNT(*) FROM Users")) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); org.junit.jupiter.api.Assertions.assertEquals(count, row.getInt(1)); }
				org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().join());
			} finally { start.countDown(); pool.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test void olderLegacySqliteUpdateCannotOverwriteNewerDirectUpdate() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		try (var real = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var create = real.createStatement()) {
				create.executeUpdate("CREATE TABLE Users (uuid TEXT PRIMARY KEY, Activations INTEGER)");
				create.executeUpdate("INSERT INTO Users (uuid, Activations) VALUES ('" + id + "', 1)");
			}
			var primary = new Column("uuid", new DataValueString(id));
			var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
			var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
			var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(plugin);
			var table = spy(new UserTable(plugin, "Users", Collections.singletonList(primary), primary));
			var realSqlite = mock(SQLite.class); when(realSqlite.getSQLConnection()).thenReturn(real); table.setSqLite(realSqlite);
			when(plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
			when(users.getUser(java.util.UUID.fromString(id), false)).thenReturn(user); when(user.getPlugin()).thenReturn(plugin);
			when(user.getUUID()).thenReturn(id); when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
			when(plugin.getSQLiteUserTable()).thenReturn(table);
			var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
			var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, java.util.UUID.fromString(id));
			cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
			manager.getUserDataCache().put(java.util.UUID.fromString(id), cache);
			when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
			doNothing().when(table).checkColumn(any(Column.class));
			var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
			var calls = new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(call -> { if (calls.getAndIncrement() == 0) { entered.countDown();
				if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
				return true; }).when(table).containsKey(anyString());
			var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				org.junit.jupiter.api.Assertions.assertTrue(manager.tryMutateLegacyIntegers(java.util.UUID.fromString(id), java.util.Map.of("Activations", 1),
						com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
				var older = worker.submit(cache::processChanges);
				org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				data.setInt("Activations", 10, false);
				release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
				try (var query = real.createStatement(); var row = query.executeQuery("SELECT Activations FROM Users")) {
					org.junit.jupiter.api.Assertions.assertTrue(row.next());
					org.junit.jupiter.api.Assertions.assertEquals(10, row.getInt(1));
				}
			} finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
		}
	}
	@Test
	void strictReadRetainsFailureThroughDelegatingOverride() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users", Collections.singletonList(primaryKey), primaryKey) {
			@Override public java.util.ArrayList<Column> getExact(Column column) { return super.getExact(column); }
		};
		table.setSqLite(sqlite);
		when(connection.prepareStatement("SELECT * FROM Users WHERE `uuid`=?")).thenThrow(new java.sql.SQLException("synthetic read failure"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> table.getExactStrict(primaryKey));
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> table.getExact(primaryKey));
	}

	private static final String UUID = "00000000-0000-0000-0000-000000000001";

	private Connection connection;
	private PreparedStatement statement;
	private SQLite sqlite;

	@BeforeEach
	void setUp() throws Exception {
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		sqlite = mock(SQLite.class);
		when(sqlite.getSQLConnection()).thenReturn(connection);
	}

	@Test
	void strictExactReadPropagatesDatabaseFailure() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users", Collections.singletonList(primaryKey), primaryKey);
		table.setSqLite(sqlite);
		when(connection.prepareStatement("SELECT * FROM Users WHERE `uuid`=?"))
				.thenThrow(new java.sql.SQLException("synthetic read failure"));
		var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> table.getExactStrict(primaryKey));
		org.junit.jupiter.api.Assertions.assertInstanceOf(java.sql.SQLException.class, failure.getCause());
	}

	@Test
	void strictExactReadClosesStatementWhenExecutionFails() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users", Collections.singletonList(primaryKey), primaryKey);
		table.setSqLite(sqlite);
		when(connection.prepareStatement("SELECT * FROM Users WHERE `uuid`=?")).thenReturn(statement);
		when(statement.executeQuery()).thenThrow(new java.sql.SQLException("synthetic read failure"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> table.getExactStrict(primaryKey));
		verify(statement).close();
	}

	@Test
	void strictExactReadClosesResultAndStatementWhenIterationFails() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users", Collections.singletonList(primaryKey), primaryKey);
		table.setSqLite(sqlite); ResultSet row = mock(ResultSet.class);
		when(connection.prepareStatement("SELECT * FROM Users WHERE `uuid`=?")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(row);
		when(row.next()).thenThrow(new java.sql.SQLException("synthetic iteration failure"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> table.getExactStrict(primaryKey));
		verify(row).close(); verify(statement).close();
	}

	@Test
	void updateBindsValuesInsteadOfEmbeddingThemInSql() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = spy(new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey));
		table.setSqLite(sqlite);
		doNothing().when(table).checkColumn(any(Column.class));
		doReturn(true).when(table).containsKey(anyString());

		String sql = "UPDATE Users SET `LastVotes`=?, `Points`=? WHERE `uuid`=?";
		when(connection.prepareStatement(sql)).thenReturn(statement);

		String lastVotes = "Site's value with punctuation;//123";
		table.update(primaryKey, Arrays.asList(new Column("LastVotes", new DataValueString(lastVotes)),
				new Column("Points", new DataValueInt(25))));

		verify(connection).prepareStatement(sql);
		verify(statement).setString(1, lastVotes);
		verify(statement).setInt(2, 25);
		verify(statement).setString(3, UUID);
		verify(statement).executeUpdate();
	}

	@Test
	void getUuidBindsPlayerName() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey);
		table.setSqLite(sqlite);

		ResultSet resultSet = mock(ResultSet.class);
		String sql = "SELECT uuid FROM Users WHERE PlayerName=?;";
		when(connection.prepareStatement(sql)).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(false);

		table.getUUID("O'Brien");

		verify(statement).setString(1, "O'Brien");
		verify(statement).executeQuery();
	}

	@Test
	void addColumnEscapesAttackerControlledIdentifier() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = spy(new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey));
		table.setSqLite(sqlite);
		doReturn(false).when(table).hasColumn(any(Column.class));

		String sql = "ALTER TABLE `Users` ADD COLUMN `name``; DROP TABLE Users;--` STRING";
		when(connection.prepareStatement(sql)).thenReturn(statement);
		table.addColoumn(new Column("name`; DROP TABLE Users;--", new DataValueString("value")));

		verify(connection).prepareStatement(sql);
		verify(statement).executeUpdate();
	}

	@Test
	void wipeColumnEscapesIdentifier() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = spy(new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey));
		table.setSqLite(sqlite);
		doNothing().when(table).checkColumn(any(Column.class));
		String sql = "UPDATE `Users` SET `points``; DROP TABLE Users;--` = 0;";
		when(connection.prepareStatement(sql)).thenReturn(statement);

		table.wipeColumnData("points`; DROP TABLE Users;--", com.bencodez.simpleapi.sql.DataType.INTEGER);
		verify(connection).prepareStatement(sql);
		verify(statement).executeUpdate();
	}
}
