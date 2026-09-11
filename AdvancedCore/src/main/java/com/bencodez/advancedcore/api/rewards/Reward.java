package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.listeners.PlayerRewardEvent;
import com.bencodez.simpleapi.file.annotation.AnnotationHandler;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class Reward.
 */
public class Reward {
	private static final ThreadLocal<ReplayState> ACTIVE_REPLAY_STATE = new ThreadLocal<>();
	private static final ThreadLocal<String> ACTIVE_REPLAY_KEY = new ThreadLocal<>();
	private static final ThreadLocal<String> ACTIVE_REPLAY_OCCURRENCE_ID = new ThreadLocal<>();
	private static final String REPLAY_SELECTION_PREFIX = "__advancedcore_replay_selection_";

	@Getter
	@Setter
	private RewardFileData config;

	@Getter
	@Setter
	private boolean delayEnabled;

	@Getter
	@Setter
	private int delayHours;

	@Getter
	@Setter
	private int delayMinutes;

	@Getter
	@Setter
	private int delaySeconds;

	@Getter
	@Setter
	private int delayMilliSeconds;

	@Getter
	@Setter
	private File file;

	@Getter
	@Setter
	private boolean forceOffline;

	@Getter
	@Setter
	private String name;

	@Getter
	private boolean needsRewardFile = true;

	@Getter
	private boolean generatedSnapshotCreated = false;

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	@Getter
	@Setter
	private boolean timedEnabled;

	@Getter
	@Setter
	private int timedHour;

	@Getter
	@Setter
	private int timedMinute;

	/**
	 * Instantiates a new reward.
	 *
	 * @param file   the file
	 * @param reward the reward
	 */
	public Reward(File file, String reward) {
		load(file, reward);
	}

	/**
	 * Instantiates a new reward.
	 *
	 * @param reward the reward
	 */
	public Reward(String reward) {
		load(plugin.getRewardHandler().getDefaultFolder(), reward);
	}

	public Reward(String name, ConfigurationSection section) {
		load(name, section);
	}

	public boolean canGiveReward(AdvancedCoreUser user, RewardOptions options) {
		for (RequirementInject inject : plugin.getRewardHandler().getInjectedRequirements()) {
			try {
				plugin.extraDebug(getRewardName() + ": Checking " + inject.getPath() + ":" + inject.getPriority());
				if (!inject.onRequirementRequest(this, user, getConfig().getConfigData(), options)) {
					return false;
				}
			} catch (Exception e) {
				plugin.debug("Failed to check requirement");
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public boolean checkDelayed(AdvancedCoreUser user, HashMap<String, String> placeholders) {
		if (!isDelayEnabled()) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		time = time.plus(getDelayHours(), ChronoUnit.HOURS);
		time = time.plus(getDelayMinutes(), ChronoUnit.MINUTES);
		time = time.plus(getDelaySeconds(), ChronoUnit.SECONDS);
		time = time.plus(getDelayMilliSeconds(), ChronoUnit.MILLIS);
		checkRewardFile();
		user.addTimedReward(this, placeholders, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		plugin.debug("Giving reward " + name + " in " + getDelayHours() + " hours, " + getDelayMinutes() + " minutes, "
				+ getDelaySeconds() + " seconds (" + time.toString() + ")");
		return true;
	}

	public void checkRewardFile() {
		if (!getConfig().hasRewardFile() && needsRewardFile) {
			setRewardFile();
		}
	}

	public boolean checkTimed(AdvancedCoreUser user, HashMap<String, String> placeholders) {
		if (!isTimedEnabled()) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		time = time.withHour(getTimedHour());
		time = time.withMinute(getTimedMinute());

		if (LocalDateTime.now().isAfter(time)) {
			time = time.plusDays(1);
		}
		checkRewardFile();
		user.addTimedReward(this, placeholders, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		plugin.debug("Giving reward " + name + " at " + time.toString());
		return true;
	}

	public ItemStack getItem() {
		return new ItemStack(Material.STONE);
	}

	public ItemStack getItemStack(AdvancedCoreUser user, String item) {
		return new ItemBuilder(getConfig().getItemSection(item)).setSkullOwner(user.getOfflinePlayer())
				.toItemStack(user.getPlayer());
	}

	/**
	 * Gets the reward name.
	 *
	 * @return the reward name
	 */
	public String getRewardName() {
		return name;
	}

	public void giveInjectedRewards(AdvancedCoreUser user, HashMap<String, String> placeholders) {

		ArrayList<RewardInject> postReward = new ArrayList<>();

		for (final RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
			boolean Addplaceholder = inject.isAddAsPlaceholder();
			try {
				Object obj = null;
				plugin.extraDebug(
						getRewardName() + ": Attempting to give " + inject.getPath() + ":" + inject.getPriority());
				if (!inject.isPostReward()) {
					if (inject.isSynchronize()) {
						synchronized (inject.getObject()) {
							obj = inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
						}
					} else {
						obj = inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
					}
					if (Addplaceholder && obj != null) {
						String placeholderName = inject.getPlaceholderName();
						String value = "";
						if (obj instanceof Boolean) {
							Boolean b = (Boolean) obj;
							value = b.toString();
						} else if (obj instanceof String) {
							String b = (String) obj;
							value = b;
						} else if (obj instanceof Double) {
							Double b = (Double) obj;
							value = b.toString();
						} else if (obj instanceof Integer) {
							Integer b = (Integer) obj;
							value = b.toString();
						}
						plugin.extraDebug("Adding placeholder " + placeholderName + ":" + value);
						placeholders.put(placeholderName, value);
					}
				} else {
					postReward.add(inject);

				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		for (RewardInject inject : postReward) {
			try {
				inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Gives injected rewards in registration order, waiting for each asynchronous
	 * injection before evaluating the next one. Synchronous injections retain
	 * their existing behavior, and post-reward injections run after all normal
	 * injections have completed.
	 *
	 * @param user         receiving user
	 * @param placeholders current placeholders
	 * @return completion stage that completes after all injections have run
	 */
	public CompletionStage<Void> giveInjectedRewardsAsync(AdvancedCoreUser user,
			HashMap<String, String> placeholders) {
		return giveInjectedRewardsAsync(user, placeholders, 0, new ReplayState(null), getRewardName(),
				UUID.randomUUID().toString());
	}

	// Compatibility bridge for internal callers/tests that only carry replay path
	// metadata. Persisted queue dispatch supplies the explicit occurrence id below.
	private CompletionStage<Void> giveInjectedRewardsAsync(AdvancedCoreUser user,
			HashMap<String, String> placeholders, int completedStages, ReplayState replayState, String replayKey) {
		return giveInjectedRewardsAsync(user, placeholders, completedStages, replayState, replayKey,
				UUID.randomUUID().toString());
	}

	/**
	 * Resumes a persisted asynchronous reward after the supplied number of
	 * injection stages have completed. Queue recovery uses this checkpoint to
	 * avoid replaying already-applied non-idempotent injections.
	 */
	private CompletionStage<Void> giveInjectedRewardsAsync(AdvancedCoreUser user,
			HashMap<String, String> placeholders, int completedStages, ReplayState replayState, String replayKey,
			String occurrenceId) {
		List<RewardInject> orderedRewards = orderedInjectedRewards();
		int resumeAfter = Math.max(0, replayState.getCompleted(replayKey, completedStages));
		String registryFingerprint = injectionRegistryFingerprint(orderedRewards);
		if (!replayState.matchesRegistryFingerprint(registryFingerprint)) {
			return CompletableFuture.failedFuture(new IncompatibleReplayCheckpointException(replayKey));
		}
		AtomicInteger completed = new AtomicInteger(resumeAfter);
		CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);
		for (int index = 0; index < orderedRewards.size(); index++) {
			if (index < resumeAfter) continue;
			final RewardInject inject = orderedRewards.get(index);
			final String injectionKey = replayKey + "/" + index;
			sequence = sequence.thenCompose(ignored -> {
				// A nested injector can checkpoint a child before this parent injector
				// completes. Bind the parent's registry first so that child checkpoint
				// cannot later be resumed against a shifted parent ordinal.
				replayState.setRegistryFingerprint(replayKey, registryFingerprint);
				return invokeInjectionAsync(inject, user, placeholders, replayState, injectionKey, occurrenceId);
			})
					.thenApply(result -> {
						if (inject.isAddAsPlaceholder() && result != null) addPlaceholder(inject, result, placeholders);
						int checkpoint = completed.incrementAndGet();
						replayState.setCompleted(replayKey, checkpoint);
						replayState.setRegistryFingerprint(replayKey, registryFingerprint);
						replayState.persistCheckpoint(placeholders);
						return result;
					}).thenCompose(ignored -> resumeOnServerThread(user));
		}
		return sequence.handle((ignored, failure) -> {
			if (failure == null) return null;
			RewardReplayFailure nestedFailure = findReplayFailure(failure);
			if (nestedFailure != null) throw nestedFailure;
			replayState.setCompleted(replayKey, completed.get());
			throw new RewardReplayFailure(replayState, placeholders, failure);
		});
	}

	private List<RewardInject> orderedInjectedRewards() {
		List<RewardInject> postRewards = new ArrayList<>();
		List<RewardInject> orderedRewards = new ArrayList<>();
		for (RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
			if (inject.isPostReward()) postRewards.add(inject);
			else orderedRewards.add(inject);
		}
		orderedRewards.addAll(postRewards);
		return orderedRewards;
	}

	/**
	 * A persisted checkpoint is valid only for this exact ordered injector
	 * registry. Paths, implementation classes, priorities, and post-reward
	 * placement are all included because each affects which side effect an
	 * ordinal identifies.
	 */
	private static String injectionRegistryFingerprint(List<RewardInject> orderedRewards) {
		StringBuilder registry = new StringBuilder();
		for (RewardInject inject : orderedRewards) {
			String path = inject.getPath() == null ? "" : inject.getPath();
			registry.append(inject.getClass().getName()).append('\t').append(Base64.getUrlEncoder().withoutPadding()
					.encodeToString(path.getBytes(StandardCharsets.UTF_8))).append('\t')
					.append(inject.getPriority()).append('\t').append(inject.isPostReward()).append('\n');
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(registry.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
			return hex.toString();
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable for reward replay checkpoints", failure);
		}
	}

	// Kept as a narrow compatibility bridge for internal callers/tests that only
	// have a single reward checkpoint.
	private CompletionStage<Void> giveInjectedRewardsAsync(AdvancedCoreUser user,
			HashMap<String, String> placeholders, int completedStages) {
		return giveInjectedRewardsAsync(user, placeholders, completedStages, new ReplayState(null), getRewardName(),
				UUID.randomUUID().toString());
	}

	/** Details the last durably safe replay checkpoint when an async chain fails. */
	public static final class RewardReplayFailure extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final ReplayState replayState;
		@Getter private final int completedInjectionCount;
		@Getter private final HashMap<String, String> replayPlaceholders;

		private RewardReplayFailure(ReplayState replayState, HashMap<String, String> replayPlaceholders, Throwable cause) {
			super("Asynchronous reward replay failed after a completed asynchronous reward stage",
					cause);
			this.replayState = replayState;
			this.completedInjectionCount = replayState.highestCompletedCount();
			this.replayPlaceholders = new HashMap<>(replayPlaceholders);
		}

		public Map<String, Integer> getReplayProgress() { return replayState.copyProgress(); }
		public Map<String, String> getReplayRegistryFingerprints() { return replayState.copyRegistryFingerprints(); }
	}

	/** Prevents an ordinal checkpoint from targeting a changed injector registry. */
	private static final class IncompatibleReplayCheckpointException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		private IncompatibleReplayCheckpointException(String replayKey) {
			super("Cannot safely resume asynchronous reward replay '" + replayKey
					+ "' because its injector registry changed or its legacy checkpoint has no registry fingerprint");
		}
	}

	private static RewardReplayFailure findReplayFailure(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof RewardReplayFailure) return (RewardReplayFailure) current;
		}
		return null;
	}

	/** Captures the active nested replay state for deferred child dispatch. */
	public static ReplayState currentReplayState() { return ACTIVE_REPLAY_STATE.get(); }

	/** Captures the active parent execution path before deferred work runs. */
	public static String currentReplayKey() { return ACTIVE_REPLAY_KEY.get(); }

	/** Returns the unique logical reward occurrence currently invoking an injector. */
	public static String currentReplayOccurrenceId() { return ACTIVE_REPLAY_OCCURRENCE_ID.get(); }

	/** Obtains one shared replay state for a group of nested dispatches. */
	public static ReplayState replayStateFor(RewardOptions options) {
		ReplayState replayState = options.getAsyncReplayState();
		if (replayState == null) {
			replayState = ACTIVE_REPLAY_STATE.get();
			if (replayState == null) replayState = new ReplayState(options.getAsyncReplayProgress(),
					options.getAsyncReplayRegistryFingerprints(), options.isLegacyAsyncReplayCheckpoint());
			options.setAsyncReplayState(replayState);
		}
		if (options.getAsyncReplayCheckpointConsumer() != null) {
			replayState.setCheckpointConsumer(options.getAsyncReplayCheckpointConsumer());
		}
		return replayState;
	}

	/**
	 * Records a nondeterministic nested-reward decision in the placeholder
	 * snapshot carried by {@link RewardReplayFailure}. A retry consequently
	 * executes the same selected branch instead of rolling a new outcome after a
	 * child has already performed a side effect.
	 */
	public static String replaySelection(HashMap<String, String> placeholders, Supplier<String> selector) {
		String activeKey = ACTIVE_REPLAY_KEY.get();
		String storageKey = REPLAY_SELECTION_PREFIX + Base64.getUrlEncoder().withoutPadding()
				.encodeToString((activeKey == null ? "root" : activeKey).getBytes(StandardCharsets.UTF_8));
		if (placeholders.containsKey(storageKey)) {
			String stored = placeholders.get(storageKey);
			return stored.isEmpty() ? null : new String(Base64.getUrlDecoder().decode(stored), StandardCharsets.UTF_8);
		}
		String selected = selector.get();
		placeholders.put(storageKey, selected == null ? "" : Base64.getUrlEncoder().withoutPadding()
				.encodeToString(selected.getBytes(StandardCharsets.UTF_8)));
		return selected;
	}

	/** Attaches a captured replay state to an option object for a deferred child. */
	public static RewardOptions withReplayState(RewardOptions options, ReplayState replayState) {
		if (replayState != null) options.setAsyncReplayState(replayState);
		String occurrenceId = ACTIVE_REPLAY_OCCURRENCE_ID.get();
		if (occurrenceId != null) options.setAsyncReplayOccurrenceId(occurrenceId);
		return options;
	}

	/**
	 * Associates a deferred nested reward with a deterministic child occurrence.
	 * A list may intentionally contain the same reward more than once, so its
	 * replay checkpoint must never be shared merely because its name is equal.
	 */
	public static RewardOptions withReplayState(RewardOptions options, ReplayState replayState,
			String childOccurrence) {
		return withReplayState(options, replayState, ACTIVE_REPLAY_KEY.get(), childOccurrence);
	}

	/**
	 * Associates deferred nested work with the parent path captured while the
	 * injector was invoked. Later completion callbacks do not retain ThreadLocal
	 * context, so they must use this explicit form.
	 */
	public static RewardOptions withReplayState(RewardOptions options, ReplayState replayState,
			String parentKey, String childOccurrence) {
		return withReplayState(options, replayState, parentKey, childOccurrence, ACTIVE_REPLAY_OCCURRENCE_ID.get());
	}

	/**
	 * Explicit deferred-child form. Completion callbacks must carry both the
	 * replay path and its logical occurrence because ThreadLocal context is not
	 * retained after an asynchronous parent injector returns.
	 */
	public static RewardOptions withReplayState(RewardOptions options, ReplayState replayState,
			String parentKey, String childOccurrence, String occurrenceId) {
		if (replayState != null) options.setAsyncReplayState(replayState);
		if (occurrenceId != null) options.setAsyncReplayOccurrenceId(occurrenceId);
		if (parentKey != null && childOccurrence != null) {
			options.setAsyncReplayKey(parentKey + "/" + childOccurrence);
		}
		return options;
	}

	/** Internal shared state passed through nested async reward dispatch. */
	public static final class ReplayState {
		private final HashMap<String, Integer> completed = new HashMap<>();
		private final HashMap<String, String> registryFingerprints = new HashMap<>();
		private final boolean legacyCheckpoint;
		private Consumer<ReplayCheckpoint> checkpointConsumer;
		private ReplayState(Map<String, Integer> initial) { this(initial, null, false); }
		private ReplayState(Map<String, Integer> initial, Map<String, String> initialFingerprints,
				boolean legacyCheckpoint) {
			if (initial != null) completed.putAll(initial);
			if (initialFingerprints != null) registryFingerprints.putAll(initialFingerprints);
			this.legacyCheckpoint = legacyCheckpoint;
		}
		private synchronized int getCompleted(String rewardName, int fallback) {
			return completed.getOrDefault(rewardName, fallback);
		}
		private synchronized void setCompleted(String rewardName, int count) { completed.put(rewardName, count); }
		private synchronized Map<String, Integer> copyProgress() { return new HashMap<>(completed); }
		private synchronized void setRegistryFingerprint(String rewardName, String fingerprint) {
			registryFingerprints.put(rewardName, fingerprint);
		}
		private synchronized Map<String, String> copyRegistryFingerprints() {
			return new HashMap<>(registryFingerprints);
		}
		private synchronized boolean hasPersistedCheckpoint() {
			return legacyCheckpoint || !completed.isEmpty() || !registryFingerprints.isEmpty();
		}
		private synchronized boolean matchesRegistryFingerprint(String currentFingerprint) {
			if (legacyCheckpoint) return false;
			for (Entry<String, Integer> entry : completed.entrySet()) {
				if (entry.getValue() != null && entry.getValue() > 0
						&& !currentFingerprint.equals(registryFingerprints.get(entry.getKey()))) return false;
			}
			for (String fingerprint : registryFingerprints.values()) {
				if (!currentFingerprint.equals(fingerprint)) return false;
			}
			return true;
		}
		private synchronized int highestCompletedCount() {
			int highest = 0;
			for (int count : completed.values()) highest = Math.max(highest, count);
			return highest;
		}
		private synchronized void setCheckpointConsumer(Consumer<ReplayCheckpoint> consumer) {
			checkpointConsumer = consumer;
		}
		private void persistCheckpoint(HashMap<String, String> placeholders) {
			Consumer<ReplayCheckpoint> consumer;
			synchronized (this) { consumer = checkpointConsumer; }
			if (consumer != null) consumer.accept(new ReplayCheckpoint(copyProgress(), copyRegistryFingerprints(), placeholders));
		}
	}

	/** Immutable durable replay data emitted after every completed injection. */
	public static final class ReplayCheckpoint {
		@Getter private final Map<String, Integer> replayProgress;
		@Getter private final Map<String, String> replayRegistryFingerprints;
		@Getter private final HashMap<String, String> placeholders;
		private ReplayCheckpoint(Map<String, Integer> replayProgress, HashMap<String, String> placeholders) {
			this(replayProgress, new HashMap<>(), placeholders);
		}
		private ReplayCheckpoint(Map<String, Integer> replayProgress, Map<String, String> replayRegistryFingerprints,
				HashMap<String, String> placeholders) {
			this.replayProgress = new HashMap<>(replayProgress);
			this.replayRegistryFingerprints = new HashMap<>(replayRegistryFingerprints);
			this.placeholders = new HashMap<>(placeholders);
		}
	}

	private CompletionStage<Object> invokeInjectionAsync(RewardInject inject, AdvancedCoreUser user,
			HashMap<String, String> placeholders, ReplayState replayState, String injectionKey, String occurrenceId) {
		try {
			if (!plugin.isEnabled()) return CompletableFuture.failedFuture(
					new IllegalStateException("Plugin disabled before reward injection completed"));
			Supplier<CompletionStage<Object>> request = () -> requestOnServerThread(user,
					() -> requestInjectionAsync(inject, user, placeholders, replayState, injectionKey, occurrenceId));
			CompletionStage<Object> result = inject.isSynchronize() && inject.supportsAsyncSynchronization()
					? inject.runSynchronizedAsync(request)
					: request.get();
			if (result == null) {
				return CompletableFuture.failedFuture(new IllegalStateException(
						"Reward injection returned a null asynchronous result: " + inject.getPath()));
			}
			return result;
		} catch (Throwable throwable) {
			return CompletableFuture.failedFuture(throwable);
		}
	}

	private <T> CompletionStage<T> requestOnServerThread(AdvancedCoreUser user,
			Supplier<CompletionStage<T>> request) {
		CompletableFuture<CompletionStage<T>> handoff = new CompletableFuture<>();
		Runnable invocation = () -> {
			if (!plugin.isEnabled()) {
				handoff.complete(CompletableFuture.failedFuture(
						new IllegalStateException("Plugin disabled before reward injection completed")));
				return;
			}
			CompletableFuture<T> result = new CompletableFuture<>();
			// Claim the handoff before invoking user code. If the timeout won, this
			// queued task must not produce late reward side effects.
			if (!handoff.complete(result)) return;
			try {
				CompletionStage<T> stage = request.get();
				if (stage == null) {
					result.completeExceptionally(
							new IllegalStateException("Reward injection returned a null asynchronous result"));
					return;
				}
				stage.whenComplete((value, failure) -> {
					if (failure == null) result.complete(value);
					else result.completeExceptionally(failure);
				});
			} catch (Throwable failure) {
				result.completeExceptionally(failure);
			}
		};
		Runnable resolvePlayer = () -> {
			if (!plugin.isEnabled()) {
				handoff.complete(CompletableFuture.failedFuture(
						new IllegalStateException("Plugin disabled before reward injection completed")));
				return;
			}
			try {
				Player player = user.getPlayer();
				if (player != null) plugin.getBukkitScheduler().executeOrScheduleSync(plugin, invocation, player);
				else invocation.run();
			} catch (Throwable failure) {
				handoff.completeExceptionally(failure);
			}
		};
		try {
			plugin.getBukkitScheduler().executeOrScheduleSync(plugin, resolvePlayer);
		} catch (Throwable failure) {
			handoff.completeExceptionally(failure);
		}
		return handoff.orTimeout(getServerThreadDispatchTimeoutMillis(), TimeUnit.MILLISECONDS)
				.thenCompose(stage -> stage);
	}

	private CompletionStage<Void> resumeOnServerThread(AdvancedCoreUser user) {
		return requestOnServerThread(user, () -> CompletableFuture.completedFuture(null));
	}

	/** Maximum time to wait when a scheduler drops a task during plugin shutdown. */
	protected long getServerThreadDispatchTimeoutMillis() {
		return TimeUnit.SECONDS.toMillis(30);
	}

	private CompletionStage<Object> requestInjectionAsync(RewardInject inject, AdvancedCoreUser user,
			HashMap<String, String> placeholders, ReplayState replayState, String injectionKey, String occurrenceId) {
		ReplayState previous = ACTIVE_REPLAY_STATE.get();
		String previousKey = ACTIVE_REPLAY_KEY.get();
		String previousOccurrenceId = ACTIVE_REPLAY_OCCURRENCE_ID.get();
		ACTIVE_REPLAY_STATE.set(replayState);
		ACTIVE_REPLAY_KEY.set(injectionKey);
		if (occurrenceId == null) ACTIVE_REPLAY_OCCURRENCE_ID.remove();
		else ACTIVE_REPLAY_OCCURRENCE_ID.set(occurrenceId);
		try {
		if (inject.supportsAsyncRequest()) {
			return inject.onRewardRequestAsync(this, user, getConfig().getConfigData(), placeholders);
		}
		try {
			return CompletableFuture.completedFuture(
					inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders));
		} catch (Exception failure) {
			// Preserve the legacy per-injection isolation contract while allowing
			// opted-in asynchronous injections to propagate durable failures.
			failure.printStackTrace();
			return CompletableFuture.completedFuture(null);
		}
		} finally {
			if (previous == null) ACTIVE_REPLAY_STATE.remove(); else ACTIVE_REPLAY_STATE.set(previous);
			if (previousKey == null) ACTIVE_REPLAY_KEY.remove(); else ACTIVE_REPLAY_KEY.set(previousKey);
			if (previousOccurrenceId == null) ACTIVE_REPLAY_OCCURRENCE_ID.remove();
			else ACTIVE_REPLAY_OCCURRENCE_ID.set(previousOccurrenceId);
		}
	}

	private void addPlaceholder(RewardInject inject, Object obj, HashMap<String, String> placeholders) {
		String placeholderName = inject.getPlaceholderName();
		String value = "";
		if (obj instanceof Boolean) {
			value = obj.toString();
		} else if (obj instanceof String) {
			value = (String) obj;
		} else if (obj instanceof Double) {
			value = obj.toString();
		} else if (obj instanceof Integer) {
			value = obj.toString();
		}
		plugin.extraDebug("Adding placeholder " + placeholderName + ":" + value);
		placeholders.put(placeholderName, value);
	}

	private boolean hasAsyncRewardInjection() {
		for (RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
			if (inject.supportsAsyncRequest()
					&& (!inject.requiresConfiguredDataForAsync() || isRewardInjectionApplicable(inject))) {
				return true;
			}
		}
		return false;
	}

	private boolean isRewardInjectionApplicable(RewardInject inject) {
		ConfigurationSection data = getConfig().getConfigData();
		return inject.isAlwaysForceNoData() || data.contains(inject.getPath(), true);
	}

	public void giveReward(AdvancedCoreUser user, RewardOptions rewardOptions) {
		if (!AdvancedCorePlugin.getInstance().getOptions().isProcessRewards()) {
			AdvancedCorePlugin.getInstance().debug("Processing rewards is disabled");
			return;
		}

		if (rewardOptions == null) {
			rewardOptions = new RewardOptions();
		}

		if (!rewardOptions.getPlaceholders().containsKey("ExecDate")) {
			rewardOptions.addPlaceholder("ExecDate", "" + System.currentTimeMillis());
		}

		if (!rewardOptions.getPlaceholders().containsKey("date")) {
			try {
				LocalDateTime ldt = LocalDateTime.now();
				Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
				rewardOptions.addPlaceholder("Date",
						"" + new SimpleDateFormat(plugin.getOptions().getFormatRewardTimeFormat()).format(date));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		PlayerRewardEvent event = new PlayerRewardEvent(this, user, rewardOptions);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			plugin.debug("Reward " + name + " was cancelled for " + user.getPlayerName());
			return;
		}

		if (rewardOptions.isCheckTimed()) {
			if (checkDelayed(user, rewardOptions.getPlaceholders())
					|| checkTimed(user, rewardOptions.getPlaceholders())) {
				return;
			}
		}

		if (!rewardOptions.isOnlineSet()) {
			rewardOptions.setOnline(user.isOnline());
		}

		for (RewardPlaceholderHandle handle : plugin.getRewardHandler().getPlaceholders()) {
			if (handle.isPreProcess()) {
				rewardOptions.addPlaceholder(handle.getKey(), handle.getValue(this, user));
			}
		}

		// Check requirements
		boolean allowOffline = false;
		boolean canGive = true;
		if (!rewardOptions.isIgnoreRequirements()) {
			for (RequirementInject inject : plugin.getRewardHandler().getInjectedRequirements()) {
				try {
					plugin.extraDebug(getRewardName() + ": Checking requirement " + inject.getPath() + ":"
							+ inject.getPriority());
					if (!inject.onRequirementRequest(this, user, getConfig().getConfigData(), rewardOptions)) {
						plugin.debug(getRewardName() + ": Requirement failed " + inject.getPath() + ":"
								+ inject.isAllowReattempt());
						canGive = false;
						if (!inject.isAllowReattempt()) {
							return;
						}
						allowOffline = true;
					}
				} catch (Exception e) {
					plugin.debug("Failed to check requirement " + inject.getPath());
					e.printStackTrace();
					canGive = false;
				}
			}
		}

		if (plugin.getOptions().isPauseRewards()) {
			checkRewardFile();
			user.addOfflineRewards(this, rewardOptions.getPlaceholders());
			plugin.getLogger()
					.info("Rewards are paused, saving offline reward " + getRewardName() + ": " + user.getPlayerName());
			return;
		}

		if ((plugin.getOptions().isTreatVanishAsOffline() && user.isVanished())) {
			checkRewardFile();
			user.addOfflineRewards(this, rewardOptions.getPlaceholders());
			plugin.getLogger()
					.info(getRewardName() + ": " + user.getPlayerName() + " is vanished, saving reward offline");
			return;
		}

		// save reward for offline
		if (((((!rewardOptions.isOnline() || rewardOptions.getServer() != null) && !user.isOnline()) || allowOffline)
				&& (!isForceOffline() && !rewardOptions.isForceOffline()))) {
			if (rewardOptions.isGiveOffline()) {
				checkRewardFile();
				user.addOfflineRewards(this, rewardOptions.getPlaceholders());
				plugin.debug("Saving offline reward " + getRewardName() + " for " + user.getPlayerName());
			}
			return;
		}

		// give reward
		if (canGive || isForceOffline() || rewardOptions.isForceOffline()) {
			plugin.debug(name + ": Passed requirements, attempting to give to " + user.getPlayerName() + "/"
					+ user.getUUID());
			giveRewardUser(user, rewardOptions.getPlaceholders(), rewardOptions);
		}
	}

	/**
	 * Gives this reward and completes when an asynchronous injection chain has
	 * finished. This is used by nested reward injectors so a child reward cannot
	 * overtake later parent post-reward injections.
	 *
	 * <p>The established void API deliberately remains fire-and-forget. Callers
	 * that need ordering or durable replay semantics must use this method.</p>
	 *
	 * @param user receiving user
	 * @param rewardOptions reward options
	 * @return completion stage for the complete reward injection chain
	 */
	public CompletionStage<Void> giveRewardAsync(AdvancedCoreUser user, RewardOptions rewardOptions) {
		if (!AdvancedCorePlugin.getInstance().getOptions().isProcessRewards()) {
			AdvancedCorePlugin.getInstance().debug("Processing rewards is disabled");
			return CompletableFuture.completedFuture(null);
		}

		if (rewardOptions == null) rewardOptions = new RewardOptions();
		if (!rewardOptions.getPlaceholders().containsKey("ExecDate")) {
			rewardOptions.addPlaceholder("ExecDate", "" + System.currentTimeMillis());
		}
		if (!rewardOptions.getPlaceholders().containsKey("date")) {
			try {
				LocalDateTime ldt = LocalDateTime.now();
				Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
				rewardOptions.addPlaceholder("Date", "" + new SimpleDateFormat(
						plugin.getOptions().getFormatRewardTimeFormat()).format(date));
			} catch (Exception e) {
				return CompletableFuture.failedFuture(e);
			}
		}

		PlayerRewardEvent event = new PlayerRewardEvent(this, user, rewardOptions);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			plugin.debug("Reward " + name + " was cancelled for " + user.getPlayerName());
			return CompletableFuture.completedFuture(null);
		}
		if (rewardOptions.isCheckTimed() && (checkDelayed(user, rewardOptions.getPlaceholders())
				|| checkTimed(user, rewardOptions.getPlaceholders()))) {
			return CompletableFuture.completedFuture(null);
		}
		if (!rewardOptions.isOnlineSet()) rewardOptions.setOnline(user.isOnline());
		for (RewardPlaceholderHandle handle : plugin.getRewardHandler().getPlaceholders()) {
			if (handle.isPreProcess()) rewardOptions.addPlaceholder(handle.getKey(), handle.getValue(this, user));
		}

		boolean allowOffline = false;
		boolean canGive = true;
		if (!rewardOptions.isIgnoreRequirements()) {
			for (RequirementInject inject : plugin.getRewardHandler().getInjectedRequirements()) {
				try {
					if (!inject.onRequirementRequest(this, user, getConfig().getConfigData(), rewardOptions)) {
						canGive = false;
						if (!inject.isAllowReattempt()) return CompletableFuture.completedFuture(null);
						allowOffline = true;
					}
				} catch (Exception e) {
					plugin.debug("Failed to check requirement " + inject.getPath());
					e.printStackTrace();
					canGive = false;
				}
			}
		}
		if (plugin.getOptions().isPauseRewards() || (plugin.getOptions().isTreatVanishAsOffline() && user.isVanished())) {
			checkRewardFile();
			user.addOfflineRewards(this, rewardOptions.getPlaceholders());
			return CompletableFuture.completedFuture(null);
		}
		if (((((!rewardOptions.isOnline() || rewardOptions.getServer() != null) && !user.isOnline()) || allowOffline)
				&& (!isForceOffline() && !rewardOptions.isForceOffline()))) {
			if (rewardOptions.isGiveOffline()) {
				checkRewardFile();
				user.addOfflineRewards(this, rewardOptions.getPlaceholders());
			}
			return CompletableFuture.completedFuture(null);
		}
		if (canGive || isForceOffline() || rewardOptions.isForceOffline()) {
			plugin.debug(name + ": Passed requirements, attempting to give to " + user.getPlayerName() + "/"
					+ user.getUUID());
			if (hasAsyncRewardInjection() || hasPersistedReplayCheckpoint(rewardOptions)) {
				return giveRewardUserAsync(user, rewardOptions.getPlaceholders(), rewardOptions);
			}
			try {
				giveRewardUser(user, rewardOptions.getPlaceholders(), rewardOptions);
				return CompletableFuture.completedFuture(null);
			} catch (Throwable failure) {
				return CompletableFuture.failedFuture(failure);
			}
		}
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Give reward user.
	 *
	 * @param user          the user
	 * @param phs           placeholders
	 * @param rewardOptions rewardOptions
	 */
	public void giveRewardUser(AdvancedCoreUser user, HashMap<String, String> phs, RewardOptions rewardOptions) {
		if (hasAsyncRewardInjection() || hasPersistedReplayCheckpoint(rewardOptions)) {
			giveRewardUserAsync(user, phs, rewardOptions).exceptionally(failure -> {
				logRewardUserFailure(failure);
				return null;
			});
			return;
		}

		HashMap<String, String> placeholders = prepareRewardUser(user, phs);
		if (placeholders == null) {
			return;
		}
		giveInjectedRewards(user, placeholders);
		plugin.debug("Gave " + user.getPlayerName() + " reward " + name);
	}

	/**
	 * Asynchronously gives a reward to a user when an injection opts into the
	 * asynchronous API. Preparation remains on the calling thread; only the
	 * opted-in injection chain is asynchronous.
	 *
	 * @param user          receiving user
	 * @param phs           placeholders
	 * @param rewardOptions reward options (reserved for API symmetry)
	 * @return completion stage that completes after reward injections finish
	 */
	public CompletionStage<Void> giveRewardUserAsync(AdvancedCoreUser user, HashMap<String, String> phs,
			RewardOptions rewardOptions) {
		ReplayState replayState = replayStateFor(rewardOptions);
		String replayKey = rewardOptions.getAsyncReplayKey();
		if (replayKey == null) {
			String parentKey = ACTIVE_REPLAY_KEY.get();
			replayKey = parentKey == null ? getRewardName() : parentKey + "/" + getRewardName();
			rewardOptions.setAsyncReplayKey(replayKey);
		}
		if (!replayState.matchesRegistryFingerprint(injectionRegistryFingerprint(orderedInjectedRewards()))) {
			return CompletableFuture.failedFuture(new IncompatibleReplayCheckpointException(replayKey));
		}
		String occurrenceId = rewardOptions.getAsyncReplayOccurrenceId();
		if (occurrenceId == null || occurrenceId.isEmpty()) {
			occurrenceId = ACTIVE_REPLAY_OCCURRENCE_ID.get();
			// A pre-occurrence persisted entry cannot safely survive an ambiguous
			// side effect, so leave it on the legacy compatibility path rather than
			// inventing a different id for each retry.
			if (occurrenceId == null && rewardOptions.getAsyncReplayCheckpointConsumer() == null) {
				occurrenceId = UUID.randomUUID().toString();
				rewardOptions.setAsyncReplayOccurrenceId(occurrenceId);
			}
		}
		final HashMap<String, String> placeholders;
		try {
			placeholders = prepareRewardUser(user, phs);
		} catch (Throwable throwable) {
			return CompletableFuture.failedFuture(throwable);
		}
		if (placeholders == null) {
			return CompletableFuture.completedFuture(null);
		}
		return giveInjectedRewardsAsync(user, placeholders, rewardOptions.getCompletedAsyncInjections(), replayState, replayKey,
				occurrenceId)
				.thenRun(() -> plugin.debug("Gave " + user.getPlayerName() + " reward " + name));
	}

	private static boolean hasPersistedReplayCheckpoint(RewardOptions options) {
		return options.isLegacyAsyncReplayCheckpoint() || options.getCompletedAsyncInjections() > 0
				|| !options.getAsyncReplayProgress().isEmpty() || !options.getAsyncReplayRegistryFingerprints().isEmpty()
				|| (options.getAsyncReplayState() != null && options.getAsyncReplayState().hasPersistedCheckpoint());
	}

	private HashMap<String, String> prepareRewardUser(AdvancedCoreUser user, HashMap<String, String> phs) {

		Player player = user.getPlayer();
		if (player == null) {
			player = Bukkit.getPlayer(user.getPlayerName());
		}
		if (player != null || isForceOffline()) {

			// placeholders
			if (phs == null) {
				phs = new HashMap<>();
			}
			final String playerName = user.getPlayerName();
			phs.put("player", playerName);
			if (player != null) {
				phs.put("displayname", player.getDisplayName());
			}
			phs.put("@p", playerName);
			LocalDateTime ldt = LocalDateTime.now();
			Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
			phs.put("CurrentDate", "" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
			phs.put("uuid", user.getUUID());

			for (RewardPlaceholderHandle handle : plugin.getRewardHandler().getPlaceholders()) {
				if (!handle.isPreProcess()) {
					phs.put(handle.getKey(), handle.getValue(this, user));
				}
			}

			return new HashMap<>(phs);
		} else {
			plugin.debug(getRewardName() + ": Player == null & forceoffline false, player: " + user.getPlayerName()
					+ "/" + user.getUUID());
			return null;
		}
	}

	private void logRewardUserFailure(Throwable failure) {
		if (plugin.getLogger() != null) {
			plugin.getLogger().log(Level.WARNING, "Failed to give reward " + getRewardName(), failure);
		} else {
			failure.printStackTrace();
		}
	}

	/**
	 * Load.
	 *
	 * @param folder the folder
	 * @param reward the reward
	 */
	public void load(File folder, String reward) {
		name = reward;
		if (folder.isDirectory()) {
			file = new File(folder, reward + ".yml");
		} else {
			file = folder;
		}
		config = new RewardFileData(this, folder);
		loadValues();
	}

	public void load(String name, ConfigurationSection section) {
		config = new RewardFileData(this, section);
		this.name = name;
		loadValues();
	}

	public void loadValues() {
		forceOffline = getConfig().getForceOffline();

		setDelayEnabled(getConfig().getDelayedEnabled());
		if (delayEnabled) {
			setDelayHours(getConfig().getDelayedHours());
			setDelayMinutes(getConfig().getDelayedMinutes());
			setDelaySeconds(getConfig().getDelayedSeconds());
			setDelayMilliSeconds(getConfig().getDelayedMilliSeconds());
		}

		setTimedEnabled(getConfig().getTimedEnabled());
		if (timedEnabled) {
			setTimedHour(getConfig().getTimedHour());
			setTimedMinute(getConfig().getTimedMinute());
		}

		new AnnotationHandler().load(getConfig().getConfigData(), this);
	}

	public Reward needsRewardFile(boolean value) {
		needsRewardFile = value;
		return this;
	}

	@SuppressWarnings("deprecation")
	private void setRewardFile() {
		Reward reward = plugin.getRewardHandler().getRewardDirectlyDefined(name);
		ConfigurationSection section = getConfig().getConfigData();
		reward.getConfig().setData(section);
		reward.getConfig().getFileData().options()
				.header("Directly defined reward file. WRONG PLACE TO EDIT THIS! DO NOT EDIT");
		reward.getConfig().setDirectlyDefinedReward(true);
		reward.getConfig().save(reward.getConfig().getFileData());
		generatedSnapshotCreated = true;
	}

	public void validate() {
		if (getName().equalsIgnoreCase("examplebasic") || getName().equalsIgnoreCase("exampleadvanced")) {
			return;
		}
		for (RequirementInject inject : plugin.getRewardHandler().getInjectedRequirements()) {
			inject.validate(this, getConfig().getConfigData());
		}
		for (RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
			inject.validate(this, getConfig().getConfigData());
		}
		for (String str : getConfig().getConfigData().getKeys(false)) {
			boolean valid = false;
			for (RequirementInject inject : plugin.getRewardHandler().getInjectedRequirements()) {
				if (inject.hasValidator()) {
					if (inject.getValidate().isValid(inject, str)) {
						valid = true;
					}
				} else if (inject.getPath().startsWith(str)) {
					valid = true;
				}
			}
			for (RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
				if (inject.isAlwaysValid()) {
					valid = true;
				} else {
					if (inject.hasValidator()) {
						if (inject.getValidate().isValid(inject, str)) {
							valid = true;
						}
					} else if (inject.getPath().startsWith(str)) {
						valid = true;
					}
				}
			}
			if (plugin.getRewardHandler().getValidPaths().contains(str)) {
				valid = true;
			}
			if (!valid) {
				plugin.getLogger().warning(str + " possibly not valid in reward " + getRewardName());
			}
		}
	}

}
