package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.UpdatingBInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditItems extends RewardEdit {
	public RewardEditItems() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Items: " + reward.getName());
		inv.addData("Reward", reward);

		UpdatingBInventoryButton b = new UpdatingBInventoryButton(new ItemBuilder(Material.PAPER)
				.setName("&cView current items").addLoreLine("&aDisplaying all current items")
				.addLoreLine("This doesn't support conditional items"), 750, 750) {

			@Override
			public void onClick(ClickEvent clickEvent) {

			}

			@Override
			public ItemBuilder onUpdate(Player player) {
				return nextItem();
			}

			public ItemBuilder nextItem() {
				try {
					int num = (int) getData("CurrentItem");
					@SuppressWarnings("unchecked")
					ArrayList<String> set = (ArrayList<String>) getData("ItemsKeys");
					num++;
					if (num >= set.size()) {
						num = 0;
					}
					addData("CurrentItem", num);
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					return new ItemBuilder(reward.getData().getConfigurationSection("Items." + set.get(num)))
							.addLoreLine("&cDisplaying: " + set.get(num));
				} catch (Exception e) {
					e.printStackTrace();
					return new ItemBuilder(Material.STONE).setName("Error");
				}
			}
		};
		b.addData("ItemsKeys",
				ArrayUtils.getInstance().convert(reward.getData().getConfigurationSection("Items").getKeys(false)));
		b.addData("CurrentItem", 0);
		inv.addButton(b);

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

}
