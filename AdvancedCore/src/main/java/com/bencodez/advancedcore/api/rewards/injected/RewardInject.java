package com.bencodez.advancedcore.api.rewards.injected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Inject;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject extends Inject {
	private CompletableFuture<Void> synchronizedAsyncTail = CompletableFuture.completedFuture(null);

	@Getter
	private boolean addAsPlaceholder = false;

	@Getter
	private boolean alwaysForce = false;

	@Getter
	private boolean alwaysForceNoData = false;

	@Getter
	private Object object;

	@Getter
	private String placeholderName;

	@Getter
	private boolean postReward = false;

	@Getter
	@Setter
	private boolean synchronize = false;

	@Getter
	private boolean alwaysValid = false;

	@Getter
	private RewardInjectValidator validate;

	public RewardInject(String path) {
		super(path);
	}

	public RewardInject addEditButton(EditGUIButton button) {
		getEditButtons().add(button);
		return this;
	}

	public RewardInject alwaysForce() {
		this.alwaysForce = true;
		return this;
	}

	public RewardInject alwaysForceNoData() {
		this.alwaysForce = true;
		this.alwaysForceNoData = true;
		return this;
	}

	public RewardInject alwaysValid() {
		alwaysValid = true;
		return this;
	}

	public RewardInject asPlaceholder(String placeholderName) {
		addAsPlaceholder = true;
		this.placeholderName = placeholderName;
		return this;
	}

	public void debug(String str) {
		AdvancedCorePlugin.getInstance().debug(str);
	}

	public void extraDebug(String str) {
		AdvancedCorePlugin.getInstance().extraDebug(str);
	}

	public boolean hasValidator() {
		return getValidate() != null;
	}

	/**
	 * Whether this injection has an asynchronous implementation. Existing
	 * injections remain synchronous by default.
	 *
	 * @return true when {@link #onRewardRequestAsync(Reward, AdvancedCoreUser,
	 *         ConfigurationSection, HashMap)} should be used
	 */
	public boolean supportsAsyncRequest() {
		return false;
	}

	public boolean isEditable() {
		return !getEditButtons().isEmpty();
	}

	public abstract Object onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders);

	/**
	 * Asynchronously evaluates this injection. The default implementation keeps
	 * the compatibility behavior by wrapping the existing synchronous callback.
	 *
	 * @param reward       reward being given
	 * @param user         receiving user
	 * @param data         reward configuration
	 * @param placeholders current placeholders
	 * @return completion stage containing the injection result
	 */
	public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
			ConfigurationSection data, HashMap<String, String> placeholders) {
		try {
			return CompletableFuture.completedFuture(onRewardRequest(reward, user, data, placeholders));
		} catch (Throwable throwable) {
			return CompletableFuture.failedFuture(throwable);
		}
	}

	/** Serializes a synchronized asynchronous injection through completion, not just invocation. */
	public synchronized CompletionStage<Object> runSynchronizedAsync(Supplier<CompletionStage<Object>> request) {
		CompletableFuture<Object> result = new CompletableFuture<>();
		synchronizedAsyncTail = synchronizedAsyncTail.handle((ignored, previousFailure) -> null)
				.thenCompose(ignored -> {
					CompletionStage<Object> stage;
					try {
						stage = request.get();
						if (stage == null) throw new IllegalStateException("Asynchronous reward injection returned null");
					} catch (Throwable failure) {
						result.completeExceptionally(failure);
						return CompletableFuture.<Void>completedFuture(null);
					}
					return stage.handle((value, failure) -> {
						if (failure == null) result.complete(value);
						else result.completeExceptionally(failure);
						return (Void) null;
					});
				}).toCompletableFuture();
		return result;
	}

	public RewardInject postReward() {
		postReward = true;
		return this;
	}

	public RewardInject priority(int priority) {
		setPriority(priority);
		return this;
	}

	public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
		return new ArrayList<>();
	}

	public RewardInject synchronize() {
		synchronize = true;
		object = new Object();
		return this;
	}

	public void validate(Reward reward, ConfigurationSection data) {
		if (validate != null && data.contains(getPath())) {
			validate.onValidate(reward, this, data);
		}
	}

	public RewardInject validator(RewardInjectValidator validate) {
		this.validate = validate;
		return this;
	}
}
