package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory;

import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventory1_7_R4Handler;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventoryReflectionHandler;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventoryVersionHandler;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

/**
 * The Class AInventory.
 */
public class AInventory {

	/**
	 * The Class AnvilClickEvent.
	 */
	public class AnvilClickEvent {

		/** The slot. */
		private AnvilSlot slot;

		/** The name. */
		private String name;

		/** The close. */
		private boolean close = true;

		/** The destroy. */
		private boolean destroy = true;

		/** The player. */
		private Player player;

		/**
		 * Instantiates a new anvil click event.
		 *
		 * @param slot
		 *            the slot
		 * @param name
		 *            the name
		 * @param player
		 *            the player
		 */
		public AnvilClickEvent(AnvilSlot slot, String name, Player player) {
			this.slot = slot;
			this.name = name;
			this.player = player;
		}

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * Gets the player.
		 *
		 * @return the player
		 */
		public Player getPlayer() {
			return player;
		}

		/**
		 * Gets the slot.
		 *
		 * @return the slot
		 */
		public AnvilSlot getSlot() {
			return slot;
		}

		/**
		 * Gets the will close.
		 *
		 * @return the will close
		 */
		public boolean getWillClose() {
			return close;
		}

		/**
		 * Gets the will destroy.
		 *
		 * @return the will destroy
		 */
		public boolean getWillDestroy() {
			return destroy;
		}

		/**
		 * Sets the will close.
		 *
		 * @param close
		 *            the new will close
		 */
		public void setWillClose(boolean close) {
			this.close = close;
		}

		/**
		 * Sets the will destroy.
		 *
		 * @param destroy
		 *            the new will destroy
		 */
		public void setWillDestroy(boolean destroy) {
			this.destroy = destroy;
		}
	}

	/**
	 * The Interface AnvilClickEventHandler.
	 */
	public interface AnvilClickEventHandler {

		/**
		 * On anvil click.
		 *
		 * @param event
		 *            the event
		 */
		void onAnvilClick(AnvilClickEvent event);
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
		 * @param slot
		 *            the slot
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
		 * @param slot
		 *            the slot
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

	/** The version handle. */
	private AInventoryVersionHandler versionHandle;

	/** The player. */
	private Player player;

	/** The handler. */
	private AnvilClickEventHandler handler;

	/** The items. */
	private HashMap<AnvilSlot, ItemStack> items = new HashMap<AnvilSlot, ItemStack>();

	/** The listener. */
	private Listener listener;

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

	/**
	 * Instantiates a new a inventory.
	 *
	 * @param player
	 *            the player
	 * @param anvilClickEventHandler
	 *            the anvil click event handler
	 */
	public AInventory(final Player player, final AnvilClickEventHandler anvilClickEventHandler) {
		if (getVersion().contains("1_7_R4")) {
			versionHandle = new AInventory1_7_R4Handler(player, anvilClickEventHandler);
		} else {
			versionHandle = new AInventoryReflectionHandler(player, anvilClickEventHandler);
		}
		this.player = player;
		handler = anvilClickEventHandler;
		PlayerUtils.getInstance().setPlayerMeta(player, "AInventory", anvilClickEventHandler);

		listener = new Listener() {
			@EventHandler
			public void onInventoryClick(InventoryClickEvent event) {
				if (event.getWhoClicked() instanceof Player) {

					if (event.getInventory().equals(versionHandle.getInventory())) {
						event.setCancelled(true);

						ItemStack item = event.getCurrentItem();
						int slot = event.getRawSlot();
						String name = "";

						if (item != null) {
							if (item.hasItemMeta()) {
								ItemMeta meta = item.getItemMeta();

								if (meta.hasDisplayName()) {
									name = meta.getDisplayName();
								}
							}
						}

						AnvilClickEvent clickEvent = new AnvilClickEvent(AnvilSlot.bySlot(slot), name,
								(Player) event.getWhoClicked());

						if (clickEvent.getSlot() == AnvilSlot.OUTPUT) {
							event.getWhoClicked().closeInventory();
							if (handler == null) {
								handler = (AnvilClickEventHandler) PlayerUtils.getInstance().getPlayerMeta(player,
										"AInventory");
								AdvancedCoreHook.getInstance().debug("Anvil handler was null, fixing...");
							}

							Bukkit.getScheduler().runTaskAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {
								
								@Override
								public void run() {
									handler.onAnvilClick(clickEvent);
								}
							});
						
							destroy();
						}

					}
				}
			}

			@EventHandler
			public void onInventoryClose(InventoryCloseEvent event) {
				if (event.getPlayer() instanceof Player) {
					Inventory inv = event.getInventory();
					player.setLevel(player.getLevel() - 1);
					if (inv.equals(versionHandle.getInventory())) {
						inv.clear();
						destroy();
					}
				}
			}

			@EventHandler
			public void onPlayerQuit(PlayerQuitEvent event) {
				if (event.getPlayer().equals(getPlayer())) {
					player.setLevel(player.getLevel() - 1);
					destroy();
				}
			}
		};

		Bukkit.getPluginManager().registerEvents(listener, AdvancedCoreHook.getInstance().getPlugin());
	}

	/**
	 * Destroy.
	 */
	public void destroy() {
		player = null;
		handler = null;
		items = null;

		HandlerList.unregisterAll(listener);

		listener = null;
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public Player getPlayer() {
		return player;
	}

	/**
	 * Open.
	 */
	public void open() {
		versionHandle.open(getPlayer(), items);
	}

	/**
	 * Sets the slot.
	 *
	 * @param slot
	 *            the slot
	 * @param item
	 *            the item
	 */
	public void setSlot(AnvilSlot slot, ItemStack item) {
		items.put(slot, item);
	}
}