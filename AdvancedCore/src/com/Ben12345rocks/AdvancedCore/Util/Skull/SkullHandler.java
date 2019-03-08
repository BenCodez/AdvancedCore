package com.Ben12345rocks.AdvancedCore.Util.Skull;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import net.minecraft.server.v1_13_R2.ItemStack;

public class SkullHandler {

	private static SkullHandler instance = new SkullHandler();

	public static SkullHandler getInstance() {
		return instance;
	}

	private HashMap<String, ItemStack> skulls = new HashMap<String, ItemStack>();

	public void loadSkull(Player player) {
		loadSkull(player.getName());
	}

	@SuppressWarnings("deprecation")
	public void loadSkull(String playerName) {
		org.bukkit.inventory.ItemStack s = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD, 1);
		SkullMeta meta = (SkullMeta) s.getItemMeta();
		meta.setOwner(playerName);
		s.setItemMeta(meta);
		skulls.put(playerName, CraftItemStack.asNMSCopy(s));
	}

	public org.bukkit.inventory.ItemStack getItemStack(String playerName) {
		return CraftItemStack.asBukkitCopy(skulls.get(playerName));
	}

	public boolean hasSkull(String playerName) {
		return skulls.containsKey(playerName);
	}

}
