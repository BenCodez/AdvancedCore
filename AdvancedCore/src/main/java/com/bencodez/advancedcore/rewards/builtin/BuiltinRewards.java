package com.bencodez.advancedcore.rewards.builtin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;

/** Registers the built-in AdvancedCore reward implementations. */
public final class BuiltinRewards {

    private BuiltinRewards() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        // Preserve the historical registration order because sorting by priority is stable,
        // so order still matters for injects with the same priority. Command implementations
        // live together in RewardCommands, but are registered at their original positions.
        RewardMoney.register(handler, plugin);
        RewardCommands.registerNumberCommand(handler, plugin);
        RewardExp.register(handler, plugin);
        RewardMessages.register(handler, plugin);
        RewardCommands.registerCommand(handler, plugin);
        RewardActionBar.register(handler, plugin);
        RewardCommands.registerCommands(handler, plugin);
        RewardJavascript.register(handler, plugin);
        RewardLucky.register(handler, plugin);
        RewardRandom.register(handler, plugin);
        RewardSubRewards.register(handler, plugin);
        RewardCommands.registerRandomCommand(handler, plugin);
        RewardRandomReward.register(handler, plugin);
        RewardTempPermission.register(handler, plugin);
        RewardAdvancedRewards.register(handler, plugin);
        RewardAdvancedRandomReward.register(handler, plugin);
        RewardPriority.register(handler, plugin);
        RewardPotions.register(handler, plugin);
        RewardTitle.register(handler, plugin);
        RewardBossBar.register(handler, plugin);
        RewardSound.register(handler, plugin);
        RewardEffect.register(handler, plugin);
        RewardFirework.register(handler, plugin);
        RewardItems.register(handler, plugin);
        RewardChoices.register(handler, plugin);
        RewardAdvancedPriority.register(handler, plugin);
        RewardAdvancedWorld.register(handler, plugin);
        RewardSpecialChance.register(handler, plugin);
    }
}
