package com.bencodez.advancedcore.api.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.scheduler.BukkitScheduler;

public class BInventoryListener implements Listener {
	private AdvancedCorePlugin plugin;

	public BInventoryListener(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		final Player player = (Player) event.getWhoClicked();

		BukkitScheduler.runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (player.getInventory().firstEmpty() != -1) {
					plugin.getFullInventoryHandler().check(player);
				}
			}
		});

		final GUISession session = GUISession.extractSession(player);
		if (session == null) {
			return;
		}
		final BInventory gui = session.getInventoryGUI();
		event.setCancelled(true);
		event.setResult(Result.DENY);
		if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.CHEST) {

			if (event.isShiftClick() && event.getClickedInventory() != null
					&& event.getRawSlot() < event.getInventory().getSize()) {
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

			// prevent spam clicking, to avoid dupe issues on large servers
			long cTime = System.currentTimeMillis();
			if (cTime - gui.getLastPressTime() < plugin.getOptions().getSpamClickTime()) {
				plugin.debug(player.getName() + " spam clicking GUI, preventing exploits");
				player.updateInventory();
				event.setCurrentItem(new ItemStack(Material.AIR));
				gui.forceClose(player);

				// spam click message
				String msg = plugin.getOptions().getSpamClickMessage();
				if (!msg.isEmpty()) {
					player.sendMessage(StringParser.getInstance().colorize(msg));
				}

				return;
			}

			gui.setLastPressTime(cTime);

			BukkitScheduler.runTaskAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					int slot = event.getSlot();
					if (!gui.isPages()) {
						for (int buttonSlot : gui.getButtons().keySet()) {
							BInventoryButton button = gui.getButtons().get(buttonSlot);
							if (slot == buttonSlot) {

								gui.closeInv(player, button);

								try {
									gui.onClick(event, button);
								} catch (Exception e) {
									e.printStackTrace();
								}

							}

						}
					} else {
						final int maxInvSize = gui.getMaxInvSize();
						final int page = session.getPage();
						final int maxPage = gui.getMaxPage();

						if (slot < maxInvSize - 9) {
							int buttonSlot = (page - 1) * (maxInvSize - 9) + event.getSlot();
							BInventoryButton button = gui.getButtons().get(buttonSlot);
							if (button != null) {
								gui.closeInv(player, button);

								try {
									gui.onClick(event, button);
								} catch (Exception e) {
									e.printStackTrace();
								}

								return;
							}

						} else if (slot == maxInvSize - 9) {
							if (page > 1) {

								final int nextPage = page - 1;

								// gui.forceClose(player);
								gui.playSound(player);
								gui.openInventory(player, nextPage);

							}
						} else if (slot == maxInvSize - 1) {
							// AdvancedCorePlugin.getInstance().debug(maxPage + " " +
							// page);
							if (maxPage > page) {

								final int nextPage = page + 1;

								gui.playSound(player);
								// gui.forceClose(player);
								gui.openInventory(player, nextPage);

							}

						}

						for (BInventoryButton b : gui.getPageButtons()) {
							if (slot == b.getSlot() + (gui.getMaxInvSize() - 9)) {
								gui.closeInv(player, b);

								try {
									gui.onClick(event, b);
								} catch (Exception e) {
									e.printStackTrace();
								}
								return;

							}

						}
					}
				}
			});
		}

	}
}
