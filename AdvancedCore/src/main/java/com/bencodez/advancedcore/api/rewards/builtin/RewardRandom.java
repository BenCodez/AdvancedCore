package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardRandom {

    private RewardRandom() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Random") {
            @SuppressWarnings("unchecked")
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                if (MiscUtils.getInstance().checkChance(section.getDouble("Chance", 100), 100)) {
                    if (section.getBoolean("PickRandom", true)) {
                        ArrayList<String> rewards = (ArrayList<String>) section.getList("Rewards", new ArrayList<>());
                        if (rewards != null && !rewards.isEmpty()) {
                            String selected = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
                            if (!selected.equals("")) {
                                handler.giveReward(user, selected, new RewardOptions().setPlaceholders(placeholders));
                            }
                        }
                    } else {
                        new RewardBuilder(reward.getConfig().getConfigData(), "Random.Rewards")
                                .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                    }
                } else {
                    new RewardBuilder(reward.getConfig().getConfigData(), "Random.FallBack")
                            .withPrefix(reward.getName()).withPlaceHolder(placeholders).send(user);
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.Rewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Random.Rewards"));
                }
                if (direct.getFileData().isConfigurationSection(direct.getPath() + direct.needsDot() + "Random.FallBack")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Random.FallBack"));
                }
                return subs;
            }
        }.priority(10));
    }
}
