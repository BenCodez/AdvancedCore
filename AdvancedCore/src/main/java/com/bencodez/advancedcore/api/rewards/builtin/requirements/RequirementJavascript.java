package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementJavascript {

    private RequirementJavascript() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectString("JavascriptExpression", "") {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String expression,
                    RewardOptions rewardOptions) {
                if (expression.equals("")) {
                    return true;
                }
                return new JavascriptEngine().addPlayer(user.getOfflinePlayer())
                        .addPlaceholders(rewardOptions.getPlaceholders()).getBooleanValue(expression);
            }
        }.priority(90).addEditButton(new EditGUIButton(new ItemBuilder("DETECTOR_RAIL"),
                new EditGUIValueString("JavascriptExpression", null) {
                    @Override
                    public void setValue(Player player, String value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.addLore("Javascript expression required to run reward"))).validator(new RequirementInjectValidator() {
                    @Override
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        String str = data.getString("JavascriptExpression", null);
                        if (str != null && str.isEmpty()) {
                            warning(reward, inject, "No javascript expression set");
                        }
                    }
                }));
    }
}
