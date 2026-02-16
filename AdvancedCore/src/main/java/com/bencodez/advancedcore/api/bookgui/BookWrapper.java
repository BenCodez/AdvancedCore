package com.bencodez.advancedcore.api.bookgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.BaseComponent;
import xyz.upperlevel.spigot.book.BookUtil;

/**
 * Wrapper for creating and managing book GUIs.
 */
public class BookWrapper {
	/**
	 * The book item stack.
	 * 
	 * @return the book item
	 */
	@Getter
	private ItemStack book;
	/**
	 * The list of pages.
	 * 
	 * @return the page builder list
	 */
	@Getter
	private ArrayList<BaseComponent[]> builder = new ArrayList<>();
	/**
	 * The current page being built.
	 * 
	 * @return the current page builder
	 */
	@Getter
	private BookUtil.PageBuilder currentPage;
	/**
	 * The current page line count.
	 * 
	 * @return the current page line count
	 * @param currentPageLines the current page line count to set
	 */
	@Getter
	@Setter
	private int currentPageLines = 0;
	/**
	 * Placeholders to be replaced in the book.
	 * 
	 * @return the placeholder map
	 */
	@Getter
	private HashMap<String, String> placeholders = new HashMap<>();
	/**
	 * The book title.
	 * 
	 * @return the book title
	 * @param title the book title to set
	 */
	@Getter
	@Setter
	private String title;

	/**
	 * Instantiates a new book wrapper.
	 * 
	 * @param title the book title
	 */
	public BookWrapper(String title) {
		this.title = title;
		currentPage = new BookUtil.PageBuilder();
	}

	/**
	 * Add a layout to the current page.
	 * 
	 * @param layout the layout to add
	 * @return this book wrapper
	 */
	public BookWrapper addLayout(Layout layout) {
		addToCurrentPage(layout.getLayout(getPlaceholders()));
		return this;
	}

	/**
	 * Add a line to the current page.
	 * 
	 * @return this book wrapper
	 */
	public BookWrapper addLine() {
		currentPageLines += 1;
		if (currentPageLines > 14) {
			nextPage(0);
		}
		currentPage.newLine();
		return this;
	}

	/**
	 * Add a placeholder to this book.
	 * 
	 * @param toReplace the string to replace
	 * @param replaceWith the replacement string
	 * @return this book wrapper
	 */
	public BookWrapper addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	/**
	 * Add components to the current page.
	 * 
	 * @param baseComponents the components to add
	 * @return this book wrapper
	 */
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

	/**
	 * Colorize a string.
	 * 
	 * @param s the string to colorize
	 * @return the colorized string
	 */
	public String colorize(String s) {
		return MessageAPI.colorize(s);
	}

	/**
	 * Move to the next page.
	 * 
	 * @param newSize the new page size
	 * @return this book wrapper
	 */
	public BookWrapper nextPage(int newSize) {
		builder.add(currentPage.build());
		currentPage = new BookUtil.PageBuilder();
		currentPageLines = newSize;
		return this;
	}

	/**
	 * Open the book for a player.
	 * 
	 * @param player the player
	 */
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
