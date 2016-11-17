package com.Ben12345rocks.AdvancedCore.Util.Metrics;

import java.io.IOException;

import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.Main;

// TODO: Auto-generated Javadoc
/**
 * The Class Metrics.
 */
public class Metrics {

	private MCStatsMetrics mcStats;
	private BStatsMetrics bStats;

	/**
	 * Send metrics data to MCStats and BStats
	 * This will not actually throw any errors
	 * 
	 * @param plugin
	 */
	public Metrics(final JavaPlugin plugin) throws IOException {
		try {
			mcStats = new MCStatsMetrics(plugin);
			mcStats.start();
		} catch (Exception ex) {
			Main.plugin.debug("Failed to load mcstats metrics for: " + plugin.getName() + ", " + ex.getMessage());
		}
		try {
			bStats = new BStatsMetrics(plugin);
		} catch (Exception ex) {
			Main.plugin.debug("Failed to load bstats metrics for: " + plugin.getName() + ", " + ex.getMessage());
		}

	}

	public MCStatsMetrics getMcStats() {
		return mcStats;
	}

	public BStatsMetrics getbStats() {
		return bStats;
	}

	/**
	 * This method is here to maintain compatibility with the old Metrics
	 * classes The method does nothing.
	 */
	public void start() {

	}

}