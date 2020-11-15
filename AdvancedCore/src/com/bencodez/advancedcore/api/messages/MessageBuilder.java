package com.bencodez.advancedcore.api.messages;

import java.util.HashMap;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.user.User;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.TextComponent;

public class MessageBuilder {
	@Getter
	@Setter
	private String text;

	public MessageBuilder(String text) {
		this.text = text;
	}

	public MessageBuilder colorize() {
		setText(StringParser.getInstance().colorize(getText()));
		return this;
	}

	public MessageBuilder parseText() {
		replaceJavascript();
		colorize();
		return this;
	}

	public MessageBuilder parseText(HashMap<String, String> placeholders) {
		if (placeholders != null) {
			replacePlaceholder(placeholders);
		}
		replaceJavascript();
		colorize();
		return this;
	}

	public MessageBuilder parseText(OfflinePlayer player) {
		parseText(player, null);
		return this;
	}

	public MessageBuilder parseText(OfflinePlayer player, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			replacePlaceholder(placeholders);
		}
		replaceJavascript(player);
		colorize();
		return this;
	}

	public MessageBuilder parseText(Player player) {
		parseText(player, null);
		return this;
	}

	public MessageBuilder parseText(Player player, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			replacePlaceholder(placeholders);
		}
		replaceJavascript((OfflinePlayer) player);
		replacePlaceHolders(player);
		colorize();
		return this;
	}

	public MessageBuilder replaceJavascript() {
		setText(StringParser.getInstance().replaceJavascript(getText()));
		return this;
	}

	public MessageBuilder replaceJavascript(CommandSender sender) {
		setText(StringParser.getInstance().replaceJavascript(sender, getText()));
		return this;
	}

	public MessageBuilder replaceJavascript(JavascriptEngine engine) {
		setText(StringParser.getInstance().replaceJavascript(getText(), engine));
		return this;
	}

	public MessageBuilder replaceJavascript(OfflinePlayer player) {
		setText(StringParser.getInstance().replaceJavascript(player, getText()));
		return this;
	}

	public MessageBuilder replaceJavascript(User user) {
		setText(StringParser.getInstance().replaceJavascript(user, getText()));
		return this;
	}

	public MessageBuilder replacePlaceholder(HashMap<String, String> placeholders) {
		setText(StringParser.getInstance().replacePlaceHolder(text, placeholders));
		return this;
	}

	public MessageBuilder replacePlaceholder(HashMap<String, String> placeholders, boolean ignoreCase) {
		setText(StringParser.getInstance().replacePlaceHolder(text, placeholders, ignoreCase));
		return this;
	}

	public MessageBuilder replacePlaceholder(String toReplace, String replaceWith) {
		setText(StringParser.getInstance().replacePlaceHolder(text, toReplace, replaceWith));
		return this;
	}

	public MessageBuilder replacePlaceholder(String toReplace, String replaceWith, boolean ignoreCase) {
		setText(StringParser.getInstance().replacePlaceHolder(text, toReplace, replaceWith, ignoreCase));
		return this;
	}

	/**
	 * Replace placeholderapi placeholders
	 *
	 * @param player the player
	 * @return the string
	 */
	public MessageBuilder replacePlaceHolders(Player player) {
		setText(StringParser.getInstance().replacePlaceHolders(player, getText()));
		return this;
	}

	@Override
	public String toString() {
		return getText();
	}

	public TextComponent toTextComponent() {
		return StringParser.getInstance().parseJson(getText());
	}
}
