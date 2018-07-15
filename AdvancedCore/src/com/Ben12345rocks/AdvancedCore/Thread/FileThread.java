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

		public void deletePlayerFile(String uuid) {
			synchronized (FileThread.getInstance()) {
				try {
					File dFile = new File(
							AdvancedCoreHook.getInstance().getPlugin().getDataFolder() + File.separator + "Data",
							uuid + ".yml");
					if (dFile.exists()) {
						dFile.delete();
					}

				} catch (Exception e) {
					AdvancedCoreHook.getInstance().debug(e);
				}
			}
		}

		public FileConfiguration getData(UserData userData, String uuid) {
			synchronized (FileThread.getInstance()) {
				try {
					File dFile = getPlayerFile(uuid);
					if (dFile != null) {
						FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
						return data;
					}
				} catch (Exception e) {
					AdvancedCoreHook.getInstance().debug(e);
				}
				AdvancedCoreHook.getInstance().getPlugin().getLogger()
						.warning("Filed to load " + uuid + ".yml, turn debug on to see full stacktraces");
				return null;
			}

		}

		public File getPlayerFile(String uuid) {
			synchronized (FileThread.getInstance()) {
				try {
					File dFile = new File(
							AdvancedCoreHook.getInstance().getPlugin().getDataFolder() + File.separator + "Data",
							uuid + ".yml");
					FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
					if (!dFile.exists()) {
						FilesManager.getInstance().editFile(dFile, data);
					}
					return dFile;
				} catch (Exception e) {
					AdvancedCoreHook.getInstance().debug(e);
				}
				AdvancedCoreHook.getInstance().getPlugin().getLogger()
						.warning("Failed to load " + uuid + ".yml, turn debug on to see full stacktraces");
				return null;
			}
		}

		public boolean hasPlayerFile(String uuid) {
			synchronized (FileThread.getInstance()) {
				try {
					File dFile = new File(
							AdvancedCoreHook.getInstance().getPlugin().getDataFolder() + File.separator + "Data",
							uuid + ".yml");
					return dFile.exists();

				} catch (Exception e) {
					AdvancedCoreHook.getInstance().debug(e);
				}
				return false;
			}
		}

		@Override
		public void run() {
			while(true) {
				try {
					sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}
			}
		}

		/**
		 * Run.
		 *
		 * @param run
		 *            the run
		 */
		public void run(Runnable run) {
			synchronized (FileThread.getInstance()) {
				run.run();
			}

		}

		public void setData(UserData userData, final String uuid, final String path, final Object value) {
			synchronized (FileThread.getInstance()) {
				try {
					File dFile = getPlayerFile(uuid);
					FileConfiguration data = getData(userData, uuid);
					data.set(path, value);
					data.save(dFile);
				} catch (Exception e) {
					AdvancedCoreHook.getInstance().getPlugin().getLogger().warning(
							"Failed to set a value for " + uuid + ".yml, turn debug on to see full stacktraces");
					AdvancedCoreHook.getInstance().debug(e);
				}
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
	 * Instantiates a new thread.
	 */
	private FileThread() {
	}

	/**
	 * @return the thread
	 */
	public ReadThread getThread() {
		if (thread == null || !thread.isAlive()) {
			plugin.debug("Loading thread");
			loadThread();
		}
		return thread;
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
	public void run(Runnable run) {
		getThread().run(run);
	}
}
