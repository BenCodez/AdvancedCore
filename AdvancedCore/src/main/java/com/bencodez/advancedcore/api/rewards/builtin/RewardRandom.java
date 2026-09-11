package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardRandom {

    private RewardRandom() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Random") {
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

            @SuppressWarnings("unchecked")
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                if (MiscUtils.getInstance().checkChance(section.getDouble("Chance", 100), 100)) {
                    if (section.getBoolean("PickRandom", true)) {
                        ArrayList<String> rewards = (ArrayList<String>) section.getList("Rewards", new ArrayList<>());
                        if (rewards != null && !rewards.isEmpty()) {
                            String selected = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
                            if (!selected.equals("")) {
                                handler.giveReward(user, selected, new RewardOptions().setPlaceholders(placeholders));
                            }
                        }
                    } else {
                        new RewardBuilder(reward.getConfig().getConfigData(), "Random.Rewards")
                                .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                    }
                } else {
                    new RewardBuilder(reward.getConfig().getConfigData(), "Random.FallBack")
                            .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                }
                return null;
            }

            @SuppressWarnings("unchecked")
            @Override
            public CompletionStage<String> onRewardRequestedAsync(Reward reward, AdvancedCoreUser user,
                    ConfigurationSection section, HashMap<String, String> placeholders) {
                String selection = Reward.replaySelection(placeholders, () -> {
                    if (!MiscUtils.getInstance().checkChance(section.getDouble("Chance", 100), 100)) return "fallback";
                    if (!section.getBoolean("PickRandom", true)) return "rewards";
                    ArrayList<String> rewards = (ArrayList<String>) section.getList("Rewards", new ArrayList<>());
                    return rewards == null || rewards.isEmpty() ? "none"
                            : "pick:" + rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
                });
                if (selection == null || selection.equals("none")) return CompletableFuture.completedFuture(null);
                if (selection.startsWith("pick:")) {
                    String selected = selection.substring("pick:".length());
                    RewardOptions childOptions = Reward.withReplayState(
                            new RewardOptions().setPlaceholders(placeholders), Reward.currentReplayState(),
                            Reward.currentReplayKey(), "selected:" + selected, Reward.currentReplayOccurrenceId());
					return selected.isEmpty() ? CompletableFuture.completedFuture(null)
							: Reward.persistReplayMetadataAsync(plugin, placeholders)
									.thenCompose(ignored -> handler.giveRewardAsync(user, selected, childOptions))
									.thenApply(ignored -> null);
                }
                String path = selection.equals("rewards") ? "Random.Rewards" : "Random.FallBack";
                RewardBuilder builder = new RewardBuilder(reward.getConfig().getConfigData(), path)
                        .withPrefix(reward.getName()).withPlaceHolder(placeholders);
                Reward.withReplayState(builder.getRewardOptions(), Reward.currentReplayState(),
                        Reward.currentReplayKey(), "path:" + path, Reward.currentReplayOccurrenceId());
				return Reward.persistReplayMetadataAsync(plugin, placeholders)
						.thenCompose(ignored -> builder.sendAsync(user)).thenApply(ignored -> null);
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.Rewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Random.Rewards"));
                }
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.FallBack")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Random.FallBack"));
                }
                return subs;
            }
        }.priority(10));
    }
}
