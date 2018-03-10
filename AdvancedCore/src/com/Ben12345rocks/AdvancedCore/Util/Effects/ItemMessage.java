package com.Ben12345rocks.AdvancedCore.Util.Effects;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

public class ItemMessage {

	public static void send(Player player, String message) {
		if (player == null) {
			return;
		}
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
			return;
		}
		int slot = player.getInventory().getHeldItemSlot();
		ItemStack stack0 = player.getInventory().getItem(slot);
		ItemStack stack = new ItemStack(stack0.getType(), stack0.getAmount(), stack0.getDurability());
		ItemMeta meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
		// fool the client into thinking the item name has changed, so it actually
		// (re)displays it
		meta.setDisplayName(message);
		stack.setItemMeta(meta);
		PacketContainer setSlot = new PacketContainer(PacketType.Play.Server.SET_SLOT);
		// int field 0: window id (0 = player inventory)
		// int field 1: slot number (36 - 44 for player hotbar)
		setSlot.getIntegers().write(0, 0).write(1, slot + 36);
		setSlot.getItemModifier().write(0, stack);
		try {
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, setSlot);

		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					ProtocolLibrary.getProtocolManager().sendServerPacket(player, setSlot);
				} catch (InvocationTargetException e) {
				}
			}
		}.runTaskLater(AdvancedCoreHook.getInstance().getPlugin(), 1);

	}

}