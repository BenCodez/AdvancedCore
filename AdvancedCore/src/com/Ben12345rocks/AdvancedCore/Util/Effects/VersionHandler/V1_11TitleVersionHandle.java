package com.Ben12345rocks.AdvancedCore.Util.Effects.VersionHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Minecraft 1.8 Title For 1.11
 *
 * @author Maxim Van de Wynckel
 * @version 1.1.0
 */
public class V1_11TitleVersionHandle implements TitleVersionHandle {
	/* Title packet */
	private Class<?> packetTitle;
	/* Title packet actions ENUM */
	private Class<?> packetActions;
	/* Chat serializer */
	private Class<?> nmsChatSerializer;
	private static Class<?> chatBaseComponent;
	/* NMS player and connection */
	private Class<?> nmsPlayer;
	private Class<?> nmsPlayerConnection;
	private Field playerConnection;
	private Method sendPacket;
	private Class<?> obcPlayer;
	private Method methodPlayerGetHandle;
	private boolean ticks = true;
	private final Map<Class<?>, Class<?>> CORRESPONDING_TYPES = new HashMap<Class<?>, Class<?>>();

	public V1_11TitleVersionHandle() {
		loadClasses();
	}

	/**
	 * Load spigot and NMS classes
	 */
	public void loadClasses() {
		if (packetTitle == null) {
			packetTitle = getNMSClass("PacketPlayOutTitle");
			packetActions = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
			chatBaseComponent = getNMSClass("IChatBaseComponent");
			nmsChatSerializer = getNMSClass("ChatComponentText");
			nmsPlayer = getNMSClass("EntityPlayer");
			nmsPlayerConnection = getNMSClass("PlayerConnection");
			playerConnection = getField(nmsPlayer, "playerConnection");
			sendPacket = getMethod(nmsPlayerConnection, "sendPacket");
			obcPlayer = getOBCClass("entity.CraftPlayer");
			methodPlayerGetHandle = getMethod("getHandle", obcPlayer);
		}
	}

	/**
	 * Send the title to a player
	 *
	 * @param player
	 *            Player
	 */
	public void send(Player player, String title, String subtitle, int fadeInTime, int stayTime, int fadeOutTime) {
		if (packetTitle != null) {
			// First reset previous settings
			resetTitle(player);
			try {
				// Send timings first
				Object handle = getHandle(player);
				Object connection = playerConnection.get(handle);
				Object[] actions = packetActions.getEnumConstants();
				Object packet = packetTitle
						.getConstructor(packetActions, chatBaseComponent, Integer.TYPE, Integer.TYPE, Integer.TYPE)
						.newInstance(actions[3], null, fadeInTime * (ticks ? 1 : 20), stayTime * (ticks ? 1 : 20),
								fadeOutTime * (ticks ? 1 : 20));
				// Send if set
				if (fadeInTime != -1 && fadeOutTime != -1 && stayTime != -1)
					sendPacket.invoke(connection, packet);
				Object serialized;
				if (!subtitle.equals("")) {
					// Send subtitle if present
					serialized = nmsChatSerializer.getConstructor(String.class)
							.newInstance(ChatColor.translateAlternateColorCodes('&', subtitle));
					packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[1],
							serialized);
					sendPacket.invoke(connection, packet);
				}
				// Send title
				serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', title));
				packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[0],
						serialized);
				sendPacket.invoke(connection, packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void updateTimes(Player player, int fadeInTime, int stayTime, int fadeOutTime) {
		if (this.packetTitle != null) {
			try {
				Object handle = getHandle(player);
				Object connection = playerConnection.get(handle);
				Object[] actions = this.packetActions.getEnumConstants();
				Object packet = this.packetTitle.getConstructor(
						new Class[] { this.packetActions, chatBaseComponent, Integer.TYPE, Integer.TYPE, Integer.TYPE })
						.newInstance(actions[3], null, fadeInTime * (this.ticks ? 1 : 20),
								stayTime * (this.ticks ? 1 : 20), fadeOutTime * (this.ticks ? 1 : 20));
				if ((fadeInTime != -1) && (fadeOutTime != -1) && (stayTime != -1)) {
					sendPacket.invoke(connection, packet);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void updateTitle(Player player, String title) {
		if (this.packetTitle != null) {
			try {
				Object handle = getHandle(player);
				Object connection = getField(handle.getClass(), "playerConnection").get(handle);
				Object[] actions = this.packetActions.getEnumConstants();
				Method sendPacket = getMethod(connection.getClass(), "sendPacket");
				Object serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', title));
				Object packet = this.packetTitle.getConstructor(new Class[] { this.packetActions, chatBaseComponent })
						.newInstance(actions[0], serialized);
				sendPacket.invoke(connection, packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void updateSubtitle(Player player, String subtitle) {
		if (this.packetTitle != null) {
			try {
				Object handle = getHandle(player);
				Object connection = playerConnection.get(handle);
				Object[] actions = this.packetActions.getEnumConstants();
				Object serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', subtitle));
				Object packet = this.packetTitle.getConstructor(new Class[] { this.packetActions, chatBaseComponent })
						.newInstance(actions[1], serialized);
				sendPacket.invoke(connection, packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Clear the title
	 *
	 * @param player
	 *            Player
	 */
	public void clearTitle(Player player) {
		try {
			// Send timings first
			Object handle = getHandle(player);
			Object connection = playerConnection.get(handle);
			Object[] actions = packetActions.getEnumConstants();
			Object packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[4], null);
			sendPacket.invoke(connection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reset the title settings
	 *
	 * @param player
	 *            Player
	 */
	public void resetTitle(Player player) {
		try {
			// Send timings first
			Object handle = getHandle(player);
			Object connection = playerConnection.get(handle);
			Object[] actions = packetActions.getEnumConstants();
			Object packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[5], null);
			sendPacket.invoke(connection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Class<?> getPrimitiveType(Class<?> clazz) {
		return CORRESPONDING_TYPES.containsKey(clazz) ? CORRESPONDING_TYPES.get(clazz) : clazz;
	}

	private Class<?>[] toPrimitiveTypeArray(Class<?>[] classes) {
		int a = classes != null ? classes.length : 0;
		Class<?>[] types = new Class<?>[a];
		for (int i = 0; i < a; i++)
			types[i] = getPrimitiveType(classes[i]);
		return types;
	}

	private static boolean equalsTypeArray(Class<?>[] a, Class<?>[] o) {
		if (a.length != o.length)
			return false;
		for (int i = 0; i < a.length; i++)
			if (!a[i].equals(o[i]) && !a[i].isAssignableFrom(o[i]))
				return false;
		return true;
	}

	private Object getHandle(Player player) {
		try {
			return methodPlayerGetHandle.invoke(player);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private Method getMethod(String name, Class<?> clazz, Class<?>... paramTypes) {
		Class<?>[] t = toPrimitiveTypeArray(paramTypes);
		for (Method m : clazz.getMethods()) {
			Class<?>[] types = toPrimitiveTypeArray(m.getParameterTypes());
			if (m.getName().equals(name) && equalsTypeArray(types, t))
				return m;
		}
		return null;
	}

	private String getVersion() {
		String name = Bukkit.getServer().getClass().getPackage().getName();
		String version = name.substring(name.lastIndexOf('.') + 1) + ".";
		return version;
	}

	private Class<?> getNMSClass(String className) {
		String fullName = "net.minecraft.server." + getVersion() + className;
		Class<?> clazz = null;
		try {
			clazz = Class.forName(fullName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return clazz;
	}

	private Class<?> getOBCClass(String className) {
		String fullName = "org.bukkit.craftbukkit." + getVersion() + className;
		Class<?> clazz = null;
		try {
			clazz = Class.forName(fullName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return clazz;
	}

	private Field getField(Class<?> clazz, String name) {
		try {
			Field field = clazz.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private Method getMethod(Class<?> clazz, String name, Class<?>... args) {
		for (Method m : clazz.getMethods())
			if (m.getName().equals(name) && (args.length == 0 || ClassListEqual(args, m.getParameterTypes()))) {
				m.setAccessible(true);
				return m;
			}
		return null;
	}

	private boolean ClassListEqual(Class<?>[] l1, Class<?>[] l2) {
		boolean equal = true;
		if (l1.length != l2.length)
			return false;
		for (int i = 0; i < l1.length; i++)
			if (l1[i] != l2[i]) {
				equal = false;
				break;
			}
		return equal;
	}
}
