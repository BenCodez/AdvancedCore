package com.Ben12345rocks.AdvancedCore.Thread;

import com.Ben12345rocks.AdvancedCore.Main;

public class Thread {
	/**
	 * The Class ReadThread.
	 */
	public class ReadThread extends java.lang.Thread {
		public void run(Runnable run) {
			run.run();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			// plugin.getLogger().info("File Editing Thread Loaded!");
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
	 * Instantiates a new files manager.
	 */
	private Thread() {
	}

	/**
	 * Instantiates a new files manager.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Thread(Main plugin) {
		Thread.plugin = plugin;
	}

	/**
	 * Edits the file.
	 *
	 * @param file
	 *            the file
	 * @param data
	 *            the data
	 */
	public void run(Runnable run) {
		thread.run(run);
	}

	/**
	 * Load file editng thread.
	 */
	public void loadThread() {
		thread = new ReadThread();
		thread.start();
	}
}
