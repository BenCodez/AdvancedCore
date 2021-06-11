package com.bencodez.advancedcore.api.inventory.anvilinventory.versionhandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory.AnvilClickEventHandler;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory.AnvilSlot;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.nms.NMSManager;
import com.bencodez.advancedcore.nms.ReflectionUtils;

/**
 * The Class AInventoryReflectionHandler.
 */
public class AInventoryReflectionHandler implements AInventoryVersionHandler {

	/** The Block position. */
	private static Class<?> BlockPosition;

	/** The Chat message. */
	private static Class<?> ChatMessage;

	/** The Container anvil. */
	private static Class<?> ContainerAnvil;

	/** The Entity human. */
	private static Class<?> EntityHuman;

	/** The Packet play out open window. */
	private static Class<?> PacketPlayOutOpenWindow;

	/** The inv. */
	private Inventory inv;

	/**
	 * Instantiates a new a inventory reflection handler.
	 *
	 * @param player                 the player
	 * @param anvilClickEventHandler the anvil click event handler
	 */
	public AInventoryReflectionHandler(final Player player, final AnvilClickEventHandler anvilClickEventHandler) {
		loadClasses();
		PlayerUtils.getInstance().setPlayerMeta(player, "AInventory", anvilClickEventHandler);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.
	 * AInventoryVersionHandler#getInventory()
	 */
	@Override
	public Inventory getInventory() {
		return inv;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.
	 * AInventoryVersionHandler#loadClasses()
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void loadClasses() {
		if (NMSManager.getInstance().isVersion("1.12", "1.13", "1.14", "1.15", "1.16")) {
			BlockPosition = NMSManager.get().getNMSClass("BlockPosition");
			PacketPlayOutOpenWindow = NMSManager.get().getNMSClass("PacketPlayOutOpenWindow");
			ContainerAnvil = NMSManager.get().getNMSClass("ContainerAnvil");
			EntityHuman = NMSManager.get().getNMSClass("EntityHuman");
			ChatMessage = NMSManager.get().getNMSClass("ChatMessage");
		} else {
			BlockPosition = ReflectionUtils.getClassForName("net.minecraft.core.BlockPosition");
			PacketPlayOutOpenWindow = ReflectionUtils
					.getClassForName("net.minecraft.network.protocol.game.PacketPlayOutOpenWindow");
			ContainerAnvil = ReflectionUtils.getClassForName("net.minecraft.world.inventory.ContainerAnvil");
			EntityHuman = ReflectionUtils.getClassForName("net.minecraft.world.entity.player.EntityHuman");
			ChatMessage = ReflectionUtils.getClassForName("net.minecraft.network.chat.ChatMessage");
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.
	 * AInventoryVersionHandler#open(org.bukkit.entity.Player, java.util.HashMap)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void open(Player player, HashMap<AnvilSlot, ItemStack> items) {
		player.setLevel(player.getLevel() + 1);

		try {
			Object p = NMSManager.get().getHandle(player);
			Object container = null;
			if (NMSManager.getInstance().isVersion("1.12", "1.13", "1.14", "1.15", "1.16")) {
				container = ContainerAnvil
						.getConstructor(NMSManager.get().getNMSClass("PlayerInventory"),
								NMSManager.get().getNMSClass("World"), BlockPosition, EntityHuman)
						.newInstance(NMSManager.get().getPlayerField(player, "inventory"),
								NMSManager.get().getPlayerField(player, "world"),
								BlockPosition.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0), p);
				NMSManager.get().getField(NMSManager.get().getNMSClass("Container"), "checkReachable").set(container,
						false);
			} else {
				container = ContainerAnvil.getConstructor(
						ReflectionUtils.getClassForName("net.minecraft.world.entity.player.PlayerInventory"),
						ReflectionUtils.getClassForName("net.minecraft.world.level.World"), BlockPosition, EntityHuman)
						.newInstance(NMSManager.get().getPlayerField(player, "inventory"),
								NMSManager.get().getPlayerField(player, "world"),
								BlockPosition.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0), p);
				NMSManager.get().getField(ReflectionUtils.getClassForName("net.minecraft.world.inventory.Container"),
						"checkReachable").set(container, false);
			}

			// Set the items to the items from the inventory given
			Object bukkitView = NMSManager.get().invokeMethod("getBukkitView", container);
			inv = (Inventory) NMSManager.get().invokeMethod("getTopInventory", bukkitView);

			for (AnvilSlot slot : items.keySet()) {
				inv.setItem(slot.getSlot(), items.get(slot));
			}

			// Counter stuff that the game uses to keep track of inventories
			int c = (int) NMSManager.get().invokeMethod("nextContainerCounter", p);

			// Send the packet
			Constructor<?> chatMessageConstructor = ChatMessage.getConstructor(String.class, Object[].class);
			Object playerConnection = NMSManager.get().getPlayerField(player, "playerConnection");

			Object packet = null;
			if (NMSManager.getInstance().isVersion("1.12", "1.13", "1.14", "1.15", "1.16")) {
				packet = PacketPlayOutOpenWindow.getConstructor(int.class, String.class,
						NMSManager.get().getNMSClass("IChatBaseComponent"), int.class).newInstance(c, "minecraft:anvil",
								chatMessageConstructor.newInstance("Repairing", new Object[] {}), 0);
			} else {
				packet = PacketPlayOutOpenWindow.getConstructor(int.class, String.class,
						ReflectionUtils.getClassForName("net.minecraft.network.chat.IChatBaseComponent"), int.class)
						.newInstance(c, "minecraft:anvil",
								chatMessageConstructor.newInstance("Repairing", new Object[] {}), 0);
			}

			Method sendPacket = NMSManager.get().getMethod("sendPacket", playerConnection.getClass(),
					PacketPlayOutOpenWindow);
			sendPacket.invoke(playerConnection, packet);

			// Set their active container to the container
			Field activeContainerField = NMSManager.get().getField(EntityHuman, "activeContainer");
			if (activeContainerField != null) {
				activeContainerField.set(p, container);

				// Set their active container window id to that counter stuff
				if (NMSManager.getInstance().isVersion("1.12", "1.13", "1.14", "1.15", "1.16")) {
					NMSManager.get().getField(NMSManager.get().getNMSClass("Container"), "windowId")
							.set(activeContainerField.get(p), c);
				} else {
					NMSManager.get()
							.getField(ReflectionUtils.getClassForName("net.minecraft.world.inventory.Container"),
									"windowId")
							.set(activeContainerField.get(p), c);
				}

				// Add the slot listener
				NMSManager.get().getMethod("addSlotListener", activeContainerField.get(p).getClass(), p.getClass())
						.invoke(activeContainerField.get(p), p);
			}
		} catch (Exception e) {
			AdvancedCorePlugin.getInstance().debug(e);
			AdvancedCorePlugin.getInstance().debug("Failed to use AnvilGUI");

		}
	}

}
