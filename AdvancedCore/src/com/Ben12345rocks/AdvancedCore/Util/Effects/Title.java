package com.Ben12345rocks.AdvancedCore.Util.Effects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;

// TODO: Auto-generated Javadoc
/**
 * The Class Title.
 */
public class Title {

	/** The title. */
	/* Title text and color */
	private String title = "";

	/** The subtitle. */
	/* Subtitle text and color */
	private String subtitle = "";

	/** The fade in time. */
	/* Title timings */
	private int fadeInTime = -1;

	/** The stay time. */
	private int stayTime = -1;

	/** The fade out time. */
	private int fadeOutTime = -1;

	/**
	 * Instantiates a new title.
	 */
	public Title() {
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title the title
	 */
	public Title(String title) {
		this.title = title;
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title    the title
	 * @param subtitle the subtitle
	 */
	public Title(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title       the title
	 * @param subtitle    the subtitle
	 * @param fadeInTime  the fade in time
	 * @param stayTime    the stay time
	 * @param fadeOutTime the fade out time
	 */
	public Title(String title, String subtitle, int fadeInTime, int stayTime, int fadeOutTime) {
		this.title = title;
		this.subtitle = subtitle;
		this.fadeInTime = fadeInTime;
		this.stayTime = stayTime;
		this.fadeOutTime = fadeOutTime;
	}

	/**
	 * Instantiates a new title.
	 *
	 * @param title the title
	 */
	public Title(Title title) {
		// Copy title
		this.title = title.getTitle();
		subtitle = title.getSubtitle();
		fadeInTime = title.getFadeInTime();
		fadeOutTime = title.getFadeOutTime();
		stayTime = title.getStayTime();
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
	 * @param player the player
	 */
	public void clearTitle(Player player) {
		player.sendTitle("", "", -1, -1, -1);
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
	 * Gets the title.
	 *
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Reset title.
	 *
	 * @param player the player
	 */
	public void resetTitle(Player player) {
		clearTitle(player);
	}

	/**
	 * Send.
	 *
	 * @param player the player
	 */
	public void send(Player player) {
		send(player, title, subtitle, fadeInTime, stayTime, fadeOutTime);
	}

	public void send(Player player, String title, String subtitle, int fadeInTime, int stayTime, int fadeOutTime) {
		player.sendTitle(StringParser.getInstance().colorize(title), StringParser.getInstance().colorize(subtitle),
				fadeInTime, stayTime, fadeOutTime);
	}

	/**
	 * Sets the fade in time.
	 *
	 * @param time the new fade in time
	 */
	public void setFadeInTime(int time) {
		fadeInTime = time;
	}

	/**
	 * Sets the fade out time.
	 *
	 * @param time the new fade out time
	 */
	public void setFadeOutTime(int time) {
		fadeOutTime = time;
	}

	/**
	 * Sets the stay time.
	 *
	 * @param time the new stay time
	 */
	public void setStayTime(int time) {
		stayTime = time;
	}

	/**
	 * Sets the subtitle.
	 *
	 * @param subtitle the new subtitle
	 */
	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	/**
	 * Sets the title.
	 *
	 * @param title the new title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Update subtitle.
	 *
	 * @param player the player
	 */
	public void updateSubtitle(Player player) {
		updateSubtitle(player, subtitle);
	}

	public void updateSubtitle(Player player, String subtitle) {
		player.sendTitle("", StringParser.getInstance().colorize(subtitle), -1, -1, -1);
	}

	/**
	 * Update times.
	 *
	 * @param player the player
	 */
	public void updateTimes(Player player) {
		updateTimes(player, fadeInTime, stayTime, fadeOutTime);
	}

	public void updateTimes(Player player, int fadeInTime, int stayTime, int fadeOutTime) {
		player.sendTitle("", "", fadeInTime, stayTime, fadeOutTime);
	}

	/**
	 * Update title.
	 *
	 * @param player the player
	 */
	public void updateTitle(Player player) {
		updateTitle(player, title);
	}

	public void updateTitle(Player player, String title) {
		player.sendTitle(StringParser.getInstance().colorize(title), "", -1, -1, -1);
	}
}