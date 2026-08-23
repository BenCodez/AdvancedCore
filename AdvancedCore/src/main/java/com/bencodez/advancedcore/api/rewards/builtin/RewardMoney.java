package com.bencodez.advancedcore.api.rewards.builtin;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

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
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMoney;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectDouble;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardMoney {

    private RewardMoney() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectDouble("Money", 0) {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, double num,
                    HashMap<String, String> placeholders) {
                user.giveMoney(num);
                return "" + (int) num;
            }
        }.asPlaceholder("Money").priority(100)
                .addEditButton(new EditGUIButton(new ItemBuilder(Material.DIAMOND), new EditGUIValueInventory("Money") {
                    @Override
                    public void openInventory(ClickEvent clickEvent) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        new RewardEditMoney() {
                            @Override
                            public void setVal(String key, Object value) {
                                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                                reward.setValue(key, value);
                                plugin.reloadAdvancedCore(false);
                            }
                        }.open(clickEvent.getPlayer(), reward);
                    }
                }.addLore("Money to execute, may not work on some economy plugins").addLore("Supports random amounts")))
                .validator(new RewardInjectValidator() {
                    @Override
                    public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                        if (data.getDouble(inject.getPath(), -1) == 0) {
                            warning(reward, inject, "Money can not be 0");
                        }
                    }
                }));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Money") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                double minMoney = section.getDouble("Min", 0);
                double maxMoney = section.getDouble("Max", 0);
                double value = ThreadLocalRandom.current().nextDouble(minMoney, maxMoney);
                if (section.getBoolean("Round")) {
                    value = Math.round(value);
                    user.giveMoney(value);
                    return "" + value;
                }
                DecimalFormat format = new DecimalFormat("##.00");
                user.giveMoney(value);
                return "" + format.format(value);
            }
        }.asPlaceholder("Money").priority(100).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                double min = data.getDouble("Money.Min", 0);
                double max = data.getDouble("Money.Max", 0);
                if (!data.isConfigurationSection("Money")) {
                    return;
                }
                if (max == 0) {
                    warning(reward, inject, "Maximum money can not be 0");
                }
                if (min > max) {
                    warning(reward, inject, "Money.Min can not be greater than Money.Max");
                }
                if (min == max) {
                    warning(reward, inject, "Money.Min and Money.Max are the same, random range is unnecessary");
                }
            }
        }));
    }
}
