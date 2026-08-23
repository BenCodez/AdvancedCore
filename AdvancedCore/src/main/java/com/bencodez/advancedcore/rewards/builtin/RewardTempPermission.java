package com.bencodez.advancedcore.rewards.builtin;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditTempPermission;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardTempPermission {

    private RewardTempPermission() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("TempPermission") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                String permission = section.getString("Permission", "");
                int time = section.getInt("Expiration");
                if (!permission.isEmpty()) {
                    if (time > 0) {
                        user.addPermission(permission, time);
                    } else {
                        extraDebug("Time is 0");
                    }
                } else {
                    extraDebug("Permission is empty");
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("TempPermission") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditTempPermission() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Give temporary permission"))).priority(90).postReward());
    }
}
