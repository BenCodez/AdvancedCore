package com.bencodez.advancedcore.api.rewards;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.PersistedQueueReference;
import com.bencodez.advancedcore.api.user.UserStartup;
import com.bencodez.advancedcore.command.gui.RewardEditGUI;

import lombok.Getter;

/**
 * Public reward facade. Specialized reward state and service responsibilities are
 * delegated to focused classes while this class preserves the existing API.
 */
public class RewardHandler {

    @Getter
    private final RewardRegistry rewardRegistry;
    @Getter
    private final RewardLoader rewardLoader;
    @Getter
    private final RewardValidator rewardValidator;
    @Getter
    private final SubRewardResolver subRewardResolver;
    @Getter
    private final RewardInjectionRegistry rewardInjectionRegistry;
    @Getter
    private final RewardExecutor rewardExecutor;

    AdvancedCorePlugin plugin;

    @Getter
    private ScheduledExecutorService delayedTimer = Executors.newSingleThreadScheduledExecutor();

    public RewardHandler(AdvancedCorePlugin plugin) {
        this.plugin = plugin;
        rewardRegistry = new RewardRegistry(plugin);
        rewardInjectionRegistry = new RewardInjectionRegistry(this, plugin);
        rewardValidator = new RewardValidator(this, plugin);
        subRewardResolver = new SubRewardResolver(this, plugin);
        rewardLoader = new RewardLoader(this, plugin);
        rewardExecutor = new RewardExecutor(this, plugin);
    }

    public void addDirectlyDefined(DirectlyDefinedReward directlyDefinedReward) {
        rewardRegistry.addDirectlyDefined(directlyDefinedReward);
    }

    public void addInjectedRequirements(RequirementInject inject) {
        rewardInjectionRegistry.addInjectedRequirement(inject);
    }

    public void addInjectedReward(RewardInject inject) {
        rewardInjectionRegistry.addInjectedReward(inject);
    }

    public void addPlaceholder(RewardPlaceholderHandle handle) {
        rewardInjectionRegistry.addPlaceholder(handle);
    }

    public void addRewardFolder(File file) {
        rewardLoader.addRewardFolder(file);
    }

    public void addRewardFolder(File file, boolean load, boolean create) {
        rewardLoader.addRewardFolder(file, load, create);
    }

    public void addSubDirectlyDefined(SubDirectlyDefinedReward subDirectlyDefinedReward) {
        rewardRegistry.addSubDirectlyDefined(subDirectlyDefinedReward);
    }

    public void addValidPath(String path) {
        rewardValidator.addValidPath(path);
    }

    public void checkDirectlyDefinedRewardFiles() {
        rewardValidator.checkDirectlyDefinedRewardFiles();
    }

    public void checkDirectlyDefined() {
        rewardLoader.checkDirectlyDefined();
    }

    public void checkSubRewards() {
        subRewardResolver.checkSubRewards();
    }

    public void checkSubRewards(DefinedReward direct) {
        subRewardResolver.checkSubRewards(direct);
    }

    public File getDefaultFolder() {
        return rewardLoader.getDefaultFolder();
    }

    public DirectlyDefinedReward getDirectlyDefined(String path) {
        return rewardRegistry.getDirectlyDefined(path);
    }

    public CopyOnWriteArrayList<DirectlyDefinedReward> getDirectlyDefinedRewards() {
        return rewardRegistry.getDirectlyDefinedRewards();
    }

    public CopyOnWriteArrayList<RequirementInject> getInjectedRequirements() {
        return rewardInjectionRegistry.getInjectedRequirements();
    }

    public CopyOnWriteArrayList<RewardInject> getInjectedRewards() {
        return rewardInjectionRegistry.getInjectedRewards();
    }

    public CopyOnWriteArrayList<RewardPlaceholderHandle> getPlaceholders() {
        return rewardInjectionRegistry.getPlaceholders();
    }

    public Reward getReward(ConfigurationSection data, String path, RewardOptions rewardOptions) {
        return rewardExecutor.getReward(data, path, rewardOptions);
    }

    public Reward getReward(String reward) {
        return rewardRegistry.getReward(reward);
    }

    public Reward getRewardDirectlyDefined(String reward) {
        return rewardLoader.getRewardDirectlyDefined(reward);
    }

    public Reward getQueuedGeneratedReward(String reward, String userUuid) {
        return rewardLoader.getQueuedGeneratedReward(reward, userUuid);
    }

    public ArrayList<String> getRewardFiles(File folder) {
        return rewardLoader.getRewardFiles(folder);
    }

    public ArrayList<String> getRewardNames(File file) {
        return rewardLoader.getRewardNames(file);
    }

    public List<Reward> getRewards() {
        return rewardRegistry.getRewards();
    }

    public SubDirectlyDefinedReward getSubDirectlyDefined(String path) {
        return rewardRegistry.getSubDirectlyDefined(path);
    }

    public CopyOnWriteArrayList<SubDirectlyDefinedReward> getSubDirectlyDefinedRewards() {
        return rewardRegistry.getSubDirectlyDefinedRewards();
    }

    public Set<String> getValidPaths() {
        return rewardValidator.getValidPaths();
    }

    public void setValidPaths(Set<String> validPaths) {
        rewardValidator.setValidPaths(validPaths);
    }

    public void giveChoicesReward(Reward mainReward, AdvancedCoreUser user, String choice) {
        rewardExecutor.giveChoicesReward(mainReward, user, choice);
    }

    public void giveReward(AdvancedCoreUser user, ConfigurationSection data, String path, RewardOptions rewardOptions) {
        rewardExecutor.giveReward(user, data, path, rewardOptions);
    }

    public void giveReward(AdvancedCoreUser user, Reward reward, RewardOptions rewardOptions) {
        rewardExecutor.giveReward(user, reward, rewardOptions);
    }

    public void giveReward(AdvancedCoreUser user, String reward, RewardOptions rewardOptions) {
        rewardExecutor.giveReward(user, reward, rewardOptions);
    }

    public void givePersistedQueueReward(AdvancedCoreUser user, PersistedQueueReference rewardReference,
            RewardOptions rewardOptions) {
        if (rewardReference == null) {
            return;
        }
        rewardExecutor.givePersistedQueueReward(user, rewardReference.getReference(), rewardOptions);
    }

    public boolean hasDirectRewardHandle(String reward) {
        return rewardRegistry.hasDirectRewardHandle(reward);
    }

    public boolean hasRewards(FileConfiguration data, String path) {
        return rewardValidator.hasRewards(data, path);
    }

    public void loadInjectedRequirements() {
        rewardInjectionRegistry.loadInjectedRequirements();
    }

    public void loadInjectedRewards() {
        rewardInjectionRegistry.loadInjectedRewards();
    }

    public void loadRewards() {
        rewardLoader.loadRewards();
    }

    public void openSubReward(Player player, String path, RewardEditData reward) {
        if (!reward.getData().contains(path)) {
            reward.createSection(path);
        }
        RewardEditGUI.getInstance().openRewardGUI(player, new RewardEditData(new DirectlyDefinedReward(path) {
            @Override
            public void createSection(String path) {
                reward.createSection(path);
            }

            @Override
            public ConfigurationSection getFileData() {
                return reward.getData();
            }

            @Override
            public void save() {
                reward.save();
            }

            @Override
            public void setData(String path, Object value) {
                reward.setValue(path, value);
            }
        }, reward), reward.getName() + "." + path);
    }

    public boolean rewardExist(String reward) {
        return rewardRegistry.rewardExist(reward);
    }

    public void setDefaultFolder(File defaultFolder) {
        rewardLoader.setDefaultFolder(defaultFolder);
    }

    public void setupExample() {
        rewardLoader.setupExample();
    }

    public void shutdown() {
        delayedTimer.shutdown();
        try {
            delayedTimer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        delayedTimer.shutdownNow();
    }

    public void sortInjectedRequirements() {
        rewardInjectionRegistry.sortInjectedRequirements();
    }

    public void sortInjectedRewards() {
        rewardInjectionRegistry.sortInjectedRewards();
    }

    public void startup() {
        plugin.addUserStartup(new UserStartup() {
            @Override
            public void onFinish() {
            }

            @Override
            public void onStart() {
                plugin.debug("Checking timed/delayed rewards");
            }

            @Override
            public void onStartUp(AdvancedCoreUser user) {
                try {
                    HashMap<String, Long> timed = user.getTimedRewards();
                    for (Entry<String, Long> entry : timed.entrySet()) {
                        user.loadTimedDelayedTimer(entry.getValue().longValue());
                    }
                } catch (Exception ex) {
                    plugin.debug("Failed to update delayed/timed for: " + user.getUUID());
                    plugin.debug(ex);
                }
            }
        });
    }

    public void updateReward(Configuration data, String path, RewardOptions rewardOptions) {
        rewardExecutor.updateReward(data, path, rewardOptions);
    }

    public void updateReward(Reward reward) {
        rewardRegistry.updateReward(reward);
    }
}
