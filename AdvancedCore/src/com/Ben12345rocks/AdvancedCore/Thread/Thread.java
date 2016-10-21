package com.Ben12345rocks.AdvancedCore.Thread;

import com.Ben12345rocks.AdvancedCore.Main;

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
	static Main plugin = Main.plugin;

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
	 * Instantiates a new thread.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Thread(Main plugin) {
		Thread.plugin = plugin;
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
		thread.run(run);
	}
}
