package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditFirework;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardFirework {

    private RewardFirework() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Firework") {
            @SuppressWarnings("unchecked")
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                if (section.getBoolean("Enabled")) {
                    FireworkHandler.getInstance().launchFirework(user.getPlayer().getLocation(),
                            section.getInt("Power", 1),
                            (ArrayList<String>) section.getList("Colors", new ArrayList<>()),
                            (ArrayList<String>) section.getList("FadeOutColor", new ArrayList<>()),
                            section.getBoolean("Trail"), section.getBoolean("Flicker"),
                            (ArrayList<String>) section.getList("Types", new ArrayList<>()),
                            section.getBoolean("Detonate", false));
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder("FIREWORK_ROCKET"), new EditGUIValueInventory("Firework") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditFirework() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Configure firework effect"))));
    }
}
