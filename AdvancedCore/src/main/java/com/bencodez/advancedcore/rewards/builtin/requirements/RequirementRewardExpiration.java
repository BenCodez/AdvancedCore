package com.bencodez.advancedcore.rewards.builtin.requirements;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectInt;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementRewardExpiration {

    private RequirementRewardExpiration() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectInt("RewardExpiration", -1) {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, int num,
                    RewardOptions rewardOptions) {
                if (rewardOptions.getOrginalTrigger() > 0) {
                    long execDate = rewardOptions.getOrginalTrigger();
                    debug("OrgTrigger: " + execDate + ", plus time: " + (execDate + num * 60 * 1000)
                            + ", current time: " + System.currentTimeMillis());
                    return execDate + num * 60 * 1000 > System.currentTimeMillis();
                }
                if (rewardOptions.getPlaceholders().containsKey("ExecDate") && num > 0) {
                    long execDate = Long.parseLong(rewardOptions.getPlaceholders().get("ExecDate"));
                    debug("ExecDate: " + execDate + ", plus time: " + (execDate + num * 60 * 1000)
                            + ", current time: " + System.currentTimeMillis());
                    return execDate + num * 60 * 1000 > System.currentTimeMillis();
                }
                return true;
            }
        }.priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder("CLOCK"), new EditGUIValueNumber("RewardExpiration", null) {
                    @Override
                    public void setValue(Player player, Number value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value.intValue());
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Time before reward expires, if not executed").addLore("In minutes"))));
    }
}
