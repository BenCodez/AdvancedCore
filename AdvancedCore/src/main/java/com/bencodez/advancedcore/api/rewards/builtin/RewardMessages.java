package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditMessages;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardMessages {

    private RewardMessages() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectString("Message") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
                    HashMap<String, String> placeholders) {
                user.sendMessage(value, placeholders);
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder("OAK_SIGN"), new EditGUIValueInventory("Messages") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditMessages() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addCheckKey("Message"))).validator(playerMessageValidator()));

        handler.getInjectedRewards().add(new RewardInjectStringList("Messages.Player") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
                    HashMap<String, String> placeholders) {
                user.sendMessage(value, placeholders);
                return null;
            }
        });

        handler.getInjectedRewards().add(new RewardInjectStringList("Message") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
                    HashMap<String, String> placeholders) {
                user.sendMessage(value, placeholders);
                return null;
            }
        });

        handler.getInjectedRewards().add(new RewardInjectStringList("RandomMessage") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
                    HashMap<String, String> placeholders) {
                user.sendMessage(value.get(ThreadLocalRandom.current().nextInt(0, value.size())), placeholders);
                return null;
            }
        });

        handler.getInjectedRewards().add(new RewardInjectString("Messages.Player") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
                    HashMap<String, String> placeholders) {
                user.sendMessage(value, placeholders);
                return null;
            }
        }.validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (data.isString(inject.getPath()) && data.getString(inject.getPath()).isEmpty()) {
                    warning(reward, inject, "No player message set");
                }
            }
        }));

        handler.getInjectedRewards().add(new RewardInjectStringList("Messages.Broadcast") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> value,
                    HashMap<String, String> placeholders) {
                if (plugin.getOptions().getBroadcastBlacklist().contains(user.getPlayerName())) {
                    debug("Not broadcasting for " + user.getPlayerName() + ", in blacklist");
                    return null;
                }
                for (String message : value) {
                    MiscUtils.getInstance().broadcast(PlaceholderUtils.replacePlaceHolders(user.getPlayer(),
                            PlaceholderUtils.replacePlaceHolder(message, placeholders)));
                }
                return null;
            }
        });

        handler.getInjectedRewards().add(new RewardInjectString("Messages.Broadcast") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, String value,
                    HashMap<String, String> placeholders) {
                if (plugin.getOptions().getBroadcastBlacklist().contains(user.getPlayerName())) {
                    debug("Not broadcasting for " + user.getPlayerName() + ", in blacklist");
                    return null;
                }
                MiscUtils.getInstance().broadcast(PlaceholderUtils.replacePlaceHolders(user.getPlayer(),
                        PlaceholderUtils.replacePlaceHolder(value, placeholders)));
                return null;
            }
        }.validator(new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (!data.isList(inject.getPath()) && data.getString(inject.getPath(), "Empty").isEmpty()) {
                    warning(reward, inject, "No broadcast was set");
                }
            }
        }));
    }

    private static RewardInjectValidator playerMessageValidator() {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                String value = data.getString(inject.getPath());
                if (value != null && value.isEmpty()) {
                    warning(reward, inject, "No player message set");
                }
            }
        };
    }
}
