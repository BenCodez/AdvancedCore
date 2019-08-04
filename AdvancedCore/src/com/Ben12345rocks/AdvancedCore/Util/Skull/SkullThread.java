package com.Ben12345rocks.AdvancedCore.Util.Skull;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.NameFetcher;

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
			synchronized (SkullThread.getInstance().getThread()) {
				if (!plugin.isEnabled()) {
					return;
				}
				if (playerName == null || playerName.isEmpty()) {
					// AdvancedCoreHook.getInstance().debug("Invalid skull name");
					return;
				}
				if (SkullHandler.getInstance().hasSkull(playerName)) {
					return;
				}
				Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

					@Override
					public void run() {
						SkullThread.getInstance().run(new Runnable() {

							@Override
							public void run() {
								ItemStack s = SkullHandler.getInstance().getSkull(playerName);

								try {
									SkullHandler.getInstance().getSkulls().put(playerName,
											SkullHandler.getInstance().getAsNMSCopy().invoke(null, s));
								} catch (Exception e) {
									plugin.getLogger()
											.info("Failed to preload skull: " + playerName + ", waiting 10 minutes");
									plugin.debug(e);
									try {
										sleep(600000);
									} catch (InterruptedException e1) {
										e1.printStackTrace();
									}
								}
								plugin.extraDebug("Loaded skull: " + playerName);

							}
						});
					}
				});
			}
		}

		public void checkQueue() {
			while (!SkullHandler.getInstance().getSkullQueue().isEmpty()) {
				load(SkullHandler.getInstance().getSkullQueue().poll());
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
			synchronized (SkullThread.getInstance()) {
				if (!plugin.isEnabled()) {
					return;
				}
				run.run();
			}
		}

		public void startup() {
			synchronized (SkullThread.getInstance().getThread()) {
				for (String name : UserManager.getInstance().getAllPlayerNames()) {
					SkullHandler.getInstance().loadSkull(name);

					try {
						sleep(1100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
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

		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				getThread().checkQueue();
			}
		}, 1000, 1000 * 10);
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
