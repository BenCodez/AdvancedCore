package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSpecialChance;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.messages.MessageAPI;

public final class RewardSpecialChance {

    private RewardSpecialChance() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("SpecialChance") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                double totalChance = 0;
                LinkedHashMap<Double, String> map = new LinkedHashMap<>();
                for (String key : section.getKeys(false)) {
                    String path = key;
                    key = key.replaceAll("_", ".");
                    if (MessageAPI.isDouble(key)) {
                        double chance = Double.valueOf(key);
                        totalChance += chance;
                        map.put(chance, path);
                    }
                }

                Set<Entry<Double, String>> copy = new HashSet<>(map.entrySet());
                double currentNum = 0;
                map.clear();
                for (Entry<Double, String> entry : copy) {
                    currentNum += entry.getKey();
                    map.put(currentNum, entry.getValue());
                }

                double randomNum = ThreadLocalRandom.current().nextDouble(totalChance);
                for (Entry<Double, String> entry : map.entrySet()) {
                    if (randomNum <= entry.getKey()) {
                        new RewardBuilder(section, entry.getValue()).withPrefix(reward.getName() + "_SpecialChance")
                                .withPlaceHolder(placeholders).withPlaceHolder("chance", "" + entry.getKey()).send(user);
                        plugin.debug("Giving special chance: " + entry.getValue() + ", Random number: " + randomNum
                                + ", Total chance: " + totalChance);
                        return null;
                    }
                }
                plugin.debug("Failed to give special chance");
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String base = direct.getPath() + direct.needsDot() + "SpecialChance";
                if (direct.getFileData().isConfigurationSection(base)) {
                    for (String key : direct.getFileData().getConfigurationSection(base).getKeys(false)) {
                        if (MessageAPI.isDouble(key.replaceAll("_", "."))) {
                            String path = "SpecialChance." + key;
                            if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + path)) {
                                subs.add(new SubDirectlyDefinedReward(direct, path));
                            }
                        }
                    }
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("SpecialChance") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditSpecialChance() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Rewards based on chance"))).priority(10).postReward());
    }
}
