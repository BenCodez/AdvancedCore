package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public abstract class RewardInjectConfigurationSection extends RewardInject {

	public RewardInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))
				|| isAlwaysForceNoData()) {
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			return onRewardRequested(reward, user, data.getConfigurationSection(getPath()), placeholders);
		}
		return null;
	}

	public abstract String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
			HashMap<String, String> placeholders);

	/** Completion-aware counterpart for section injections. */
	public CompletionStage<String> onRewardRequestedAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection section, HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequested(reward, user, section, placeholders));
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}

	/** Fails closed when a durable section replay can no longer read its payload. */
	protected CompletionStage<Object> onMissingConfiguredDataAsync(Reward reward, AdvancedCoreUser user,
			HashMap<String, String> placeholders) {
		return CompletableFuture.failedFuture(
				new IllegalStateException("Pending reward replay configuration is missing: " + getPath()));
	}

	@Override
	public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection data, HashMap<String, String> placeholders) {
		if (!data.isConfigurationSection(getPath()) && !(isAlwaysForce() && data.contains(getPath(), true))
				&& !isAlwaysForceNoData()) {
			return hasPendingReplayWork(placeholders)
					? onMissingConfiguredDataAsync(reward, user, placeholders)
					: CompletableFuture.completedFuture(null);
		}
		return onRewardRequestedAsync(reward, user, data.getConfigurationSection(getPath()), placeholders)
				.thenApply(result -> result);
	}

}
