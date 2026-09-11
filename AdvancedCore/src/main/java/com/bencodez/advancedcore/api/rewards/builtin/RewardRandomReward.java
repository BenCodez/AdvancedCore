package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardRandomReward {

    private RewardRandomReward() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectStringList("RandomReward") {
            @Override
            public boolean supportsAsyncRequest() { return true; }

            @Override
            public boolean requiresConfiguredDataForAsync() { return true; }

            @Override
            public boolean supportsAsyncSynchronization() { return false; }

            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    String selected = list.get(ThreadLocalRandom.current().nextInt(list.size()));
                    handler.giveReward(user, selected, new RewardOptions().setPlaceholders(placeholders));
                    return selected;
                }
                return null;
            }

            @Override
            public CompletionStage<String> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
                    ArrayList<String> list, HashMap<String, String> placeholders) {
                if (list.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                String selected = Reward.replaySelection(placeholders,
                        () -> list.get(ThreadLocalRandom.current().nextInt(list.size())));
                RewardOptions childOptions = Reward.withReplayState(
                        new RewardOptions().setPlaceholders(placeholders), Reward.currentReplayState(),
                        Reward.currentReplayKey(), "selected:" + selected, Reward.currentReplayOccurrenceId());
                return handler.giveRewardAsync(user, selected, childOptions)
                        .thenApply(ignored -> selected);
            }
        }.asPlaceholder("RandomReward").priority(20).addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomReward", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.addLore("Execute random reward"))).postReward());
    }
}
