package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEventHandler;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilSlot;

public class AInventoryReflectionHandler implements AInventoryVersionHandler {

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

	/** The inv. */
	private Inventory inv;

	/**
	 * Instantiates a new a inventory.
	 *
	 * @param player
	 *            the player
	 * @param anvilClickEventHandler
	 *            the anvil click event handler
	 */
	public AInventoryReflectionHandler(final Player player,
			final AnvilClickEventHandler anvilClickEventHandler) {
		loadClasses();
		Utils.getInstance().setPlayerMeta(player, "AInventory",
				anvilClickEventHandler);
	}

	/**
	 * Load classes.
	 */
	@Override
	public void loadClasses() {
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
	@Override
	public void open(Player player, HashMap<AnvilSlot, ItemStack> items) {
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
			if (Config.getInstance().getDebugEnabled()) {
				e.printStackTrace();
			}
			Main.plugin.debug("Failed to use AnvilGUI");

		}
	}

	@Override
	public Inventory getInventory() {
		return inv;
	}

}
