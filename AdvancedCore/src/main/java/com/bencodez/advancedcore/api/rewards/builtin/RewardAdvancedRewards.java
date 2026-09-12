package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
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

public final class RewardAdvancedRewards {

    private RewardAdvancedRewards() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("AdvancedRewards") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public boolean requiresConfiguredDataForAsync() {
				return true;
			}

			@Override
			public boolean hasPendingReplayWork(HashMap<String, String> placeholders) {
				return Reward.hasReplayNestedRewardSnapshot(placeholders, "advanced-rewards:" + getPath());
			}

			@Override
			public boolean supportsAsyncSynchronization() {
				// This injector awaits nested rewards, which can invoke this same
				// shared instance. Serializing that recursive chain would deadlock.
				return false;
			}

            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                Set<String> keys = section.getKeys(false);
                ArrayList<String> rewards = ArrayUtils.convert(keys);
                for (String rewardName : rewards) {
                    handler.giveReward(user, section, rewardName, new RewardOptions().setPlaceholders(placeholders)
                            .setPrefix(reward.getRewardName() + "_AdvancedRewards"));
                }
                return null;
            }

			@Override
			public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				if (!data.isConfigurationSection(getPath()) && !(isAlwaysForce() && data.contains(getPath(), true))
						&& !isAlwaysForceNoData()) {
					return hasPendingReplayWork(placeholders)
							? CompletableFuture.failedFuture(new IllegalStateException(
									"Pending reward replay configuration is missing: " + getPath()))
							: CompletableFuture.completedFuture(null);
				}
				com.bencodez.advancedcore.api.rewards.Reward.ReplayState replayState = Reward.currentReplayState();
				String parentReplayKey = Reward.currentReplayKey();
				String parentOccurrenceId = Reward.currentReplayOccurrenceId();
				ConfigurationSection section = data.getConfigurationSection(getPath());
				ArrayList<String> configured = ArrayUtils.convert(section.getKeys(false));
				return Reward.replayNestedRewardSnapshot(plugin, placeholders, "advanced-rewards:" + getPath(),
						configured, replayState, parentReplayKey).thenCompose(rewards -> {
					for (String rewardName : rewards) {
						if (!section.contains(rewardName, true)) {
							return CompletableFuture.failedFuture(new IllegalStateException(
									"Pending nested reward configuration is missing: " + rewardName));
						}
					}
					CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);
					for (int index = 0; index < rewards.size(); index++) {
						String rewardName = rewards.get(index);
						int childIndex = index;
						sequence = sequence.thenCompose(ignored -> Reward.continueOnServerThread(plugin, user,
								() -> handler.giveRewardAsync(user, section, rewardName,
										Reward.withReplayState(new RewardOptions().setPlaceholders(placeholders), replayState,
												parentReplayKey, rewardName + ":" + childIndex, parentOccurrenceId)
												.setPrefix(reward.getRewardName() + "_AdvancedRewards"))));
					}
					return sequence.thenApply(ignored -> (Object) null);
				});
			}

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String base = direct.getPath() + direct.needsDot() + "AdvancedRewards";
                if (direct.getFileData().isConfigurationSection(base)) {
                    for (String key : direct.getFileData().getConfigurationSection(base).getKeys(false)) {
                        if (direct.getFileData().isConfigurationSection(base + "." + key)) {
                            subs.add(new SubDirectlyDefinedReward(direct, "AdvancedRewards." + key));
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedRewards") {
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
        }.addLore("Execute rewards"))).synchronize().priority(20).postReward());
    }
}
