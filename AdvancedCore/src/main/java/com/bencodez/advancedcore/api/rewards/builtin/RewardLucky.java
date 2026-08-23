package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditLucky;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

public final class RewardLucky {

    private RewardLucky() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Lucky") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                HashMap<Integer, String> luckyRewards = new HashMap<>();
                for (String key : section.getKeys(false)) {
                    if (MessageAPI.isInt(key)) {
                        int num = Integer.parseInt(key);
                        if (num > 0) {
                            luckyRewards.put(num, "Lucky." + num);
                        }
                    }
                }
                HashMap<String, Integer> map = new LinkedHashMap<>();
                for (Entry<Integer, String> entry : luckyRewards.entrySet()) {
                    if (MiscUtils.getInstance().checkChance(1, entry.getKey())) {
                        map.put(entry.getValue(), entry.getKey());
                    }
                }
                map = ArrayUtils.sortByValuesStr(map, false);
                if (!map.isEmpty()) {
                    if (reward.getConfig().getConfigData().getBoolean("OnlyOneLucky", false)) {
                        for (Entry<String, Integer> entry : map.entrySet()) {
                            new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
                                    .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                            return null;
                        }
                    } else {
                        for (Entry<String, Integer> entry : map.entrySet()) {
                            new RewardBuilder(reward.getConfig().getConfigData(), entry.getKey())
                                    .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                        }
                    }
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Lucky")) {
                    for (String key : direct.getFileData()
                            .getConfigurationSection(direct.getPath() + direct.needsDot() + "Lucky").getKeys(false)) {
                        if (MessageAPI.isInt(key) && Integer.parseInt(key) > 0) {
                            String path = "Lucky." + key;
                            if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + path)) {
                                subs.add(new SubDirectlyDefinedReward(direct, path));
                            }
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Lucky") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditLucky() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        })).priority(10).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
            }
        }.addPath("OnlyOneLucky")));
    }
}
