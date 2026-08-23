package com.bencodez.advancedcore.rewards.builtin;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditActionBar;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardActionBar {

    private RewardActionBar() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("ActionBar") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                user.sendActionBar(PlaceholderUtils.replacePlaceHolder(section.getString("Message", ""), placeholders),
                        section.getInt("Delay", 30));
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("ActionBar") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditActionBar() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Actionbar configuration"))).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                String message = data.getString("ActionBar.Message");
                int delay = data.getInt("ActionBar.Delay", -1);
                if (message != null && message.isEmpty()) {
                    warning(reward, inject, "No actionbar message set");
                }
                if (delay == -1) {
                    warning(reward, inject, "No actionbar delay set");
                } else if (delay == 0) {
                    warning(reward, inject, "Actionbar delay can not be 0");
                }
            }
        }));
    }
}
