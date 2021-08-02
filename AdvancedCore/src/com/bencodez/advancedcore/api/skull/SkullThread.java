package com.bencodez.advancedcore.api.skull;

import java.util.Arrays;

import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.NameFetcher;

/**
 * The Class Thread.
 */
public class SkullThread {

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

		public void load(String playerName) {
			if (!plugin.isEnabled()) {
				return;
			}
			if (playerName == null || playerName.isEmpty()) {
				return;
			}
			if (SkullHandler.getInstance().hasSkull(playerName)) {
				return;
			}

			try {
				ItemStack s = SkullHandler.getInstance().getSkull(playerName);

				SkullHandler.getInstance().getSkulls().put(playerName,
						SkullHandler.getInstance().getAsNMSCopy().invoke(null, s));
			} catch (Exception e) {
				plugin.getLogger().info("Failed to preload skull: " + playerName + ", waiting 10 minutes");
				plugin.debug(e);
				try {
					sleep(600000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			plugin.extraDebug("Loaded skull: " + playerName);

		}

		public void runBackgroundCheck() {
			if (plugin.isEnabled()) {
				String str = SkullHandler.getInstance().skullsToLoad.poll();
				int count = 0;
				while (str != null) {
					if (!SkullHandler.getInstance().getSkulls().containsKey(str)) {
						load(str);
					}
					count++;

					if (count > 50) {
						str = null;
					} else {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							count = -1;
						}
						if (count != -1) {
							str = SkullHandler.getInstance().skullsToLoad.poll();
						}
					}

				}
			}
		}

		@Override
		public void run() {
			while (true) {
				runBackgroundCheck();
				try {
					sleep(60000);
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
			synchronized (obj) {
				if (!plugin.isEnabled()) {
					return;
				}
				run.run();
			}
		}
	}

	/** The instance. */
	static SkullThread instance = new SkullThread();

	/**
	 * Gets the single instance of Thread.
	 *
	 * @return single instance of Thread
	 */
	public static SkullThread getInstance() {
		return instance;
	}

	private Object obj = new Object();

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The thread. */
	private ReadThread thread;

	/**
	 * Instantiates a new thread.
	 */
	private SkullThread() {
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
	 * @param run the run
	 */
	public void run(Runnable run) {
		getThread().run(run);
	}
}
