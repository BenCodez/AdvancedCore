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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
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
	private AdvancedCoreUser user;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		userManager = mock(UserManager.class);
		dataManager = mock(UserDataManager.class);
		data = mock(UserData.class);
		rewardHandler = mock(RewardHandler.class);
		MySQL mysql = mock(MySQL.class);

		AdvancedCoreConfigOptions configOptions = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(configOptions);
		when(configOptions.isOnlineMode()).thenReturn(true); // Stub the required method
		when(plugin.getMysql()).thenReturn(mysql);
		when(userManager.getDataManager()).thenReturn(dataManager);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(plugin.getRewardHandler()).thenReturn(rewardHandler);
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
		when(data.getStringList("offlineRewardsPath", UserDataFetchMode.DEFAULT))
				.thenReturn(new ArrayList<>(initial)).thenReturn(new ArrayList<>());
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")));

		user.checkOfflineRewards();

		ArgumentCaptor<ArrayList<String>> saved = ArgumentCaptor.forClass(ArrayList.class);
		verify(data, org.mockito.Mockito.times(3)).setStringList(eq("offlineRewardsPath"), saved.capture());
		assertEquals(initial, saved.getAllValues().get(2));
	}

	@Test
	void failedAsyncTimedReplayIsRestoredAfterDueEntriesAreSaved() {
		long due = System.currentTimeMillis() - 1_000;
		String entry = "VoteReward%extime%1%placeholders%Server%pair%server-a";
		ArrayList<String> timed = new ArrayList<>();
		timed.add(entry + "%ExecutionTime/%" + due);
		when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
				.thenReturn(new ArrayList<>(timed)).thenReturn(new ArrayList<>());
		when(rewardHandler.givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class),
				any(RewardOptions.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")));

		user.checkDelayedTimedRewards();

		ArgumentCaptor<ArrayList<String>> saved = ArgumentCaptor.forClass(ArrayList.class);
		verify(data, org.mockito.Mockito.times(2)).setStringList(eq("TimedRewards"), saved.capture());
		assertEquals(timed, saved.getAllValues().get(1));
	}
}
