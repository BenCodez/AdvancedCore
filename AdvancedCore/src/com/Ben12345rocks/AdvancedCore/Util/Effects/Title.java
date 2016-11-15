package com.Ben12345rocks.AdvancedCore.Util.Effects;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;

// TODO: Auto-generated Javadoc
/**
 * The Class Title.
 */
public class Title {

	/** The packet title. */
	/* Title packet */
	private static Class<?> packetTitle;

	/** The packet actions. */
	/* Title packet actions ENUM */
	private static Class<?> packetActions;

	/** The nms chat serializer. */
	/* Chat serializer */
	private static Class<?> nmsChatSerializer;

	/** The chat base component. */
	private static Class<?> chatBaseComponent;

	/** The title. */
	/* Title text and color */
	private String title = "";

	/** The title color. */
	private ChatColor titleColor = ChatColor.WHITE;

	/** The subtitle. */
	/* Subtitle text and color */
	private String subtitle = "";

	/** The subtitle color. */
	private ChatColor subtitleColor = ChatColor.WHITE;

	/** The fade in time. */
	/* Title timings */
	private int fadeInTime = -1;

	/** The stay time. */
	private int stayTime = -1;

	/** The fade out time. */
	private int fadeOutTime = -1;

	/** The ticks. */
	private boolean ticks = false;

	/**
	 * Instantiates a new title.
	 */
	public Title() {
		loadClasses();
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title
	 *            the title
	 */
	public Title(String title) {
		this.title = title;
		loadClasses();
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title
	 *            the title
	 * @param subtitle
	 *            the subtitle
	 */
	public Title(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
		loadClasses();
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title
	 *            the title
	 * @param subtitle
	 *            the subtitle
	 * @param fadeInTime
	 *            the fade in time
	 * @param stayTime
	 *            the stay time
	 * @param fadeOutTime
	 *            the fade out time
	 */
	public Title(String title, String subtitle, int fadeInTime, int stayTime, int fadeOutTime) {
		this.title = title;
		this.subtitle = subtitle;
		this.fadeInTime = fadeInTime;
		this.stayTime = stayTime;
		this.fadeOutTime = fadeOutTime;
		loadClasses();
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title
	 *            the title
	 */
	public Title(Title title) {
		// Copy title
		this.title = title.getTitle();
		subtitle = title.getSubtitle();
		titleColor = title.getTitleColor();
		subtitleColor = title.getSubtitleColor();
		fadeInTime = title.getFadeInTime();
		fadeOutTime = title.getFadeOutTime();
		stayTime = title.getStayTime();
		ticks = title.isTicks();
		loadClasses();
	}

	/**
	 * Send title to all players
	 */
	public void broadcast() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			send(p);
		}
	}

	/**
	 * Clear title.
	 *
	 * @param player
	 *            the player
	 */
	public void clearTitle(Player player) {
		try {
			// Send timings first
			Object handle = NMSManager.getInstance().getHandle(player);
			Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection").get(handle);
			Object[] actions = packetActions.getEnumConstants();
			Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
			Object packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[3], null);
			sendPacket.invoke(connection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets the fade in time.
	 *
	 * @return the fade in time
	 */
	public int getFadeInTime() {
		return fadeInTime;
	}

	/**
	 * Gets the fade out time.
	 *
	 * @return the fade out time
	 */
	public int getFadeOutTime() {
		return fadeOutTime;
	}

	/**
	 * Gets the stay time.
	 *
	 * @return the stay time
	 */
	public int getStayTime() {
		return stayTime;
	}

	/**
	 * Gets the subtitle.
	 *
	 * @return the subtitle
	 */
	public String getSubtitle() {
		return subtitle;
	}

	/**
	 * Gets the subtitle color.
	 *
	 * @return the subtitle color
	 */
	public ChatColor getSubtitleColor() {
		return subtitleColor;
	}

	/**
	 * Gets the title.
	 *
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Gets the title color.
	 *
	 * @return the title color
	 */
	public ChatColor getTitleColor() {
		return titleColor;
	}

	/**
	 * Checks if is ticks.
	 *
	 * @return true, if is ticks
	 */
	public boolean isTicks() {
		return ticks;
	}

	/**
	 * Load classes.
	 */
	private void loadClasses() {
		if (packetTitle == null) {
			packetTitle = NMSManager.getInstance().getNMSClass("PacketPlayOutTitle");
			packetActions = NMSManager.getInstance().getNMSClass("PacketPlayOutTitle$EnumTitleAction");
			chatBaseComponent = NMSManager.getInstance().getNMSClass("IChatBaseComponent");
			nmsChatSerializer = NMSManager.getInstance().getNMSClass("ChatComponentText");
		}
	}

	/**
	 * Reset title.
	 *
	 * @param player
	 *            the player
	 */
	public void resetTitle(Player player) {
		try {
			// Send timings first
			Object handle = NMSManager.getInstance().getHandle(player);
			Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection").get(handle);
			Object[] actions = packetActions.getEnumConstants();
			Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
			Object packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[4], null);
			sendPacket.invoke(connection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send.
	 *
	 * @param player
	 *            the player
	 */
	public void send(Player player) {
		if (packetTitle != null) {
			// First reset previous settings
			resetTitle(player);
			try {
				// Send timings first
				Object handle = NMSManager.getInstance().getHandle(player);
				Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection")
						.get(handle);
				Object[] actions = packetActions.getEnumConstants();
				Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
				Object packet = packetTitle
						.getConstructor(packetActions, chatBaseComponent, Integer.TYPE, Integer.TYPE, Integer.TYPE)
						.newInstance(actions[2], null, fadeInTime * (ticks ? 1 : 20), stayTime * (ticks ? 1 : 20),
								fadeOutTime * (ticks ? 1 : 20));
				// Send if set
				if (fadeInTime != -1 && fadeOutTime != -1 && stayTime != -1) {
					sendPacket.invoke(connection, packet);
				}

				// Send title
				Object serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', title));
				packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[0],
						serialized);
				sendPacket.invoke(connection, packet);
				if (subtitle != "") {
					// Send subtitle if present
					serialized = nmsChatSerializer.getConstructor(String.class)
							.newInstance(ChatColor.translateAlternateColorCodes('&', subtitle));
					packet = packetTitle.getConstructor(packetActions, chatBaseComponent).newInstance(actions[1],
							serialized);
					sendPacket.invoke(connection, packet);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sets the fade in time.
	 *
	 * @param time
	 *            the new fade in time
	 */
	public void setFadeInTime(int time) {
		fadeInTime = time;
	}

	/**
	 * Sets the fade out time.
	 *
	 * @param time
	 *            the new fade out time
	 */
	public void setFadeOutTime(int time) {
		fadeOutTime = time;
	}

	/**
	 * Sets the stay time.
	 *
	 * @param time
	 *            the new stay time
	 */
	public void setStayTime(int time) {
		stayTime = time;
	}

	/**
	 * Sets the subtitle.
	 *
	 * @param subtitle
	 *            the new subtitle
	 */
	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	/**
	 * Sets the subtitle color.
	 *
	 * @param color
	 *            the new subtitle color
	 */
	public void setSubtitleColor(ChatColor color) {
		subtitleColor = color;
	}

	/**
	 * Sets the timings to seconds.
	 */
	public void setTimingsToSeconds() {
		ticks = false;
	}

	/**
	 * Sets the timings to ticks.
	 */
	public void setTimingsToTicks() {
		ticks = true;
	}

	/**
	 * Sets the title.
	 *
	 * @param title
	 *            the new title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Sets the title color.
	 *
	 * @param color
	 *            the new title color
	 */
	public void setTitleColor(ChatColor color) {
		titleColor = color;
	}

	/**
	 * Update subtitle.
	 *
	 * @param player
	 *            the player
	 */
	public void updateSubtitle(Player player) {
		if (Title.packetTitle != null) {
			try {
				Object handle = NMSManager.getInstance().getHandle(player);
				Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection")
						.get(handle);
				Object[] actions = Title.packetActions.getEnumConstants();
				Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
				Object serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', subtitle));
				Object packet = Title.packetTitle.getConstructor(new Class[] { Title.packetActions, chatBaseComponent })
						.newInstance(new Object[] { actions[1], serialized });
				sendPacket.invoke(connection, packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Update times.
	 *
	 * @param player
	 *            the player
	 */
	public void updateTimes(Player player) {
		if (Title.packetTitle != null) {
			try {
				Object handle = NMSManager.getInstance().getHandle(player);
				Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection")
						.get(handle);
				Object[] actions = Title.packetActions.getEnumConstants();
				Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
				Object packet = Title.packetTitle
						.getConstructor(new Class[] { Title.packetActions, chatBaseComponent, Integer.TYPE,
								Integer.TYPE, Integer.TYPE })
						.newInstance(new Object[] { actions[2], null, Integer.valueOf(fadeInTime * (ticks ? 1 : 20)),
								Integer.valueOf(stayTime * (ticks ? 1 : 20)),
								Integer.valueOf(fadeOutTime * (ticks ? 1 : 20)) });
				if ((fadeInTime != -1) && (fadeOutTime != -1) && (stayTime != -1)) {
					sendPacket.invoke(connection, packet);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Update title.
	 *
	 * @param player
	 *            the player
	 */
	public void updateTitle(Player player) {
		if (Title.packetTitle != null) {
			try {
				Object handle = NMSManager.getInstance().getHandle(player);
				Object connection = NMSManager.getInstance().getField(handle.getClass(), "playerConnection")
						.get(handle);
				Object[] actions = Title.packetActions.getEnumConstants();
				Method sendPacket = NMSManager.getInstance().getMethod(connection.getClass(), "sendPacket");
				Object serialized = nmsChatSerializer.getConstructor(String.class)
						.newInstance(ChatColor.translateAlternateColorCodes('&', title));
				Object packet = Title.packetTitle.getConstructor(new Class[] { Title.packetActions, chatBaseComponent })
						.newInstance(new Object[] { actions[0], serialized });
				sendPacket.invoke(connection, packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}