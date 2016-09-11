package com.Ben12345rocks.AdvancedCore.Util.Book;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.User;

/**
 * The Class BookManager.
 */
public class BookManager implements Listener {

	/** The listener. */
	public Listener listener;

	/**
	 * Instantiates a new book manager.
	 *
	 * @param player
	 *            the player
	 * @param start
	 *            the start
	 * @param listener
	 *            the listener
	 */
	public BookManager(Player player, String start, BookSign listener) {
		User user = new User(Main.plugin, player);
		ItemStack item = new ItemStack(Material.BOOK_AND_QUILL);
		player.setMetadata("BookManager", new MetadataValue() {

			@Override
			public boolean asBoolean() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public byte asByte() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public double asDouble() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public float asFloat() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int asInt() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public long asLong() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public short asShort() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public String asString() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Plugin getOwningPlugin() {
				return Main.plugin;
			}

			@Override
			public void invalidate() {

			}

			@Override
			public Object value() {
				return listener;
			}
		});
		user.giveItem(item);

		this.listener = new Listener() {
			@EventHandler
			public void bookEdit(PlayerEditBookEvent event) {
				Player player = event.getPlayer();
				List<MetadataValue> data = player.getMetadata("BookManager");
				boolean destory = false;
				for (MetadataValue meta : data) {
					if (meta.getOwningPlugin().equals(Main.plugin)) {
						String input = "";
						for (String str : event.getNewBookMeta().getPages()) {
							input += str;
						}
						BookSign listener = (BookSign) meta.value();
						listener.onBookSign(player, input);
						player.getInventory().getItem(event.getSlot())
								.setType(Material.AIR);
						player.getInventory().setItem(event.getSlot(),
								new ItemStack(Material.AIR));
						player.removeMetadata("BookManager", Main.plugin);
						destory = true;
					}

				}
				if (destory) {
					destroy();
				}

			}

		};
		Bukkit.getPluginManager().registerEvents(this.listener, Main.plugin);
	}

	/**
	 * Destroy.
	 */
	public void destroy() {
		HandlerList.unregisterAll(listener);
	}
}