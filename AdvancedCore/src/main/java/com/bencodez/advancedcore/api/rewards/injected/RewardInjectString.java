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

public abstract class RewardInjectString extends RewardInject {

	@Getter
	@Setter
	private String defaultValue;

	public RewardInjectString(String path) {
		super(path);
	}

	public RewardInjectString(String path, String defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if ((data.isString(getPath()) && !data.getString(getPath(), "").isEmpty())
				|| (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			String value = data.getString(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			String re = onRewardRequest(reward, user, value, placeholders);
			if (re == null) {
				return value;
			}
			return re;
		}
		return null;
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
			HashMap<String, String> placeholders);

	/** Completion-aware counterpart for string injections. */
	public CompletionStage<String> onRewardRequestAsync(Reward reward, AdvancedCoreUser user, String value,
			HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequest(reward, user, value, placeholders));
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}

	@Override
	public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection data, HashMap<String, String> placeholders) {
		if (!((data.isString(getPath()) && !data.getString(getPath(), "").isEmpty())
				|| (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData())) {
			return CompletableFuture.completedFuture(null);
		}
		String value = data.getString(getPath(), getDefaultValue());
		CompletionStage<String> result = onRewardRequestAsync(reward, user, value, placeholders);
		if (result == null) {
			return CompletableFuture.failedFuture(new IllegalStateException(
					"Reward injection returned a null asynchronous result: " + getPath()));
		}
		return result.thenApply(valueResult -> (Object) (valueResult == null ? value : valueResult));
	}

}
