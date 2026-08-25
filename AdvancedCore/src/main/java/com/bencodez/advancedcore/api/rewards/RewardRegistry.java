package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * Owns registered reward state and reward lookup behavior.
 * <p>
 * {@link RewardHandler} remains the compatibility facade for callers while
 * registry-specific state and lookup responsibilities live here.
 */
public class RewardRegistry {

    private final AdvancedCorePlugin plugin;
    private final CopyOnWriteArrayList<DirectlyDefinedReward> directlyDefinedRewards = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<SubDirectlyDefinedReward> subDirectlyDefinedRewards = new CopyOnWriteArrayList<>();
    private List<Reward> rewards;

    public RewardRegistry(AdvancedCorePlugin plugin) {
        this.plugin = plugin;
        resetRewards();
    }

    public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
        if (getDirectlyDefined(directlyDefinedReward.getPath()) != null) {
            plugin.extraDebug(
                    "DirectlyDefinedReward with path already exists, skipping: " + directlyDefinedReward.getPath());
            return;
        }
        plugin.extraDebug("Adding directlydefined reward handle: " + directlyDefinedReward.getPath()
                + ", isdirectlydefined: " + directlyDefinedReward.isDirectlyDefined());
        directlyDefinedRewards.add(directlyDefinedReward);
    }

    public void addSubDirectlyDefined(SubDirectlyDefinedReward subDirectlyDefinedReward) {
        if (getSubDirectlyDefined(subDirectlyDefinedReward.getFullPath()) != null) {
            plugin.extraDebug("SubDirectlyDefinedReward with path already exists, skipping: "
                    + subDirectlyDefinedReward.getFullPath());
            return;
        }
        plugin.extraDebug("Adding subdirectlydefined reward handle: " + subDirectlyDefinedReward.getFullPath()
                + ", isdirectlydefined: " + subDirectlyDefinedReward.isDirectlyDefined());
        subDirectlyDefinedRewards.add(subDirectlyDefinedReward);
    }

    public DirectlyDefinedReward getDirectlyDefined(String path) {
        for (DirectlyDefinedReward direct : directlyDefinedRewards) {
            if (matchesDirectPath(direct.getPath(), path)) {
                return direct;
            }
        }
        return null;
    }

    public CopyOnWriteArrayList<DirectlyDefinedReward> getDirectlyDefinedRewards() {
        return directlyDefinedRewards;
    }

    public Reward getReward(String reward) {
        reward = normalizeLookupName(reward);

        if (reward.isEmpty()) {
            plugin.getLogger().warning("Tried to get any empty reward file name, renaming to EmptyName");
            reward = "EmptyName";
        }

        if (reward.equalsIgnoreCase("examplebasic") || reward.equalsIgnoreCase("exampleadvanced")) {
            plugin.getLogger().warning("Using example rewards as a reward, be carefull");
        }

        for (DirectlyDefinedReward direct : directlyDefinedRewards) {
            if (matchesDirectPath(direct.getPath(), reward)) {
                plugin.debug("Using directlydefined reward for: " + reward);
                return direct.getReward();
            }
        }

        for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
            if (matchesSubDirectlyDefined(direct, reward)) {
                plugin.debug("Using subdirectlydefined reward for: " + reward);
                return direct.getReward();
            }
        }

        for (Reward rewardFile : getRewards()) {
            if (rewardFile.getName().equalsIgnoreCase(reward)) {
                return rewardFile;
            }
        }

        if (!isSafeRewardFileName(reward)) {
            plugin.getLogger().warning("Rejected unsafe reward file name: " + reward);
            throw new IllegalArgumentException("Reward name must not contain path separators or be an absolute path");
        }

        return new Reward(reward);
    }

    public List<Reward> getRewards() {
        if (rewards == null) {
            resetRewards();
        }
        return rewards;
    }

    public SubDirectlyDefinedReward getSubDirectlyDefined(String path) {
        for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
            if (matchesSubDirectlyDefined(direct, path)) {
                return direct;
            }
        }
        return null;
    }

    public CopyOnWriteArrayList<SubDirectlyDefinedReward> getSubDirectlyDefinedRewards() {
        return subDirectlyDefinedRewards;
    }

    public boolean hasDirectRewardHandle(String reward) {
        for (DirectlyDefinedReward direct : directlyDefinedRewards) {
            if (matchesDirectPath(direct.getPath(), reward)) {
                return true;
            }
        }
        for (SubDirectlyDefinedReward direct : subDirectlyDefinedRewards) {
            if (matchesSubDirectlyDefined(direct, reward)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeLookupName(String reward) {
        return reward == null ? "" : reward.replace(" ", "_");
    }

    public static String normalizeDirectPath(String path) {
        return normalizeLookupName(path).replace('.', '_').toLowerCase(Locale.ROOT);
    }

    static boolean isSafeRewardFileName(String reward) {
        if (reward == null || reward.indexOf('\0') >= 0 || new File(reward).isAbsolute()) {
            return false;
        }
        return reward.indexOf('/') < 0 && reward.indexOf('\\') < 0;
    }

    public boolean rewardExist(String reward) {
        reward = normalizeLookupName(reward);
        if (reward.isEmpty()) {
            return false;
        }
        for (Reward rewardName : getRewards()) {
            if (rewardName.getName().equalsIgnoreCase(reward)) {
                return true;
            }
        }
        return false;
    }

    public void resetRewards() {
        rewards = Collections.synchronizedList(new ArrayList<Reward>());
    }

    public void resetSubDirectlyDefinedRewards() {
        subDirectlyDefinedRewards = new CopyOnWriteArrayList<>();
    }

    public void updateReward(Reward reward) {
        if (reward != null && reward.getConfig().isDirectlyDefinedReward()) {
            File folder = reward.getConfig().getRewardFolder();
            if (folder != null && folder.getName().equalsIgnoreCase("DirectlyDefined")) {
                plugin.extraDebug("Keeping generated queued reward snapshot out of public registry: " + reward.getName());
                return;
            }
        }
        reward.validate();
        for (int i = getRewards().size() - 1; i >= 0; i--) {
            if (getRewards().get(i).getFile().getPath().equals(reward.getFile().getPath())) {
                getRewards().set(i, reward);
                return;
            }
        }
        getRewards().add(reward);
    }

    private boolean matchesDirectPath(String registeredPath, String lookupPath) {
        return normalizeDirectPath(registeredPath).equals(normalizeDirectPath(lookupPath));
    }

    private boolean matchesSubDirectlyDefined(SubDirectlyDefinedReward direct, String reward) {
        return matchesDirectPath(direct.getFullPath(), reward);
    }
}
