package com.bencodez.advancedcore.api.rewards;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.builtin.BuiltinRewards;
import com.bencodez.advancedcore.api.rewards.builtin.requirements.BuiltinRequirements;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;

/**
 * Owns injected reward, requirement, and reward-placeholder registrations.
 */
public class RewardInjectionRegistry {

	private final RewardHandler handler;
	private final AdvancedCorePlugin plugin;
	private final CopyOnWriteArrayList<RequirementInject> injectedRequirements = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<RewardInject> injectedRewards = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<RewardPlaceholderHandle> placeholders = new CopyOnWriteArrayList<>();

	public RewardInjectionRegistry(RewardHandler handler, AdvancedCorePlugin plugin) {
		this.handler = handler;
		this.plugin = plugin;
	}

	public void addInjectedRequirement(RequirementInject inject) {
		injectedRequirements.add(inject);
		sortInjectedRequirements();
	}

	public void addInjectedReward(RewardInject inject) {
		injectedRewards.add(inject);
		sortInjectedRewards();
	}

	public void addPlaceholder(RewardPlaceholderHandle handle) {
		placeholders.add(handle);
	}

	public CopyOnWriteArrayList<RequirementInject> getInjectedRequirements() {
		return injectedRequirements;
	}

	public CopyOnWriteArrayList<RewardInject> getInjectedRewards() {
		return injectedRewards;
	}

	public CopyOnWriteArrayList<RewardPlaceholderHandle> getPlaceholders() {
		return placeholders;
	}

	public void loadInjectedRequirements() {
		injectedRequirements.clear();
		BuiltinRequirements.register(handler, plugin);
		for (RequirementInject requirement : injectedRequirements) {
			requirement.setInternalReward(true);
		}
		sortInjectedRequirements();
	}

	public void loadInjectedRewards() {
		injectedRewards.clear();
		BuiltinRewards.register(handler, plugin);
		for (RewardInject reward : injectedRewards) {
			reward.setInternalReward(true);
		}
		sortInjectedRewards();
	}

	public void sortInjectedRequirements() {
		Collections.sort(injectedRequirements, new Comparator<RequirementInject>() {
			@Override
			public int compare(RequirementInject o1, RequirementInject o2) {
				return Integer.compare(o2.getPriority(), o1.getPriority());
			}
		});
	}

	public void sortInjectedRewards() {
		Collections.sort(injectedRewards, new Comparator<RewardInject>() {
			@Override
			public int compare(RewardInject o1, RewardInject o2) {
				return Integer.compare(o2.getPriority(), o1.getPriority());
			}
		});
	}
}
