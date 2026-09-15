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

public abstract class RewardInjectInt extends RewardInject {

	@Getter
	@Setter
	private int defaultValue = 0;

	public RewardInjectInt(String path) {
		super(path);
	}

	public RewardInjectInt(String path, int defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isInt(getPath()) || (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			int value = data.getInt(getPath(), getDefaultValue());
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
		if (data.isInt(getPath()) || (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			int value = data.getInt(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			try {
				CompletionStage<String> result = onRewardRequestAsync(reward, user, value, placeholders);
				if (result == null) {
					return CompletableFuture.failedFuture(new IllegalStateException(
							"Reward injection returned a null asynchronous result: " + getPath()));
				}
				return result.thenApply(valueResult -> valueResult == null ? "" + value : valueResult)
						.thenApply(valueResult -> (Object) valueResult);
			} catch (Throwable throwable) {
				return CompletableFuture.failedFuture(throwable);
			}
		}
		return CompletableFuture.completedFuture(null);
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, int num,
			HashMap<String, String> placeholders);

	/**
	 * Typed asynchronous hook for integer injections. Subclasses can override it
	 * without reimplementing configuration parsing.
	 *
	 * @param reward       reward being given
	 * @param user         receiving user
	 * @param num          configured integer value
	 * @param placeholders current placeholders
	 * @return completion stage containing the typed result
	 */
	public CompletionStage<String> onRewardRequestAsync(Reward reward, AdvancedCoreUser user, int num,
			HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequest(reward, user, num, placeholders));
		} catch (Throwable throwable) {
			return CompletableFuture.failedFuture(throwable);
		}
	}

}
