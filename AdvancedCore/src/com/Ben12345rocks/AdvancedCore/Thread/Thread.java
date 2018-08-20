package com.Ben12345rocks.AdvancedCore.Thread;

import java.util.Arrays;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.NameFetcher;

/**
 * The Class Thread.
 */
public class Thread {

	/**
	 * The Class ReadThread.
	 */
	public class ReadThread extends java.lang.Thread {

		public String getName(java.util.UUID uuid) {
			plugin.debug("Looking up player name: " + uuid);
			NameFetcher fet = new NameFetcher(Arrays.asList(uuid));
			try {
				return fet.call().get(uuid);
			} catch (Exception e) {
				// AdvancedCoreHook.getInstance().debug(e);
				return "";
			}
		}

		@Override
		public void run() {
			while (true) {
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
			synchronized (Thread.getInstance()) {
				run.run();
			}
		}
	}

	/** The instance. */
	static Thread instance = new Thread();

	/**
	 * Gets the single instance of Thread.
	 *
	 * @return single instance of Thread
	 */
	public static Thread getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/** The thread. */
	private ReadThread thread;

	/**
	 * Instantiates a new thread.
	 */
	private Thread() {
	}

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
