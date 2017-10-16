package com.Ben12345rocks.AdvancedCore.Util.Effects;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

// TODO: Auto-generated Javadoc
/**
 * The Class BossBar.
 */
public class BossBar {

	/** The boss bar. */
	public org.bukkit.boss.BossBar bossBar;

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

	public void setProgress(double progress) {
		bossBar.setProgress(progress);
	}

	public void setTitle(String title) {
		bossBar.setTitle(title);
	}

	public void setStyle(String barStyle) {
		bossBar.setStyle(BarStyle.valueOf(barStyle));
	}

	public void setColor(String barColor) {
		bossBar.setColor(BarColor.valueOf(barColor));
	}

	public void hide() {
		bossBar.setVisible(false);
		bossBar.removeAll();
	}

	public void addPlayer(Player player) {
		bossBar.addPlayer(player);
	}

	public void send(int delay) {
		bossBar.setVisible(true);
		Bukkit.getScheduler().runTaskLater(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

			@Override
			public void run() {
				hide();
			}
		}, delay);
	}

	public void setVisilble(boolean visible) {
		bossBar.setVisible(visible);
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
		Bukkit.getScheduler().runTaskLater(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

			@Override
			public void run() {
				hide();
			}
		}, delay);
	}
}