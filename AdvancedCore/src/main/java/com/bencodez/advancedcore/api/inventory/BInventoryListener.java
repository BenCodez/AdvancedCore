package com.bencodez.advancedcore.api.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.player.PlayerUtils;

/**
 * Listener for inventory interactions.
 */
public class BInventoryListener implements Listener {
	private final AdvancedCorePlugin plugin;

	public BInventoryListener(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		Player player = (Player) event.getWhoClicked();
		scheduleFullInventoryCheck(player);

		GUISession session = GUISession.extractSession(player);
		if (session == null) {
			return;
		}

		BInventory gui = session.getInventoryGUI();
		event.setCancelled(true);
		event.setResult(Result.DENY);

		if (event.getClickedInventory() == null || event.getClickedInventory() != PlayerUtils.getTopInventory(player)) {
			return;
		}

		prepareClick(event, player, gui);
		if (isSpamClick(player, gui, event)) {
			return;
		}

		gui.setLastPressTime(System.currentTimeMillis());
		if (gui.isPages()) {
			handlePagedClick(event, player, gui, session);
		} else {
			handleButtonClick(event, player, gui, gui.getButtons().get(event.getSlot()));
		}
	}

	private void scheduleFullInventoryCheck(Player player) {
		plugin.getBukkitScheduler().runTask(plugin, () -> {
			if (player.getInventory().firstEmpty() != -1) {
				plugin.getFullInventoryHandler().check(player);
			}
		}, player);
	}

	private void prepareClick(InventoryClickEvent event, Player player, BInventory gui) {
		if (event.isShiftClick() && event.getRawSlot() < event.getInventory().getSize()) {
			event.setCurrentItem(new ItemStack(Material.AIR));
		}
		player.setItemOnCursor(new ItemStack(Material.AIR));
		player.updateInventory();

		if (plugin.getOptions().isCloseGUIOnShiftClick()) {
			gui.forceClose(player);
		}
		if (gui.isCloseInv()) {
			gui.closeInv(player, null);
		}
	}

	private boolean isSpamClick(Player player, BInventory gui, InventoryClickEvent event) {
		long currentTime = System.currentTimeMillis();
		if (currentTime - gui.getLastPressTime() >= plugin.getOptions().getSpamClickTimeMs()) {
			return false;
		}

		plugin.debug(player.getName() + " spam clicking GUI, preventing exploits");
		player.updateInventory();
		event.setCurrentItem(new ItemStack(Material.AIR));
		gui.forceClose(player);

		String message = plugin.getOptions().getSpamClickMessage();
		if (!message.isEmpty()) {
			player.sendMessage(MessageAPI.colorize(message));
		}
		return true;
	}

	private void handlePagedClick(InventoryClickEvent event, Player player, BInventory gui, GUISession session) {
		int slot = event.getSlot();
		int maxInventorySize = gui.getMaxInvSize();
		int contentSize = InventoryPagination.getContentSize(maxInventorySize);
		int page = session.getPage();

		if (InventoryPagination.isContentSlot(slot, maxInventorySize)) {
			int buttonSlot = InventoryPagination.getButtonSlot(page, slot, maxInventorySize);
			BInventoryButton button = gui.getButtons().get(buttonSlot);
			if (button != null) {
				handleButtonClick(event, player, gui, button);
			}
			return;
		}

		if (slot == contentSize) {
			if (page > 1) {
				gui.playSound(player);
				gui.openInventory(player, page - 1);
			}
			return;
		}

		if (slot == maxInventorySize - 1) {
			if (page < gui.getMaxPage()) {
				gui.playSound(player);
				gui.openInventory(player, page + 1);
			}
			return;
		}

		for (BInventoryButton button : gui.getPageButtons()) {
			if (slot == button.getSlot() + contentSize) {
				handleButtonClick(event, player, gui, button);
				return;
			}
		}
	}

	private void handleButtonClick(InventoryClickEvent event, Player player, BInventory gui, BInventoryButton button) {
		if (button == null) {
			return;
		}

		gui.closeInv(player, button);
		try {
			gui.onClick(event, button);
		} catch (Exception exception) {
			plugin.debug(exception);
		}
	}
}
