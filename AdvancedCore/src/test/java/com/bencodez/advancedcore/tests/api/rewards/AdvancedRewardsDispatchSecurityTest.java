package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.builtin.RewardAdvancedRewards;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class AdvancedRewardsDispatchSecurityTest {

	@Test
	void childDispatchPrefixDoesNotDuplicateChildName() {
		RewardHandler handler = mock(RewardHandler.class);
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		Reward reward = mock(Reward.class);
		CopyOnWriteArrayList<RewardInject> injects = new CopyOnWriteArrayList<>();
		when(handler.getInjectedRewards()).thenReturn(injects);
		when(reward.getRewardName()).thenReturn("Daily");

		try (MockedConstruction<ItemBuilder> ignored = mockConstruction(ItemBuilder.class,
				withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
			RewardAdvancedRewards.register(handler, plugin);
		}

		YamlConfiguration data = new YamlConfiguration();
		ConfigurationSection section = data.createSection("AdvancedRewards");
		section.createSection("foo");

		((RewardInjectConfigurationSection) injects.get(0)).onRewardRequested(reward, user, section, new HashMap<>());

		ArgumentCaptor<RewardOptions> options = ArgumentCaptor.forClass(RewardOptions.class);
		verify(handler).giveReward(eq(user), eq(section), eq("foo"), options.capture());
		assertEquals("Daily_AdvancedRewards", options.getValue().getPrefix());
	}
}
