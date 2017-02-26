package com.Ben12345rocks.AdvancedCore.Thread;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

/**
 * The Class Thread.
 */
public class Thread {

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
	private Thread() {
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
