package com.Ben12345rocks.AdvancedCore.Util.Skull;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.Ben12345rocks.AdvancedCore.NMSManager.ReflectionUtils;

public class SkullHandler {

	private static SkullHandler instance = new SkullHandler();

	@SuppressWarnings("rawtypes")
	private Class craftItemStack;
	private Field asNMSCopy;
	private Field asBukkitCopy;

	public static SkullHandler getInstance() {
		return instance;
	}

	public void load() {
		craftItemStack = ReflectionUtils.getClassForName("org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack");
		try {
			asNMSCopy = craftItemStack.getDeclaredField("asNMSCopy");
			asNMSCopy.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}

		try {
			asBukkitCopy = craftItemStack.getDeclaredField("asBukkitCopy");
			asBukkitCopy.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}

	private ConcurrentHashMap<String, Object> skulls = new ConcurrentHashMap<String, Object>();

	public void loadSkull(Player player) {
		loadSkull(player.getName());
	}

	@SuppressWarnings("deprecation")
	public void loadSkull(String playerName) {
		org.bukkit.inventory.ItemStack s = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD, 1);
		SkullMeta meta = (SkullMeta) s.getItemMeta();
		meta.setOwner(playerName);
		s.setItemMeta(meta);

		try {
			skulls.put(playerName, asNMSCopy.get(s));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	public org.bukkit.inventory.ItemStack getItemStack(String playerName) {
		if (hasSkull(playerName)) {
			try {
				return (ItemStack) asBukkitCopy.get(skulls.get(playerName));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public boolean hasSkull(String playerName) {
		return skulls.containsKey(playerName);
	}

}
