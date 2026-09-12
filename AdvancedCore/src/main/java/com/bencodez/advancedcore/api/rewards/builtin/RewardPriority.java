package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardPriority {

    private RewardPriority() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectStringList("Priority") {
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
            public String onRewardRequest(Reward source, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                for (String rewardName : list) {
                    Reward reward = handler.getReward(rewardName);
                    if (reward.canGiveReward(user, new RewardOptions().withPlaceHolder(placeholders))) {
                        new RewardBuilder(reward).withPlaceHolder(placeholders).setIgnoreChance(true)
                                .setIgnoreRequirements(true).send(user);
                        return reward.getName();
                    }
                }
                return null;
            }

            @Override
            public CompletionStage<String> onRewardRequestAsync(Reward source, AdvancedCoreUser user,
                    ArrayList<String> list, HashMap<String, String> placeholders) {
				String selectedName = Reward.replaySelection(placeholders, () -> {
                for (String rewardName : list) {
                    Reward reward = handler.getReward(rewardName);
                    if (reward != null && reward.canGiveReward(user,
                            new RewardOptions().withPlaceHolder(placeholders))) {
						return rewardName;
                    }
                }
					return null;
				});
				if (selectedName == null) return CompletableFuture.completedFuture(null);
				Reward selected = handler.getReward(selectedName);
				RewardOptions childOptions = Reward.withReplayState(
						new RewardOptions().withPlaceHolder(placeholders).setIgnoreChance(true)
								.setIgnoreRequirements(true),
						Reward.currentReplayState(), Reward.currentReplayKey(), "selected:" + selectedName,
						Reward.currentReplayOccurrenceId());
				if (selected == null) return CompletableFuture.failedFuture(
						new IllegalStateException("Selected priority reward could not be resolved: " + selectedName));
				return Reward.persistReplayMetadataAsync(plugin, placeholders)
						.thenCompose(ignored -> Reward.continueOnServerThread(plugin, user,
								() -> handler.giveRewardAsync(user, selected, childOptions)))
						.thenApply(ignored -> selected.getName());
            }
        }.asPlaceholder("Priority").addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Priority", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Execute first reward file that can be executed"))).postReward());
    }
}
