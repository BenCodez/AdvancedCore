package com.bencodez.advancedcore.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardSubRewards {

    private RewardSubRewards() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Rewards") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                plugin.debug("12345c" + reward.getName());
                new RewardBuilder(reward.getConfig().getConfigData(), "Rewards").withPrefix(reward.getName())
                        .withPlaceHolder(placeholders).send(user);
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Rewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Rewards"));
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.DISPENSER), new EditGUIValueInventory("Rewards") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                handler.openSubReward(clickEvent.getPlayer(), "Rewards", reward);
            }
        }.addLore("Sub rewards"))).priority(5).alwaysForce().postReward());
    }
}
