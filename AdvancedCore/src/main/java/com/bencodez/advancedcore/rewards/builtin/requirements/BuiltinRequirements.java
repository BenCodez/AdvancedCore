package com.bencodez.advancedcore.rewards.builtin.requirements;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;

/** Registers the built-in AdvancedCore reward requirements. */
public final class BuiltinRequirements {

    private BuiltinRequirements() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        RequirementChance.register(handler, plugin);
        RequirementRewardExpiration.register(handler, plugin);
        RequirementPermission.register(handler, plugin);
        RequirementDayOfMonth.register(handler, plugin);
        RequirementServer.register(handler, plugin);
        RequirementWorld.register(handler, plugin);
        RequirementRewardType.register(handler, plugin);
        RequirementJavascript.register(handler, plugin);
        RequirementDate.register(handler, plugin);
        RequirementLocationDistance.register(handler, plugin);
    }
}
