package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/** The deterministic, reattemptable player-context requirements. */
public final class RequirementGameMode {
    private RequirementGameMode() { }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        RequirementInjectValidator validator = new RequirementInjectValidator() {
            @Override
            public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                for (String path : new String[] { "GameModes", "BlackListedGameModes" }) {
                    if (!RewardEligibility.validModes(data, path)) warning(reward, inject,
                            path + " must be a list of Bukkit gamemode names");
                }
            }
        }.addPath("BlackListedGameModes");
        handler.getInjectedRequirements().add(new RequirementInject("GameModes") {
            @Override
            public boolean requiresPlayerThread() { return true; }

            @Override
            public boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
                    RewardOptions options) {
                return reward.checkPlayerEligibility(user, options);
            }

            @Override
            public void validate(Reward reward, ConfigurationSection data) {
                validator.onValidate(reward, this, data);
            }
        }.priority(100).allowReattempt().alwaysForceNoData().validator(validator));
    }
}
