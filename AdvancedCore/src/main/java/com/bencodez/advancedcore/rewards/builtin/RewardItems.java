package com.bencodez.advancedcore.rewards.builtin;

import java.util.HashMap;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditItems;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectKeys;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

public final class RewardItems {

    private RewardItems() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Item") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                ItemBuilder builder = new ItemBuilder(section);
                builder.setCheckLoreLength(false);
                user.giveItem(builder);
                return null;
            }
        }.validator(itemValidator()));

        handler.getInjectedRewards().add(new RewardInjectKeys("RandomItem") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
                    ConfigurationSection data, HashMap<String, String> placeholders) {
                if (!section.isEmpty()) {
                    String item = ArrayUtils.pickRandom(ArrayUtils.convert(section));
                    ItemBuilder builder = new ItemBuilder(data.getConfigurationSection(item));
                    builder.setCheckLoreLength(false);
                    user.giveItem(builder);
                    return item;
                }
                return null;
            }
        }.asPlaceholder("RandomItem").priority(90).validator(randomItemValidator()));

        handler.getInjectedRewards().add(new RewardInjectKeys("Items") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
                    ConfigurationSection data, HashMap<String, String> placeholders) {
                boolean oneChance = reward.getConfig().getConfigData().getBoolean("OnlyOneItemChance", false);
                if (!section.isEmpty()) {
                    for (String item : section) {
                        ItemBuilder builder = new ItemBuilder(data.getConfigurationSection(item));
                        builder.setCheckLoreLength(false);
                        user.giveItem(builder.setPlaceholders(placeholders));
                        debug("Giving item " + item + ":" + builder);
                        if (builder.isChancePass() && oneChance) {
                            return item;
                        }
                    }
                }
                return "";
            }
        }.priority(90).asPlaceholder("Item").validator(itemsValidator().addPath("OnlyOneItemChance"))
                .addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Items") {
                    @Override
                    public void openInventory(ClickEvent clickEvent) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        new RewardEditItems(plugin) {
                            @Override
                            public void setVal(String key, Object value) {
                                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                                reward.setValue(key, value);
                                plugin.reloadAdvancedCore(false);
                            }
                        }.open(clickEvent.getPlayer(), reward);
                    }
                }.addLore("Edit items"))));
    }

    private static RewardInjectValidator itemValidator() {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                String material = data.getString("Item.Material", "");
                if (material.isEmpty()) {
                    warning(reward, inject, "No material is set on item");
                    return;
                }
                validateMaterial(this, reward, inject, material, "");
            }
        };
    }

    private static RewardInjectValidator randomItemValidator() {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                ConfigurationSection section = data.getConfigurationSection("RandomItem");
                if (section == null) {
                    warning(reward, inject, "RandomItem section is missing");
                    return;
                }
                for (String item : section.getKeys(false)) {
                    String material = section.getString(item + ".Material", "");
                    if (material.isEmpty()) {
                        warning(reward, inject, "No material is set on item: " + item);
                    } else {
                        validateMaterial(this, reward, inject, material, " on RandomItem." + item);
                    }
                }
            }
        };
    }

    private static RewardInjectValidator itemsValidator() {
        return new RewardInjectValidator() {
            @Override
            public void onValidate(Reward reward, RewardInject inject, ConfigurationSection data) {
                if (!data.isConfigurationSection("Items")) {
                    warning(reward, inject, "Invalid item section");
                    return;
                }
                for (String item : data.getConfigurationSection("Items").getKeys(false)) {
                    String material = data.getString("Items." + item + ".Material", "");
                    if (material.isEmpty()) {
                        try {
                            Material.valueOf(item);
                        } catch (Exception e) {
                            warning(reward, inject, "No material is set on item: " + item);
                        }
                    } else {
                        validateMaterial(this, reward, inject, material, "");
                    }
                    if (data.getInt("Items." + item + ".Amount", 0) == 0
                            && data.getInt("Items." + item + ".MinAmount", 0) == 0
                            && data.getInt("Items." + item + ".MaxAmount") == 0) {
                        warning(reward, inject, "No amount on item: " + item);
                    }
                }
            }
        };
    }

    private static void validateMaterial(RewardInjectValidator validator, Reward reward, RewardInject inject,
            String material, String suffix) {
        try {
            Material matched = Material.matchMaterial(material.toUpperCase());
            if (matched == null) {
                matched = Material.matchMaterial(material, true);
                if (matched != null) {
                    validator.warning(reward, inject,
                            "Found legacy material: " + material + ", please update material" + suffix);
                } else {
                    validator.warning(reward, inject, "Invalid material set: " + material);
                }
            }
        } catch (NoSuchMethodError ignored) {
        }
    }
}
