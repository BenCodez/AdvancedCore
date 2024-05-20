
package com.bencodez.advancedcore.api.gui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;

public abstract class GUIHandler {
	@Getter
	private HashMap<String, Object> data = new HashMap<String, Object>();

	@Getter
	private CommandSender player;

	@Getter
	private AdvancedCorePlugin plugin;

	public GUIHandler(AdvancedCorePlugin plugin, CommandSender player) {
		this.plugin = plugin;
		this.player = player;
	}

	public String colorize(String str) {
		return MessageAPI.colorize(str);
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
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendMessage(message);
		} else {
			player.sendMessage(ArrayUtils.convert(message));
		}
	}

	public void sendMessage(String... message) {
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendMessage(message);
		} else {
			player.sendMessage(message);
		}
	}

	public void sendMessageJson(ArrayList<TextComponent> text) {
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendJson(text);
		} else {
			player.sendMessage(ArrayUtils.convert(ArrayUtils.comptoString(text)));
		}
	}

	public void setData(String str, Object value) {
		data.put(str, value);
	}
}
