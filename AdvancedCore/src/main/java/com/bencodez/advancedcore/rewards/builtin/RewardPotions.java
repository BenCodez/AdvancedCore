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
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditPotions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardPotions {

    private RewardPotions() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Potions") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                for (String potion : section.getKeys(false)) {
                    user.givePotionEffect(potion, section.getInt(potion + ".Duration", 1),
                            section.getInt(potion + ".Amplifier", 1));
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAINTING), new EditGUIValueInventory("Potions") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditPotions() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Configure Potion Effects"))));
    }
}
