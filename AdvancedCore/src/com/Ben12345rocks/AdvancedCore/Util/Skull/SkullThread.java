package com.Ben12345rocks.AdvancedCore.Util.Skull;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
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
				run.run();
			}
		}

		public void load(String playerName) {
			synchronized (SkullThread.getInstance().getThread()) {
				if (playerName == null || playerName.isEmpty()) {
					// AdvancedCoreHook.getInstance().debug("Invalid skull name");
					return;
				}
				if (SkullHandler.getInstance().hasSkull(playerName)) {
					return;
				}
				Bukkit.getScheduler().runTaskAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

					@Override
					public void run() {
						SkullThread.getInstance().run(new Runnable() {

							@SuppressWarnings("deprecation")
							@Override
							public void run() {
								ItemStack s = new ItemStack(Material.PLAYER_HEAD, 1);
								SkullMeta meta = (SkullMeta) s.getItemMeta();
								meta.setOwner(playerName);
								s.setItemMeta(meta);

								try {
									SkullHandler.getInstance().getSkulls().put(playerName,
											SkullHandler.getInstance().getAsNMSCopy().invoke(null, s));
								} catch (Exception e) {
									AdvancedCoreHook.getInstance().getPlugin().getLogger()
											.info("Failed to preload skull: " + playerName + ", waiting 10 minutes");
									AdvancedCoreHook.getInstance().debug(e);
									try {
										sleep(600000);
									} catch (InterruptedException e1) {
										e1.printStackTrace();
									}
								}
								AdvancedCoreHook.getInstance().extraDebug("Loading skull: " + playerName);

							}
						});
					}
				});
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
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

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
	 * @param run
	 *            the run
	 */
	public void run(Runnable run) {
		getThread().run(run);
	}
}
