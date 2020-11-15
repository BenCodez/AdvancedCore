
package com.bencodez.advancedcore.api.gui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.User;
import com.bencodez.advancedcore.api.user.UserManager;

import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;

public abstract class GUIHandler {
	@Getter
	private HashMap<String, Object> data = new HashMap<String, Object>();

	@Getter
	private CommandSender player;

	public GUIHandler(CommandSender player) {
		this.player = player;
	}

	public String colorize(String str) {
		return StringParser.getInstance().colorize(str);
	}

	public abstract ArrayList<String> getChat(CommandSender sender);

	public abstract void onBook(Player player);

	public abstract void onChat(CommandSender player);

	public abstract void onChest(Player player);

	public abstract void open();

	public void open(GUIMethod method) {
		if (player instanceof Player) {
			switch (method) {
			case BOOK:
				onBook((Player) player);
				return;
			case CHAT:
				onChat(player);
				return;
			case CHEST:
				onChest((Player) player);
				return;
			default:
				break;

			}
		} else {
			onChat(player);
		}
	}

	public void sendMessage(ArrayList<String> message) {
		player.sendMessage(ArrayUtils.getInstance().convert(message));
	}

	public void sendMessage(String... message) {
		player.sendMessage(message);
	}

	public void sendMessageJson(ArrayList<TextComponent> text) {
		if (player instanceof Player) {
			User user = UserManager.getInstance().getUser((Player) player);
			user.sendJson(text);
		} else {
			player.sendMessage(ArrayUtils.getInstance().convert(ArrayUtils.getInstance().comptoString(text)));
		}
	}

	public void setData(String str, Object value) {
		data.put(str, value);
	}
}
