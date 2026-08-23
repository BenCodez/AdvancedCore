package com.bencodez.advancedcore.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardCommands {

    private RewardCommands() {
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

        handler.getInjectedRewards().add(new RewardInjectString("Command") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
                    HashMap<String, String> placeholders) {
                MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), value, placeholders);
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueString("Command", null) {
            @Override
            public void setValue(Player player, String value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Execute single console command"))).validator(commandValidator()));

        handler.getInjectedRewards().add(new RewardInjectStringList("Commands") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), list, placeholders, true);
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder("COMMAND_BLOCK"), new EditGUIValueList("Commands", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("List of console commands"))).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (data.isList(inject.getPath()) && !data.isConfigurationSection(inject.getPath())) {
                    List<String> list = data.getStringList(inject.getPath());
                    if (list.isEmpty()) {
                        warning(reward, inject, "No commands listed");
                    }
                    for (String command : list) {
                        if (command.startsWith("/")) {
                            warning(reward, inject, "Commands can not start with /");
                        }
                    }
                }
            }
        }));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Commands") {
            @SuppressWarnings("unchecked")
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                ArrayList<String> consoleCommands = (ArrayList<String>) section.getList("Console", new ArrayList<>());
                ArrayList<String> userCommands = (ArrayList<String>) section.getList("Player", new ArrayList<>());
                if (!consoleCommands.isEmpty()) {
                    MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(), consoleCommands, placeholders,
                            section.getBoolean("Stagger", true));
                }
                if (!userCommands.isEmpty()) {
                    user.preformCommand(userCommands, placeholders);
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Commands.Console", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Old style for console commands"))).addEditButton(
                new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Commands.Player", null) {
                    @Override
                    public void setValue(Player player, ArrayList<String> value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                        reward.reOpenEditGUI(player);
                    }
                }.addLore("Execute commands as player"))));

        handler.getInjectedRewards().add(new RewardInjectStringList("RandomCommand") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    MiscUtils.getInstance().executeConsoleCommands(user.getPlayerName(),
                            list.get(ThreadLocalRandom.current().nextInt(list.size())), placeholders);
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("RandomCommand", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Execute random command"))).validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                List<String> list = data.getStringList(inject.getPath());
                if (list.isEmpty()) {
                    warning(reward, inject, "No rewards listed for random reward");
                } else if (list.size() == 1) {
                    warning(reward, inject, "Only one reward listed for random reward");
                }
            }
        }));
    }

    private static RewardInjectValidator commandValidator() {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                String command = data.getString(inject.getPath());
                if (command != null && command.startsWith("/")) {
                    warning(reward, inject, "Can't start command with /");
                }
            }
        };
    }
}
