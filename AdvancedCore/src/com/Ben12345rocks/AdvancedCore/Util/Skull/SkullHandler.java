package com.Ben12345rocks.AdvancedCore.Util.Skull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.NMSManager.ReflectionUtils;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

import lombok.Getter;

public class SkullHandler {

	private static SkullHandler instance = new SkullHandler();

	@SuppressWarnings("rawtypes")
	private Class craftItemStack;
	@SuppressWarnings("rawtypes")
	private Class itemStack;
	@Getter
	private Method asNMSCopy;
	private Method asBukkitCopy;

	public static SkullHandler getInstance() {
		return instance;
	}

	@SuppressWarnings("unchecked")
	public void load() {
		craftItemStack = ReflectionUtils.getClassForName("org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack");
		itemStack = NMSManager.getInstance().getNMSClass("ItemStack");
		try {
			asNMSCopy = craftItemStack.getDeclaredMethod("asNMSCopy", ItemStack.class);
			asNMSCopy.setAccessible(true);
		} catch (SecurityException | NoSuchMethodException e) {
			e.printStackTrace();
		}

		try {
			asBukkitCopy = craftItemStack.getDeclaredMethod("asBukkitCopy", itemStack);
			asBukkitCopy.setAccessible(true);
		} catch (SecurityException | NoSuchMethodException e) {
			e.printStackTrace();
		}

		if (AdvancedCoreHook.getInstance().getOptions().isPreloadSkulls()) {
			Bukkit.getScheduler().runTaskAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

				@Override
				public void run() {
					SkullThread.getInstance().getThread().startup();
				}
			});
		}

	}

	@Getter
	private ConcurrentHashMap<String, Object> skulls = new ConcurrentHashMap<String, Object>();

	public void loadSkull(Player player) {
		loadSkull(player.getName());
	}

	public void loadSkull(final String playerName) {
		SkullThread.getInstance().getThread().load(playerName);
	}

	@SuppressWarnings("deprecation")
	public org.bukkit.inventory.ItemStack getItemStack(String playerName) {
		if (hasSkull(playerName)) {
			try {
				return (ItemStack) asBukkitCopy.invoke(null, skulls.get(playerName));
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}

		} else {
			loadSkull(playerName);
		}
		return new ItemBuilder(Material.PLAYER_HEAD).setSkullOwner(playerName).toItemStack();
	}

	public boolean hasSkull(String playerName) {
		if (skulls.containsKey(playerName)) {
			if (skulls.get(playerName) != null) {
				return true;
			}
		}
		return false;
	}

}
