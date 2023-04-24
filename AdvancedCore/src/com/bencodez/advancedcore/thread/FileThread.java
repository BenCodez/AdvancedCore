package com.bencodez.advancedcore.thread;

import com.bencodez.advancedcore.AdvancedCorePlugin;

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
			while (!thread.isInterrupted()) {
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
		 * @param run the run
		 */
		public void run(Runnable run) {
			synchronized (FileThread.getInstance()) {
				run.run();
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
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

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
	 * @param run the run
	 */
	public void run(Runnable run) {
		getThread().run(run);
	}
}