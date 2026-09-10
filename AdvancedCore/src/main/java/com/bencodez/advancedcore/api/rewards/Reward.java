package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
		List<RewardInject> postRewards = new ArrayList<>();
		CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);

		for (RewardInject inject : plugin.getRewardHandler().getInjectedRewards()) {
			if (inject.isPostReward()) {
				postRewards.add(inject);
				continue;
			}
			sequence = sequence.thenCompose(ignored -> invokeInjectionAsync(inject, user, placeholders))
					.thenCompose(result -> resumeOnServerThread(user).thenRun(() -> {
						if (inject.isAddAsPlaceholder() && result != null) addPlaceholder(inject, result, placeholders);
					}));
		}

		for (RewardInject inject : postRewards) {
			sequence = sequence.thenCompose(ignored -> invokeInjectionAsync(inject, user, placeholders))
					.thenCompose(result -> resumeOnServerThread(user));
		}
		return sequence;
	}

	private CompletionStage<Object> invokeInjectionAsync(RewardInject inject, AdvancedCoreUser user,
			HashMap<String, String> placeholders) {
		try {
			if (!plugin.isEnabled()) return CompletableFuture.failedFuture(
					new IllegalStateException("Plugin disabled before reward injection completed"));
			Supplier<CompletionStage<Object>> request = () -> requestOnServerThread(user,
					() -> requestInjectionAsync(inject, user, placeholders));
			CompletionStage<Object> result = inject.isSynchronize()
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
			HashMap<String, String> placeholders) {
		if (inject.supportsAsyncRequest()) {
			return inject.onRewardRequestAsync(this, user, getConfig().getConfigData(), placeholders);
		}
		return CompletableFuture.completedFuture(inject.onRewardRequest(this, user, getConfig().getConfigData(), placeholders));
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
			if (inject.supportsAsyncRequest()) {
				return true;
			}
		}
		return false;
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
	 * Give reward user.
	 *
	 * @param user          the user
	 * @param phs           placeholders
	 * @param rewardOptions rewardOptions
	 */
	public void giveRewardUser(AdvancedCoreUser user, HashMap<String, String> phs, RewardOptions rewardOptions) {
		if (hasAsyncRewardInjection()) {
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
		final HashMap<String, String> placeholders;
		try {
			placeholders = prepareRewardUser(user, phs);
		} catch (Throwable throwable) {
			return CompletableFuture.failedFuture(throwable);
		}
		if (placeholders == null) {
			return CompletableFuture.completedFuture(null);
		}
		return giveInjectedRewardsAsync(user, placeholders)
				.thenRun(() -> plugin.debug("Gave " + user.getPlayerName() + " reward " + name));
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
