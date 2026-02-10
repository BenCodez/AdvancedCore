package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;

public class AdvancedCoreUserTest {
	private AdvancedCorePlugin plugin;
	private UserManager userManager;
	private UserDataManager dataManager;
	private UserData data;
	private AdvancedCoreUser user;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		userManager = mock(UserManager.class);
		dataManager = mock(UserDataManager.class);
		data = mock(UserData.class);
		MySQL mysql = mock(MySQL.class);

		AdvancedCoreConfigOptions configOptions = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(configOptions);
		when(configOptions.isOnlineMode()).thenReturn(true); // Stub the required method
		when(plugin.getMysql()).thenReturn(mysql);
		when(userManager.getDataManager()).thenReturn(dataManager);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(userManager.getOfflineRewardsPath()).thenReturn("offlineRewardsPath");

		user = new AdvancedCoreUser(plugin, UUID.randomUUID(),"Test");
		user.setData(data); // Inject the mocked UserData object
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
}