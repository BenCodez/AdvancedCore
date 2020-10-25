package com.Ben12345rocks.AdvancedCore.Util.bookgui;

import java.util.ArrayList;
import java.util.HashMap;

import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

import lombok.Getter;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

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
				String first = split[0];
				String last = split[1];
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
