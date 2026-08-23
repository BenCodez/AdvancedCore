package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

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
