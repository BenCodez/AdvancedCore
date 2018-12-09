package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInject;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUI;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueBoolean;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueList;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueNumber;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueString;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

/**
 * The Class RewardGUI.
 */
public class RewardEditGUI {

	/** The instance. */
	static RewardEditGUI instance = new RewardEditGUI();

	/**
	 * Gets the single instance of RewardGUI.
	 *
	 * @return single instance of RewardGUI
	 */
	public static RewardEditGUI getInstance() {
		return instance;
	}

	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Instantiates a new reward GUI.
	 */
	private RewardEditGUI() {
	}

	/**
	 * Gets the current reward.
	 *
	 * @param player
	 *            the player
	 * @return the current reward
	 */
	public Reward getCurrentReward(Player player) {
		return (Reward) PlayerUtils.getInstance().getPlayerMeta(player, "Reward");
	}

	public void openRewardGUI(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCoreHook.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		EditGUI inv = new EditGUI("Reward: " + reward.getRewardName());

		setCurrentReward(player, reward);
		inv.addData("Reward", reward);

		inv.addButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueNumber("Money", reward.getMoney()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setMoney(num.intValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueNumber("MinMoney", reward.getMinMoney()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setMinMoney(num.intValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueNumber("MaxMoney", reward.getMaxMoney()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setMaxMoney(num.intValue());
						plugin.reload();
					}
				}));
		inv.addButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueNumber("Exp", reward.getExp()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setEXP(num.intValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueNumber("MinExp", reward.getMinExp()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setMinExp(num.intValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueNumber("MaxExp", reward.getMaxExp()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setMaxExp(num.intValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueString("RewardType", reward.getRewardType()) {

					@Override
					public void setValue(Player player, String value) {
						getCurrentReward(player).getConfig().setRewardType(value);
						plugin.reload();
					}
				}.addOptions("BOTH", "OFFLINE", "ONLINE")));

		inv.addButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Worlds", reward.getWorlds()) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						getCurrentReward(player).getConfig().setWorlds((ArrayList<String>) value);
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueNumber("Chance", reward.getChance()) {

					@Override
					public void setValue(Player player, Number num) {
						getCurrentReward(player).getConfig().setChance(num.doubleValue());
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueList("Commands.Console", reward.getConsoleCommands()) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						getCurrentReward(player).getConfig().setCommandsConsole((ArrayList<String>) value);
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueList("Commands.Player", reward.getConsoleCommands()) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						getCurrentReward(player).getConfig().setCommandsPlayer((ArrayList<String>) value);
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueString("Permission", reward.getPermission()) {

					@Override
					public void setValue(Player player, String value) {
						getCurrentReward(player).getConfig().setPermission(value);
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("RequirePermission", reward.isRequirePermission()) {

					@Override
					public void setValue(Player player, boolean value) {
						getCurrentReward(player).getConfig().setRequirePermission(value);
						plugin.reload();
					}
				}));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("ForceOffline", reward.isForceOffline()) {

					@Override
					public void setValue(Player player, boolean value) {
						getCurrentReward(player).getConfig().setRequirePermission((boolean) value);
						plugin.reload();
					}
				}));

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.DIAMOND).setName("&cEdit items")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUIItems(player, reward);
			}
		});

		for (RewardInject injectReward : RewardHandler.getInstance().getInjectedRewards()) {
			if (injectReward.isEditable()) {
				for (EditGUIButton b : injectReward.getEditButtons()) {
					b.getEditer().setCurrentValue(reward.getConfig().getConfigData().get(b.getEditer().getKey()));
					inv.addButton(b);
				}
			}
		}

		inv.sort();
		inv.openInventory(player);
	}

	/**
	 * Open reward GUI items.
	 *
	 * @param player
	 *            the player
	 * @param reward
	 *            the reward
	 */
	public void openRewardGUIItems(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCoreHook.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("&cAdd current item inhand");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Add item", ArrayUtils.getInstance().convert(lore),
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = getCurrentReward(player);
				Player player = event.getWhoClicked();

				@SuppressWarnings("deprecation")
				ItemStack item = player.getItemInHand();
				if (item != null && !item.getType().equals(Material.AIR)) {
					String material = item.getType().toString();
					int amount = item.getAmount();
					@SuppressWarnings("deprecation")
					int durability = item.getDurability();
					String name = item.getItemMeta().getDisplayName();
					ArrayList<String> lore = (ArrayList<String>) item.getItemMeta().getLore();
					Map<Enchantment, Integer> enchants = item.getEnchantments();
					String itemStack = material;
					reward.getConfig().setItemAmount(itemStack, amount);
					reward.getConfig().setItemMaterial(itemStack, material);
					reward.getConfig().setItemName(itemStack, name);
					reward.getConfig().setItemLore(itemStack, lore);
					reward.getConfig().setItemDurability(itemStack, durability);
					for (Entry<Enchantment, Integer> entry : enchants.entrySet()) {
						reward.getConfig().setItemEnchant(itemStack, entry.getKey().getKey().getKey(),
								entry.getValue().intValue());
					}
					plugin.reload();
				}
			}

		});

		lore = new ArrayList<String>();
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Remove item", ArrayUtils.getInstance().convert(lore),
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = getCurrentReward(player);
				Player player = event.getWhoClicked();
				String rewardName = reward.getRewardName();
				BInventory inv = new BInventory("RewardRemoveItem: " + rewardName);

				int slot = 0;
				for (String item : reward.getItems()) {
					inv.addButton(slot, new BInventoryButton(item, new String[0],
							reward.getItemStack(UserManager.getInstance().getUser(player), item)) {

						@Override
						public void onClick(ClickEvent event) {
							if (event.getWhoClicked() instanceof Player) {
								Player player = event.getWhoClicked();
								String item = event.getCurrentItem().getItemMeta().getDisplayName();
								Reward reward = (Reward) PlayerUtils.getInstance().getPlayerMeta(player, "Reward");
								reward.getConfig().set("Items." + item, null);
								player.closeInventory();
								player.sendMessage("Removed item");
								plugin.reload();

							}

						}
					});
					slot++;
				}

				inv.openInventory(player);

			}

		});

		inv.openInventory(player);
	}

	/**
	 * Open rewards GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openRewardsGUI(Player player) {
		if (!player.hasPermission(AdvancedCoreHook.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		BInventory inv = new BInventory("Rewards");
		int count = 0;
		for (Reward reward : RewardHandler.getInstance().getRewards()) {
			ArrayList<String> lore = new ArrayList<String>();
			if (reward.getConfig().isDirectlyDefinedReward()) {
				lore.add("&cReward is directly defined, can not edit");
			}

			inv.addButton(count, new BInventoryButton(reward.getRewardName(), ArrayUtils.getInstance().convert(lore),
					new ItemStack(Material.STONE)) {

				@Override
				public void onClick(ClickEvent event) {
					Player player = event.getWhoClicked();

					Reward reward = (Reward) getData("Reward");
					if (!reward.getConfig().isDirectlyDefinedReward()) {
						openRewardGUI(player, reward);
					} else {
						player.sendMessage("Can't edit this reward, directly defined reward");
					}
				}
			}.addData("Reward", reward));
			count++;
		}

		inv.openInventory(player);
	}

	/**
	 * Sets the current reward.
	 *
	 * @param player
	 *            the player
	 * @param reward
	 *            the reward
	 */
	private void setCurrentReward(Player player, Reward reward) {
		PlayerUtils.getInstance().setPlayerMeta(player, "Reward", reward);
	}

}
