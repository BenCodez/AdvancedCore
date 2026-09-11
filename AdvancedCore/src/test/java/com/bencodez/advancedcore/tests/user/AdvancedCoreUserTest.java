package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
		verify(rewardHandler, org.mockito.Mockito.times(2)).givePersistedQueueRewardAsync(eq(user),
				any(PersistedQueueReference.class), options.capture());
		options.getAllValues().get(0).getAsyncReplayCheckpointConsumer().accept(replayCheckpoint(Map.of("VoteReward", 1),
				new HashMap<>(Map.of("Server", "server-a"))));

		assertEquals(2, persisted.size());
		assertTrue(persisted.stream().anyMatch(value -> value.contains("%asyncprogress%v2-")));
		assertTrue(persisted.stream().anyMatch(value -> value.equals(entry)));
		user.checkOfflineRewards();
		verify(rewardHandler, org.mockito.Mockito.times(2)).givePersistedQueueRewardAsync(eq(user),
				any(PersistedQueueReference.class), any(RewardOptions.class));
		verify(data).setStringList(eq("offlineRewardsPath"), any(), eq(false));
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
}
