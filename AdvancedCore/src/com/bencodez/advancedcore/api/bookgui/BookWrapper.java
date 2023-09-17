package com.bencodez.advancedcore.api.bookgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.BaseComponent;
import xyz.upperlevel.spigot.book.BookUtil;

public class BookWrapper {
	@Getter
	private ItemStack book;
	@Getter
	private ArrayList<BaseComponent[]> builder = new ArrayList<BaseComponent[]>();
	@Getter
	private BookUtil.PageBuilder currentPage;
	@Getter
	@Setter
	private int currentPageLines = 0;
	@Getter
	private HashMap<String, String> placeholders = new HashMap<String, String>();
	@Getter
	@Setter
	private String title;

	public BookWrapper(String title) {
		this.title = title;
		currentPage = new BookUtil.PageBuilder();
	}

	public BookWrapper addLayout(Layout layout) {
		addToCurrentPage(layout.getLayout(getPlaceholders()));
		return this;
	}

	public BookWrapper addLine() {
		currentPageLines += 1;
		if (currentPageLines > 14) {
			nextPage(0);
		}
		currentPage.newLine();
		return this;
	}

	public BookWrapper addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
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

	public String colorize(String s) {
		return StringParser.getInstance().colorize(s);
	}

	public BookWrapper nextPage(int newSize) {
		builder.add(currentPage.build());
		currentPage = new BookUtil.PageBuilder();
		currentPageLines = newSize;
		return this;
	}

	public void open(Player player) {
		builder.add(currentPage.build());
		book = BookUtil.writtenBook().author(player.getName()).title(colorize(title)).pages(builder).build();
		AdvancedCorePlugin.getInstance().getBukkitScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				BookUtil.openPlayer(player, book);
			}
		}, player);
	}
}
