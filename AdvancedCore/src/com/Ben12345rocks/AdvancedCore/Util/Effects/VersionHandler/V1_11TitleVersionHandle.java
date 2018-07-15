package com.Ben12345rocks.AdvancedCore.Util.Effects.VersionHandler;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

/**
 * Minecraft 1.8 Title For 1.11
 *
 * @author Maxim Van de Wynckel
 * @version 1.1.0
 */
public class V1_11TitleVersionHandle implements TitleVersionHandle {

	/**
	 * Clear the title
	 *
	 * @param player
	 *            Player
	 */
	@Override
	public void clearTitle(Player player) {
		player.sendTitle("", "", -1, -1, -1);
	}

	/**
	 * Reset the title settings
	 *
	 * @param player
	 *            Player
	 */
	@Override
	public void resetTitle(Player player) {
		clearTitle(player);
	}

	/**
	 * Send the title to a player
	 *
	 * @param player
	 *            Player
	 */
	@Override
	public void send(Player player, String title, String subtitle, int fadeInTime, int stayTime, int fadeOutTime) {
		player.sendTitle(StringUtils.getInstance().colorize(title), StringUtils.getInstance().colorize(subtitle),
				fadeInTime, stayTime, fadeOutTime);
	}

	@Override
	public void updateSubtitle(Player player, String subtitle) {
		player.sendTitle("", StringUtils.getInstance().colorize(subtitle), -1, -1, -1);
	}

	@Override
	public void updateTimes(Player player, int fadeInTime, int stayTime, int fadeOutTime) {
		player.sendTitle("", "", fadeInTime, stayTime, fadeOutTime);
	}

	@Override
	public void updateTitle(Player player, String title) {
		player.sendTitle(StringUtils.getInstance().colorize(title), "", -1, -1, -1);
	}
}
