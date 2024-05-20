package com.bencodez.advancedcore.api.scheduler;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.tcoded.folialib.FoliaLib;

import lombok.Getter;

@Deprecated
public class BukkitScheduler {
	@Getter
	private FoliaLib foliaLib;

	public BukkitScheduler(JavaPlugin plugin) {
		foliaLib = new FoliaLib(plugin);
	}

	public void executeOrScheduleSync(Plugin plugin, Runnable task) {
		getFoliaLib().getImpl().runNextTick(task);
	}

	public void executeOrScheduleSync(Plugin plugin, Runnable task, Entity entity) {
		getFoliaLib().getImpl().runAtEntity(entity, task);
	}

	public void executeOrScheduleSync(Plugin plugin, Runnable task, Location location) {
		getFoliaLib().getImpl().runAtLocation(location, task);
	}

	public void runTask(Plugin plugin, Runnable task, Entity entity) {
		getFoliaLib().getImpl().runAtEntity(entity, task);
	}

	public void runTaskLater(Plugin plugin, Runnable task, long delay, Entity entity) {
		getFoliaLib().getImpl().runAtEntityLater(entity, task, delay, TimeUnit.SECONDS);
	}

	public void runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Entity entity) {
		getFoliaLib().getImpl().runTimer(task, delay, period, TimeUnit.SECONDS);
	}

	public void runTask(Plugin plugin, Runnable task, Location location) {
		getFoliaLib().getImpl().runAtLocation(location, task);
	}

	public void runTaskLater(Plugin plugin, Runnable task, long delay, Location location) {
		getFoliaLib().getImpl().runAtLocationLater(location, task, delay, TimeUnit.SECONDS);
	}

	public void runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Location location) {
		getFoliaLib().getImpl().runAtLocationTimer(location, task, delay, period, TimeUnit.SECONDS);
	}

	public void runTask(Plugin plugin, Runnable task) {
		getFoliaLib().getImpl().runNextTick(task);
	}

	public void runTaskLater(Plugin plugin, Runnable task, long delay) {
		getFoliaLib().getImpl().runLater(task, delay, TimeUnit.SECONDS);
	}

	public void runTaskLater(Plugin plugin, Runnable task, long delay, TimeUnit time) {
		getFoliaLib().getImpl().runLater(task, delay, time);
	}

	public void runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
		getFoliaLib().getImpl().runTimer(task, delay, period, TimeUnit.SECONDS);
	}

	public void runTaskAsynchronously(Plugin plugin, Runnable task) {
		getFoliaLib().getImpl().runAsync(task);
	}

	public void runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
		getFoliaLib().getImpl().runLaterAsync(task, delay, TimeUnit.SECONDS);
	}

	public void runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
		getFoliaLib().getImpl().runTimerAsync(task, delay, period, TimeUnit.SECONDS);
	}

}