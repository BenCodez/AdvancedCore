package com.bencodez.advancedcore.api.misc.effects;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

/**
 * Bukkit boss bar wrapper used by rewards and downstream plugins.
 */
public class BossBar {

	@Getter
	private org.bukkit.boss.BossBar bossBar;

	public BossBar(String msg, String barColor, String barStyle, double progress) {
		bossBar = Bukkit.createBossBar(MessageAPI.colorize(msg), EffectCompatibility.parseBarColor(barColor),
				EffectCompatibility.parseBarStyle(barStyle));
		bossBar.setProgress(EffectCompatibility.clampProgress(progress));
	}

	public void addPlayer(Player player) {
		if (player != null) {
			bossBar.addPlayer(player);
		}
	}

	public void addPlayer(final Player player, int delay) {
		try {
			if (player == null) {
				return;
			}
			bossBar.addPlayer(player);
			if (delay > 0) {
				AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
				if (plugin != null) {
					plugin.getBukkitScheduler().runTaskLater(plugin, () -> {
						if (bossBar != null) {
							bossBar.removePlayer(player);
						}
					}, delay * 50L + 60L, TimeUnit.MILLISECONDS);
				}
			}
		} catch (Exception e) {
			AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
			if (plugin != null) {
				plugin.debug(e);
			}
		}
	}

	public List<Player> getPlayers() {
		return bossBar.getPlayers();
	}

	public void hide() {
		if (bossBar != null) {
			bossBar.setVisible(false);
			bossBar.removeAll();
		}
	}

	private void hideInDelay(int delay) {
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		if (plugin == null) {
			return;
		}
		plugin.getBukkitScheduler().runTaskLater(plugin, this::hide, delay * 50L, TimeUnit.MILLISECONDS);
	}

	public void removePlayer(Player player) {
		if (player != null) {
			bossBar.removePlayer(player);
		}
	}

	public void send() {
		bossBar.setVisible(true);
	}

	public void send(int delay) {
		bossBar.setVisible(true);
		hideInDelay(delay);
	}

	public void send(Player player, int delay) {
		if (player == null) {
			return;
		}
		bossBar.addPlayer(player);
		bossBar.setVisible(true);
		hideInDelay(delay);
	}

	public void setColor(String barColor) {
		if (barColor != null) {
			bossBar.setColor(EffectCompatibility.parseBarColor(barColor));
		}
	}

	public void setProgress(double progress) {
		bossBar.setProgress(EffectCompatibility.clampProgress(progress));
	}

	public void setStyle(String barStyle) {
		if (barStyle != null) {
			bossBar.setStyle(EffectCompatibility.parseBarStyle(barStyle));
		}
	}

	public void setTitle(String title) {
		if (title != null) {
			bossBar.setTitle(MessageAPI.colorize(title));
		}
	}

	public void setVisible(boolean visible) {
		bossBar.setVisible(visible);
	}
}
