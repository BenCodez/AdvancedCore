package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectDouble;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementChance {

    private RequirementChance() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectDouble("Chance", 100) {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, double num,
                    RewardOptions rewardOptions) {
                if (rewardOptions.isIgnoreChance()) {
                    return true;
                }
                return MiscUtils.getInstance().checkChance(num, 100);
            }
        }.priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder("DROPPER"), new EditGUIValueNumber("Chance", null) {
                    @Override
                    public void setValue(Player player, Number value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value.intValue());
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Set chance for reward to execute"))).validator(new RequirementInjectValidator() {
                    @Override
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        if (data.getDouble(inject.getPath(), 0) == 100) {
                            warning(reward, inject,
                                    "Chance is 100, if intended then remove the chance option, as it's unneeded");
                        } else if (data.getDouble(inject.getPath(), 0) > 100) {
                            warning(reward, inject, "Chance is greater than 100, this will always give the reward");
                        } else if (data.getDouble(inject.getPath(), 1) == 0) {
                            warning(reward, inject, "Chance can not be 0");
                        } else if (data.getDouble(inject.getPath(), 1) < 0) {
                            warning(reward, inject, "Chance can not be negative");
                        }
                    }
                }));
    }
}
