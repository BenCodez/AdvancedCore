package com.bencodez.advancedcore.rewards.builtin.requirements;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementDayOfMonth {

    private RequirementDayOfMonth() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectConfigurationSection("DayOfMonth") {
            @Override
            public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
                    RewardOptions rewardOptions) {
                if (!data.getBoolean("Enabled", false)) {
                    return true;
                }
                List<Integer> days = data.getIntegerList("Days");
                return days.contains(LocalDateTime.now().getDayOfMonth());
            }
        }.priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Days", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Set the days of the month for the requirement")))
                .validator(new RequirementInjectValidator() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        List<Integer> days = (List<Integer>) data.getList("Days", null);
                        if (days != null && days.isEmpty()) {
                            warning(reward, inject, "No days specified for DayOfMonth requirement");
                        }
                    }
                }));
    }
}
