package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedWorld;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

public final class RewardAdvancedWorld {

    private RewardAdvancedWorld() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("AdvancedWorld") {
            @Override
            public boolean supportsAsyncRequest() { return true; }

            @Override
            public boolean requiresConfiguredDataForAsync() { return true; }

			@Override
			public boolean hasPendingReplayWork(HashMap<String, String> placeholders) {
				return Reward.hasReplayNestedRewardSnapshot(placeholders, "advanced-world:" + getPath());
			}

            @Override
            public boolean supportsAsyncSynchronization() { return false; }

            @Override
            public String onRewardRequested(Reward sourceReward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
				for (String key : section.getKeys(false)) {
                    plugin.extraDebug("AdvancedWorld: Giving reward " + sourceReward.getName() + "_AdvancedWorld");
                    section.set(key + ".Worlds", ArrayUtils.convert(new String[] { key }));
                    handler.giveReward(user, section, key, new RewardOptions().withPlaceHolder(placeholders)
                            .setPrefix(sourceReward.getName() + "_AdvancedWorld"));
                }
                return null;
            }

            @Override
            public CompletionStage<String> onRewardRequestedAsync(Reward sourceReward, AdvancedCoreUser user,
                    ConfigurationSection section, HashMap<String, String> placeholders) {
				com.bencodez.advancedcore.api.rewards.Reward.ReplayState replayState = Reward.currentReplayState();
				String parentReplayKey = Reward.currentReplayKey();
				String parentOccurrenceId = Reward.currentReplayOccurrenceId();
				return Reward.replayNestedRewardSnapshot(plugin, placeholders, "advanced-world:" + getPath(),
						new ArrayList<>(section.getKeys(false)), replayState, parentReplayKey).thenCompose(worlds -> {
					for (String key : worlds) {
						if (!section.contains(key, true)) {
							return CompletableFuture.failedFuture(new IllegalStateException(
									"Pending nested reward configuration is missing: " + key));
						}
					}
					CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);
					for (int index = 0; index < worlds.size(); index++) {
						String key = worlds.get(index);
						int childIndex = index;
						section.set(key + ".Worlds", ArrayUtils.convert(new String[] { key }));
						sequence = sequence.thenCompose(ignored -> Reward.continueOnServerThread(plugin, user,
								() -> handler.giveRewardAsync(user, section, key,
										Reward.withReplayState(new RewardOptions().withPlaceHolder(placeholders), replayState,
												parentReplayKey, key + ":" + childIndex, parentOccurrenceId)
												.setPrefix(sourceReward.getRewardName() + "_AdvancedWorld"))));
					}
					return sequence.thenApply(ignored -> (String) null);
				});
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String base = direct.getPath() + direct.needsDot() + "AdvancedWorld";
                if (direct.getFileData().isConfigurationSection(base)) {
                    for (String key : direct.getFileData().getConfigurationSection(base).getKeys(false)) {
                        if (direct.getFileData().isConfigurationSection(base + "." + key)) {
                            subs.add(new SubDirectlyDefinedReward(direct, "AdvancedWorld." + key));
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedWorld") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditAdvancedWorld() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("AdvancedReward will run rewards based worlds specified"))).priority(10).postReward());
    }
}
