package com.Ben12345rocks.AdvancedCore.gui;

import java.util.HashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;

import lombok.Getter;

public abstract class GUIHandler {
	public abstract void onChat(CommandSender player);

	public abstract void onBook(Player player);

	public abstract void onChest(Player player);

	@Getter
	private HashMap<String, Object> data = new HashMap<String, Object>();

	public void setData(String str, Object value) {
		data.put(str, value);
	}

	public String colorize(String str) {
		return StringParser.getInstance().colorize(str);
	}

	public void sendMessage(CommandSender sender, String... message) {
		sender.sendMessage(message);
	}

	@Getter
	private CommandSender player;

	public GUIHandler(CommandSender player) {
		this.player = player;
	}

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
}
