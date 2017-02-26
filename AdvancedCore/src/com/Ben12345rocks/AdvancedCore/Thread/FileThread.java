package com.Ben12345rocks.AdvancedCore.Thread;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UserData;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

/**
 * The Class Thread.
 */
public class FileThread {

	/**
	 * The Class ReadThread.
	 */
	public class ReadThread extends java.lang.Thread {

		@Override
		public void run() {
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
				File dFile = getPlayerFile(uuid);
				FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
				notify();
				return data;
			}

		}

		public synchronized void setData(UserData userData, final String uuid, final String path, final Object value) {
			synchronized (this) {
				try {
					File dFile = getPlayerFile(uuid);
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

		public synchronized File getPlayerFile(String uuid) {
			synchronized (this) {
				File dFile = new File(
						AdvancedCoreHook.getInstance().getPlugin().getDataFolder() + File.separator + "Data",
						uuid + ".yml");

				FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
				if (!dFile.exists()) {
					FilesManager.getInstance().editFile(dFile, data);
				}
				notify();
				return dFile;
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
		getThread().run(run);
	}
}
