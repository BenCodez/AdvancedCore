package com.bencodez.advancedcore.api.inventory.anvilinventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import net.wesjd.anvilgui.AnvilGUI;

public class AInventory {

	public interface AnvilClickEventHandler {

		AnvilGUI.Response onAnvilClick(String value, Player player);
	}

	private void open(Player playerToOpen, AnvilClickEventHandler anvilClickEventHandler, String textToStart,
			String title, String[] strings) {
		new AnvilGUI.Builder().onClose(player -> { // called when the inventory is closing
			player.sendMessage("You closed the inventory.");
		}).onComplete((player, text) -> { // called when the inventory output slot is clicked
			return anvilClickEventHandler.onAnvilClick(text, player);
		}) // prevents the inventory from being closed
				.text(textToStart) // sets the text the GUI should start with
				.title(title) // set the title of the GUI (only works in 1.14+)
				.itemLeft(new ItemBuilder(Material.NAME_TAG).setLore(strings).toItemStack(playerToOpen))
				.plugin(AdvancedCorePlugin.getInstance()) // set the plugin instance
				.open(playerToOpen);
	}

	public AInventory(Player playerToOpen, AnvilClickEventHandler anvilClickEventHandler, String textToStart,
			String title) {

		open(playerToOpen, anvilClickEventHandler, textToStart, title, new String[] {});
	}

	public AInventory(Player playerToOpen, AnvilClickEventHandler anvilClickEventHandler, String textToStart,
			String title, String[] strings) {
		open(playerToOpen, anvilClickEventHandler, textToStart, title, strings);
	}
}