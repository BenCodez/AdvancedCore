package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedRandomReward;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

public final class RewardAdvancedRandomReward {

    private RewardAdvancedRandomReward() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("AdvancedRandomReward") {
            @Override
            public boolean supportsAsyncRequest() { return true; }

            @Override
            public boolean requiresConfiguredDataForAsync() { return true; }

			@Override
			public boolean hasPendingReplayWork(HashMap<String, String> placeholders) {
				return Reward.hasReplaySelection(placeholders);
			}

            @Override
            public boolean supportsAsyncSynchronization() { return false; }

            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                Set<String> keys = section.getKeys(false);
                ArrayList<String> rewards = ArrayUtils.convert(keys);
                if (!rewards.isEmpty()) {
                    String selected = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
                    handler.giveReward(user, section, selected, new RewardOptions().setPlaceholders(placeholders)
                            .setPrefix(reward.getRewardName() + "_AdvancedRandomReward"));
                    return selected;
                }
                return null;
            }

            @Override
            public CompletionStage<String> onRewardRequestedAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection section, HashMap<String, String> placeholders) {
				ArrayList<String> rewards = ArrayUtils.convert(section.getKeys(false));
				if (rewards.isEmpty() && !hasPendingReplayWork(placeholders)) {
					return CompletableFuture.completedFuture(null);
				}
                String selected = Reward.replaySelection(placeholders,
                        () -> rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())));
                RewardOptions childOptions = Reward.withReplayState(new RewardOptions().setPlaceholders(placeholders),
                        Reward.currentReplayState(), Reward.currentReplayKey(), "selected:" + selected,
                        Reward.currentReplayOccurrenceId())
                        .setPrefix(reward.getRewardName() + "_AdvancedRandomReward");
				return Reward.persistReplayMetadataAsync(plugin, placeholders)
						.thenCompose(ignored -> handler.giveRewardAsync(user, section, selected, childOptions))
						.thenApply(ignored -> selected);
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String base = direct.getPath() + direct.needsDot() + "AdvancedRandomReward";
                if (direct.getFileData().isConfigurationSection(base)) {
                    for (String key : direct.getFileData().getConfigurationSection(base).getKeys(false)) {
                        if (direct.getFileData().isConfigurationSection(base + "." + key)) {
                            subs.add(new SubDirectlyDefinedReward(direct, "AdvancedRandomReward." + key));
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedRandomReward") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditAdvancedRandomReward() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Execute random reward"))).asPlaceholder("RandomReward").synchronize().priority(20).postReward());
    }
}
