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
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIValueType;
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

	@SuppressWarnings("deprecation")
	public void openRewardGUI(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCoreHook.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		EditGUI inv = new EditGUI("Reward: " + reward.getRewardName());

		setCurrentReward(player, reward);
		inv.addData("Reward", reward);

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Money", reward.getMoney(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				int num = (int) value;
				getCurrentReward(player).getConfig().setMoney(num);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "MinMoney", reward.getMinMoney(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				int num = (int) value;
				getCurrentReward(player).getConfig().setMinMoney(num);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "MaxMoney", reward.getMaxMoney(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				int num = (int) value;
				getCurrentReward(player).getConfig().setMaxMoney(num);
				plugin.reload();
			}
		});

		inv.addButton(
				new EditGUIButton(new ItemBuilder(Material.PAPER), "Exp", reward.getExp(), EditGUIValueType.NUMBER) {

					@Override
					public void setValue(Player player, Object value) {
						int num = (int) value;
						getCurrentReward(player).getConfig().setEXP(num);
						plugin.reload();
					}
				});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "MinExp", reward.getMinExp(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				int num = (int) value;
				getCurrentReward(player).getConfig().setMinExp(num);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "MaxExp", reward.getMaxExp(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				int num = (int) value;
				getCurrentReward(player).getConfig().setMaxExp(num);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "RewardType", reward.getRewardType(),
				EditGUIValueType.STRING) {

			@Override
			public void setValue(Player player, Object value) {
				String str = (String) value;
				getCurrentReward(player).getConfig().setRewardType(str);
				plugin.reload();
			}
		}.addOptions("BOTH", "OFFLINE", "ONLINE"));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Worlds", reward.getWorlds(),
				EditGUIValueType.LIST) {

			@SuppressWarnings("unchecked")
			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setWorlds((ArrayList<String>) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Chance", reward.getChance(),
				EditGUIValueType.NUMBER) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setChance((double) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Commands.Console",
				reward.getConsoleCommands(), EditGUIValueType.LIST) {

			@SuppressWarnings("unchecked")
			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setCommandsConsole((ArrayList<String>) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Commands.Player", reward.getPlayerCommands(),
				EditGUIValueType.LIST) {

			@SuppressWarnings("unchecked")
			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setCommandsPlayer((ArrayList<String>) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Messages.Broadcast", reward.getBroadcastMsg(),
				EditGUIValueType.STRING) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setMessagesBroadcast((String) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Messages.Player", reward.getRewardMsg(),
				EditGUIValueType.STRING) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setMessagesPlayer((String) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "Permission", reward.getPermission(),
				EditGUIValueType.STRING) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setPermission((String) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "RequirePermission",
				reward.isRequirePermission(), EditGUIValueType.BOOLEAN) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setRequirePermission((boolean) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "ForceOffline", reward.isForceOffline(),
				EditGUIValueType.BOOLEAN) {

			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setRequirePermission((boolean) value);
				plugin.reload();
			}
		});

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER), "RandomCommand", reward.getRandomCommand(),
				EditGUIValueType.LIST) {

			@SuppressWarnings("unchecked")
			@Override
			public void setValue(Player player, Object value) {
				getCurrentReward(player).getConfig().setRandomCommand((ArrayList<String>) value);
				plugin.reload();
			}
		});

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
