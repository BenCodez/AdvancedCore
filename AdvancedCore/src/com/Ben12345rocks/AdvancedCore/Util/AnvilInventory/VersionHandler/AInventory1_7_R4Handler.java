package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEventHandler;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilSlot;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

/**
 * The Class AInventory1_7_R4Handler.
 */
public class AInventory1_7_R4Handler implements AInventoryVersionHandler {

	/** The inv. */
	private Inventory inv;

	/**
	 * Instantiates a new a inventory 1 7 R 4 handler.
	 *
	 * @param player
	 *            the player
	 * @param anvilClickEventHandler
	 *            the anvil click event handler
	 */
	public AInventory1_7_R4Handler(final Player player, AnvilClickEventHandler anvilClickEventHandler) {
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
	@Override
	public void loadClasses() {

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler.
	 * AInventoryVersionHandler#open(org.bukkit.entity.Player, java.util.HashMap)
	 */
	@Override
	public void open(Player player, HashMap<AnvilSlot, ItemStack> items) {
		player.sendMessage("Anvil GUI is not supported on 1.7.10");

	}

}
