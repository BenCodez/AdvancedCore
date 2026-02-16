package com.bencodez.advancedcore.api.bookgui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Represents a layout for book GUIs.
 */
public class Layout {
	private BaseComponent compToAdd;

	private HashMap<String, String> placeholders = new HashMap<>();
	/**
	 * Gets the string layout.
	 * 
	 * @return the string layout
	 */
	@Getter
	private ArrayList<String> stringLayout;
	private String text;

	/**
	 * Creates a new layout.
	 * 
	 * @param layout the string layout
	 */
	public Layout(ArrayList<String> layout) {
		this.stringLayout = layout;
	}

	/**
	 * Adds a placeholder replacement.
	 * 
	 * @param toReplace the text to replace
	 * @param replaceWith the replacement text
	 * @return this layout for chaining
	 */
	public Layout addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	private String colorize(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	/**
	 * Gets the layout with placeholders replaced.
	 * 
	 * @param placeholders the placeholders to replace
	 * @return the layout components
	 */
	public BaseComponent[] getLayout(HashMap<String, String> placeholders) {
		stringLayout = PlaceholderUtils.replacePlaceHolder(stringLayout, placeholders);
		stringLayout = PlaceholderUtils.replacePlaceHolder(stringLayout, this.placeholders);
		ArrayList<BaseComponent> layout = new ArrayList<>();
		for (int i = 0; i < stringLayout.size(); i++) {
			String str = stringLayout.get(i);

			if (text != null && !text.equals("") && MessageAPI.containsIgnorecase(str, text)) {
				String[] split = str.split(Pattern.quote(text));

				String first = "";
				String last = "";
				if (split.length > 0) {
					first = split[0];
					if (split.length > 1) {
						last = split[1];
					}
				}
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

	/**
	 * Replaces text with a component.
	 * 
	 * @param text the text to replace
	 * @param compToAdd the component to add
	 * @return this layout for chaining
	 */
	public Layout replaceTextComponent(String text, BaseComponent compToAdd) {
		this.text = text;
		this.compToAdd = compToAdd;
		return this;
	}
}
