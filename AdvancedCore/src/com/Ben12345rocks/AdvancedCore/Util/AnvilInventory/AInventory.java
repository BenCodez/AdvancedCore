package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.bukkit.Bukkit;
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

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;

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

	/** The Block position. */
	private static Class<?> BlockPosition;

	/** The Packet play out open window. */
	private static Class<?> PacketPlayOutOpenWindow;

	/** The Container anvil. */
	private static Class<?> ContainerAnvil;

	/** The Chat message. */
	private static Class<?> ChatMessage;

	/** The Entity human. */
	private static Class<?> EntityHuman;

	/** The player. */
	private Player player;

	/** The handler. */
	@SuppressWarnings("unused")
	private AnvilClickEventHandler handler;

	/** The items. */
	private HashMap<AnvilSlot, ItemStack> items = new HashMap<AnvilSlot, ItemStack>();

	/** The inv. */
	private Inventory inv;

	/** The listener. */
	private Listener listener;

	/**
	 * Instantiates a new a inventory.
	 *
	 * @param player
	 *            the player
	 * @param anvilClickEventHandler
	 *            the anvil click event handler
	 */
	public AInventory(final Player player,
			final AnvilClickEventHandler anvilClickEventHandler) {
		loadClasses();
		this.player = player;
		handler = anvilClickEventHandler;

		listener = new Listener() {
			@EventHandler
			public void onInventoryClick(InventoryClickEvent event) {
				if (event.getWhoClicked() instanceof Player) {

					if (event.getInventory().equals(inv)) {
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

						AnvilClickEvent clickEvent = new AnvilClickEvent(
								AnvilSlot.bySlot(slot), name,
								(Player) event.getWhoClicked());

						if (clickEvent.getSlot() == AnvilSlot.OUTPUT) {
							event.getWhoClicked().closeInventory();
							anvilClickEventHandler.onAnvilClick(clickEvent);
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
					if (inv.equals(AInventory.this.inv)) {
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

		Bukkit.getPluginManager().registerEvents(listener, Main.plugin);
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
	 * Load classes.
	 */
	private void loadClasses() {
		BlockPosition = com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager
				.get().getNMSClass("BlockPosition");
		PacketPlayOutOpenWindow = NMSManager.get().getNMSClass(
				"PacketPlayOutOpenWindow");
		ContainerAnvil = NMSManager.get().getNMSClass("ContainerAnvil");
		EntityHuman = NMSManager.get().getNMSClass("EntityHuman");
		ChatMessage = NMSManager.get().getNMSClass("ChatMessage");
	}

	/**
	 * Open.
	 */
	public void open() {
		player.setLevel(player.getLevel() + 1);

		try {
			Object p = NMSManager.get().getHandle(player);

			Object container = ContainerAnvil.getConstructor(
					NMSManager.get().getNMSClass("PlayerInventory"),
					NMSManager.get().getNMSClass("World"), BlockPosition,
					EntityHuman).newInstance(
							NMSManager.get().getPlayerField(player, "inventory"),
							NMSManager.get().getPlayerField(player, "world"),
							BlockPosition.getConstructor(int.class, int.class,
									int.class).newInstance(0, 0, 0), p);
			NMSManager
			.get()
			.getField(NMSManager.get().getNMSClass("Container"),
					"checkReachable").set(container, false);

			// Set the items to the items from the inventory given
			Object bukkitView = NMSManager.get().invokeMethod("getBukkitView",
					container);
			inv = (Inventory) NMSManager.get().invokeMethod("getTopInventory",
					bukkitView);

			for (AnvilSlot slot : items.keySet()) {
				inv.setItem(slot.getSlot(), items.get(slot));
			}

			// Counter stuff that the game uses to keep track of inventories
			int c = (int) NMSManager.get().invokeMethod("nextContainerCounter",
					p);

			// Send the packet
			Constructor<?> chatMessageConstructor = ChatMessage.getConstructor(
					String.class, Object[].class);
			Object playerConnection = NMSManager.get().getPlayerField(player,
					"playerConnection");
			Object packet = PacketPlayOutOpenWindow.getConstructor(int.class,
					String.class,
					NMSManager.get().getNMSClass("IChatBaseComponent"),
					int.class).newInstance(
							c,
							"minecraft:anvil",
							chatMessageConstructor.newInstance("Repairing",
									new Object[] {}), 0);

			Method sendPacket = NMSManager.get().getMethod("sendPacket",
					playerConnection.getClass(), PacketPlayOutOpenWindow);
			sendPacket.invoke(playerConnection, packet);

			// Set their active container to the container
			Field activeContainerField = NMSManager.get().getField(EntityHuman,
					"activeContainer");
			if (activeContainerField != null) {
				activeContainerField.set(p, container);

				// Set their active container window id to that counter stuff
				NMSManager
				.get()
				.getField(NMSManager.get().getNMSClass("Container"),
						"windowId").set(activeContainerField.get(p), c);

				// Add the slot listener
				NMSManager
				.get()
				.getMethod("addSlotListener",
						activeContainerField.get(p).getClass(),
						p.getClass())
						.invoke(activeContainerField.get(p), p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
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