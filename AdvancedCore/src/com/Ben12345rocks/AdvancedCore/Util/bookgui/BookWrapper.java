package com.Ben12345rocks.AdvancedCore.Util.bookgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
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

	public class Layout {
		@Getter
		private ArrayList<String> stringLayout;

		private String text;
		private BaseComponent compToAdd;
		private HashMap<String, String> placeholders = new HashMap<String, String>();

		public Layout(ArrayList<String> layout) {
			this.stringLayout = layout;
		}

		private String colorize(String s) {
			return StringParser.getInstance().colorize(s);
		}

		public Layout replaceTextComponent(String text, BaseComponent compToAdd) {
			this.text = text;
			this.compToAdd = compToAdd;
			return this;
		}

		public Layout addPlaceholder(String toReplace, String replaceWith) {
			placeholders.put(toReplace, replaceWith);
			return this;
		}

		public BaseComponent[] getLayout(HashMap<String, String> placeholders) {
			stringLayout = ArrayUtils.getInstance().replacePlaceHolder(stringLayout, placeholders);
			stringLayout = ArrayUtils.getInstance().replacePlaceHolder(stringLayout, this.placeholders);
			ArrayList<BaseComponent> layout = new ArrayList<BaseComponent>();
			for (int i = 0; i < stringLayout.size(); i++) {
				String str = stringLayout.get(i);

				if (StringParser.getInstance().containsIgnorecase(str, text)) {
					String[] split = str.split(text);
					String first = split[0].substring(0, split[0].length());
					String last = str.substring(first.length() + text.length());
					BaseComponent comp = new TextComponent(colorize(first));
					comp.addExtra(compToAdd);
					comp.addExtra(colorize(last));
					layout.add(comp);
				} else {
					layout.add(new TextComponent(colorize(str)));
				}
			}
			BaseComponent[] comps = new BaseComponent[layout.size()];
			for (int i = 0; i < layout.size(); i++) {
				comps[i] = layout.get(i);
			}
			return comps;
		}
	}
}
