package com.bencodez.advancedcore.api.misc.effects;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

// TODO: Auto-generated Javadoc
/**
 * The Class BossBar.
 */
public class BossBar {

	/**
	 * Gets the boss bar.
	 *
	 * @return the boss bar
	 */
	@Getter
	private org.bukkit.boss.BossBar bossBar;

	/**
	 * Instantiates a new boss bar.
	 *
	 * @param msg      the msg
	 * @param barColor the bar color
	 * @param barStyle the bar style
	 * @param progress the progress
	 */
	public BossBar(String msg, String barColor, String barStyle, double progress) {
		bossBar = Bukkit.createBossBar(MessageAPI.colorize(msg), BarColor.valueOf(barColor),
				BarStyle.valueOf(barStyle));
		bossBar.setProgress(progress);
	}

	/**
	 * Adds a player to the boss bar.
	 *
	 * @param player the player to add
	 */
	public void addPlayer(Player player) {
		bossBar.addPlayer(player);
	}

	/**
	 * Adds a player to the boss bar with delay.
	 *
	 * @param player the player to add
	 * @param delay the delay in seconds before removing
	 */
	public void addPlayer(final Player player, int delay) {
		try {
			if (player == null) {
				return;
			}
			bossBar.addPlayer(player);

			if (delay > 0) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler().runTaskLater(AdvancedCorePlugin.getInstance(),
						new Runnable() {

							@Override
							public void run() {
								if (bossBar != null && player != null) {
									bossBar.removePlayer(player);
								}
							}
						}, delay * 50 + 60, TimeUnit.MILLISECONDS);
			}
		} catch (Exception e) {
			AdvancedCorePlugin.getInstance().debug(e);
		}
	}

	/**
	 * Gets the players viewing the boss bar.
	 *
	 * @return the list of players
	 */
	public List<Player> getPlayers() {
		return bossBar.getPlayers();
	}

	/**
	 * Hides the boss bar.
	 */
	public void hide() {
		if (bossBar != null) {
			bossBar.setVisible(false);
			bossBar.removeAll();
		}
	}

	private void hideInDelay(int delay) {
		AdvancedCorePlugin.getInstance().getBukkitScheduler().runTaskLater(AdvancedCorePlugin.getInstance(),
				new Runnable() {

					@Override
					public void run() {
						hide();
					}
				}, delay * 50, TimeUnit.MILLISECONDS);
	}

	/**
	 * Removes a player from the boss bar.
	 *
	 * @param player the player to remove
	 */
	public void removePlayer(Player player) {
		bossBar.removePlayer(player);
	}

	/**
	 * Sends the boss bar to all players.
	 */
	public void send() {
		bossBar.setVisible(true);
	}

	/**
	 * Sends the boss bar with delay.
	 *
	 * @param delay the delay in seconds
	 */
	public void send(int delay) {
		bossBar.setVisible(true);

		hideInDelay(delay);
	}

	/**
	 * Send.
	 *
	 * @param player the player
	 * @param delay  the delay
	 */
	public void send(Player player, int delay) {
		bossBar.addPlayer(player);
		bossBar.setVisible(true);
		hideInDelay(delay);
	}

	/**
	 * Sets the color of the boss bar.
	 *
	 * @param barColor the bar color
	 */
	public void setColor(String barColor) {
		if (barColor != null) {
			bossBar.setColor(BarColor.valueOf(barColor));
		}
	}

	/**
	 * Sets the progress of the boss bar.
	 *
	 * @param progress the progress (0.0 to 1.0)
	 */
	public void setProgress(double progress) {
		if (progress > 1) {
			progress = 1;
		}
		if (progress < 0) {
			progress = 0;
		}
		bossBar.setProgress(progress);
	}

	/**
	 * Sets the style of the boss bar.
	 *
	 * @param barStyle the bar style
	 */
	public void setStyle(String barStyle) {
		if (barStyle != null) {
			bossBar.setStyle(BarStyle.valueOf(barStyle));
		}
	}

	/**
	 * Sets the title of the boss bar.
	 *
	 * @param title the title
	 */
	public void setTitle(String title) {
		if (title != null) {
			bossBar.setTitle(MessageAPI.colorize(title));
		}

	}

	/**
	 * Sets the visibility of the boss bar.
	 *
	 * @param visible true to show, false to hide
	 */
	public void setVisible(boolean visible) {
		bossBar.setVisible(visible);
	}
}