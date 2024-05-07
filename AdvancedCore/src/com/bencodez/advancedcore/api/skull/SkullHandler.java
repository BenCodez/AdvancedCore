package com.bencodez.advancedcore.api.skull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.PluginUtils;
import com.bencodez.simpleapi.nms.NMSManager;
import com.bencodez.simpleapi.nms.ReflectionUtils;

import lombok.Getter;

public class SkullHandler {

	private static SkullHandler instance = new SkullHandler();

	public static SkullHandler getInstance() {
		return instance;
	}

	private Method asBukkitCopy;
	@Getter
	private Method asNMSCopy;
	@SuppressWarnings("rawtypes")
	private Class craftItemStack;

	@SuppressWarnings("rawtypes")
	private Class itemStack;

	@Getter
	private ConcurrentHashMap<String, Object> skulls = new ConcurrentHashMap<String, Object>();

	Queue<String> skullsToLoad = new ConcurrentLinkedQueue<String>();

	private ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);

	private void add(String playerName) {
		if (!skullsToLoad.contains(playerName) && !getSkulls().containsKey(playerName)) {
			skullsToLoad.add(playerName);
		}
	}

	public void close() {
		timer.shutdownNow();
	}

	/*
	 * @SuppressWarnings("rawtypes") private Class gameProfile;
	 *
	 * @SuppressWarnings("rawtypes") private Class property;
	 *
	 * @SuppressWarnings("rawtypes") private Constructor gameProfileConstructor;
	 *
	 * @SuppressWarnings("rawtypes") private Constructor propertyConstructor;
	 * private Method gameProfileGetProperties; public ItemStack getHead(String url)
	 * { ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1); ItemMeta headMeta
	 * = head.getItemMeta(); try { // GameProfile profile = new
	 * GameProfile(UUID.randomUUID(), null); Object profile =
	 * gameProfileConstructor.newInstance(UUID.randomUUID(), null); byte[]
	 * encodedData = Base64.getEncoder()
	 * .encode((String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}",
	 * url).getBytes())); gameProfileGetProperties.invoke(gameProfile, "textures",
	 * propertyConstructor.newInstance("textures", new String(encodedData))); Field
	 * profileField = null; profileField =
	 * headMeta.getClass().getDeclaredField("profile");
	 * profileField.setAccessible(true); profileField.set(headMeta, profile); }
	 * catch (IllegalArgumentException | IllegalAccessException |
	 * NoSuchFieldException | SecurityException | InvocationTargetException |
	 * InstantiationException e) { e.printStackTrace(); }
	 * head.setItemMeta(headMeta); return head; }
	 */

	@SuppressWarnings("deprecation")
	public org.bukkit.inventory.ItemStack getItemStack(String playerName)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if (hasSkull(playerName)) {
			return (ItemStack) asBukkitCopy.invoke(null, skulls.get(playerName));
		} else {
			loadSkull(playerName);
		}
		return new ItemBuilder("PLAYER_HEAD").setSkullOwner(playerName).toItemStack();
	}

	@SuppressWarnings("deprecation")
	public ItemStack getSkull(String playerName) {
		return new ItemBuilder("PLAYER_HEAD").setSkullOwner(playerName).toItemStack();

	}

	public boolean hasSkull(String playerName) {
		if (playerName != null && skulls.containsKey(playerName)) {
			if (skulls.get(playerName) != null) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	public void load() {
		try {
			/*
			 * gameProfile =
			 * ReflectionUtils.getClassForName("com.mojang.authlib.GameProfile"); property =
			 * ReflectionUtils.getClassForName("com.mojang.authlib.properties.Property");
			 * gameProfileConstructor = gameProfile.getConstructor(UUID.class,
			 * String.class); propertyConstructor = property.getConstructor(String.class,
			 * String.class); gameProfileGetProperties =
			 * gameProfile.getDeclaredMethod("getProperties");
			 */

			craftItemStack = ReflectionUtils.getClassForName(
					Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");

			if (NMSManager.getInstance().isVersion("1.12", "1.13", "1.14", "1.15", "1.16")) {
				itemStack = NMSManager.getInstance().getNMSClass("ItemStack");
			} else {
				itemStack = ReflectionUtils.getClassForName("net.minecraft.world.item.ItemStack");
			}

			asNMSCopy = craftItemStack.getDeclaredMethod("asNMSCopy", ItemStack.class);
			asNMSCopy.setAccessible(true);
		} catch (SecurityException | NoSuchMethodException e) {
			AdvancedCorePlugin.getInstance().debug(e);
		}

		try {
			asBukkitCopy = craftItemStack.getDeclaredMethod("asBukkitCopy", itemStack);
			asBukkitCopy.setAccessible(true);
		} catch (SecurityException | NoSuchMethodException e) {
			AdvancedCorePlugin.getInstance().debug(e);
		}

		SkullThread.getInstance().loadThread();
	}

	public void loadSkull(Player player) {
		loadSkull(player.getName());
	}

	public void loadSkull(final String playerName) {
		if (AdvancedCorePlugin.getInstance().getOptions().isLoadSkulls()
				&& AdvancedCorePlugin.getInstance().isEnabled()) {
			if (PluginUtils.getInstance().getFreeMemory() > 300 && PluginUtils.getInstance().getMemory() > 800) {
				if (Bukkit.isPrimaryThread()) {
					timer.execute(new Runnable() {

						@Override
						public void run() {
							add(playerName);
						}
					});
				} else {
					add(playerName);
				}
			} else {
				AdvancedCorePlugin.getInstance()
						.extraDebug("Not loading skull, not alot of free ram available, free "
								+ PluginUtils.getInstance().getFreeMemory() + ", allocated "
								+ PluginUtils.getInstance().getMemory());
			}
		}
	}

}
