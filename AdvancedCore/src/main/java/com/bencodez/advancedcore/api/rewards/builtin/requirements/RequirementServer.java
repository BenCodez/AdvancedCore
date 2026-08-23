package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectStringList;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

public final class RequirementServer {

    private RequirementServer() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectString("Server", "") {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String str,
                    RewardOptions rewardOptions) {
                String serverToMatch = str;
                boolean hadPlaceholder = false;
                if (str.isEmpty()) {
                    if (rewardOptions.getPlaceholders().containsKey("Server")) {
                        serverToMatch = rewardOptions.getPlaceholders().get("Server");
                        hadPlaceholder = true;
                    } else if (!rewardOptions.getServer().isEmpty()) {
                        serverToMatch = rewardOptions.getServer();
                    }
                }
                String currentServer = plugin.getOptions().getServer();
                if (!serverToMatch.isEmpty()) {
                    debug("Current Server: " + currentServer + ", ServerToMatch: " + serverToMatch);
                    boolean matched = serverToMatch.equalsIgnoreCase(currentServer);
                    if (!matched && !hadPlaceholder) {
                        rewardOptions.addPlaceholder("Server", serverToMatch);
                    }
                    return matched;
                }
                return true;
            }
        }.priority(100).allowReattempt().alwaysForceNoData().addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueString("Server", null) {
                    @Override
                    public void setValue(Player player, String value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.addOptions(Bukkit.getServer().getName()).addLore("Server to execute reward on"))));

        handler.getInjectedRequirements().add(new RequirementInjectStringList("BlockedServers", new ArrayList<>()) {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> servers,
                    RewardOptions rewardOptions) {
                if (servers.isEmpty()) {
                    return true;
                }
                String currentServer = plugin.getOptions().getServer();
                if (ArrayUtils.containsIgnoreCase(servers, currentServer)) {
                    debug("Current server is in blocked servers list: " + currentServer + " " + servers);
                    return false;
                }
                return true;
            }
        }.priority(100).allowReattempt().addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("BlockedServers", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("List of servers for reward not to run on"))).validator(new RequirementInjectValidator() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        ArrayList<String> list = (ArrayList<String>) data.getList("BlockedServers", null);
                        if (list != null && list.isEmpty()) {
                            warning(reward, inject, "No blocked servers were listed");
                        }
                    }
                }));
    }
}
