package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectBoolean extends RewardInject {

	@Getter
	@Setter
	private boolean defaultValue = false;

	public RewardInjectBoolean(String path) {
		super(path);
	}

	public RewardInjectBoolean(String path, boolean defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, boolean num,
			HashMap<String, String> placeholders);

	/** Completion-aware counterpart for boolean injections. */
	public CompletionStage<String> onRewardRequestAsync(Reward reward, AdvancedCoreUser user, boolean value,
			HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequest(reward, user, value, placeholders));
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isBoolean(getPath()) || (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			boolean value = data.getBoolean(getPath(), isDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			String re = onRewardRequest(reward, user, value, placeholders);
			if (re == null) {
				return "" + value;
			}
			return re;
		}
		return null;
	}

	@Override
	public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection data, HashMap<String, String> placeholders) {
		if (!(data.isBoolean(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))
				|| isAlwaysForceNoData())) {
			if (!hasPendingReplayWork(placeholders)) return CompletableFuture.completedFuture(null);
		}
		boolean value = data.getBoolean(getPath(), isDefaultValue());
		CompletionStage<String> result = onRewardRequestAsync(reward, user, value, placeholders);
		if (result == null) return CompletableFuture.failedFuture(new IllegalStateException(
				"Reward injection returned a null asynchronous result: " + getPath()));
		return result.thenApply(valueResult -> (Object) (valueResult == null ? String.valueOf(value) : valueResult));
	}

}
