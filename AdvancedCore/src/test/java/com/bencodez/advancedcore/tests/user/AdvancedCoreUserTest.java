package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.PersistedQueueReference;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;

public class AdvancedCoreUserTest {
	private AdvancedCorePlugin plugin;
	private UserManager userManager;
	private UserDataManager dataManager;
	private UserData data;
	private RewardHandler rewardHandler;
	private ScheduledExecutorService delayedTimer;
	private AdvancedCoreUser user;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		userManager = mock(UserManager.class);
		dataManager = mock(UserDataManager.class);
		data = mock(UserData.class);
		rewardHandler = mock(RewardHandler.class);
		delayedTimer = mock(ScheduledExecutorService.class);
		MySQL mysql = mock(MySQL.class);

		AdvancedCoreConfigOptions configOptions = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(configOptions);
		when(configOptions.isOnlineMode()).thenReturn(true); // Stub the required method
		when(plugin.getMysql()).thenReturn(mysql);
		when(userManager.getDataManager()).thenReturn(dataManager);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(plugin.getRewardHandler()).thenReturn(rewardHandler);
		when(rewardHandler.getDelayedTimer()).thenReturn(delayedTimer);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		when(userManager.getOfflineRewardsPath()).thenReturn("offlineRewardsPath");
		when(configOptions.isProcessRewards()).thenReturn(true);

		user = new AdvancedCoreUser(plugin, UUID.randomUUID(),"Test");
		user.setData(data); // Inject the mocked UserData object
	}

	@Test
	void unclaimedChoiceOccurrenceIsIdempotentButDistinctOccurrencesRemainClaimable() {
		AtomicReference<ArrayList<String>> stored = new AtomicReference<>(new ArrayList<>());
		when(data.getStringList("UnClaimedChoices", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(stored.get()));
		org.mockito.Mockito.doAnswer(invocation -> {
			stored.set(new ArrayList<>(invocation.getArgument(1)));
			return null;
		}).when(data).setStringList(eq("UnClaimedChoices"), any());

		user.addUnClaimedChoiceReward("ChoiceReward", "occurrence-one");
		user.addUnClaimedChoiceReward("ChoiceReward", "occurrence-one");
		user.addUnClaimedChoiceReward("ChoiceReward", "occurrence-two");

		assertEquals(List.of("ChoiceReward", "ChoiceReward"), user.getUnClaimedChoices());
		assertEquals(2, stored.get().size());
		assertTrue(stored.get().stream().noneMatch("ChoiceReward"::equals));
	}

	@Test
	void claimingOneOccurrencePreservesAnotherIdenticalChoiceReward() {
		AtomicReference<ArrayList<String>> stored = new AtomicReference<>(new ArrayList<>());
		when(data.getStringList("UnClaimedChoices", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(stored.get()));
		org.mockito.Mockito.doAnswer(invocation -> {
			stored.set(new ArrayList<>(invocation.getArgument(1)));
			return null;
		}).when(data).setStringList(eq("UnClaimedChoices"), any());
		user.addUnClaimedChoiceReward("ChoiceReward", "occurrence-one");
		user.addUnClaimedChoiceReward("ChoiceReward", "occurrence-two");

		user.removeUnClaimedChoiceReward("ChoiceReward");

		assertEquals(List.of("ChoiceReward"), user.getUnClaimedChoices());
		assertEquals(1, stored.get().size());
	}

	@Test
	void legacyChoiceAdditionPreservesModernOccurrenceIdentity() {
		AtomicReference<ArrayList<String>> stored = new AtomicReference<>(new ArrayList<>());
		when(data.getStringList("UnClaimedChoices", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(stored.get()));
		org.mockito.Mockito.doAnswer(invocation -> {
			stored.set(new ArrayList<>(invocation.getArgument(1)));
			return null;
		}).when(data).setStringList(eq("UnClaimedChoices"), any());

		user.addUnClaimedChoiceReward("Modern", "occurrence-one");
		String encodedModern = stored.get().get(0);
		user.addUnClaimedChoiceReward("Legacy");
		user.addUnClaimedChoiceReward("Modern", "occurrence-one");

		assertEquals(List.of("Modern", "Legacy"), user.getUnClaimedChoices());
		assertEquals(encodedModern, stored.get().get(0));
		assertEquals(2, stored.get().size());
	}

	@Test
	void checkOfflineRewards_preservesServerRequirementForNormalReplay() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("VoteReward%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);

		ArgumentCaptor<PersistedQueueReference> referenceCaptor = ArgumentCaptor.forClass(PersistedQueueReference.class);
		ArgumentCaptor<RewardOptions> optionsCaptor = ArgumentCaptor.forClass(RewardOptions.class);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));
		user.checkOfflineRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), referenceCaptor.capture(), optionsCaptor.capture());
		assertEquals("VoteReward", referenceCaptor.getValue().getReference());
		RewardOptions options = optionsCaptor.getValue();
		assertFalse(options.isForceOffline());
		assertTrue(options.isGiveOffline());
		assertFalse(options.isCheckTimed());
		assertEquals("server-a", options.getPlaceholders().get("Server"));
	}

	@Test
	void forceRunOfflineRewards_stillBypassesNormalReplayChecks() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("VoteReward%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);

		ArgumentCaptor<PersistedQueueReference> referenceCaptor = ArgumentCaptor.forClass(PersistedQueueReference.class);
		ArgumentCaptor<RewardOptions> optionsCaptor = ArgumentCaptor.forClass(RewardOptions.class);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));
		user.forceRunOfflineRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), referenceCaptor.capture(), optionsCaptor.capture());
		assertEquals("VoteReward", referenceCaptor.getValue().getReference());
		RewardOptions options = optionsCaptor.getValue();
		assertTrue(options.isForceOffline());
		assertFalse(options.isGiveOffline());
		assertFalse(options.isCheckTimed());
		assertEquals("server-a", options.getPlaceholders().get("Server"));
	}

	@Test
	void queuedAsyncReplayResumesAtItsStoredCheckpoint() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("VoteReward%asyncprogress%2%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));

		ArgumentCaptor<PersistedQueueReference> reference = ArgumentCaptor.forClass(PersistedQueueReference.class);
		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkOfflineRewards();

		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), reference.capture(), options.capture());
		assertEquals("VoteReward", reference.getValue().getReference());
		assertEquals(2, options.getValue().getCompletedAsyncInjections());
	}

	@Test
	void queuedOccurrenceIsForwardedBeforeAnyAsyncInjectorRuns() {
		String occurrence = UUID.randomUUID().toString();
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("VoteReward%asyncoccurrence%" + occurrence + "%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));

		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkOfflineRewards();

		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), options.capture());
		assertEquals(occurrence, options.getValue().getAsyncReplayOccurrenceId());
	}

	@Test
	void deferredReplayRetainsOccurrenceAndCheckpoint() {
		ArrayList<String> persisted = new ArrayList<>();
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(persisted));
		org.mockito.Mockito.doAnswer(invocation -> {
			persisted.clear();
			persisted.addAll(invocation.getArgument(1));
			return null;
		}).when(data).setStringList(eq("offlineRewardsPath"), any());
		Reward reward = mock(Reward.class);
		when(reward.getRewardName()).thenReturn("VoteReward");
		RewardOptions options = new RewardOptions().addPlaceholder("Server", "server-a");
		String occurrence = UUID.randomUUID().toString();
		options.setAsyncReplayOccurrenceId(occurrence);
		options.setAsyncReplayProgress(Map.of("root/Grant:0", 1));
		options.setAsyncReplayRegistryFingerprints(Map.of("root/Grant:0", "fingerprint"));

		user.addOfflineRewards(reward, options.getPlaceholders(), options);

		assertEquals(1, persisted.size());
		assertTrue(persisted.get(0).contains("%asyncoccurrence%" + occurrence));
		assertTrue(persisted.get(0).contains("%asyncprogress%v3-"));
		assertTrue(persisted.get(0).contains("Server%pair%server-a"));
	}

	@Test
	void replayClaimCleanupRechecksSharedStateUnderItsLock() throws Exception {
		java.lang.reflect.Method replayClaims = AdvancedCoreUser.class.getDeclaredMethod("replayClaims");
		replayClaims.setAccessible(true);
		Object claims = replayClaims.invoke(user);
		java.lang.reflect.Field lockField = AdvancedCoreUser.class.getDeclaredField("REPLAY_CLAIMS_LOCK");
		lockField.setAccessible(true);
		Object lock = lockField.get(null);
		java.lang.reflect.Field offlineField = claims.getClass().getDeclaredField("offline");
		offlineField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, Integer> offline = (Map<String, Integer>) offlineField.get(claims);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread cleanup;
		synchronized (lock) {
			cleanup = new Thread(() -> {
				try {
					java.lang.reflect.Method release = AdvancedCoreUser.class
							.getDeclaredMethod("releaseReplayClaimsIfEmpty", claims.getClass());
					release.setAccessible(true);
					release.invoke(user, claims);
				} catch (Throwable error) {
					failure.set(error);
				}
			}, "replay-claim-cleanup-test");
			cleanup.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (cleanup.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
				Thread.yield();
			}
			assertEquals(Thread.State.BLOCKED, cleanup.getState());
			offline.put("new-claim", 1);
		}
		cleanup.join(TimeUnit.SECONDS.toMillis(5));
		assertFalse(cleanup.isAlive());
		assertTrue(failure.get() == null, () -> "cleanup failed: " + failure.get());
		assertSame(claims, replayClaims.invoke(user));
		offline.clear();
		java.lang.reflect.Method release = AdvancedCoreUser.class
				.getDeclaredMethod("releaseReplayClaimsIfEmpty", claims.getClass());
		release.setAccessible(true);
		release.invoke(user, claims);
	}

	@Test
	void setOfflineRewards_emptyList() {
		ArrayList<String> rewards = new ArrayList<>();
		user.setOfflineRewards(rewards);
		verify(data).setStringList("offlineRewardsPath", rewards);
	}

	@Test
	void setOfflineRewards_exceedsLimit() {
	    ArrayList<String> rewards = new ArrayList<>();
	    for (int i = 0; i < 5000; i++) {
	        rewards.add("reward123456789a123456789a" + i);
	    }
	    int initialSize = rewards.size();
	    user.setOfflineRewards(rewards);

	    // Verify the method call
	    verify(data).setStringList("offlineRewardsPath", rewards);

	    // Ensure the data is within limits
	    String result = String.join("%line%", rewards);
	    int maxLength = 65535;
	    assertTrue(result.getBytes().length <= maxLength, "The resulting string exceeds the maximum length");
	    
	    // Verify that not everything got deleted
	    assertTrue(rewards.size() > 0, "All rewards were deleted");
	    assertTrue(rewards.size() < initialSize, "No rewards were deleted");
	}

	@Test
	void setOfflineRewards_singleLargeReward() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("largeReward");
		user.setOfflineRewards(rewards);
		verify(data).setStringList("offlineRewardsPath", rewards);
	}

	@Test
	void setOfflineRewards_withinLimit() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("reward1");
		rewards.add("reward2");
		user.setOfflineRewards(rewards);
		verify(data).setStringList("offlineRewardsPath", rewards);
	}

	@Test
	void durableCheckpointTrimmingPreservesTheActiveEntry() throws Exception {
		String active = "active-" + "a".repeat(70_000);
		ArrayList<String> rewards = new ArrayList<>(List.of(active, "old-reward"));
		java.lang.reflect.Method write = AdvancedCoreUser.class.getDeclaredMethod(
				"setOfflineRewards", ArrayList.class, boolean.class, String.class);
		write.setAccessible(true);

		write.invoke(user, rewards, false, active);

		assertEquals(List.of(active), rewards);
		verify(data).setStringList("offlineRewardsPath", rewards, false);
	}

	@Test
	void failedAsyncOfflineReplayIsRestoredForRetry() {
		ArrayList<String> initial = new ArrayList<>();
		initial.add("VoteReward%placeholders%Server%pair%server-a");
		initial.add("VoteReward%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(new ArrayList<>(initial));
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")));

		user.checkOfflineRewards();

		ArgumentCaptor<ArrayList<String>> saved = ArgumentCaptor.forClass(ArrayList.class);
		verify(data, org.mockito.Mockito.atLeastOnce()).setStringList(eq("offlineRewardsPath"), saved.capture());
		assertTrue(saved.getAllValues().stream().allMatch(value -> value.size() == initial.size()));
	}

	@Test
	void failedAsyncTimedReplayIsRestoredAfterDueEntriesAreSaved() {
		long due = System.currentTimeMillis() - 1_000;
		String entry = "VoteReward%extime%1%placeholders%Server%pair%server-a";
		ArrayList<String> timed = new ArrayList<>();
		timed.add(entry + "%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT)).thenReturn(new ArrayList<>(timed));
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")));

		user.checkDelayedTimedRewards();

		ArgumentCaptor<ArrayList<String>> saved = ArgumentCaptor.forClass(ArrayList.class);
		verify(data).setStringList(eq("TimedRewards"), saved.capture());
		String restored = saved.getValue().get(0);
		assertTrue(restored.contains("%asyncretry%1%"));
		assertTrue(restored.contains("VoteReward%extime%1"));
		verify(delayedTimer).schedule(any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
				eq(TimeUnit.MILLISECONDS));
	}

	@Test
	void pendingAsyncOfflineReplayRemainsDurableAndIsNotDispatchedTwice() {
		ArrayList<String> rewards = new ArrayList<>();
		rewards.add("VoteReward%placeholders%Server%pair%server-a");
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(pending);

		user.checkOfflineRewards();
		user.checkOfflineRewards();

		verify(rewardHandler, org.mockito.Mockito.times(1)).givePersistedQueueRewardAsync(eq(user),
				any(PersistedQueueReference.class), any(RewardOptions.class));
		verify(data, org.mockito.Mockito.never()).setStringList(eq("offlineRewardsPath"), any());
		pending.complete(null);
		assertTrue(rewards.isEmpty());
		verify(data).setStringList(eq("offlineRewardsPath"), any());
	}

	@Test
	void pendingOfflineReplayClaimIsSharedAcrossWrappersForTheSameUuid() {
		ArrayList<String> rewards = new ArrayList<>(List.of("VoteReward"));
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT)).thenReturn(rewards);
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(any(AdvancedCoreUser.class),
				any(PersistedQueueReference.class), any(RewardOptions.class))).thenReturn(pending);
		AdvancedCoreUser second = new AdvancedCoreUser(plugin, UUID.fromString(user.getUUID()), "Test");
		second.setData(data);

		user.checkOfflineRewards();
		second.checkOfflineRewards();

		verify(rewardHandler, org.mockito.Mockito.times(1)).givePersistedQueueRewardAsync(
				any(AdvancedCoreUser.class), any(PersistedQueueReference.class), any(RewardOptions.class));
	}

	@Test
	void offlineCheckpointMigratesOnlyItsOwnDuplicateInFlightClaim() throws Exception {
		String entry = "VoteReward%placeholders%Server%pair%server-a";
		ArrayList<String> persisted = new ArrayList<>();
		persisted.add(entry);
		persisted.add(entry);
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(persisted));
		org.mockito.Mockito.doAnswer(invocation -> {
			persisted.clear();
			persisted.addAll(invocation.getArgument(1));
			return null;
		}).when(data).setStringList(eq("offlineRewardsPath"), any(), eq(false));
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(pending);

			ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
			user.checkOfflineRewards();
			verify(rewardHandler).givePersistedQueueRewardAsync(eq(user),
					any(PersistedQueueReference.class), options.capture());
			options.getValue().getAsyncReplayCheckpointConsumer().accept(replayCheckpoint(Map.of("VoteReward", 1),
					new HashMap<>(Map.of("Server", "server-a"))));

		assertEquals(2, persisted.size());
		assertTrue(persisted.stream().anyMatch(value -> value.contains("%asyncprogress%v2-")));
			assertTrue(persisted.stream().anyMatch(value -> value.equals(entry)));
			user.checkOfflineRewards();
			verify(rewardHandler).givePersistedQueueRewardAsync(eq(user),
					any(PersistedQueueReference.class), any(RewardOptions.class));
			verify(data).setStringList(eq("offlineRewardsPath"), any(), eq(false));

			pending.complete(null);
			verify(rewardHandler, org.mockito.Mockito.times(2)).givePersistedQueueRewardAsync(eq(user),
					any(PersistedQueueReference.class), any(RewardOptions.class));
		}

	@Test
	void timedAsyncReplayPreservesItsStoredCheckpointAfterExecutionMarker() {
		long due = System.currentTimeMillis() - 1_000;
		ArrayList<String> timed = new ArrayList<>();
		timed.add("VoteReward%extime%1%asyncprogress%2%placeholders%Server%pair%server-a%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT)).thenReturn(timed);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));

		ArgumentCaptor<PersistedQueueReference> reference = ArgumentCaptor.forClass(PersistedQueueReference.class);
		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkDelayedTimedRewards();

		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), reference.capture(), options.capture());
		assertEquals("VoteReward", reference.getValue().getReference());
		assertEquals(2, options.getValue().getCompletedAsyncInjections());
	}

	@Test
	void timedAsyncCheckpointIsDurableAndMigratesTheExactEntryWhileInFlight() throws Exception {
		long due = System.currentTimeMillis() - 1_000;
		String original = "VoteReward%extime%12345%placeholders%Server%pair%server-a";
		ArrayList<String> persisted = new ArrayList<>();
		persisted.add(original + "%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(persisted));
		org.mockito.Mockito.doAnswer(invocation -> {
			persisted.clear();
			persisted.addAll(invocation.getArgument(1));
			return null;
		}).when(data).setStringList(eq("TimedRewards"), any(), eq(false));
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(pending);

		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkDelayedTimedRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), options.capture());
		options.getValue().getAsyncReplayCheckpointConsumer().accept(replayCheckpoint(Map.of("VoteReward", 1),
				new HashMap<>(Map.of("Server", "server-a"))));

		assertEquals(1, persisted.size());
		assertTrue(persisted.get(0).startsWith("VoteReward%extime%12345%asyncprogress%v2-"));
		assertTrue(persisted.get(0).endsWith("%ExecutionTime/%" + due));
		verify(data).setStringList(eq("TimedRewards"), any(), eq(false));
		// The key has migrated, but its in-flight claim must migrate with it too.
		user.checkDelayedTimedRewards();
		verify(rewardHandler, org.mockito.Mockito.times(1)).givePersistedQueueRewardAsync(eq(user),
				any(PersistedQueueReference.class), any(RewardOptions.class));
	}

	@Test
	void pendingTimedReplayClaimIsSharedAcrossWrappersForTheSameUuid() {
		long due = System.currentTimeMillis() - 1_000;
		ArrayList<String> timed = new ArrayList<>(List.of("VoteReward%ExecutionTime/%" + due));
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT)).thenReturn(timed);
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(any(AdvancedCoreUser.class),
				any(PersistedQueueReference.class), any(RewardOptions.class))).thenReturn(pending);
		AdvancedCoreUser second = new AdvancedCoreUser(plugin, UUID.fromString(user.getUUID()), "Test");
		second.setData(data);

		user.checkDelayedTimedRewards();
		second.checkDelayedTimedRewards();

		verify(rewardHandler, org.mockito.Mockito.times(1)).givePersistedQueueRewardAsync(
				any(AdvancedCoreUser.class), any(PersistedQueueReference.class), any(RewardOptions.class));
	}

	@Test
	void timedRewardMutationsAreSharedAcrossUserWrappers() throws Exception {
		java.util.concurrent.atomic.AtomicReference<ArrayList<String>> stored =
				new java.util.concurrent.atomic.AtomicReference<>(new ArrayList<>());
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch releaseFirstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> {
					if (reads.getAndIncrement() == 0) {
						firstRead.countDown();
						await(releaseFirstRead);
					} else {
						secondRead.countDown();
					}
					return new ArrayList<>(stored.get());
				});
		org.mockito.Mockito.doAnswer(invocation -> {
			stored.set(new ArrayList<>(invocation.getArgument(1)));
			return null;
		}).when(data).setStringList(eq("TimedRewards"), any());
		AdvancedCoreUser second = new AdvancedCoreUser(plugin, UUID.fromString(user.getUUID()), "Test");
		second.setData(data);
		Reward firstReward = mock(Reward.class);
		Reward secondReward = mock(Reward.class);
		when(firstReward.getRewardName()).thenReturn("First");
		when(secondReward.getRewardName()).thenReturn("Second");
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.Future<?> first = workers.submit(
					() -> user.addTimedReward(firstReward, new HashMap<>(), 1));
			assertTrue(firstRead.await(5, TimeUnit.SECONDS));
			java.util.concurrent.Future<?> secondTask = workers.submit(
					() -> second.addTimedReward(secondReward, new HashMap<>(), 2));
			assertFalse(secondRead.await(100, TimeUnit.MILLISECONDS));
			releaseFirstRead.countDown();
			first.get(10, TimeUnit.SECONDS);
			secondTask.get(10, TimeUnit.SECONDS);
		} finally {
			releaseFirstRead.countDown();
			workers.shutdownNow();
		}

		assertEquals(2, stored.get().size());
	}

	@Test
	void unavailablePlayerFailsAsyncCommandDispatch() {
		AdvancedCorePlugin.setInstance(plugin);
		try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(UUID.fromString(user.getUUID()))).thenReturn(null);

			assertThrows(java.util.concurrent.CompletionException.class,
					() -> user.preformCommandAsync(new ArrayList<>(List.of("say hello")), new HashMap<>())
							.toCompletableFuture().join());
		} finally {
			AdvancedCorePlugin.setInstance(null);
		}
	}

	@Test
	void timedAsyncCheckpointSurvivesRestartBeforeTheInFlightCompletion() throws Exception {
		long due = System.currentTimeMillis() - 1_000;
		ArrayList<String> persisted = new ArrayList<>();
		persisted.add("VoteReward%extime%12345%placeholders%Server%pair%server-a%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(persisted));
		org.mockito.Mockito.doAnswer(invocation -> {
			persisted.clear();
			persisted.addAll(invocation.getArgument(1));
			return null;
		}).when(data).setStringList(eq("TimedRewards"), any(), eq(false));
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(pending);

		ArgumentCaptor<RewardOptions> firstOptions = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkDelayedTimedRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), firstOptions.capture());
		firstOptions.getValue().getAsyncReplayCheckpointConsumer().accept(replayCheckpoint(Map.of("root/Grant:0", 1),
				new HashMap<>(Map.of("Server", "server-a"))));

		AdvancedCoreUser restarted = new AdvancedCoreUser(plugin, UUID.randomUUID(), "Test");
		restarted.setData(data);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(restarted), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));
		ArgumentCaptor<RewardOptions> resumed = ArgumentCaptor.forClass(RewardOptions.class);
		restarted.checkDelayedTimedRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(restarted), any(PersistedQueueReference.class), resumed.capture());
		assertEquals(1, resumed.getValue().getAsyncReplayProgress().get("root/Grant:0"));
		assertEquals("server-a", resumed.getValue().getPlaceholders().get("Server"));
	}

	@Test
	void timedAsyncCheckpointPersistsAndRestoresItsRegistryFingerprint() throws Exception {
		long due = System.currentTimeMillis() - 1_000;
		ArrayList<String> persisted = new ArrayList<>();
		persisted.add("VoteReward%placeholders%Server%pair%server-a%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
				.thenAnswer(ignored -> new ArrayList<>(persisted));
		org.mockito.Mockito.doAnswer(invocation -> {
			persisted.clear();
			persisted.addAll(invocation.getArgument(1));
			return null;
		}).when(data).setStringList(eq("TimedRewards"), any(), eq(false));
		CompletableFuture<Void> pending = new CompletableFuture<>();
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(pending);

		ArgumentCaptor<RewardOptions> firstOptions = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkDelayedTimedRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), firstOptions.capture());
		firstOptions.getValue().getAsyncReplayCheckpointConsumer().accept(replayCheckpoint(Map.of("VoteReward", 1),
				Map.of("VoteReward", "fingerprint", "VoteReward/0", "parent-fingerprint"),
				new HashMap<>(Map.of("Server", "server-a"))));
		assertTrue(persisted.get(0).contains("%asyncprogress%v3-"));

		AdvancedCoreUser restarted = new AdvancedCoreUser(plugin, UUID.randomUUID(), "Test");
		restarted.setData(data);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(restarted), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));
		ArgumentCaptor<RewardOptions> resumed = ArgumentCaptor.forClass(RewardOptions.class);
		restarted.checkDelayedTimedRewards();
		verify(rewardHandler).givePersistedQueueRewardAsync(eq(restarted), any(PersistedQueueReference.class), resumed.capture());
		assertEquals("fingerprint", resumed.getValue().getAsyncReplayRegistryFingerprints().get("VoteReward"));
		assertEquals("parent-fingerprint", resumed.getValue().getAsyncReplayRegistryFingerprints().get("VoteReward/0"));
	}

	@Test
	void timedRetryParsesProgressAfterTheExecutionMarkerWasConsumed() {
		long due = System.currentTimeMillis() - 1_000;
		ArrayList<String> timed = new ArrayList<>();
		timed.add("VoteReward%asyncprogress%2%asyncretry%1%placeholders%Server%pair%server-a%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT)).thenReturn(timed);
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.completedFuture(null));

		ArgumentCaptor<PersistedQueueReference> reference = ArgumentCaptor.forClass(PersistedQueueReference.class);
		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		user.checkDelayedTimedRewards();

		verify(rewardHandler).givePersistedQueueRewardAsync(eq(user), reference.capture(), options.capture());
		assertEquals("VoteReward", reference.getValue().getReference());
		assertEquals(2, options.getValue().getCompletedAsyncInjections());
	}

	private Reward.ReplayCheckpoint replayCheckpoint(Map<String, Integer> progress,
			HashMap<String, String> placeholders) throws Exception {
		java.lang.reflect.Constructor<Reward.ReplayCheckpoint> constructor = Reward.ReplayCheckpoint.class
				.getDeclaredConstructor(Map.class, HashMap.class);
		constructor.setAccessible(true);
		return constructor.newInstance(progress, placeholders);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(failure);
		}
	}

	private Reward.ReplayCheckpoint replayCheckpoint(Map<String, Integer> progress, Map<String, String> fingerprints,
			HashMap<String, String> placeholders) throws Exception {
		java.lang.reflect.Constructor<Reward.ReplayCheckpoint> constructor = Reward.ReplayCheckpoint.class
				.getDeclaredConstructor(Map.class, Map.class, HashMap.class);
		constructor.setAccessible(true);
		return constructor.newInstance(progress, fingerprints, placeholders);
	}
}
