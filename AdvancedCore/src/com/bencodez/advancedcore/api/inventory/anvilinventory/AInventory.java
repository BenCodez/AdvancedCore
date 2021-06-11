package com.bencodez.advancedcore.api.inventory.anvilinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import net.wesjd.anvilgui.AnvilGUI;

public class AInventory {

	public interface AnvilClickEventHandler {

		/**
		 * On anvil click.
		 *
		 * @param event the event
		 */
		AnvilGUI.Response onAnvilClick(String value, Player player);
	}

	/**
	 * The Enum AnvilSlot.
	 */
	public enum AnvilSlot {

		/** The input left. */
		INPUT_LEFT(0),

		/** The input right. */
		INPUT_RIGHT(1),

		/** The output. */
		OUTPUT(2);

		/**
		 * By slot.
		 *
		 * @param slot the slot
		 * @return the anvil slot
		 */
		public static AnvilSlot bySlot(int slot) {
			for (AnvilSlot anvilSlot : values()) {
				if (anvilSlot.getSlot() == slot) {
					return anvilSlot;
				}
			}

			return null;
		}

		/** The slot. */
		private int slot;

		/**
		 * Instantiates a new anvil slot.
		 *
		 * @param slot the slot
		 */
		private AnvilSlot(int slot) {
			this.slot = slot;
		}

		/**
		 * Gets the slot.
		 *
		 * @return the slot
		 */
		public int getSlot() {
			return slot;
		}
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public static String getVersion() {
		Server server = Bukkit.getServer();
		final String packageName = server.getClass().getPackage().getName();

		return packageName.substring(packageName.lastIndexOf('.') + 1);
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