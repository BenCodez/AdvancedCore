package com.bencodez.advancedcore.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditChoices;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectBoolean;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardChoices {

    private RewardChoices() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectBoolean("EnableChoices") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, boolean value,
                    HashMap<String, String> placeholders) {
                if (value) {
                    debug("Checking choice rewards");
                    String choice = user.getChoicePreference(reward.getName());
                    if (choice.isEmpty() || choice.equalsIgnoreCase("none")) {
                        debug("No choice specified");
                        user.addUnClaimedChoiceReward(reward.getName());
                    } else {
                        handler.giveChoicesReward(reward, user, choice);
                    }
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                String root = direct.getPath() + direct.needsDot();
                if (direct.getFileData().getBoolean(root + "EnableChoices")
                        && direct.getFileData().isConfigurationSection(root + "Choices")) {
                    for (String choice : direct.getFileData().getConfigurationSection(root + "Choices").getKeys(false)) {
                        String path = "Choices." + choice + ".Rewards";
                        if (direct.getFileData().isConfigurationSection(root + path)) {
                            subs.add(new SubDirectlyDefinedReward(direct, path));
                        }
                    }
                }
                return subs;
            }
        }.priority(10).synchronize().validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (data.getBoolean("EnableChoices")) {
                    ConfigurationSection choices = data.getConfigurationSection("Choices");
                    if (choices == null) {
                        warning(reward, inject, "Choices section is missing while EnableChoices is true");
                        return;
                    }
                    if (choices.getKeys(false).size() <= 1) {
                        warning(reward, inject, "Not enough choices for choice rewards, 1 or less is not a choice");
                    }
                }
            }
        }.addPath("Choices")).addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Choices") {
                    @Override
                    public void openInventory(ClickEvent clickEvent) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        new RewardEditChoices() {
                            @Override
                            public void setVal(String key, Object value) {
                                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                                reward.setValue(key, value);
                                plugin.reloadAdvancedCore(false);
                            }
                        }.open(clickEvent.getPlayer(), reward);
                    }
                }.addCheckKey("EnableChoices").addLore("Give users a choice on the reward"))));
    }
}
