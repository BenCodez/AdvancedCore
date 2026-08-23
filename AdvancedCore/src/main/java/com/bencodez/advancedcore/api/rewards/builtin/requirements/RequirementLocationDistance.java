package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditLocationDistance;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementLocationDistance {

    private RequirementLocationDistance() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectConfigurationSection("LocationDistance") {
            @Override
            public boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    RewardOptions rewardOptions) {
                if (!user.isOnline()) {
                    plugin.debug("user not online");
                    return false;
                }
                World world = Bukkit.getWorld(section.getString("World"));
                if (world == null) {
                    plugin.debug("Invalid world for LocationDistance");
                    return false;
                }
                Location loc = new Location(world, section.getInt("X"), section.getInt("Y"), section.getInt("Z"));
                Location playerLocation = user.getPlayer().getLocation();
                if (!loc.getWorld().getName().equals(playerLocation.getWorld().getName())) {
                    plugin.debug("Worlds don't match");
                    return false;
                }
                return playerLocation.distance(loc) < section.getInt("Distance");
            }
        }.priority(90).validator(new RequirementInjectValidator() {
            @Override
            public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                if (!data.isConfigurationSection("LocationDistance")) {
                    return;
                }
                ConfigurationSection section = data.getConfigurationSection("LocationDistance");
                try {
                    new Location(Bukkit.getWorld(section.getString("World")), section.getInt("X"), section.getInt("Y"),
                            section.getInt("Z"));
                } catch (Exception e) {
                    warning(reward, inject, "Failed to get location for LocationDistance");
                    e.printStackTrace();
                }
                if (section.getInt("Distance") < 0) {
                    warning(reward, inject, "Invalid distance for LocationDistance");
                }
            }
        }).addEditButton(new EditGUIButton(new ItemBuilder("MAP"), new EditGUIValueInventory("LocationDistance") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditLocationDistance() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Require player to be within a certain distance of location to get reward"))));
    }
}
