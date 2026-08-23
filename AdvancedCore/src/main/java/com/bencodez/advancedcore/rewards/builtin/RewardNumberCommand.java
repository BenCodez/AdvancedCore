package com.bencodez.advancedcore.rewards.builtin;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/** Built-in NumberCommand reward. */
public final class RewardNumberCommand {

    private RewardNumberCommand() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("NumberCommand") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                int min = section.getInt("Min", 0);
                int max = section.getInt("Max", 100);
                int number = ThreadLocalRandom.current().nextInt(min, max + 1);
                String command = section.getString("Command", "").replace("%number%", String.valueOf(number));
                MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), command, placeholders);
                return String.valueOf(number);
            }
        }.asPlaceholder("Number").priority(100).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (!data.isInt("NumberCommand.Min") || !data.isInt("NumberCommand.Max")
                        || !data.isString("NumberCommand.Command")) {
                    warning(reward, inject, "NumberCommand requires Min, Max, and Command to be set");
                    return;
                }
                int min = data.getInt("NumberCommand.Min");
                int max = data.getInt("NumberCommand.Max");
                if (min > max) {
                    warning(reward, inject, "NumberCommand Min can not be greater than Max");
                }
                if (min == max) {
                    warning(reward, inject, "NumberCommand Min and Max are the same, random range is unnecessary");
                }
            }
        }));
    }
}
