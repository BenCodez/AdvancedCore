package com.bencodez.advancedcore.api.rewards.injected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectStringList extends RewardInject {

	@Getter
	@Setter
	private ArrayList<String> defaultValue = new ArrayList<>();

	public RewardInjectStringList(String path) {
		super(path);
	}

	public RewardInjectStringList(String path, ArrayList<String> defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> num,
			HashMap<String, String> placeholders);

	/**
	 * Completion-aware counterpart for list injections. Existing list injections
	 * retain their synchronous behavior unless they opt in by overriding this
	 * method.
	 */
	public CompletionStage<String> onRewardRequestAsync(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
			HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequest(reward, user, value, placeholders));
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isList(getPath()) || (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			ArrayList<String> value = (ArrayList<String>) data.getList(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance().extraDebug(
					reward.getRewardName() + ": Giving " + getPath() + ", value: " + ArrayUtils.makeStringList(value));
			return onRewardRequest(reward, user, value, placeholders);

		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection data, HashMap<String, String> placeholders) {
		if (!data.isList(getPath()) && !(isAlwaysForce() && data.contains(getPath(), true)) && !isAlwaysForceNoData()) {
			return hasPendingReplayWork(placeholders)
					? onRewardRequestAsync(reward, user, new ArrayList<>(), placeholders).thenApply(result -> result)
					: CompletableFuture.completedFuture(null);
		}
		List<?> stored = data.getList(getPath(), getDefaultValue());
		ArrayList<String> value = new ArrayList<>();
		for (Object entry : stored) value.add(String.valueOf(entry));
		return onRewardRequestAsync(reward, user, value, placeholders).thenApply(result -> result);
	}

}
