package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementRewardType {

    private RequirementRewardType() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectString("RewardType", "BOTH") {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String type,
                    RewardOptions rewardOptions) {
                if (rewardOptions.isOnline()) {
                    if (type.equalsIgnoreCase("offline")) {
                        debug("Reward Type Don't match");
                        return false;
                    }
                } else if (type.equalsIgnoreCase("online")) {
                    debug("Reward Type Don't match");
                    return false;
                }
                return true;
            }
        }.priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder("REDSTONE_TORCH"), new EditGUIValueString("RewardType", null) {
                    @Override
                    public void setValue(Player player, String value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addOptions("ONLINE", "OFFLINE", "BOTH")
                        .addLore("Define whether should execute if player was offline/online"))));
    }
}
