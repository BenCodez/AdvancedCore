package com.bencodez.advancedcore.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/** Built-in RandomCommand reward. */
public final class RewardRandomCommand {

    private RewardRandomCommand() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectStringList("RandomCommand") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(),
                            list.get(ThreadLocalRandom.current().nextInt(list.size())), placeholders);
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomCommand", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Execute random command"))).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                List<String> list = data.getStringList(inject.getPath());
                if (list.isEmpty()) {
                    warning(reward, inject, "No rewards listed for random reward");
                } else if (list.size() == 1) {
                    warning(reward, inject, "Only one reward listed for random reward");
                }
            }
        }));
    }
}
