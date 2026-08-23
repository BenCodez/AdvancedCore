package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectStringList;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementWorld {

    private RequirementWorld() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectStringList("Worlds", new ArrayList<>()) {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> worlds,
                    RewardOptions rewardOptions) {
                if (worlds.isEmpty()) {
                    if (plugin.getOptions().getDefaultRewardWorlds().isEmpty() || !rewardOptions.isUseDefaultWorlds()) {
                        debug("No whitelisted worlds specified");
                        return true;
                    }
                    Player player = user.getPlayer();
                    if (player == null) {
                        debug("No player");
                        return false;
                    }
                    reward.checkRewardFile();
                    String world = player.getWorld().getName();
                    if (plugin.getOptions().getDefaultRewardWorlds().contains(world)) {
                        debug("Player in default whitelisted world: " + world);
                        return true;
                    }
                    user.setCheckWorld(true);
                    debug("Player not in default whitelisted world: " + world);
                    return false;
                }

                Player player = user.getPlayer();
                if (player == null) {
                    debug("No player");
                    return false;
                }
                reward.checkRewardFile();
                String world = player.getWorld().getName();
                if (worlds.contains(world)) {
                    debug("Player in whitelisted world: " + world);
                    return true;
                }
                user.setCheckWorld(true);
                debug("Player not in whitelisted world: " + world);
                return false;
            }
        }.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
                new EditGUIButton(new ItemBuilder("END_PORTAL_FRAME"), new EditGUIValueList("Worlds", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Worlds to execute reward in, only executes into one reward")))
                .validator(listValidator("Worlds")));

        handler.getInjectedRequirements().add(new RequirementInjectStringList("BlackListedWorlds", new ArrayList<>()) {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> worlds,
                    RewardOptions rewardOptions) {
                if (worlds.isEmpty()) {
                    if (plugin.getOptions().getDefaultRewardBlackListedWorlds().isEmpty()
                            || !rewardOptions.isUseDefaultWorlds()) {
                        debug("No blacklisted worlds specified");
                        return true;
                    }
                    Player player = user.getPlayer();
                    if (player == null) {
                        debug("No player");
                        return false;
                    }
                    reward.checkRewardFile();
                    String world = player.getWorld().getName();
                    if (plugin.getOptions().getDefaultRewardBlackListedWorlds().contains(world)) {
                        user.setCheckWorld(true);
                        debug("Player in default blacklisted world: " + world);
                        return false;
                    }
                    debug("Player not in default blacklisted worlds");
                    return true;
                }

                Player player = user.getPlayer();
                if (player == null) {
                    debug("No player");
                    return false;
                }
                reward.checkRewardFile();
                String world = player.getWorld().getName();
                if (worlds.contains(world)) {
                    user.setCheckWorld(true);
                    debug("Player in default blacklisted world: " + world);
                    return false;
                }
                debug("Player not in blacklisted worlds");
                return true;
            }
        }.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
                new EditGUIButton(new ItemBuilder("END_PORTAL_FRAME"), new EditGUIValueList("BlackListedWorlds", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Worlds to never execute the reward in"))).validator(listValidator("BlackListedWorlds")));
    }

    private static RequirementInjectValidator listValidator(String path) {
        return new RequirementInjectValidator() {
            @Override
            @SuppressWarnings("unchecked")
            public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                ArrayList<String> list = (ArrayList<String>) data.getList(path, null);
                if (list != null && list.isEmpty()) {
                    warning(reward, inject, "No worlds were listed");
                }
            }
        };
    }
}
