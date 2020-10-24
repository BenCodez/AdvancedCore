package com.Ben12345rocks.AdvancedCore.Util.Book;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

/**
 * The Class BookManager.
 */
public class BookManager implements Listener {

	/** The listener. */
	public Listener listener;

	/**
	 * Instantiates a new book manager.
	 *
	 * @param player   the player
	 * @param start    the start
	 * @param listener the listener
	 */
	public BookManager(Player player, String start, BookSign listener) {
		User user = UserManager.getInstance().getUser(player);
		ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
		PlayerUtils.getInstance().setPlayerMeta(player, "BookManager", listener);

		user.giveItem(item);

		this.listener = new Listener() {
			@SuppressWarnings("deprecation")
			@EventHandler
			public void bookEdit(PlayerEditBookEvent event) {
				Player player = event.getPlayer();
				boolean destory = false;

				String st = "";
				for (String str : event.getNewBookMeta().getPages()) {
					st += str;
				}
				final String input = st;
				BookSign listener = (BookSign) PlayerUtils.getInstance().getPlayerMeta(player, "BookManager");
				Bukkit.getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

					@Override
					public void run() {
						listener.onBookSign(player, input);
					}
				});

				player.getInventory().getItem(event.getSlot()).setType(Material.AIR);
				player.getInventory().setItem(event.getSlot(), new ItemStack(Material.AIR));
				destory = true;

				if (destory) {
					destroy();
				}

			}

		};
		Bukkit.getPluginManager().registerEvents(this.listener, AdvancedCorePlugin.getInstance());
	}

	/**
	 * Destroy.
	 */
	public void destroy() {
		HandlerList.unregisterAll(listener);
	}
}