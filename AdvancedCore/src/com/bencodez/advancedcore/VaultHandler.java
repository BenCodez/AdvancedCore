package com.bencodez.advancedcore;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class VaultHandler {
	public void loadVault(AdvancedCorePlugin plugin) {
		if (plugin.isLoadVault()) {
			plugin.getBukkitScheduler().runTaskLater(plugin, new Runnable() {

				@Override
				public void run() {
					if (setupEconomy(plugin)) {
						plugin.getLogger().info("Successfully hooked into vault economy!");
					} else {
						plugin.getLogger().warning("Failed to hook into vault economy");
					}

					if (setupPermissions(plugin)) {
						plugin.getLogger().info("Hooked into vault permissions");

						plugin.getRewardHandler()
								.addInjectedRequirements(new RequirementInjectString("VaultGroup", "") {

									@Override
									public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user,
											String type, RewardOptions rewardOptions) {
										if (type.equals("")) {
											return true;
										}
										String group = "";
										if (!rewardOptions.isGiveOffline() && user.isOnline()) {
											group = getPerms().getPrimaryGroup(user.getPlayer());
										} else {
											group = getPerms().getPrimaryGroup(null, user.getOfflinePlayer());
										}
										if (group.equalsIgnoreCase(type)) {
											return true;
										}
										return false;
									}
								}.priority(100).addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
										new EditGUIValueString("VaultGroup", null) {

											@Override
											public void setValue(Player player, String value) {
												RewardEditData reward = (RewardEditData) getInv().getData("Reward");
												reward.setValue(getKey(), value);
												plugin.reloadAdvancedCore(false);
											}
										}.addOptions(getPerms().getGroups()))));
					} else {
						plugin.getLogger().warning("Failed to hook into vault permissions");
					}
				}
			}, 5);
		}
	}

	private boolean setupEconomy(AdvancedCorePlugin plugin) {
		if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	@Getter
	private Economy econ = null;

	private boolean setupPermissions(AdvancedCorePlugin plugin) {
		if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Permission> rsp = plugin.getServer().getServicesManager()
				.getRegistration(Permission.class);
		if (rsp == null) {
			return false;
		}
		perms = rsp.getProvider();
		return perms != null;
	}

	@Getter
	private Permission perms;

}
