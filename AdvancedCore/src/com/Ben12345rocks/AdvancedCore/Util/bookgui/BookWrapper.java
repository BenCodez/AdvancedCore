package com.Ben12345rocks.AdvancedCore.Util.bookgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.BaseComponent;
import xyz.upperlevel.spigot.book.BookUtil;

public class BookWrapper {
	@Getter
	@Setter
	private String title;
	@Getter
	private ItemStack book;
	@Getter
	private BookUtil.PageBuilder currentPage;
	@Getter
	@Setter
	private int currentPageLines = 0;
	@Getter
	private ArrayList<BaseComponent[]> builder = new ArrayList<BaseComponent[]>();
	@Getter
	private HashMap<String, String> placeholders = new HashMap<String, String>();

	public BookWrapper addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public BookWrapper(String title) {
		this.title = title;
		currentPage = new BookUtil.PageBuilder();
	}

	public BookWrapper addLayout(Layout layout) {
		addToCurrentPage(layout.getLayout(getPlaceholders()));
		return this;
	}

	public BookWrapper nextPage(int newSize) {
		builder.add(currentPage.build());
		currentPage = new BookUtil.PageBuilder();
		currentPageLines = newSize;
		return this;
	}

	public BookWrapper addToCurrentPage(BaseComponent... baseComponents) {
		currentPageLines += baseComponents.length;
		if (currentPageLines > 14) {
			nextPage(baseComponents.length);
		}
		for (BaseComponent comp : baseComponents) {
			currentPage.add(comp);
			currentPage.newLine();
		}
		return this;
	}

	private String colorize(String s) {
		return StringParser.getInstance().colorize(s);
	}

	public void open(Player player) {
		builder.add(currentPage.build());
		book = BookUtil.writtenBook().author(player.getName()).title(colorize(title)).pages(builder).build();
		Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				BookUtil.openPlayer(player, book);
			}
		});
	}
}
