package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditDate;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementDate {

    private RequirementDate() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectConfigurationSection("Date") {
            @Override
            public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    RewardOptions rewardOptions) {
                LocalDateTime now = LocalDateTime.now();
                if (section.isString("WeekDay")) {
                    String requiredWeekDay = section.getString("WeekDay").toUpperCase();
                    if (!now.getDayOfWeek().name().equals(requiredWeekDay)) {
                        debug("WeekDay does not match: " + requiredWeekDay);
                        return false;
                    }
                } else if (section.isInt("WeekDay")) {
                    int requiredWeekDay = section.getInt("WeekDay");
                    if (now.getDayOfWeek().getValue() != requiredWeekDay) {
                        debug("WeekDay does not match: " + requiredWeekDay);
                        return false;
                    }
                }
                if (section.isInt("DayOfMonth")) {
                    int requiredDayOfMonth = section.getInt("DayOfMonth");
                    if (now.getDayOfMonth() != requiredDayOfMonth) {
                        debug("DayOfMonth does not match: " + requiredDayOfMonth);
                        return false;
                    }
                }
                if (section.isString("Month")) {
                    String requiredMonth = section.getString("Month").toUpperCase();
                    if (!now.getMonth().name().equals(requiredMonth)) {
                        debug("Month does not match: " + requiredMonth);
                        return false;
                    }
                }
                return true;
            }
        }.priority(90).validator(new RequirementInjectValidator() {
            @Override
            public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                if (!data.isConfigurationSection("Date")) {
                    return;
                }
                ConfigurationSection section = data.getConfigurationSection("Date");
                if (section.isString("WeekDay")) {
                    try {
                        DayOfWeek.valueOf(section.getString("WeekDay").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        warning(reward, inject, "Invalid WeekDay: " + section.getString("WeekDay"));
                    }
                } else if (section.isInt("WeekDay")) {
                    int weekDay = section.getInt("WeekDay");
                    if (weekDay < 1 || weekDay > 7) {
                        warning(reward, inject, "Invalid WeekDay: " + weekDay);
                    }
                }
                if (section.isInt("DayOfMonth")) {
                    int day = section.getInt("DayOfMonth");
                    if (day < 1 || day > 31) {
                        warning(reward, inject, "Invalid DayOfMonth: " + day);
                    }
                }
                if (section.isString("Month")) {
                    try {
                        Month.valueOf(section.getString("Month").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        warning(reward, inject, "Invalid Month: " + section.getString("Month"));
                    }
                }
            }
        }).addEditButton(new EditGUIButton(new ItemBuilder("PAPER"), new EditGUIValueInventory("Date") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditDate() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Edit date-based requirements for the reward"))));
    }
}
