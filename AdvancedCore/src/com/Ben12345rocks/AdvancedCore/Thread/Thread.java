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
	}

	/** The instance. */
	static Thread instance = new Thread();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Gets the single instance of Thread.
	 *
	 * @return single instance of Thread
	 */
	public static Thread getInstance() {
		return instance;
	}

	/** The thread. */
	public ReadThread thread;

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
		if (thread == null || !thread.isAlive()) {
			loadThread();
		}
		thread.run(run);
	}
}
