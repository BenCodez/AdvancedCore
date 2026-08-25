package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;

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

    public void giveReward(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
        RewardExecutionContext context = new RewardExecutionContext(rewardOptions).initializeOnlineState(user);
        if (reward == null || reward.isEmpty()) {
            return;
        }

        if (reward.startsWith("/")) {
            MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), reward, context.getPlaceholders());
            return;
        }

        Reward resolved = null;
        if (isPersistedQueueReplay(context.getOptions())) {
            resolved = handler.getQueuedGeneratedReward(reward, user.getUUID());
        }
        if (resolved == null) {
            resolved = handler.getReward(reward);
        }
        giveReward(user, resolved, context.getOptions());
    }

    private boolean isPersistedQueueReplay(RewardOptions options) {
        if (options == null || options.isCheckTimed()) {
            return false;
        }
        if (options.isOnlineSet() && !options.isOnline()) {
            return true;
        }
        return options.getPlaceholders().containsKey("date");
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
}
