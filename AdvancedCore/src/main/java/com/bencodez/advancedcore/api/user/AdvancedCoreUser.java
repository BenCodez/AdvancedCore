package com.bencodez.advancedcore.api.user;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.misc.effects.BossBar;
import com.bencodez.advancedcore.api.misc.effects.Title;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.actionbar.ActionBar;
import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.simpleapi.sql.Column;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * The Class User.
 */
public class AdvancedCoreUser {

	private static final String QUEUED_REFERENCE_PREFIX = "\\AdvancedCoreQueue/1/";
	private static final String ASYNC_PROGRESS_DELIMITER = "%asyncprogress%";
	private static final String ASYNC_RETRY_DELIMITER = "%asyncretry%";
	private static final String ASYNC_OCCURRENCE_DELIMITER = "%asyncoccurrence%";
	private static final Object REPLAY_CLAIMS_LOCK = new Object();
	private static final WeakHashMap<AdvancedCorePlugin, HashMap<String, ReplayClaims>> REPLAY_CLAIMS = new WeakHashMap<>();
	private static final ThreadLocal<AsyncActionCollection> ASYNC_ACTION_COLLECTION = new ThreadLocal<>();
	private static final ThreadLocal<ArrayList<CompletionStage<Void>>> CONTINUATION_ASYNC_ACTIONS =
			ThreadLocal.withInitial(ArrayList::new);
	private final Set<AsyncActionCollection> activeAsyncActionCollections = Collections
			.newSetFromMap(new IdentityHashMap<>());

	/** Internal completion scope used by asynchronous reward dispatch. */
	public static final class AsyncActionCollection {
		private final AsyncActionCollection previous;
		private final ArrayList<CompletionStage<Void>> actions = new ArrayList<>();
		private boolean closed;

		private AsyncActionCollection(AsyncActionCollection previous) {
			this.previous = previous;
		}

		private synchronized boolean add(CompletionStage<Void> action) {
			if (closed) return false;
			actions.add(action);
			return true;
		}

		private synchronized CompletionStage<Void> closeAndAwait() {
			closed = true;
			CompletionStage<Void> result = CompletableFuture.completedFuture(null);
			for (CompletionStage<Void> action : actions) {
				result = result.thenCompose(ignored -> action);
			}
			return result;
		}
	}

	/** Starts collecting completion stages created by legacy reward callbacks. */
	public AsyncActionCollection beginAsyncActionCollection() {
		AsyncActionCollection collection = new AsyncActionCollection(ASYNC_ACTION_COLLECTION.get());
		synchronized (activeAsyncActionCollections) {
			activeAsyncActionCollections.add(collection);
		}
		ASYNC_ACTION_COLLECTION.set(collection);
		return collection;
	}

	/** Restores the calling thread's previous collection while keeping this scope active for its returned stage. */
	public void restoreAsyncActionCollectionScope(AsyncActionCollection collection) {
		if (collection == null || ASYNC_ACTION_COLLECTION.get() != collection) return;
		if (collection.previous == null) ASYNC_ACTION_COLLECTION.remove();
		else ASYNC_ACTION_COLLECTION.set(collection.previous);
	}

	/** Closes a completed async scope and returns completion for every collected action. */
	public CompletionStage<Void> endAsyncActionCollection(AsyncActionCollection collection) {
		if (collection == null) return CompletableFuture.completedFuture(null);
		restoreAsyncActionCollectionScope(collection);
		synchronized (activeAsyncActionCollections) {
			activeAsyncActionCollections.remove(collection);
			return collection.closeAndAwait();
		}
	}

	private boolean collectAsyncAction(CompletionStage<Void> action) {
		AsyncActionCollection collection = ASYNC_ACTION_COLLECTION.get();
		if (collection != null && collection.add(action)) return true;
		synchronized (activeAsyncActionCollections) {
			if (activeAsyncActionCollections.isEmpty()) return false;
			// A CompletionStage continuation runs before completion callbacks attached
			// to its returned stage. Hold its legacy action on that same thread until
			// requestInjectionAsync's callback claims it for the exact collection.
			CONTINUATION_ASYNC_ACTIONS.get().add(action);
			return true;
		}
	}

	/** Claims legacy actions created by the continuation that just completed on this thread. */
	public void claimAsyncContinuationActions(AsyncActionCollection collection) {
		if (collection == null) return;
		ArrayList<CompletionStage<Void>> pending = CONTINUATION_ASYNC_ACTIONS.get();
		if (pending.isEmpty()) return;
		for (CompletionStage<Void> action : pending) collection.add(action);
		pending.clear();
		CONTINUATION_ASYNC_ACTIONS.remove();
	}

	private void collectAsyncFailure(Throwable failure) {
		collectAsyncAction(CompletableFuture.failedFuture(failure));
	}

	private static final class ReplayClaims {
		private final HashMap<String, Integer> offline = new HashMap<>();
		private final HashSet<String> timed = new HashSet<>();
		private CompletableFuture<Void> serialReplayTail = CompletableFuture.completedFuture(null);
	}

	/**
	 * User data fetch mode for this user.
	 * 
	 * @return the user data fetch mode
	 */
	@Getter
	private UserDataFetchMode userDataFetchMode = UserDataFetchMode.DEFAULT;

	/**
	 * User data for this user.
	 * 
	 * @param data the user data to set
	 */
	@Setter
	private UserData data;

	private boolean loadName = true;

	/** The player name. */
	private String playerName;

	/**
	 * Plugin instance.
	 * 
	 * @return the plugin instance
	 */
	@Getter
	private AdvancedCorePlugin plugin = null;

	/** The uuid. */
	private String uuid;

	/**
	 * Instantiates a new user from an existing user.
	 *
	 * @param plugin the plugin
	 * @param user   the user to copy from
	 */
	public AdvancedCoreUser(AdvancedCorePlugin plugin, AdvancedCoreUser user) {
		this.userDataFetchMode = user.userDataFetchMode;
		this.data = user.getUserData();
		this.uuid = user.getUUID();
		this.playerName = user.getPlayerName();
		this.loadName = user.loadName;
		this.plugin = plugin;
		loadData();
		getUserData().setTempCache(user.getUserData().getTempCache());
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin the plugin
	 * @param player the player
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, Player player) {
		this.plugin = plugin;
		loadData();
		uuid = player.getUniqueId().toString();
		setPlayerName(player.getName());
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin     the plugin
	 * @param playerName the player name
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, String playerName) {
		this.plugin = plugin;
		loadData();
		uuid = PlayerManager.getInstance().getUUID(playerName);
		setPlayerName(playerName);
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin the plugin
	 * @param uuid   the uuid
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		loadData();
		setPlayerName(PlayerManager.getInstance().getPlayerName(this, this.uuid, false));
	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin   the plugin
	 * @param uuid     the uuid
	 * @param loadName the load name
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, boolean loadName) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		this.loadName = loadName;
		loadData();
		if (this.loadName) {
			setPlayerName(PlayerManager.getInstance().getPlayerName(this, this.uuid));
		}

	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin   the plugin
	 * @param uuid     the uuid
	 * @param loadName the load name
	 * @param loadData the load data
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, boolean loadName, boolean loadData) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		this.loadName = loadName;
		if (loadData) {
			loadData();
		}
		if (this.loadName) {
			setPlayerName(PlayerManager.getInstance().getPlayerName(this, this.uuid));
		}

	}

	/**
	 * Instantiates a new user.
	 *
	 * @param plugin     the plugin
	 * @param uuid       the uuid
	 * @param playerName the player name
	 */
	@Deprecated
	public AdvancedCoreUser(AdvancedCorePlugin plugin, UUID uuid, String playerName) {
		this.plugin = plugin;
		this.uuid = uuid.toString();
		if (!plugin.getOptions().isOnlineMode()) {
			this.uuid = PlayerManager.getInstance().getUUID(playerName);
		}
		loadData();
		setPlayerName(playerName);
	}

	/**
	 * Adds offline rewards to be given when the player next logs in.
	 *
	 * @param reward       the reward
	 * @param placeholders the placeholders
	 */
	public void addOfflineRewards(Reward reward, HashMap<String, String> placeholders) {
		addOfflineRewards(reward, placeholders, null);
	}

	/**
	 * Adds an offline reward while retaining an in-flight asynchronous replay's
	 * occurrence and durable checkpoint.  This overload is used when pause or
	 * vanish handling defers a reward before its async chain can complete.
	 *
	 * @param reward       the reward
	 * @param placeholders the placeholders
	 * @param options      replay options to preserve, if any
	 */
	public void addOfflineRewards(Reward reward, HashMap<String, String> placeholders, RewardOptions options) {
		synchronized (plugin) {
			ArrayList<String> offlineRewards = getOfflineRewards();
			Reward.preserveReplayState(options);
			HashMap<String, String> savedPlaceholders = placeholders == null ? new HashMap<>()
					: new HashMap<>(placeholders);
			if (options != null) savedPlaceholders.putAll(options.getPlaceholders());
			offlineRewards.add(queuedRewardReference(reward, options) + "%placeholders%"
					+ ArrayUtils.makeString(savedPlaceholders));
			setOfflineRewards(offlineRewards);
		}
	}

	/**
	 * Adds a permission to the player.
	 *
	 * @param permission the permission
	 */
	public void addPermission(String permission) {
		plugin.getPermissionHandler().addPermission(getPlayer(), permission);
	}

	/**
	 * Adds a permission to the player with a delay.
	 *
	 * @param permission the permission
	 * @param delay      the delay in milliseconds
	 */
	public void addPermission(String permission, long delay) {
		plugin.getPermissionHandler().addPermission(getPlayer(), permission, delay);
	}

	/**
	 * Adds a timed reward to be given at a specific time.
	 *
	 * @param reward       the reward
	 * @param placeholders the placeholders
	 * @param epochMilli   the epoch time in milliseconds when the reward should be
	 *                     given
	 */
	public void addTimedReward(Reward reward, HashMap<String, String> placeholders, long epochMilli) {
		synchronized (plugin) {
			HashMap<String, Long> timed = new HashMap<>(getTimedRewards());
			String rewardName = queuedRewardReference(reward);
			rewardName += "%extime%" + System.currentTimeMillis();

			timed.put(rewardName + "%placeholders%" + ArrayUtils.makeString(placeholders), epochMilli);
			setTimedRewards(timed);
		}
		loadTimedDelayedTimer(epochMilli);
	}

	private String queuedRewardReference(Reward reward) {
		return queuedRewardReference(reward, null);
	}

	private String queuedRewardReference(Reward reward, RewardOptions options) {
		String encodedName = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(reward.getRewardName().getBytes(StandardCharsets.UTF_8));
		String reference = QUEUED_REFERENCE_PREFIX + (reward.isGeneratedSnapshotCreated() ? "snapshot/" : "normal/")
				+ encodedName + ASYNC_OCCURRENCE_DELIMITER
				+ (options == null || options.getAsyncReplayOccurrenceId() == null
						|| options.getAsyncReplayOccurrenceId().isEmpty() ? UUID.randomUUID()
							: options.getAsyncReplayOccurrenceId());
		if (options != null) {
			String serialized = encodeAsyncReplayProgress(options.getAsyncReplayProgress(),
					options.getAsyncReplayRegistryFingerprints());
			if (!serialized.isEmpty()) reference += ASYNC_PROGRESS_DELIMITER + serialized;
			else if (options.getCompletedAsyncInjections() > 0) {
				reference += ASYNC_PROGRESS_DELIMITER + options.getCompletedAsyncInjections();
			}
		}
		return reference;
	}

	private static QueuedReplay parseQueuedReplay(String storedReference) {
		String occurrenceId = occurrenceId(storedReference);
		String withoutOccurrence = stripAsyncOccurrenceMarker(storedReference);
		int marker = withoutOccurrence.lastIndexOf(ASYNC_PROGRESS_DELIMITER);
		if (marker < 0) return new QueuedReplay(withoutOccurrence, 0, new HashMap<>(), new HashMap<>(), false,
				occurrenceId);
		String value = withoutOccurrence.substring(marker + ASYNC_PROGRESS_DELIMITER.length());
		if (value.startsWith("v3-")) {
			try {
				HashMap<String, Integer> progress = new HashMap<>();
				HashMap<String, String> fingerprints = new HashMap<>();
				String decoded = new String(Base64.getUrlDecoder().decode(value.substring(3)), StandardCharsets.UTF_8);
				for (String line : decoded.split("\\n")) {
					String[] pair = line.split("\\t", 3);
					if (pair.length != 3) throw new IllegalArgumentException("Malformed replay checkpoint");
					progress.put(new String(Base64.getUrlDecoder().decode(pair[0]), StandardCharsets.UTF_8),
							Integer.parseInt(pair[1]));
					fingerprints.put(new String(Base64.getUrlDecoder().decode(pair[0]), StandardCharsets.UTF_8), pair[2]);
				}
				return new QueuedReplay(withoutOccurrence.substring(0, marker), 0, progress, fingerprints, false,
						occurrenceId);
			} catch (IllegalArgumentException ignored) {
				return new QueuedReplay(withoutOccurrence, 0, new HashMap<>(), new HashMap<>(), false, occurrenceId);
			}
		}
		if (value.startsWith("v2-")) {
			try {
				HashMap<String, Integer> progress = new HashMap<>();
				String decoded = new String(Base64.getUrlDecoder().decode(value.substring(3)), StandardCharsets.UTF_8);
				for (String line : decoded.split("\\n")) {
					String[] pair = line.split("\\t", 2);
					if (pair.length == 2) progress.put(pair[0], Integer.parseInt(pair[1]));
				}
				return new QueuedReplay(withoutOccurrence.substring(0, marker), 0, progress, new HashMap<>(), true,
						occurrenceId);
			} catch (IllegalArgumentException ignored) {
				return new QueuedReplay(withoutOccurrence, 0, new HashMap<>(), new HashMap<>(), false, occurrenceId);
			}
		}
		try {
			int progress = Integer.parseInt(value);
			return progress > 0 ? new QueuedReplay(withoutOccurrence.substring(0, marker), progress, new HashMap<>(), new HashMap<>(), true,
						occurrenceId)
					: new QueuedReplay(withoutOccurrence.substring(0, marker), 0, new HashMap<>(), new HashMap<>(), false,
							occurrenceId);
		} catch (NumberFormatException ignored) {
			// Preserve malformed legacy values as a normal reward reference instead
			// of accidentally skipping an arbitrary portion of a reward chain.
			return new QueuedReplay(withoutOccurrence, 0, new HashMap<>(), new HashMap<>(), false, occurrenceId);
		}
	}

	private static String occurrenceId(String storedReference) {
		int marker = storedReference.indexOf(ASYNC_OCCURRENCE_DELIMITER);
		if (marker < 0) return null;
		int start = marker + ASYNC_OCCURRENCE_DELIMITER.length();
		int end = storedReference.indexOf('%', start);
		String candidate = storedReference.substring(start, end < 0 ? storedReference.length() : end);
		try {
			return UUID.fromString(candidate).toString();
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String stripAsyncOccurrenceMarker(String storedReference) {
		int marker = storedReference.indexOf(ASYNC_OCCURRENCE_DELIMITER);
		if (marker < 0) return storedReference;
		int start = marker + ASYNC_OCCURRENCE_DELIMITER.length();
		int end = storedReference.indexOf('%', start);
		return storedReference.substring(0, marker) + (end < 0 ? "" : storedReference.substring(end));
	}

	private static String queuedReference(QueuedReplay replay) {
		return replay.rewardReference + (replay.asyncReplayOccurrenceId == null ? ""
				: ASYNC_OCCURRENCE_DELIMITER + replay.asyncReplayOccurrenceId);
	}

	private static String encodeAsyncReplayProgress(Map<String, Integer> progress) {
		if (progress.isEmpty()) return "";
		StringBuilder encoded = new StringBuilder();
		for (Entry<String, Integer> entry : progress.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0) {
				encoded.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
			}
		}
		return encoded.length() == 0 ? "" : "v2-" + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(encoded.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String encodeAsyncReplayProgress(Map<String, Integer> progress, Map<String, String> fingerprints) {
		if (fingerprints.isEmpty()) return encodeAsyncReplayProgress(progress);
		StringBuilder encoded = new StringBuilder();
		for (Entry<String, Integer> entry : progress.entrySet()) {
			String fingerprint = fingerprints.get(entry.getKey());
			if (entry.getValue() != null && entry.getValue() > 0 && fingerprint == null) {
				return encodeAsyncReplayProgress(progress);
			}
		}
		for (Entry<String, String> entry : fingerprints.entrySet()) {
			if (entry.getValue() == null) continue;
			encoded.append(Base64.getUrlEncoder().withoutPadding()
						.encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8))).append('\t')
						.append(progress.getOrDefault(entry.getKey(), 0)).append('\t').append(entry.getValue()).append('\n');
		}
		return encoded.length() == 0 ? encodeAsyncReplayProgress(progress) : "v3-"
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String stripTimedExecutionMarker(String storedReference) {
		int marker = storedReference.indexOf("%extime%");
		if (marker < 0) return stripAsyncRetryMarker(storedReference);
		int valueStart = marker + "%extime%".length();
		int nextMarker = storedReference.indexOf('%', valueStart);
		return stripAsyncRetryMarker(storedReference.substring(0, marker)
				+ (nextMarker < 0 ? "" : storedReference.substring(nextMarker)));
	}

	private static String stripAsyncRetryMarker(String storedReference) {
		int marker = storedReference.indexOf(ASYNC_RETRY_DELIMITER);
		if (marker < 0) return storedReference;
		int valueStart = marker + ASYNC_RETRY_DELIMITER.length();
		int nextMarker = storedReference.indexOf('%', valueStart);
		return storedReference.substring(0, marker) + (nextMarker < 0 ? "" : storedReference.substring(nextMarker));
	}

	private static int asyncRetryCount(String rewardEntry) {
		int marker = rewardEntry.indexOf(ASYNC_RETRY_DELIMITER);
		if (marker < 0) return 0;
		int valueStart = marker + ASYNC_RETRY_DELIMITER.length();
		int nextMarker = rewardEntry.indexOf('%', valueStart);
		try { return Integer.parseInt(rewardEntry.substring(valueStart, nextMarker < 0 ? rewardEntry.length() : nextMarker)); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private static String withAsyncRetryCount(String rewardEntry, int count) {
		int placeholders = rewardEntry.indexOf("%placeholders%");
		String reference = placeholders < 0 ? rewardEntry : rewardEntry.substring(0, placeholders);
		String suffix = placeholders < 0 ? "" : rewardEntry.substring(placeholders);
		return stripAsyncRetryMarker(reference) + ASYNC_RETRY_DELIMITER + count + suffix;
	}

	private static String withAsyncReplayProgress(String rewardEntry, Throwable failure) {
		Reward.RewardReplayFailure replayFailure = replayFailure(failure);
		int completed = completedAsyncInjections(failure);
		String serializedProgress = replayFailure == null ? "" : encodeAsyncReplayProgress(replayFailure.getReplayProgress(),
				replayFailure.getReplayRegistryFingerprints());
		if (completed <= 0 && serializedProgress.isEmpty()) return rewardEntry;
		int placeholders = rewardEntry.indexOf("%placeholders%");
		String storedReference = placeholders < 0 ? rewardEntry : rewardEntry.substring(0, placeholders);
		String suffix = placeholders < 0 ? "" : rewardEntry.substring(placeholders);
		if (replayFailure != null) suffix = "%placeholders%" + ArrayUtils.makeString(replayFailure.getReplayPlaceholders());
		QueuedReplay queuedReplay = parseQueuedReplay(stripAsyncRetryMarker(storedReference));
		if (!serializedProgress.isEmpty()) return queuedReference(queuedReplay) + ASYNC_PROGRESS_DELIMITER
				+ serializedProgress + suffix;
		return queuedReference(queuedReplay) + ASYNC_PROGRESS_DELIMITER
				+ Math.max(queuedReplay.completedAsyncInjections, completed) + suffix;
	}

	private static String withAsyncReplayProgress(String rewardEntry, Reward.ReplayCheckpoint checkpoint) {
		int marker = rewardEntry.indexOf("%placeholders%");
		String reference = marker < 0 ? rewardEntry : rewardEntry.substring(0, marker);
		QueuedReplay queuedReplay = parseQueuedReplay(stripAsyncRetryMarker(reference));
		String serialized = encodeAsyncReplayProgress(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints());
		return queuedReference(queuedReplay) + (serialized.isEmpty() ? "" : ASYNC_PROGRESS_DELIMITER + serialized)
				+ "%placeholders%" + ArrayUtils.makeString(checkpoint.getPlaceholders());
	}

	private static int completedAsyncInjections(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof Reward.RewardReplayFailure) {
				return ((Reward.RewardReplayFailure) current).getCompletedInjectionCount();
			}
			current = current.getCause();
		}
		return 0;
	}

	private static Reward.RewardReplayFailure replayFailure(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof Reward.RewardReplayFailure) return (Reward.RewardReplayFailure) current;
		}
		return null;
	}

	private static final class QueuedReplay {
		private final String rewardReference;
		private final int completedAsyncInjections;
		private final Map<String, Integer> asyncReplayProgress;
		private final Map<String, String> asyncReplayRegistryFingerprints;
		private final boolean legacyAsyncReplayCheckpoint;
		private final String asyncReplayOccurrenceId;

		private QueuedReplay(String rewardReference, int completedAsyncInjections,
				Map<String, Integer> asyncReplayProgress, Map<String, String> asyncReplayRegistryFingerprints,
				boolean legacyAsyncReplayCheckpoint, String asyncReplayOccurrenceId) {
			this.rewardReference = rewardReference;
			this.completedAsyncInjections = completedAsyncInjections;
			this.asyncReplayProgress = asyncReplayProgress;
			this.asyncReplayRegistryFingerprints = asyncReplayRegistryFingerprints;
			this.legacyAsyncReplayCheckpoint = legacyAsyncReplayCheckpoint;
			this.asyncReplayOccurrenceId = asyncReplayOccurrenceId;
		}
	}

	/**
	 * Adds an unclaimed choice reward.
	 *
	 * @param name the reward name
	 */
	public void addUnClaimedChoiceReward(String name) {
		ArrayList<String> choices = getUnClaimedChoices();
		choices.add(name);
		setUnClaimedChoice(choices);
	}

	/**
	 * Caches the user data.
	 */
	public void cache() {
		plugin.getUserManager().getDataManager().cacheUser(UUID.fromString(uuid), getPlayerName());
	}

	/**
	 * Caches the user data asynchronously.
	 */
	public void cacheAsync() {
		getPlugin().getBukkitScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				cache();
			}
		});
	}

	/**
	 * Caches the user data if needed.
	 */
	public void cacheIfNeeded() {
		plugin.getUserManager().getDataManager().cacheUserIfNeeded(UUID.fromString(uuid));
	}

	/**
	 * Checks and processes delayed/timed rewards.
	 */
	public void checkDelayedTimedRewards() {
		plugin.debug("Checking timed/delayed for " + getPlayerName());
		HashMap<String, Long> timed = getTimedRewards();
		for (Entry<String, Long> entry : timed.entrySet()) {
			long time = entry.getValue();

			if (time != 0) {
				Date timeDate = new Date(time);
				if (new Date().after(timeDate) && claimTimedReward(entry.getKey(), time)) {
					String[] data = entry.getKey().split("%placeholders%", 2);
					QueuedReplay queuedReplay = parseQueuedReplay(stripTimedExecutionMarker(data[0]));
					String rewardReference = queuedReplay.rewardReference;
					String placeholders = "";
					if (data.length > 1) {
						placeholders = data[1];
					}
					RewardOptions replayOptions = new RewardOptions().setCheckTimed(false)
							.withPlaceHolder(ArrayUtils.fromString(placeholders));
					replayOptions.setCompletedAsyncInjections(queuedReplay.completedAsyncInjections);
					replayOptions.setAsyncReplayProgress(queuedReplay.asyncReplayProgress);
					replayOptions.setAsyncReplayRegistryFingerprints(queuedReplay.asyncReplayRegistryFingerprints);
					replayOptions.setLegacyAsyncReplayCheckpoint(queuedReplay.legacyAsyncReplayCheckpoint);
					replayOptions.setAsyncReplayOccurrenceId(queuedReplay.asyncReplayOccurrenceId);
					replayOptions.addPlaceholder("date",
							"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(new Date(time)));
					// Keep the due entry durable while asynchronous stages are running.  A
					// checkpoint can change its serialized key (for example by adding the
					// v2 progress marker), so completion and failure must follow this
					// reference rather than the original entry key.
					AtomicReference<String> currentEntry = new AtomicReference<>(entry.getKey());
					replayOptions.setAsyncReplayCheckpointConsumer(
							checkpoint -> checkpointTimedReward(currentEntry, time, checkpoint));
					enqueuePersistedReplay(() -> {
						CompletionStage<Void> replay;
						try {
							replay = plugin.getRewardHandler().givePersistedQueueRewardAsync(this,
									new PersistedQueueReference(rewardReference), replayOptions);
							if (replay == null) throw new IllegalStateException("Timed reward replay returned no completion stage");
						} catch (Throwable failure) {
							restoreTimedReward(currentEntry.get(), time, failure);
							return CompletableFuture.completedFuture(null);
						}
						return replay.handle((ignored, failure) -> {
							if (failure == null) completeTimedReward(currentEntry.get(), time);
							else restoreTimedReward(currentEntry.get(), time, failure);
							return null;
						});
					});
					String rewardName = rewardReference;
					plugin.debug("Giving timed/delayed reward " + rewardName + " for " + getPlayerName()
							+ " with placeholders " + ArrayUtils.fromString(placeholders));
				}
			}

		}
	}

	/**
	 * Check offline rewards.
	 */
	public void checkOfflineRewards() {
		if (!plugin.getOptions().isProcessRewards()) {
			plugin.debug("Processing rewards is disabled");
			return;
		}
		if (isCheckWorld()) {
			setCheckWorld(false);
		}
		dispatchOfflineRewards(false);
	}

	private void dispatchOfflineRewards(boolean force) {
		ArrayList<String> rewards = new ArrayList<>(getOfflineRewards());
		for (String rewardEntry : rewards) {
			if (rewardEntry == null || rewardEntry.equals("null")) {
				continue;
			}
			if (!claimOfflineReward(rewardEntry)) continue;

			String[] parts = rewardEntry.split("%placeholders%", 2);
			QueuedReplay queuedReplay = parseQueuedReplay(parts[0]);
			String rewardReference = queuedReplay.rewardReference;
			String placeholderStr = parts.length > 1 ? parts[1] : "";

			RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false)
					.withPlaceHolder(ArrayUtils.fromString(placeholderStr));
			if (force) options.setGiveOffline(false).forceOffline();
			options.setCompletedAsyncInjections(queuedReplay.completedAsyncInjections);
			options.setAsyncReplayProgress(queuedReplay.asyncReplayProgress);
			options.setAsyncReplayRegistryFingerprints(queuedReplay.asyncReplayRegistryFingerprints);
			options.setLegacyAsyncReplayCheckpoint(queuedReplay.legacyAsyncReplayCheckpoint);
			options.setAsyncReplayOccurrenceId(queuedReplay.asyncReplayOccurrenceId);
			AtomicReference<String> currentEntry = new AtomicReference<>(rewardEntry);
			options.setAsyncReplayCheckpointConsumer(checkpoint -> checkpointOfflineReward(currentEntry, checkpoint));

			enqueuePersistedReplay(() -> {
				CompletionStage<Void> replay;
				try {
					replay = plugin.getRewardHandler().givePersistedQueueRewardAsync(this,
							new PersistedQueueReference(rewardReference), options);
					if (replay == null) throw new IllegalStateException("Offline reward replay returned no completion stage");
				} catch (Throwable failure) {
					restoreOfflineReward(currentEntry.get(), failure);
					return CompletableFuture.completedFuture(null);
				}
				return replay.handle((ignored, failure) -> {
					if (failure == null) completeOfflineReward(currentEntry.get());
					else restoreOfflineReward(currentEntry.get(), failure);
					return null;
				});
			});
		}
	}

	/**
	 * Runs persisted offline and timed occurrences one at a time for this plugin
	 * and user. The tail deliberately absorbs a completed occurrence's failure:
	 * its own restore path retains the queue entry, while the next occurrence must
	 * still be allowed to start without blocking a caller thread.
	 */
	private void enqueuePersistedReplay(Supplier<CompletionStage<Void>> replay) {
		ReplayClaims claims;
		CompletableFuture<Void> previous;
		CompletableFuture<Void> next = new CompletableFuture<>();
		synchronized (plugin) {
			claims = replayClaims();
			previous = claims.serialReplayTail;
			claims.serialReplayTail = next;
		}
		previous.whenComplete((ignored, previousFailure) -> {
			CompletionStage<Void> stage;
			try {
				stage = replay.get();
				if (stage == null) throw new IllegalStateException("Persisted reward replay returned no completion stage");
			} catch (Throwable failure) {
				next.complete(null);
				releaseReplayClaimsIfEmpty(claims);
				return;
			}
			stage.whenComplete((result, failure) -> {
				next.complete(null);
				releaseReplayClaimsIfEmpty(claims);
			});
		});
	}

	private void checkpointOfflineReward(AtomicReference<String> currentEntry, Reward.ReplayCheckpoint checkpoint) {
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			String current = currentEntry.get();
			String updated = withAsyncReplayProgress(current, checkpoint);
			ArrayList<String> pending = getOfflineRewards();
			int index = pending.indexOf(current);
			if (index >= 0) {
				pending.set(index, updated);
				int claimed = claims.offline.getOrDefault(current, 0);
				// Migrate this occurrence only. Identical legacy queue entries share a
				// serialized key, but may each already be executing; moving the whole
				// count would make the remaining old entry appear unclaimed.
				if (claimed <= 1) claims.offline.remove(current);
				else claims.offline.put(current, claimed - 1);
				claims.offline.put(updated, claims.offline.getOrDefault(updated, 0) + 1);
				currentEntry.set(updated);
				setOfflineRewardsDurably(pending);
			}
		}
	}

	private boolean claimOfflineReward(String rewardEntry) {
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			int occurrences = 0;
			for (String pending : getOfflineRewards()) if (rewardEntry.equals(pending)) occurrences++;
			int claimed = claims.offline.getOrDefault(rewardEntry, 0);
			if (claimed >= occurrences) return false;
			claims.offline.put(rewardEntry, claimed + 1);
			return true;
		}
	}

	private void completeOfflineReward(String rewardEntry) {
		synchronized (plugin) {
			ArrayList<String> pending = getOfflineRewards();
			pending.remove(rewardEntry);
			setOfflineRewards(pending);
			releaseOfflineReward(rewardEntry);
		}
	}

	/** Retains and updates a queue entry only after an asynchronous replay fails. */
	private void restoreOfflineReward(String rewardEntry, Throwable failure) {
		// Match addOfflineRewards' lock so a newly queued reward cannot be lost
		// while a failed replay is being restored.
		synchronized (plugin) {
			ArrayList<String> pending = getOfflineRewards();
			int index = pending.indexOf(rewardEntry);
			if (index >= 0) pending.set(index, withAsyncReplayProgress(rewardEntry, failure));
			releaseOfflineReward(rewardEntry);
			setOfflineRewards(pending);
		}
		plugin.getLogger().warning("Could not deliver queued offline reward for " + getPlayerName()
				+ "; it will be retried: " + failure.getMessage());
	}

	private void releaseOfflineReward(String rewardEntry) {
		ReplayClaims claims = replayClaims();
		int claimed = claims.offline.getOrDefault(rewardEntry, 0);
		if (claimed <= 1) claims.offline.remove(rewardEntry);
		else claims.offline.put(rewardEntry, claimed - 1);
		releaseReplayClaimsIfEmpty(claims);
	}

	private boolean claimTimedReward(String rewardEntry, long time) {
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			if (claims.timed.contains(rewardEntry) || !Long.valueOf(time).equals(getTimedRewards().get(rewardEntry))) {
				return false;
			}
			claims.timed.add(rewardEntry);
			return true;
		}
	}

	private void completeTimedReward(String rewardEntry, long time) {
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			HashMap<String, Long> pending = getTimedRewards();
			if (Long.valueOf(time).equals(pending.get(rewardEntry))) {
				pending.remove(rewardEntry);
				setTimedRewards(pending);
			}
			claims.timed.remove(rewardEntry);
			releaseReplayClaimsIfEmpty(claims);
		}
	}

	/**
	 * Persists every completed asynchronous stage before the next one can run.
	 * Timed reward keys are map keys, so migrate the exact current key while
	 * retaining its execution time and its in-flight claim.
	 */
	private void checkpointTimedReward(AtomicReference<String> currentEntry, long time,
			Reward.ReplayCheckpoint checkpoint) {
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			String current = currentEntry.get();
			String updated = withAsyncReplayProgress(current, checkpoint);
			if (current.equals(updated)) return;
			HashMap<String, Long> pending = getTimedRewards();
			if (!Long.valueOf(time).equals(pending.get(current))) return;
			pending.remove(current);
			pending.put(updated, time);
			setTimedRewardsDurably(pending);
			claims.timed.remove(current);
			claims.timed.add(updated);
			currentEntry.set(updated);
		}
	}

	/** Restores a due timed entry after an asynchronous replay fails. */
	private void restoreTimedReward(String rewardEntry, long time, Throwable failure) {
		long retryTime;
		synchronized (plugin) {
			ReplayClaims claims = replayClaims();
			HashMap<String, Long> pending = getTimedRewards();
			int retry = Math.min(8, asyncRetryCount(rewardEntry) + 1);
			long retryDelay = Math.min(TimeUnit.MINUTES.toMillis(5), TimeUnit.SECONDS.toMillis(1L << retry));
			retryTime = System.currentTimeMillis() + retryDelay;
			pending.remove(rewardEntry);
			pending.put(withAsyncRetryCount(withAsyncReplayProgress(rewardEntry, failure), retry), retryTime);
			setTimedRewards(pending);
			claims.timed.remove(rewardEntry);
			releaseReplayClaimsIfEmpty(claims);
		}
		// A due entry restored after its original timer fired needs its own retry
		// timer; waiting for reconnect would strand it indefinitely.
		loadTimedDelayedTimer(retryTime);
		plugin.getLogger().warning("Could not deliver queued timed reward for " + getPlayerName()
				+ "; it will be retried: " + failure.getMessage());
	}

	private ReplayClaims replayClaims() {
		synchronized (REPLAY_CLAIMS_LOCK) {
			return REPLAY_CLAIMS.computeIfAbsent(plugin, ignored -> new HashMap<>())
					.computeIfAbsent(getUUID(), ignored -> new ReplayClaims());
		}
	}

	private void releaseReplayClaimsIfEmpty(ReplayClaims claims) {
		synchronized (plugin) {
			synchronized (REPLAY_CLAIMS_LOCK) {
				// Claim mutations use the same lock ordering (plugin, then this
				// lock). Recheck only while holding both locks; checking before
				// entering them permits a concurrent wrapper to add a claim that
				// cleanup then drops.
				if (!claims.offline.isEmpty() || !claims.timed.isEmpty()
						|| !claims.serialReplayTail.isDone()) return;
				HashMap<String, ReplayClaims> byUser = REPLAY_CLAIMS.get(plugin);
				if (byUser == null || byUser.get(getUUID()) != claims) return;
				byUser.remove(getUUID());
				if (byUser.isEmpty()) REPLAY_CLAIMS.remove(plugin);
			}
		}
	}

	/**
	 * Clears the user cache.
	 */
	public void clearCache() {
		if (isCached()) {
			getCache().clearCache();
		}
	}

	/**
	 * Clears the temporary cache.
	 */
	public void clearTempCache() {
		getUserData().clearTempCache();
	}

	/**
	 * Closes the player's inventory.
	 */
	public void closeInv() {
		if (plugin.isEnabled()) {
			getPlugin().getBukkitScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					Player player = getPlayer();
					if (player != null) {
						player.closeInventory();
					}
				}
			}, getPlayer());
		}
	}

	/**
	 * Sets the user data fetch mode.
	 *
	 * @param mode the fetch mode
	 * @return this user instance
	 */
	public AdvancedCoreUser userDataFetechMode(UserDataFetchMode mode) {
		this.userDataFetchMode = mode;
		return this;
	}

	/**
	 * Forces running of offline rewards without processing checks.
	 */
	public void forceRunOfflineRewards() {
		if (!plugin.getOptions().isProcessRewards()) {
			plugin.debug("Processing rewards is disabled");
			return;
		}

		setCheckWorld(false);
		dispatchOfflineRewards(true);
	}

	/**
	 * Gets the user data cache.
	 *
	 * @return the cache
	 */
	public UserDataCache getCache() {
		return plugin.getUserManager().getDataManager().getCache(java.util.UUID.fromString(getUUID()));
	}

	/**
	 * Gets the user's choice preference for a reward.
	 *
	 * @param rewardName the reward name
	 * @return the choice preference
	 */
	public String getChoicePreference(String rewardName) {
		ArrayList<String> data = getChoicePreferenceData();

		for (String str : data) {
			String[] data1 = str.split(":");
			if (data1.length > 1) {
				if (data1[0].equals(rewardName)) {
					return data1[1];
				}
			}
		}
		return "";
	}

	/**
	 * Gets the choice preference data.
	 *
	 * @return the choice preference data list
	 */
	public ArrayList<String> getChoicePreferenceData() {
		return getData().getStringList("ChoicePreference", userDataFetchMode);
	}

	/**
	 * Gets the user data.
	 *
	 * @return the user data
	 */
	public UserData getData() {
		if (data == null) {
			loadData();
		}
		return data;
	}

	/**
	 * Gets the input method.
	 *
	 * @return the input method string
	 */
	public String getInputMethod() {
		return getUserData().getString("InputMethod", userDataFetchMode);
	}

	/**
	 * Gets the Java UUID object.
	 *
	 * @return the Java UUID
	 */
	public UUID getJavaUUID() {
		return UUID.fromString(uuid);
	}

	/**
	 * Gets the last online time in milliseconds.
	 *
	 * @return the last online time
	 */
	public long getLastOnline() {
		String d = getData().getString("LastOnline", userDataFetchMode);
		long time = 0;
		if (d != null && !d.equals("") && !d.equals("null")) {
			time = Long.valueOf(d);
		}
		if (time == 0 && getPlugin().getOptions().isOnlineMode()) {
			OfflinePlayer player = getOfflinePlayer();
			if (player != null) {
				time = player.getLastPlayed();
				if (time > 0) {
					setLastOnline(time);
				}
			}

		}
		return time;
	}

	/**
	 * Gets the number of days since the player's last login.
	 *
	 * @return the number of days since login
	 */
	public int getNumberOfDaysSinceLogin() {
		long time = getLastOnline();
		if (time > 0) {
			LocalDateTime online = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
			LocalDateTime now = LocalDateTime.now();
			Duration dur = Duration.between(online, now);
			return (int) dur.toDays();
		}

		return -1;
	}

	/**
	 * Gets the offline player.
	 *
	 * @return the offline player
	 */
	@SuppressWarnings("deprecation")
	public OfflinePlayer getOfflinePlayer() {
		if (getPlayerName().isBlank()) {
			return null;
		}
		if (!plugin.getOptions().isOnlineMode()) {
			return Bukkit.getOfflinePlayer(getPlayerName());
		}
		if (uuid != null && !uuid.equals("")) {
			return Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
		}
		return null;
	}

	/**
	 * Gets the offline rewards list.
	 *
	 * @return the offline rewards
	 */
	public ArrayList<String> getOfflineRewards() {
		return getUserData().getStringList(plugin.getUserManager().getOfflineRewardsPath(), userDataFetchMode);
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public Player getPlayer() {
		if (!plugin.getOptions().isOnlineMode()) {
			return Bukkit.getPlayer(getPlayerName());
		}
		if (uuid != null && !uuid.isEmpty()) {
			return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
		}
		return null;
	}

	/**
	 * Gets the player's head as an ItemStack.
	 *
	 * @return the player head
	 */
	public ItemStack getPlayerHead() {
		return PlayerManager.getInstance().getPlayerSkull(getJavaUUID(), getPlayerName(), false);
	}

	/**
	 * Gets the player name.
	 *
	 * @return the player name
	 */
	public String getPlayerName() {
		if (playerName != null) {
			return playerName;
		}
		if (userDataFetchMode.allowTempCache()) {
			return getUserData().getString("PlayerName", UserDataFetchMode.TEMP_ONLY);
		}
		return "";
	}

	/**
	 * Gets the repeat amount for a reward.
	 *
	 * @param reward the reward
	 * @return the repeat amount
	 */
	public int getRepeatAmount(Reward reward) {
		return getData().getInt("Repeat" + reward.getName(), userDataFetchMode);
	}

	/**
	 * Gets the timed rewards map.
	 *
	 * @return the timed rewards
	 */
	public HashMap<String, Long> getTimedRewards() {
		ArrayList<String> timedReward = getUserData().getStringList("TimedRewards", userDataFetchMode);
		HashMap<String, Long> timedRewards = new HashMap<>();
		for (String str : timedReward) {
			if (str != null && !str.equals("null")) {
				String[] data = str.split("%ExecutionTime/%");
				plugin.extraDebug("TimedReward: " + str);
				if (data.length > 1) {
					String name = data[0];

					String timeStr = data[1];
					timedRewards.put(name, Long.valueOf(timeStr));
				}
			}
		}
		return timedRewards;
	}

	/**
	 * Gets the unclaimed choices list.
	 *
	 * @return the unclaimed choices
	 */
	public ArrayList<String> getUnClaimedChoices() {
		return getData().getStringList("UnClaimedChoices", userDataFetchMode);
	}

	/**
	 * Gets the user data instance.
	 *
	 * @return the user data
	 */
	public UserData getUserData() {
		if (data == null) {
			loadData();
		}
		return data;
	}

	/**
	 * Gets the uuid.
	 *
	 * @return the uuid
	 */
	public String getUUID() {
		return uuid;
	}

	/**
	 * Give exp.
	 *
	 * @param exp the exp
	 */
	public void giveExp(int exp) {
		Player player = getPlayer();
		if (player != null) {
			player.giveExp(exp);
		}
	}

	/**
	 * Gives experience levels to the player.
	 *
	 * @param num the number of levels to give
	 */
	public void giveExpLevels(int num) {
		Player p = getPlayer();
		if (p != null) {
			p.setLevel(p.getLevel() + num);
		}
	}

	/**
	 * Gives an item to the player from an ItemBuilder.
	 *
	 * @param builder the item builder
	 */
	public void giveItem(ItemBuilder builder) {
		giveItem(builder.toItemStack(getPlayer()));
	}

	/**
	 * Give item.
	 *
	 * @param item the item
	 */
	public void giveItem(ItemStack item) {
		if ((item == null) || (item.getAmount() == 0)) {
			return;
		}

		final Player player = getPlayer();

		if (plugin.isEnabled()) {
			scheduleLegacyRewardAction(() -> {
				if (player != null) plugin.getFullInventoryHandler().giveItem(player, item);
			}, player, true);
		} else {
			collectAsyncFailure(new IllegalStateException("Plugin disabled before item reward was scheduled"));
		}

	}

	/**
	 * Gives an item to the player with placeholders.
	 *
	 * @param itemStack    the item stack
	 * @param placeholders the placeholders
	 */
	public void giveItem(ItemStack itemStack, HashMap<String, String> placeholders) {
		giveItem(new ItemBuilder(itemStack).setPlaceholders(placeholders).toItemStack(getPlayer()));
	}

	/**
	 * Gives multiple items to the player.
	 *
	 * @param item the items to give
	 */
	public void giveItems(ItemStack... item) {
		if (item == null) {
			return;
		}

		final Player player = getPlayer();

		if (plugin.isEnabled()) {
			scheduleLegacyRewardAction(() -> {
				if (player != null) plugin.getFullInventoryHandler().giveItem(player, item);
			}, player, true);
		} else {
			collectAsyncFailure(new IllegalStateException("Plugin disabled before item reward was scheduled"));
		}

	}

	/**
	 * Give user money, needs vault installed
	 *
	 * @param m Amount of money to give
	 */
	public void giveMoney(double m) {
		if (!plugin.isEnabled()) {
			collectAsyncFailure(new IllegalStateException("Plugin disabled before money reward was scheduled"));
			return;
		}
		if (plugin.getVaultHandler() != null && plugin.getVaultHandler().getEcon() != null) {
			try {
				if (m > 0) {
					final double money = m;
					scheduleLegacyRewardAction(
							() -> plugin.getVaultHandler().getEcon().depositPlayer(getOfflinePlayer(), money), null, false);

				} else if (m < 0) {
					m = m * -1;
					final double money = m;
					scheduleLegacyRewardAction(
							() -> plugin.getVaultHandler().getEcon().withdrawPlayer(getOfflinePlayer(), money), null, false);

				}
			} catch (

			IllegalStateException e) {
				e.printStackTrace();
				collectAsyncFailure(e);
			}
		}
	}

	/**
	 * Give money.
	 *
	 * @param money the money
	 */
	public void giveMoney(int money) {
		giveMoney((double) money);
	}

	/**
	 * Give potion effect.
	 *
	 * @param potionName the potion name
	 * @param duration   the duration
	 * @param amplifier  the amplifier
	 */
	public void givePotionEffect(String potionName, int duration, int amplifier) {
		Player player = getPlayer();
		if (player != null && plugin.isEnabled()) {
			scheduleLegacyRewardAction(() -> player.addPotionEffect(
					new PotionEffect(PotionEffectType.getByName(potionName), 20 * duration, amplifier)), player, true);
		} else if (player != null && ASYNC_ACTION_COLLECTION.get() != null) {
			collectAsyncFailure(new IllegalStateException(
					"Potion reward could not be scheduled because the plugin is unavailable"));
		}
	}

	/** Queues a legacy Bukkit action while exposing a nonblocking completion internally. */
	private void scheduleLegacyRewardAction(Runnable action, Player player, boolean playerAware) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		if (!collectAsyncAction(completion)) {
			if (playerAware) getPlugin().getBukkitScheduler().runTask(plugin, action, player);
			else getPlugin().getBukkitScheduler().runTask(plugin, action);
			return;
		}
		AtomicBoolean claimed = new AtomicBoolean();
		Runnable dispatch = () -> {
			if (!claimed.compareAndSet(false, true)) return;
			if (!plugin.isEnabled()) {
				completion.completeExceptionally(new IllegalStateException("Plugin disabled before scheduled reward action ran"));
				return;
			}
			try {
				action.run();
				completion.complete(null);
			} catch (Throwable failure) {
				completion.completeExceptionally(failure);
			}
		};
		try {
			if (playerAware) getPlugin().getBukkitScheduler().runTask(plugin, dispatch, player);
			else getPlugin().getBukkitScheduler().runTask(plugin, dispatch);
		} catch (Throwable failure) {
			claimed.set(true);
			completion.completeExceptionally(failure);
		}
		CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
			if (claimed.compareAndSet(false, true)) {
				completion.completeExceptionally(new TimeoutException("Timed out waiting for scheduled reward action"));
			}
		});
	}

	/**
	 * Gives a reward from a configuration file.
	 *
	 * @param data          the configuration data
	 * @param path          the path to the reward
	 * @param rewardOptions the reward options
	 */
	public void giveReward(FileConfiguration data, String path, RewardOptions rewardOptions) {
		plugin.getRewardHandler().giveReward(this, data, path, rewardOptions);
	}

	/**
	 * Gives a reward to the user.
	 *
	 * @param reward        the reward
	 * @param rewardOptions the reward options
	 */
	public void giveReward(Reward reward, RewardOptions rewardOptions) {
		reward.giveReward(this, rewardOptions);
	}

	/**
	 * Checks if the user has unclaimed choices.
	 *
	 * @return true if the user has choices
	 */
	public boolean hasChoices() {
		return getUnClaimedChoices().size() > 0;
	}

	/**
	 * Check if player joined before
	 *
	 * @return true, if successful
	 */
	public boolean hasLoggedOnBefore() {
		OfflinePlayer player = Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
		if (player != null) {
			if (player.hasPlayedBefore() || player.isOnline()) {
				return true;
			}

		}
		ArrayList<String> uuids = plugin.getUserManager().getAllUUIDs();
		if (uuids.contains(getUUID())) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if the user has a permission.
	 *
	 * @param perm the permission
	 * @return true if the user has the permission
	 */
	public boolean hasPermission(String perm) {
		return hasPermission(perm, true);
	}

	/**
	 * Checks if the user has a permission.
	 *
	 * @param perm         the permission
	 * @param offlineCheck whether to check offline permissions
	 * @return true if the user has the permission
	 */
	public boolean hasPermission(String perm, boolean offlineCheck) {
		boolean negate = perm != null && perm.startsWith("!");
		if (negate) {
			perm = perm.substring(1);
		}

		Player player = getPlayer();

		// Online fast path
		if (player != null) {
			boolean has = player.hasPermission(perm);
			return negate ? !has : has;
		}

		if (!offlineCheck) {
			plugin.debug("Unable to get player for permission check for " + getPlayerName() + "/" + getUUID()
					+ " (offlineCheck is false)");
			return false;
		}
		// Offline path: LuckPerms (if available)
		if (plugin.getLuckPermsHandle() != null && plugin.getLuckPermsHandle().luckpermsApiLoaded()) {
			boolean has = plugin.getLuckPermsHandle().hasPermission(getJavaUUID(), perm);
			return negate ? !has : has;
		}

		plugin.debug("Unable to get player for permission check for " + getPlayerName() + "/" + getUUID()
				+ " (offline and no LuckPerms hook)");
		return false;
	}

	/**
	 * Checks if the player is banned.
	 *
	 * @return true if the player is banned
	 */
	public boolean isBanned() {
		if (plugin.getBannedPlayers().contains(getUUID())) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if the player is a Bedrock user.
	 *
	 * @return true if the player is a Bedrock user
	 */
	public boolean isBedrockUser() {
		return getData().getBoolean("isBedrock", userDataFetchMode);
	}

	/**
	 * Sets whether the player is a Bedrock user.
	 *
	 * @param isBedrock true if the player is a Bedrock user
	 */
	public void setBedrockUser(boolean isBedrock) {
		getData().setBoolean("isBedrock", isBedrock);
	}

	/**
	 * Checks if the user data is cached.
	 *
	 * @return true if cached
	 */
	public boolean isCached() {
		return plugin.getUserManager().getDataManager().isCached(UUID.fromString(uuid));
	}

	/**
	 * Checks if world checking is enabled for the user.
	 *
	 * @return true if world checking is enabled
	 */
	public boolean isCheckWorld() {
		if (!plugin.isLoadUserData()) {
			return false;
		}
		return Boolean.valueOf(getData().getString("CheckWorld", userDataFetchMode));
	}

	/**
	 * Checks if the player is in any of the specified worlds.
	 *
	 * @param worlds the list of world names
	 * @return true if the player is in one of the worlds
	 */
	public boolean isInWorld(ArrayList<String> worlds) {
		Player p = getPlayer();
		if (p != null) {
			for (String world : worlds) {
				if (p.getWorld().getName().equalsIgnoreCase(world)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Checks if the player is in a specific world.
	 *
	 * @param world the world name
	 * @return true if the player is in the world
	 */
	public boolean isInWorld(String world) {
		Player p = getPlayer();
		if (p != null) {
			return p.getWorld().getName().equalsIgnoreCase(world);
		}

		return false;
	}

	/**
	 * Checks if is online.
	 *
	 * @return true, if is online
	 */
	public boolean isOnline() {
		boolean online = PlayerUtils.isPlayerOnline(getPlayerName());
		if (!online) {
			return false;
		}
		if (plugin.getOptions().isTreatVanishAsOffline()) {
			if (isVanished()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the player is vanished.
	 *
	 * @return true if the player is vanished
	 */
	public boolean isVanished() {
		Player player = getPlayer();
		if (player != null) {
			for (MetadataValue meta : player.getMetadata("vanished")) {
				if (meta.asBoolean()) {
					return true;
				}
			}

			try {
				try {
					if (plugin.getCmiHandle() != null) {
						return plugin.getCmiHandle().isVanished(player);
					}
				} catch (Exception e) {
				}
			} catch (Exception e) {
				plugin.debug(e);
			}
		}
		return false;
	}

	/**
	 * Loads the user cache.
	 */
	public void loadCache() {
		plugin.getUserManager().getDataManager().cacheUser(UUID.fromString(uuid), getPlayerName());
	}

	/**
	 * Loads the user data.
	 */
	public void loadData() {
		data = new UserData(this);
	}

	/**
	 * Loads a timer for delayed/timed rewards.
	 *
	 * @param time the time in milliseconds
	 */
	public void loadTimedDelayedTimer(long time) {
		long delay = time - System.currentTimeMillis();
		if (delay < 0) {
			delay = 0;
		}
		delay += 500;
		plugin.getRewardHandler().getDelayedTimer().schedule(new Runnable() {

			@Override
			public void run() {
				checkDelayedTimedRewards();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	/**
	 * Play particle effect.
	 *
	 * @param effectName the effect name
	 * @param data       the data
	 * @param particles  the particles
	 * @param radius     the radius
	 */
	public void playEffect(String effectName, int data, int particles, int radius) {
		Player player = getPlayer();
		if ((player != null) && (effectName != null)) {
			try {
				Effect effect = Effect.valueOf(effectName);
				for (int i = 0; i < particles; i++) {
					player.getWorld().playEffect(player.getLocation(), effect, data, radius);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Plays a particle effect for the player.
	 *
	 * @param effectName the particle effect name
	 * @param data       the data value
	 * @param particles  the number of particles
	 * @param radius     the radius
	 */
	public void playParticle(String effectName, int data, int particles, int radius) {
		Player player = getPlayer();
		if ((player != null) && (effectName != null)) {
			try {
				Particle effect = Particle.valueOf(effectName);
				for (int i = 0; i < particles; i++) {
					player.getWorld().spawnParticle(effect, player.getLocation(), particles, radius, radius, radius,
							data);
				}

			} catch (Exception e) {
				plugin.getLogger().warning(
						"Failed to create particle: " + effectName + ", " + data + ", " + particles + ", " + radius);
				e.printStackTrace();
			}
		}
	}

	/**
	 * Plays a particle effect for the player.
	 *
	 * @param effectName the particle effect name
	 * @param data       the data value
	 * @param particles  the number of particles
	 * @param radius     the radius
	 */
	@Deprecated
	public void playParticleEffect(String effectName, int data, int particles, int radius) {
		playParticle(effectName, data, particles, radius);
	}

	/**
	 * Play sound.
	 *
	 * @param soundName the sound name
	 * @param volume    the volume
	 * @param pitch     the pitch
	 */
	public void playSound(String soundName, float volume, float pitch) {
		Player player = getPlayer();
		if (player != null) {
			Sound sound = null;
			try {
				sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundName));
			} catch (Exception e) {
				plugin.debug(e);
			}
			if (sound != null) {
				player.playSound(player.getLocation(), sound, volume, pitch);
			} else {
				plugin.debug("Invalid sound: " + soundName);
			}
		}
	}

	/**
	 * Performs commands as the player with placeholders.
	 *
	 * @param commands     the list of commands
	 * @param placeholders the placeholders
	 */
	public void preformCommand(ArrayList<String> commands, HashMap<String, String> placeholders) {
		if (commands != null && !commands.isEmpty()) {
			final ArrayList<String> cmds = PlaceholderUtils.replaceJavascript(getPlayer(),
					PlaceholderUtils.replacePlaceHolder(commands, placeholders));

			final Player player = getPlayer();
			if (player != null && plugin.isEnabled()) {
				for (final String cmd : cmds) {
					plugin.debug("Executing player command for " + getPlayerName() + ": " + cmd);
					getPlugin().getBukkitScheduler().runTask(plugin, new Runnable() {

						@Override
						public void run() {
							player.chat("/" + cmd);
						}
					});
				}
			}
		}
	}

	/**
	 * Completion-aware player-command dispatch used by durable asynchronous reward
	 * replay. It completes only after each queued player command has run.
	 */
	public CompletionStage<Void> preformCommandAsync(ArrayList<String> commands, HashMap<String, String> placeholders) {
		if (commands == null || commands.isEmpty()) return CompletableFuture.completedFuture(null);
		try {
			final ArrayList<String> cmds = PlaceholderUtils.replaceJavascript(getPlayer(),
					PlaceholderUtils.replacePlaceHolder(commands, placeholders));
			final Player player = getPlayer();
			if (player == null || !plugin.isEnabled()) {
				return CompletableFuture.failedFuture(
						new IllegalStateException("Player command could not run because the player or plugin is unavailable"));
			}
			return Reward.replayCommandSequence(plugin, placeholders, "player", commands, cmds, (command, ignoredIndex) -> {
				plugin.debug("Executing player command for " + getPlayerName() + ": " + command);
				return runPlayerCommandAsync(player, command);
			});
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}

	private CompletableFuture<Void> runPlayerCommandAsync(Player player, String command) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		AtomicBoolean claimed = new AtomicBoolean();
		Runnable dispatch = () -> {
			if (!claimed.compareAndSet(false, true)) return;
			try {
				player.chat("/" + command);
				completion.complete(null);
			} catch (Throwable failure) {
				completion.completeExceptionally(failure);
			}
		};
		try {
			getPlugin().getBukkitScheduler().runTask(plugin, dispatch);
		} catch (Throwable failure) {
			claimed.set(true);
			completion.completeExceptionally(failure);
			return completion;
		}
		CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
			if (claimed.compareAndSet(false, true)) {
				completion.completeExceptionally(new TimeoutException("Timed out waiting for scheduled player command"));
			}
		});
		return completion;
	}

	/**
	 * Performs a command as the player with placeholders.
	 *
	 * @param command      the command
	 * @param placeholders the placeholders
	 */
	public void preformCommand(String command, HashMap<String, String> placeholders) {
		if (command != null && !command.isEmpty()) {
			final String cmd = PlaceholderUtils.replaceJavascript(getPlayer(),
					PlaceholderUtils.replacePlaceHolder(command, placeholders));
			plugin.debug("Executing player command for " + getPlayerName() + ": " + command);
			if (plugin.isEnabled()) {
				getPlugin().getBukkitScheduler().runTask(plugin, new Runnable() {

					@Override
					public void run() {
						Player player = getPlayer();
						if (player != null) {
							player.chat("/" + cmd);
						}
					}
				});
			}
		}
	}

	/**
	 * Removes the user from storage.
	 */
	public void remove() {
		plugin.debug("Removing " + getUUID() + " (" + getPlayerName() + ") from storage...");
		getData().remove();
	}

	/**
	 * Removes a permission from the player.
	 *
	 * @param permission the permission
	 */
	public void removePermission(String permission) {
		plugin.getPermissionHandler().removePermission(UUID.fromString(getUUID()), getPlayerName(), permission);
	}

	/**
	 * Removes an unclaimed choice reward.
	 *
	 * @param name the reward name
	 */
	public void removeUnClaimedChoiceReward(String name) {
		ArrayList<String> choices = getUnClaimedChoices();
		choices.remove(name);
		setUnClaimedChoice(choices);
	}

	/**
	 * Send action bar.
	 *
	 * @param msg   the msg
	 * @param delay the delay
	 */
	public void sendActionBar(String msg, int delay) {
		// plugin.debug("attempting to send action bar");
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {

				try {
					ActionBar actionBar = new ActionBar(PlaceholderUtils.replaceJavascript(getPlayer(), msg), delay);
					actionBar.send(player);
				} catch (Exception ex) {
					plugin.debug("Failed to send ActionBar, turn debug on to see stack trace");
					plugin.debug(ex);
				}
			}
		}
	}

	/**
	 * Send boss bar.
	 *
	 * @param msg      the msg
	 * @param color    the color
	 * @param style    the style
	 * @param progress the progress
	 * @param delay    the delay
	 */
	public void sendBossBar(String msg, String color, String style, double progress, int delay) {
		if (msg != null && msg != "") {
			Player player = getPlayer();
			if (player != null) {
				try {
					BossBar bossBar = new BossBar(PlaceholderUtils.replaceJavascript(getPlayer(), msg), color, style,
							progress);
					bossBar.send(player, delay);
				} catch (Exception ex) {
					plugin.debug("Failed to send BossBar");
					plugin.debug(ex);
				}
			}
		}
	}

	/**
	 * Send json.
	 *
	 * @param messages the messages
	 */
	public void sendJson(ArrayList<TextComponent> messages) {
		sendJson(messages, true);
	}

	/**
	 * Sends JSON messages to the player.
	 *
	 * @param messages   the text component messages
	 * @param javascript whether to process javascript placeholders
	 */
	public void sendJson(ArrayList<TextComponent> messages, boolean javascript) {
		Player player = getPlayer();
		if ((player != null) && (messages != null)) {
			ArrayList<BaseComponent> texts = new ArrayList<>();
			TextComponent newLine = new TextComponent(ComponentSerializer.parse("{text: \"\n\"}"));
			for (int i = 0; i < messages.size(); i++) {
				TextComponent txt = messages.get(i);
				if (javascript) {
					txt.setText(PlaceholderUtils.replaceJavascript(getPlayer(), txt.getText()));
				}
				texts.add(txt);
				if (i + 1 < messages.size()) {
					texts.add(newLine);
				}

			}

			PlayerUtils.getServerHandle().sendMessage(player, ArrayUtils.convertBaseComponent(texts));
		}

	}

	/**
	 * Send json.
	 *
	 * @param message the message
	 */
	public void sendJson(TextComponent message) {
		Player player = getPlayer();
		if ((player != null) && (message != null)) {
			message.setText(PlaceholderUtils.replaceJavascript(getPlayer(), message.getText()));
			PlayerUtils.getServerHandle().sendMessage(player, message);
		}
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(ArrayList<String> msg) {
		sendMessage(ArrayUtils.convert(msg));
	}

	/**
	 * Sends a message with placeholders to the player.
	 *
	 * @param msg          the message list
	 * @param placeholders the placeholders
	 */
	public void sendMessage(ArrayList<String> msg, HashMap<String, String> placeholders) {
		sendMessage(ArrayUtils.convert(PlaceholderUtils.replacePlaceHolder(msg, placeholders)));
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(String msg) {
		Player player = getPlayer();
		if ((player != null) && (msg != null)) {
			if (!msg.equals("")) {
				for (String str : msg.split("%NewLine%")) {
					PlayerUtils.getServerHandle().sendMessage(player,
							PlaceholderUtils.parseJson(PlaceholderUtils.parseText(player, str)));
				}
			}
		}
	}

	/**
	 * Sends a message with placeholders to the player.
	 *
	 * @param msg          the message
	 * @param placeholders the placeholders
	 */
	public void sendMessage(String msg, HashMap<String, String> placeholders) {
		sendMessage(PlaceholderUtils.replacePlaceHolder(msg, placeholders));
	}

	/**
	 * Sends a message with a single placeholder replacement to the player.
	 *
	 * @param msg       the message
	 * @param toReplace the placeholder to replace
	 * @param replace   the replacement value
	 */
	public void sendMessage(String msg, String toReplace, String replace) {
		sendMessage(PlaceholderUtils.replacePlaceHolder(msg, toReplace, replace));
	}

	/**
	 * Send message.
	 *
	 * @param msg the msg
	 */
	public void sendMessage(String[] msg) {
		Player player = getPlayer();
		if ((player != null) && (msg != null)) {

			ArrayList<TextComponent> texts = new ArrayList<>();
			for (String str : msg) {
				if ((player != null) && (msg != null)) {
					if (!str.equals("")) {
						for (String str1 : str.split("%NewLine%")) {
							TextComponent text = PlaceholderUtils.parseJson(PlaceholderUtils.parseText(player, str1));
							text.setText(PlaceholderUtils.replaceJavascript(getPlayer(), text.getText()));
							texts.add(text);
						}
					}

				}
			}
			if (texts.size() > 0) {
				sendJson(texts, false);
			}
		}

	}

	/**
	 * Send title.
	 *
	 * @param title    the title
	 * @param subTitle the sub title
	 * @param fadeIn   the fade in
	 * @param showTime the show time
	 * @param fadeOut  the fade out
	 */
	public void sendTitle(String title, String subTitle, int fadeIn, int showTime, int fadeOut) {
		Player player = getPlayer();
		if (player != null) {
			try {
				Title titleObject = new Title(PlaceholderUtils.replaceJavascript(getPlayer(), title),
						PlaceholderUtils.replaceJavascript(getPlayer(), subTitle), fadeIn, showTime, fadeOut);
				titleObject.send(player);
			} catch (Exception ex) {
				plugin.getLogger().info("Failed to send Title, turn debug on to see stack trace");
				plugin.debug(ex);
			}
		}
	}

	/**
	 * Sets whether to check the world for the user.
	 *
	 * @param b true to enable world checking
	 */
	public void setCheckWorld(boolean b) {
		getData().setString("CheckWorld", "" + b);
	}

	/**
	 * Sets the user's choice preference for a reward.
	 *
	 * @param reward     the reward name
	 * @param preference the preference
	 */
	public void setChoicePreference(String reward, String preference) {
		ArrayList<String> data = getChoicePreferenceData();
		ArrayList<String> choices = new ArrayList<>();

		boolean added = false;
		for (String str : data) {
			String[] data1 = str.split(":");
			if (data1.length > 1) {
				if (data1[0].equals(reward)) {
					choices.add(reward + ":" + preference);
					added = true;
				} else {
					choices.add(str);
				}
			}
		}
		if (!added) {
			choices.add(reward + ":" + preference);
		}
		getData().setStringList("ChoicePreference", choices);
	}

	/**
	 * Sets the input method for the user.
	 *
	 * @param inputMethod the input method
	 */
	public void setInputMethod(String inputMethod) {
		data.setString("InputMethod", inputMethod);
	}

	/**
	 * Sets the last online time for the user.
	 *
	 * @param online the last online time in milliseconds
	 */
	public void setLastOnline(long online) {
		getData().setString("LastOnline", "" + online);
	}

	/**
	 * Sets the offline rewards list.
	 *
	 * @param offlineRewards the offline rewards
	 */
	public void setOfflineRewards(ArrayList<String> offlineRewards) {
		setOfflineRewards(offlineRewards, true);
	}

	/** Writes an async replay checkpoint before the next stage can execute. */
	private void setOfflineRewardsDurably(ArrayList<String> offlineRewards) {
		persistReplayCheckpoint(() -> setOfflineRewards(offlineRewards, false));
	}

	private void setOfflineRewards(ArrayList<String> offlineRewards, boolean queue) {
		// MySQL TEXT max length is 65535 bytes
		int maxLength = 65535;
		String str = String.join("%line%", offlineRewards);

		// Remove oldest rewards until within limit
		while (str.getBytes().length > maxLength && !offlineRewards.isEmpty()) {
			offlineRewards.remove(0);
			str = String.join("%line%", offlineRewards);
		}

		if (queue) data.setStringList(plugin.getUserManager().getOfflineRewardsPath(), offlineRewards);
		else data.setStringList(plugin.getUserManager().getOfflineRewardsPath(), offlineRewards, false);
	}

	/**
	 * Sets the player name.
	 *
	 * @param playerName the player name
	 */
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	/**
	 * Sets the repeat amount for a reward.
	 *
	 * @param reward the reward
	 * @param amount the repeat amount
	 */
	public void setRepeatAmount(Reward reward, int amount) {
		getData().setInt("Repeat" + reward.getName(), amount);
	}

	/**
	 * Sets the timed rewards map.
	 *
	 * @param timed the timed rewards
	 */
	public void setTimedRewards(HashMap<String, Long> timed) {
		synchronized (plugin) {
			setTimedRewards(timed, true);
		}
	}

	/** Writes an async replay checkpoint before the next stage can execute. */
	private void setTimedRewardsDurably(HashMap<String, Long> timed) {
		persistReplayCheckpoint(() -> setTimedRewards(timed, false));
	}

	/**
	 * A cached user may already have an older queued value for this key. Drain it
	 * before the direct write so the delayed cache flush cannot replace a replay
	 * checkpoint that an asynchronous reward stage has already relied on.
	 */
	private void persistReplayCheckpoint(Runnable write) {
		if (isCached()) getCache().flushChangesAndRun(write);
		else write.run();
	}

	private void setTimedRewards(HashMap<String, Long> timed, boolean queue) {
		ArrayList<String> timedRewards = new ArrayList<>();
		for (Entry<String, Long> entry : timed.entrySet()) {

			String str = "";
			str += entry.getKey() + "%ExecutionTime/%";
			str += entry.getValue();
			timedRewards.add(str);

		}
		if (queue) data.setStringList("TimedRewards", timedRewards);
		else data.setStringList("TimedRewards", timedRewards, false);
	}

	/**
	 * Sets the unclaimed choice rewards list.
	 *
	 * @param rewards the unclaimed rewards
	 */
	public void setUnClaimedChoice(ArrayList<String> rewards) {
		getData().setStringList("UnClaimedChoices", rewards);
	}

	/**
	 * Sets the user to not cache data.
	 */
	@Deprecated
	public void dontCache() {
		userDataFetchMode = UserDataFetchMode.NO_CACHE;
	}

	/**
	 * Sets the uuid.
	 *
	 * @param uuid the new uuid
	 */
	public void setUUID(String uuid) {
		this.uuid = uuid;
	}

	/**
	 * Enables temporary caching for this user.
	 *
	 * @return this user instance
	 */
	public AdvancedCoreUser tempCache() {
		getUserData().tempCache();
		return this;
	}

	/**
	 * Updates the player name in storage.
	 *
	 * @param force whether to force the update
	 */
	public void updateName(boolean force) {
		if (getData().hasData() || force) {
			String playerName = getData().getString("PlayerName", userDataFetchMode);
			if (playerName == null || !playerName.equals(getPlayerName())) {
				getData().setString("PlayerName", getPlayerName(), true);
			}
		}
	}

	/**
	 * Updates the temporary cache with specific columns.
	 *
	 * @param cols the columns to update
	 */
	public void updateTempCacheWithColumns(ArrayList<Column> cols) {
		getUserData().updateTempCacheWithColumns(cols);
	}

}
