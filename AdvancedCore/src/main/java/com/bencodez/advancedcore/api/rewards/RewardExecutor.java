package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

/**
 * Executes and constructs rewards while {@link RewardHandler} remains the
 * compatibility facade exposed to callers.
 */
public class RewardExecutor {

    private static final String QUEUED_REFERENCE_PREFIX = "\\AdvancedCoreQueue/1/";

    private final RewardHandler handler;
    private final AdvancedCorePlugin plugin;

    public RewardExecutor(RewardHandler handler, AdvancedCorePlugin plugin) {
        this.handler = handler;
        this.plugin = plugin;
    }

    public Reward getReward(ConfigurationSection data, String path, RewardOptions rewardOptions) {
        if (path == null) {
            plugin.getLogger().warning("Path is null, failing to give reward");
            return null;
        }
        if (data == null) {
            plugin.getLogger().warning("ConfigurationSection is null, failing to give reward: " + path);
            return null;
        }
        if (!data.isConfigurationSection(path)) {
            return null;
        }

        RewardExecutionContext context = new RewardExecutionContext(rewardOptions);
        return new Reward(context.buildRewardName(path), data.getConfigurationSection(path));
    }

    public void giveChoicesReward(Reward mainReward, AdvancedCoreUser user, String choice) {
        RewardBuilder reward = new RewardBuilder(mainReward.getConfig().getConfigData(),
                mainReward.getConfig().getChoicesRewardsPath(choice));
        reward.withPrefix(mainReward.getName());
        reward.withPlaceHolder("choice", choice);
        reward.send(user);
    }

    public void giveReward(AdvancedCoreUser user, ConfigurationSection data, String path, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        RewardOptions options = context.getOptions();

        if (path == null) {
            plugin.getLogger().warning("Path is null, failing to give reward");
            return;
        }
        if (data == null) {
            plugin.getLogger().warning("ConfigurationSection is null, failing to give reward: " + path);
            return;
        }
        if (!plugin.isEnabled()) {
            plugin.getLogger().severe("Not giving reward " + path + ", plugin is not enabled");
            return;
        }

        if (data.isList(path)) {
            ArrayList<String> rewards = new ArrayList<>(data.getStringList(path));
            if (rewards.isEmpty()) {
                plugin.debug("Not giving empty list of rewards from " + path + ", Options: " + options);
                return;
            }

            plugin.debug("Giving list of rewards (" + ArrayUtils.makeStringList(rewards) + ") from " + path
                    + ", Options: " + options + " to " + user.getPlayerName() + "/" + user.getUUID());
            for (String reward : rewards) {
                giveReward(user, reward, options);
            }
            return;
        }

        if (data.isConfigurationSection(path)) {
            giveSectionReward(user, data, path, context);
            return;
        }

        String reward = data.getString(path, "");
        if (!reward.isEmpty()) {
            plugin.debug("Giving reward " + reward + " from path " + path + ", Options: " + options + " to "
                    + user.getPlayerName() + "/" + user.getUUID());
            giveReward(user, reward, options);
        } else {
            plugin.debug("Not giving reward " + reward + " from path " + path + ", Options: " + options);
        }
    }

    /**
     * Asynchronous counterpart for nested rewards that must preserve injection
     * ordering. The legacy void dispatch methods intentionally remain unchanged.
     */
    public CompletionStage<Void> giveRewardAsync(AdvancedCoreUser user, ConfigurationSection data, String path,
            RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        RewardOptions options = context.getOptions();
        if (path == null || data == null) return CompletableFuture.completedFuture(null);
        if (!plugin.isEnabled()) return disabledDispatch();

        Reward.ReplayState replayState = Reward.replayStateFor(options);
        String parentReplayKey = options.getAsyncReplayKey();
        if (parentReplayKey == null) parentReplayKey = Reward.currentReplayKey();
        if (parentReplayKey == null) parentReplayKey = "list:" + path;
        String nestedLane = "nested-list:" + path;
        if (data.isList(path)
                || Reward.hasReplayNestedRewardSnapshot(options.getPlaceholders(), nestedLane, replayState,
                        parentReplayKey)) {
            final String stableParentReplayKey = parentReplayKey;
			return Reward.replayNestedRewardSnapshot(plugin, options.getPlaceholders(), nestedLane,
					data.isList(path) ? new ArrayList<>(data.getStringList(path)) : java.util.List.of(), replayState,
					stableParentReplayKey)
					.thenCompose(rewards -> {
						CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);
						for (int index = 0; index < rewards.size(); index++) {
							String nestedReward = rewards.get(index);
							int nestedIndex = index;
							sequence = sequence.thenCompose(ignored -> {
								// Clone only when this child is actually reached. This lets
								// metadata from earlier children be merged before a later
								// child receives its isolated ordinary placeholders.
								RewardOptions nestedOptions = options.copyForNestedDispatch(
										stableParentReplayKey + "/" + nestedReward + ":" + nestedIndex);
								nestedOptions.setAsyncReplayState(replayState);
								return giveRewardAsync(user, nestedReward, nestedOptions).handle((childResult, failure) -> {
									// Child options stay isolated for normal placeholders. Replay
									// markers are shared lazily so an earlier child remains durable
									// when a later child fails and the list is restarted.
									replayState.mergeReplayMetadataInto(options.getPlaceholders());
									if (failure != null) throw new CompletionException(failure);
									return childResult;
								});
							});
						}
						return sequence;
					});
        }
        if (data.isConfigurationSection(path)) {
            return giveSectionRewardAsync(user, data, path, context);
        }
        String nestedReward = data.getString(path, "");
        return nestedReward.isEmpty() ? CompletableFuture.completedFuture(null)
                : giveRewardAsync(user, nestedReward, options);
    }

    public void giveReward(AdvancedCoreUser user, Reward reward, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null) {
            plugin.debug("Reward == null");
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            plugin.getBukkitScheduler().runTaskAsynchronously(plugin,
                    () -> reward.giveReward(user, context.getOptions()));
        } else {
            reward.giveReward(user, context.getOptions());
        }
    }

    public CompletionStage<Void> giveRewardAsync(AdvancedCoreUser user, Reward reward, RewardOptions rewardOptions) {
        if (reward == null) return CompletableFuture.completedFuture(null);
        if (!plugin.isEnabled()) return disabledDispatch();
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        return reward.giveRewardAsync(user, context.getOptions());
    }

    public void giveReward(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null || reward.isEmpty()) {
            return;
        }

        if (reward.startsWith("/")) {
            MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), reward, context.getPlaceholders());
            return;
        }

        giveReward(user, handler.getReward(reward), context.getOptions());
    }

    public CompletionStage<Void> giveRewardAsync(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null || reward.isEmpty()) return CompletableFuture.completedFuture(null);
		if (!plugin.isEnabled()) return disabledDispatch();
		if (reward.startsWith("/")) {
			RewardOptions options = context.getOptions();
			return Reward.replayCommandSequence(plugin, context.getPlaceholders(), "direct", java.util.List.of(reward),
					Reward.replayStateFor(options), options.getAsyncReplayKey(),
					(command, ignoredIndex) -> MiscUtils.getInstance().executeConsoleCommandsAsync(
							user.getPlayerName(), command, context.getPlaceholders()));
		}
        return giveRewardAsync(user, handler.getReward(reward), context.getOptions());
    }

    public void givePersistedQueueReward(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null || reward.isEmpty()) {
            return;
        }

        String rewardName = reward;
        Boolean generatedSnapshot = null;
        if (reward.startsWith(QUEUED_REFERENCE_PREFIX)) {
            String encoded = reward.substring(QUEUED_REFERENCE_PREFIX.length());
            int modeEnd = encoded.indexOf('/');
            if (modeEnd > 0) {
                String mode = encoded.substring(0, modeEnd);
                String encodedName = encoded.substring(modeEnd + 1);
                if ((mode.equals("snapshot") || mode.equals("normal")) && !encodedName.isEmpty()) {
                    try {
                        rewardName = new String(Base64.getUrlDecoder().decode(encodedName), StandardCharsets.UTF_8);
                        generatedSnapshot = Boolean.valueOf(mode.equals("snapshot"));
                    } catch (IllegalArgumentException ignored) {
                        rewardName = reward;
                    }
                }
            }
        }

        Reward resolved;
        if (generatedSnapshot != null) {
            if (generatedSnapshot.booleanValue()) {
                resolved = handler.getQueuedGeneratedReward(rewardName, user.getUUID());
            } else {
                resolved = handler.getReward(rewardName);
            }
        } else if (handler.rewardExist(rewardName) || handler.hasDirectRewardHandle(rewardName)) {
            // Legacy persisted queue entry: a real registered reward wins over any
            // stale generated file with the same name.
            resolved = handler.getReward(rewardName);
        } else {
            // Legacy generated queue entries predate explicit provenance. This branch is
            // reachable only through the PersistedQueueReference capability path.
            resolved = handler.getQueuedGeneratedReward(rewardName, user.getUUID());
            if (resolved == null) {
                resolved = handler.getReward(rewardName);
            }
        }
        giveReward(user, resolved, context.getOptions());
    }

    /** Resolves and awaits a persisted queue item so a failed async injection can be requeued. */
    public CompletionStage<Void> givePersistedQueueRewardAsync(AdvancedCoreUser user, String reward,
            RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null || reward.isEmpty()) return CompletableFuture.completedFuture(null);
        if (!plugin.isEnabled()) return disabledDispatch();

        String rewardName = reward;
        Boolean generatedSnapshot = null;
        if (reward.startsWith(QUEUED_REFERENCE_PREFIX)) {
            String encoded = reward.substring(QUEUED_REFERENCE_PREFIX.length());
            int modeEnd = encoded.indexOf('/');
            if (modeEnd > 0) {
                String mode = encoded.substring(0, modeEnd);
                String encodedName = encoded.substring(modeEnd + 1);
                if ((mode.equals("snapshot") || mode.equals("normal")) && !encodedName.isEmpty()) {
                    try {
                        rewardName = new String(Base64.getUrlDecoder().decode(encodedName), StandardCharsets.UTF_8);
                        generatedSnapshot = Boolean.valueOf(mode.equals("snapshot"));
                    } catch (IllegalArgumentException ignored) {
                        rewardName = reward;
                    }
                }
            }
        }
        Reward resolved;
        if (generatedSnapshot != null) {
            resolved = generatedSnapshot.booleanValue() ? handler.getQueuedGeneratedReward(rewardName, user.getUUID())
                    : handler.getReward(rewardName);
        } else if (handler.rewardExist(rewardName) || handler.hasDirectRewardHandle(rewardName)) {
            resolved = handler.getReward(rewardName);
        } else {
            resolved = handler.getQueuedGeneratedReward(rewardName, user.getUUID());
            if (resolved == null) resolved = handler.getReward(rewardName);
        }
        if (resolved == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Persisted queued reward could not be resolved: " + rewardName));
        }
        return giveRewardAsync(user, resolved, context.getOptions());
    }

    private CompletionStage<Void> disabledDispatch() {
        return CompletableFuture.failedFuture(
                new IllegalStateException("Plugin disabled before asynchronous reward dispatch"));
    }

    public void updateReward(Configuration data, String path, RewardOptions rewardOptions) {
        if (data == null || path == null || !data.isConfigurationSection(path)) {
            return;
        }

        RewardExecutionContext context = new RewardExecutionContext(rewardOptions);
        Reward reward = new Reward(context.buildRewardName(path), data.getConfigurationSection(path));
        reward.checkRewardFile();
    }

    private void giveSectionReward(AdvancedCoreUser user, ConfigurationSection data, String path,
            RewardExecutionContext context) {
        RewardOptions options = context.getOptions();
        String rewardName = context.buildRewardName(path);
        DirectlyDefinedReward direct = handler.getDirectlyDefined(path);
        SubDirectlyDefinedReward sub = handler.getSubDirectlyDefined(rewardName);
        SubRewardResolver resolver = handler.getSubRewardResolver();
        SubDirectlyDefinedReward fileSub = resolver == null ? null : resolver.getFileBackedSubReward(rewardName);

        if (context.supportsDirectDispatch() && (direct != null || sub != null || fileSub != null)) {
            if (direct != null) {
                Reward reward = direct.getReward();
                if (reward != null) {
                    plugin.debug("Giving directlydefined reward " + path + ", Options: " + options + " to "
                            + user.getPlayerName() + "/" + user.getUUID());
                    giveReward(user, reward, options);
                } else {
                    plugin.debug("Failed to give directlydefined reward " + path + ", Options: " + options
                            + ", Reward == null");
                }
                return;
            }

            SubDirectlyDefinedReward selectedSub = sub != null ? sub : fileSub;
            Reward reward = selectedSub.getReward();
            if (reward != null) {
                plugin.debug("Giving sub reward " + rewardName + ", Options: " + options + " to "
                        + user.getPlayerName() + "/" + user.getUUID());
                giveReward(user, reward, options);
            } else {
                plugin.debug("Failed to give sub reward " + path + ", Options: " + options + ", Reward == null");
            }
            return;
        }

        Reward reward = new Reward(rewardName, data.getConfigurationSection(path));
        reward.checkRewardFile();
        plugin.debug("Giving reward " + path + ", Options: " + options + " to " + user.getPlayerName() + "/"
                + user.getUUID());
        giveReward(user, reward, options);
    }

    private CompletionStage<Void> giveSectionRewardAsync(AdvancedCoreUser user, ConfigurationSection data, String path,
            RewardExecutionContext context) {
        RewardOptions options = context.getOptions();
        String rewardName = context.buildRewardName(path);
        DirectlyDefinedReward direct = handler.getDirectlyDefined(path);
        SubDirectlyDefinedReward sub = handler.getSubDirectlyDefined(rewardName);
        SubRewardResolver resolver = handler.getSubRewardResolver();
        SubDirectlyDefinedReward fileSub = resolver == null ? null : resolver.getFileBackedSubReward(rewardName);
        if (context.supportsDirectDispatch() && (direct != null || sub != null || fileSub != null)) {
            Reward selected = direct != null ? direct.getReward() : (sub != null ? sub : fileSub).getReward();
            return giveRewardAsync(user, selected, options);
        }
        Reward selected = new Reward(rewardName, data.getConfigurationSection(path));
        selected.checkRewardFile();
        return giveRewardAsync(user, selected, options);
    }
}
