package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.UpdatingBInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditItems extends RewardEdit {
	private AdvancedCorePlugin plugin;

	public RewardEditItems(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Items: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.PAPER).setName("&cView current items")
				.addLoreLine("&aDisplaying all current items on next slot")
				.addLoreLine("&aThis doesn't support adding conditional items")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
			}
		});

		UpdatingBInventoryButton b = new UpdatingBInventoryButton(plugin,
				new ItemBuilder(Material.PAPER).setName("&cView current items")
						.addLoreLine("&aDisplaying all current items")
						.addLoreLine("This doesn't support adding conditional items"),
				750, 750) {

			public ItemBuilder nextItem() {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Items")) {
					try {
						int num = (int) getData("CurrentItem");
						@SuppressWarnings("unchecked")
						ArrayList<String> set = (ArrayList<String>) getData("ItemsKeys");
						num++;
						if (num >= set.size()) {
							num = 0;
						}
						addData("CurrentItem", num);

						return new ItemBuilder(reward.getData().getConfigurationSection("Items." + set.get(num)))
								.addLoreLine("&cDisplaying: " + set.get(num));
					} catch (Exception e) {
						e.printStackTrace();
						return new ItemBuilder(Material.STONE).setName("Error");
					}
				}
				return new ItemBuilder(Material.PAPER).setName("&cNo Items");
			}

			@Override
			public void onClick(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}

			@Override
			public ItemBuilder onUpdate(Player player) {
				return nextItem();
			}
		};
		if (reward.hasPath("Items")) {
			b.addData("ItemsKeys",
					ArrayUtils.getInstance().convert(reward.getData().getConfigurationSection("Items").getKeys(false)));
		} else {
			b.addData("ItemsKeys", new ArrayList<String>());
		}

		b.addData("CurrentItem", 0);
		inv.addButton(b);

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Items") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Items")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove item"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Items") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Items")) {
					openEdit(player, reward);
				}
			}
		}).setName("&aEdit existing item"));

		inv.addButton(
				new BInventoryButton(new ItemBuilder(Material.PAPER).setName("&aAdd item in hand").addLoreLine("Click for more")) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						openAdd(clickEvent.getPlayer(), reward);
					}
				});

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openAdd(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Item Add: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(new BInventoryButton(
				new ItemBuilder(player.getInventory().getItemInMainHand().clone()).addLoreLine("&cClick to add")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				ItemBuilder item = new ItemBuilder(clickEvent.getPlayer().getInventory().getItemInMainHand().clone());
				Map<String, Object> map = item.getConfiguration(false);
				for (Entry<String, Object> entry : map.entrySet()) {
					reward.setValue("Items." + item.getType().toString() + "." + entry.getKey(), entry.getValue());
				}
				open(player, reward);
			}
		});
		inv.addButton(new BInventoryButton(new ItemBuilder(player.getInventory().getItemInMainHand().clone())
				.addLoreLine("&cClick to add with exact data")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				ItemBuilder item = new ItemBuilder(clickEvent.getPlayer().getInventory().getItemInMainHand().clone());
				Map<String, Object> map = item.getConfiguration(true);
				for (Entry<String, Object> entry : map.entrySet()) {
					reward.setValue("Items." + item.getType().toString() + ".ItemStack." + entry.getKey(),
							entry.getValue());
				}
				open(player, reward);
			}
		});

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

	public void openEdit(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Item Edit: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Items").getKeys(false)) {
			inv.addButton(new BInventoryButton(new ItemBuilder(reward.getData().getConfigurationSection("Items." + key))
					.setName("&c" + key).addLoreLine("&cClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					String key = (String) getData("key");
					openEditItem(player, key, reward);
				}
			}.addData("key", key));
		}

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

	public void openEditItem(Player player, String item, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Item " + item + ": " + reward.getName());
		inv.addData("Reward", reward);
		inv.addData("Item", item);

		inv.addButton(new BInventoryButton(new ItemBuilder(reward.getData().getConfigurationSection("Items." + item))) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				String item = (String) getInv().getData("Item");
				openEditItem(player, item, reward);
			}
		});

		inv.addButton(new EditGUIButton(
				new EditGUIValueString("Items." + item + ".Name", reward.getValue("Items." + item + ".Name")) {

					@Override
					public void setValue(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						reloadAdvancedCore();
						String item = (String) getInv().getData("Item");
						openEditItem(player, item, reward);
					}
				}));

		inv.addButton(new EditGUIButton(
				new EditGUIValueList("Items." + item + ".Lore", reward.getValue("Items." + item + ".Lore")) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						reloadAdvancedCore();
						String item = (String) getInv().getData("Item");
						openEditItem(player, item, reward);
					}
				}));
		inv.addButton(new EditGUIButton(
				new EditGUIValueNumber("Items." + item + ".Amount", reward.getValue("Items." + item + ".Amount")) {

					@Override
					public void setValue(Player player, Number value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value.intValue());
						reloadAdvancedCore();
						String item = (String) getInv().getData("Item");
						openEditItem(player, item, reward);
					}
				}));

		inv.addButton(new EditGUIButton(new EditGUIValueNumber("Items." + item + ".CustomModelData",
				reward.getValue("Items." + item + ".CustomModelData")) {

			@Override
			public void setValue(Player player, Number value) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				reward.setValue(getKey(), value.intValue());
				reloadAdvancedCore();
				String item = (String) getInv().getData("Item");
				openEditItem(player, item, reward);
			}
		}));

		ArrayList<String> flagList = new ArrayList<String>();
		for (ItemFlag flag : ItemFlag.values()) {
			flagList.add(flag.toString());
		}
		inv.addButton(new EditGUIButton(
				new EditGUIValueList("Items." + item + ".ItemFlags", reward.getValue("Items." + item + ".ItemFlags")) {

					@Override
					public void setValue(Player player, ArrayList<String> value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						reloadAdvancedCore();
						String item = (String) getInv().getData("Item");
						openEditItem(player, item, reward);
					}
				}).addOptions(ArrayUtils.getInstance().convert(flagList)));

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

	public void openRemove(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Item Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Items").getKeys(false)) {
			inv.addButton(new BInventoryButton(new ItemBuilder(reward.getData().getConfigurationSection("Items." + key))
					.setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("Items." + key, null);
					reloadAdvancedCore();
					open(player, reward);
				}
			});
		}

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

}
