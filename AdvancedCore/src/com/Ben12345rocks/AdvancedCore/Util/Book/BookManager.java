package com.Ben12345rocks.AdvancedCore.Util.Book;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.User;

public class BookManager implements Listener {

	public Listener listener;

	public BookManager(Player player, String start, BookSign listener) {
		User user = new User(Main.plugin, player);
		ItemStack item = new ItemStack(Material.BOOK_AND_QUILL);
		BookMeta meta = (BookMeta) item.getItemMeta();
		meta.setAuthor(player.getName());
		meta.setTitle("BookManager");
		item.setItemMeta(meta);
		user.giveItem(item);

		this.listener = new Listener() {
			@EventHandler
			public void bookEdit(PlayerEditBookEvent event) {
				Player player = event.getPlayer();
				if (event.getNewBookMeta().getAuthor().equals(player.getName())
						&& event.getNewBookMeta().getTitle()
								.equals("BookManager")) {
					String input = "";
					for (String str : event.getNewBookMeta().getPages()) {
						input += str;
					}
					listener.onBookSign(player, input);
					player.getInventory().getItem(event.getSlot())
							.setType(Material.AIR);
				}
			}

		};
	}
}