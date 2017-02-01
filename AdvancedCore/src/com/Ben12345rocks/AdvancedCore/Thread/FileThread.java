package com.Ben12345rocks.AdvancedCore.Thread;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UserData;

/**
 * The Class Thread.
 */
public class FileThread {

	/**
	 * The Class ReadThread.
	 */
	public class ReadThread extends java.lang.Thread {

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			// plugin.getLogger().info("File Editing Thread Loaded!");
		}

		/**
		 * Run.
		 *
		 * @param run
		 *            the run
		 */
		public synchronized void run(Runnable run) {
			run.run();
		}

		public synchronized FileConfiguration getData(UserData userData, String uuid) {
			synchronized (this) {
				File dFile = userData.getPlayerFile(uuid);
				FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
				notify();
				return data;
			}

		}

		public synchronized void setData(UserData userData, final String uuid, final String path, final Object value) {
			synchronized (this) {
				try {
					File dFile = userData.getPlayerFile(uuid);
					FileConfiguration data = getData(userData, uuid);
					data.set(path, value);
					data.save(dFile);
				} catch (Exception e) {
					AdvancedCoreHook.getInstance().debug("Failed to set a value for " + uuid + ".yml");
					AdvancedCoreHook.getInstance().debug(e);
				}
				notify();
			}

		}
	}

	/** The instance. */
	static FileThread instance = new FileThread();

	/**
	 * Gets the single instance of Thread.
	 *
	 * @return single instance of Thread
	 */
	public static FileThread getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/** The thread. */
	private ReadThread thread;

	/**
	 * @return the thread
	 */
	public ReadThread getThread() {
		if (thread == null || !thread.isAlive()) {
			loadThread();
		}
		return thread;
	}

	/**
	 * Instantiates a new thread.
	 */
	private FileThread() {
	}

	/**
	 * Load thread.
	 */
	public void loadThread() {
		thread = new ReadThread();
		thread.start();
	}

	/**
	 * Run.
	 *
	 * @param run
	 *            the run
	 */
	public synchronized void run(Runnable run) {
		if (thread == null || !thread.isAlive()) {
			loadThread();
		}
		thread.run(run);
	}
}
