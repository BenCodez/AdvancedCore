package com.bencodez.advancedcore.api.rewards.builtin;

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
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditAdvancedPriority;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardAdvancedPriority {

    private RewardAdvancedPriority() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("AdvancedPriority") {
            @Override
            public String onRewardRequested(Reward sourceReward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                for (String key : section.getKeys(false)) {
                    RewardOptions namingOptions = new RewardOptions()
                            .setPrefix(sourceReward.getName() + "_AdvancedPriority");
                    Reward reward = handler.getReward(section, key, namingOptions);
                    if (reward != null && reward.canGiveReward(user, new RewardOptions().withPlaceHolder(placeholders))) {
                        plugin.extraDebug("AdvancedPriority: Giving reward " + reward.getName());
                        reward.giveReward(user, new RewardOptions().setIgnoreChance(true).setIgnoreRequirements(true)
                                .setPrefix(sourceReward.getName() + "_AdvancedPriority").withPlaceHolder(placeholders));
                        return reward.getName();
                    }
                    plugin.extraDebug("AdvancedPriority: Can't give reward " + key);
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String base = direct.getPath() + direct.needsDot() + "AdvancedPriority";
                if (direct.getFileData().isConfigurationSection(base)) {
                    for (String key : direct.getFileData().getConfigurationSection(base).getKeys(false)) {
                        if (direct.getFileData().isConfigurationSection(base + "." + key)) {
                            subs.add(new SubDirectlyDefinedReward(direct, "AdvancedPriority." + key));
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("AdvancedPriority") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditAdvancedPriority() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("AdvancedPriority will run first sub reward that it can, then ignore the rest of the sub rewards")
                .addLore("Can be used for permission based rewards or chance based rewards")))
                .priority(10).alwaysValid().postReward());
    }
}
