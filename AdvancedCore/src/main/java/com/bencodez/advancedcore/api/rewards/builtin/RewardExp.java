package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXP;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditEXPLevels;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardExp {

    private RewardExp() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectInt("EXP", 0) {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, int num,
                    HashMap<String, String> placeholders) {
                user.giveExp(num);
                return null;
            }
        }.asPlaceholder("EXP").priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder("EXPERIENCE_BOTTLE"), new EditGUIValueInventory("EXP") {
                    @Override
                    public void openInventory(ClickEvent clickEvent) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        new RewardEditEXP() {
                            @Override
                            public void setVal(String key, Object value) {
                                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                                reward.setValue(key, value);
                                plugin.reloadAdvancedCore(false);
                            }
                        }.open(clickEvent.getPlayer(), reward);
                    }
                }.addLore("EXP to give"))).validator(zeroValidator("EXP can not be 0")));

        handler.getInjectedRewards().add(new RewardInjectInt("EXPLevels", 0) {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, int num,
                    HashMap<String, String> placeholders) {
                user.giveExpLevels(num);
                return null;
            }
        }.asPlaceholder("EXP").priority(100).addEditButton(
                new EditGUIButton(new ItemBuilder("EXPERIENCE_BOTTLE"), new EditGUIValueInventory("EXPLevels") {
                    @Override
                    public void openInventory(ClickEvent clickEvent) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        new RewardEditEXPLevels() {
                            @Override
                            public void setVal(String key, Object value) {
                                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                                reward.setValue(key, value);
                                plugin.reloadAdvancedCore(false);
                            }
                        }.open(clickEvent.getPlayer(), reward);
                    }
                }.addLore("EXPLevels to give"))).validator(zeroValidator("EXP can not be 0")));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("EXP") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                int minExp = section.getInt("Min", 0);
                int maxExp = section.getInt("Max", 0);
                int value = ThreadLocalRandom.current().nextInt(minExp, maxExp);
                user.giveExp(value);
                return "" + value;
            }
        }.asPlaceholder("EXP").priority(100).validator(rangeValidator("EXP")));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("EXPLevels") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                int minExp = section.getInt("Min", 0);
                int maxExp = section.getInt("Max", 0);
                int value = ThreadLocalRandom.current().nextInt(minExp, maxExp);
                user.giveExpLevels(value);
                return "" + value;
            }
        }.asPlaceholder("EXP").priority(100).validator(rangeValidator("EXPLevels")));
    }

    private static RewardInjectValidator zeroValidator(String message) {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (data.getDouble(inject.getPath(), -1) == 0) {
                    warning(reward, inject, message);
                }
            }
        };
    }

    private static RewardInjectValidator rangeValidator(String path) {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                int min = data.getInt(path + ".Min", 0);
                int max = data.getInt(path + ".Max", 0);
                if (max == 0) {
                    warning(reward, inject, "Max " + path + " can not be 0");
                }
                if (min > max) {
                    warning(reward, inject, path + ".Min can not be greater than " + path + ".Max");
                }
                if (min == max) {
                    warning(reward, inject, path + ".Min and " + path + ".Max are the same, random range is unnecessary");
                }
            }
        };
    }
}
