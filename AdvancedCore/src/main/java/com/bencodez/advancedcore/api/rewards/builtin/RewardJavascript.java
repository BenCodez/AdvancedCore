package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditJavascript;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardJavascript {

    private RewardJavascript() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectStringList("Javascripts") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getOfflinePlayer());
                    for (String script : list) {
                        String prepared = engine.preparePlaceholders(user.getOfflinePlayer(), script, placeholders);
                        engine.execute(prepared == null ? script : prepared);
                    }
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Javascripts", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Javascript expressions to run"))));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Javascript") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                if (section.getBoolean("Enabled")) {
                    String expression = section.getString("Expression");
                    JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getOfflinePlayer());
                    String prepared = engine.preparePlaceholders(user.getOfflinePlayer(), expression, placeholders);
                    if (engine.getBooleanValue(prepared == null ? expression : prepared)) {
                        new RewardBuilder(section, "TrueRewards").withPrefix(reward.getName() + ".Javascript").send(user);
                    } else {
                        new RewardBuilder(section, "FalseRewards").withPrefix(reward.getName() + ".Javascript").send(user);
                    }
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(
                        direct.getPath() + direct.needsDot() + "Javascript.TrueRewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Javascript.TrueRewards"));
                }
                if (direct.getFileData().isConfigurationSection(
                        direct.getPath() + direct.needsDot() + "Javascript.FalseRewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Javascript.FalseRewards"));
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Javascript") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditJavascript() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Run javascript to run rewards based on expression return value of true/false"))));
    }
}
