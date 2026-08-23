package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementPermission {

    private RequirementPermission() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectString("Permission", "") {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String str,
                    RewardOptions rewardOptions) {
                if (!reward.getConfig().getRequirePermission()) {
                    return true;
                }
                if (str.isEmpty()) {
                    str = "AdvancedCore.Reward." + reward.getName();
                }

                boolean reverse = false;
                if (str.startsWith("!")) {
                    reverse = true;
                    str = str.substring(1);
                    debug("Doing permission check in reverse");
                }

                boolean perm = PlayerManager.getInstance().hasServerPermission(UUID.fromString(user.getUUID()),
                        user.getPlayerName(), str);
                if (reverse) {
                    perm = !perm;
                }
                if (!perm) {
                    debug(user.getPlayerName() + " does not have permission " + str + " to get reward "
                            + reward.getName() + ", reverse: " + reverse);
                    return false;
                }
                return true;
            }
        }.priority(100).alwaysForce().addEditButton(
                new EditGUIButton(new ItemBuilder("IRON_DOOR"), new EditGUIValueString("Permission", null) {
                    @Override
                    public void setValue(Player player, String value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Set permission required to be given, set RequirePermission to true if using this")))
                .addEditButton(new EditGUIButton(new EditGUIValueBoolean("RequirePermission", null) {
                    @Override
                    public void setValue(Player player, boolean value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("If true, permission is required to run reward")))
                .validator(new RequirementInjectValidator() {
                    @Override
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        if (!data.getBoolean("RequirePermission", false)
                                && !data.getString("Permission", "").isEmpty()) {
                            warning(reward, inject, "Detected permission set but RequirePermission is false");
                        }
                    }
                }.addPath("RequirePermission")));
    }
}
