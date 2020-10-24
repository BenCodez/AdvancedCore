package com.Ben12345rocks.AdvancedCore.Util.Effects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class ActionBar.
 */
public class ActionBar {

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The duration. */
	private int duration;

	/** The msg. */
	private String msg;

	/**
	 * Instantiates a new action bar.
	 *
	 * @param msg      the msg
	 * @param duration the duration
	 */
	public ActionBar(String msg, int duration) {
		setMsg(StringParser.getInstance().colorize(msg));
		setDuration(duration);
	}

	/**
	 * Gets the duration.
	 *
	 * @return the duration
	 */
	public int getDuration() {
		return duration;
	}

	/**
	 * Gets the msg.
	 *
	 * @return the msg
	 */
	public String getMsg() {
		return msg;
	}

	/**
	 * Send.
	 *
	 * @param players the players
	 */
	public void send(Player... players) {
		for (Player player : players) {
			sendActionBar(player, getMsg(), getDuration());
		}
	}

	/**
	 * Send action bar.
	 *
	 * @param player  the player
	 * @param message the message
	 */
	public void sendActionBar(Player player, String message) {
		if (!NMSManager.getInstance().isVersion("1.7", "1.8", "1.9", "1.10", "1.11", "1.12")) {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
		}
	}

	/**
	 * Send action bar.
	 *
	 * @param player   the player
	 * @param message  the message
	 * @param duration the duration
	 */
	public void sendActionBar(final Player player, final String message, int duration) {
		sendActionBar(player, message);

		if (duration >= 0) {
			// Sends empty message at the end of the duration. Allows messages
			// shorter than 3 seconds, ensures precision.
			new BukkitRunnable() {
				@Override
				public void run() {
					sendActionBar(player, "");
				}
			}.runTaskLater(plugin, duration + 1);
		}

		// Re-sends the messages every 3 seconds so it doesn't go away from the
		// player's screen.
		while (duration > 60) {
			duration -= 60;
			int sched = duration % 60;
			new BukkitRunnable() {
				@Override
				public void run() {
					sendActionBar(player, message);
				}
			}.runTaskLater(plugin, sched);
		}
	}

	/**
	 * Send action bar to all players.
	 *
	 * @param message the message
	 */
	public void sendActionBarToAllPlayers(String message) {
		sendActionBarToAllPlayers(message, -1);
	}

	/**
	 * Send action bar to all players.
	 *
	 * @param message  the message
	 * @param duration the duration
	 */
	public void sendActionBarToAllPlayers(String message, int duration) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			sendActionBar(p, message, duration);
		}
	}

	/**
	 * Sets the duration.
	 *
	 * @param duration the new duration
	 */
	public void setDuration(int duration) {
		this.duration = duration;
	}

	/**
	 * Sets the msg.
	 *
	 * @param msg the new msg
	 */
	public void setMsg(String msg) {
		this.msg = msg;
	}
}
