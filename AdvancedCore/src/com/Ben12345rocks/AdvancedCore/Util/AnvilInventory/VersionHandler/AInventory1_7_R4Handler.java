package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler;

import java.util.HashMap;

import net.minecraft.server.v1_7_R4.ContainerAnvil;
import net.minecraft.server.v1_7_R4.EntityHuman;
import net.minecraft.server.v1_7_R4.EntityPlayer;
import net.minecraft.server.v1_7_R4.PacketPlayOutOpenWindow;

import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEventHandler;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilSlot;

/**
 * The Class AInventory1_7_R4Handler.
 */
public class AInventory1_7_R4Handler implements AInventoryVersionHandler {

	/**
	 * Instantiates a new a inventory 1 7 R 4 handler.
	 *
	 * @param player
	 *            the player
	 * @param anvilClickEventHandler
	 *            the anvil click event handler
	 */
	public AInventory1_7_R4Handler(final Player player,
			AnvilClickEventHandler anvilClickEventHandler) {
		Utils.getInstance().setPlayerMeta(player, "AInventory",
				anvilClickEventHandler);
	}

	/**
	 * The Class AnvilContainer.
	 */
	private class AnvilContainer extends ContainerAnvil {
		
		/**
		 * Instantiates a new anvil container.
		 *
		 * @param entity
		 *            the entity
		 */
		public AnvilContainer(EntityHuman entity) {
			super(entity.inventory, entity.world, 0, 0, 0, entity);
		}

		/* (non-Javadoc)
		 * @see net.minecraft.server.v1_7_R4.ContainerAnvil#a(net.minecraft.server.v1_7_R4.EntityHuman)
		 */
		@Override
		public boolean a(EntityHuman entityhuman) {
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventoryVersionHandler#loadClasses()
	 */
	@Override
	public void loadClasses() {

	}

	/** The inv. */
	private Inventory inv;

	/* (non-Javadoc)
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventoryVersionHandler#open(org.bukkit.entity.Player, java.util.HashMap)
	 */
	@Override
	public void open(Player player, HashMap<AnvilSlot, ItemStack> items) {
		EntityPlayer p = ((CraftPlayer) player).getHandle();

		AnvilContainer container = new AnvilContainer(p);

		// Set the items to the items from the inventory given
		inv = container.getBukkitView().getTopInventory();

		for (AnvilSlot slot : items.keySet()) {
			inv.setItem(slot.getSlot(), items.get(slot));
		}

		// Counter stuff that the game uses to keep track of inventories
		int c = p.nextContainerCounter();

		// Send the packet
		p.playerConnection.sendPacket(new PacketPlayOutOpenWindow(c, 8,
				"Repairing", 9, true));

		// Set their active container to the container
		p.activeContainer = container;

		// Set their active container window id to that counter stuff
		p.activeContainer.windowId = c;

		// Add the slot listener
		p.activeContainer.addSlotListener(p);

	}

	/* (non-Javadoc)
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.AInventoryVersionHandler#getInventory()
	 */
	@Override
	public Inventory getInventory() {

		return inv;
	}

}
