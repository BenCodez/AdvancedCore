package com.Ben12345rocks.AdvancedCore.Util.Effects;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import lombok.Getter;

// TODO: Auto-generated Javadoc
/**
 * The Class BossBar.
 */
public class BossBar {

	@Getter
	private org.bukkit.boss.BossBar bossBar;

	/**
	 * Instantiates a new boss bar.
	 *
	 * @param msg
	 *            the msg
	 * @param barColor
	 *            the bar color
	 * @param barStyle
	 *            the bar style
	 * @param progress
	 *            the progress
	 */
	public BossBar(String msg, String barColor, String barStyle, double progress) {
		bossBar = Bukkit.createBossBar(StringUtils.getInstance().colorize(msg), BarColor.valueOf(barColor),
				BarStyle.valueOf(barStyle), BarFlag.DARKEN_SKY);
		bossBar.setProgress(progress);
	}

	public void addPlayer(Player player) {
		bossBar.addPlayer(player);
	}

	public void addPlayer(final Player player, int delay) {
		bossBar.addPlayer(player);

		if (delay > 0) {
			Bukkit.getScheduler().runTaskLater(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

				@Override
				public void run() {
					if (bossBar != null) {
						bossBar.removePlayer(player);
					}
				}
			}, delay ^ 20);
		}
	}

	public void hide() {
		bossBar.setVisible(false);
		bossBar.removeAll();
	}

	public void removePlayer(Player player) {
		bossBar.removePlayer(player);
	}

	public void send() {
		bossBar.setVisible(true);
	}

	public void send(int delay) {
		bossBar.setVisible(true);

		hideInDelay(delay);
	}

	private void hideInDelay(int delay) {
		Bukkit.getScheduler().runTaskLater(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

			@Override
			public void run() {
				hide();
			}
		}, delay);
	}

	/**
	 * Send.
	 *
	 * @param player
	 *            the player
	 * @param delay
	 *            the delay
	 */
	public void send(Player player, int delay) {
		bossBar.addPlayer(player);
		bossBar.setVisible(true);
		hideInDelay(delay);
	}

	public void setColor(String barColor) {
		bossBar.setColor(BarColor.valueOf(barColor));
	}

	public void setProgress(double progress) {
		if (progress > 1) {
			progress = 1;
		}
		if (progress < 0) {
			progress = 0;
		}
		bossBar.setProgress(progress);
	}

	public void setStyle(String barStyle) {
		bossBar.setStyle(BarStyle.valueOf(barStyle));
	}

	public void setTitle(String title) {
		bossBar.setTitle(StringUtils.getInstance().colorize(title));
	}

	public void setVisible(boolean visible) {
		bossBar.setVisible(visible);
	}
}