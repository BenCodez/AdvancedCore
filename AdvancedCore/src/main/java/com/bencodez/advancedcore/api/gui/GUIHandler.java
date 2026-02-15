
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

/**
 * Abstract handler for GUI operations supporting multiple display methods.
 */
public abstract class GUIHandler {
	/**
	 * @return the data map for storing GUI-related information
	 */
	@Getter
	private HashMap<String, Object> data = new HashMap<>();

	/**
	 * @return the command sender viewing the GUI
	 */
	@Getter
	private CommandSender player;

	/**
	 * @return the plugin instance
	 */
	@Getter
	private AdvancedCorePlugin plugin;

	/**
	 * Constructs a new GUIHandler.
	 * 
	 * @param plugin the plugin instance
	 * @param player the command sender
	 */
	public GUIHandler(AdvancedCorePlugin plugin, CommandSender player) {
		this.plugin = plugin;
		this.player = player;
	}

	/**
	 * Colorizes the given string.
	 * 
	 * @param str the string to colorize
	 * @return the colorized string
	 */
	public String colorize(String str) {
		return MessageAPI.colorize(str);
	}

	/**
	 * Gets the chat representation of the GUI.
	 * 
	 * @param sender the command sender
	 * @return the list of chat messages
	 */
	public abstract ArrayList<String> getChat(CommandSender sender);

	/**
	 * Opens the GUI as a book for the player.
	 * 
	 * @param player the player
	 */
	public abstract void onBook(Player player);

	/**
	 * Opens the GUI as chat messages for the command sender.
	 * 
	 * @param player the command sender
	 */
	public abstract void onChat(CommandSender player);

	/**
	 * Opens the GUI as a chest inventory for the player.
	 * 
	 * @param player the player
	 */
	public abstract void onChest(Player player);

	/**
	 * Opens the GUI using the default method.
	 */
	public abstract void open();

	/**
	 * Opens the GUI using the specified method.
	 * 
	 * @param method the GUI method to use
	 */
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

	/**
	 * Sends messages to the command sender.
	 * 
	 * @param message the list of messages to send
	 */
	public void sendMessage(ArrayList<String> message) {
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendMessage(message);
		} else {
			player.sendMessage(ArrayUtils.convert(message));
		}
	}

	/**
	 * Sends messages to the command sender.
	 * 
	 * @param message the messages to send
	 */
	public void sendMessage(String... message) {
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendMessage(message);
		} else {
			player.sendMessage(message);
		}
	}

	/**
	 * Sends JSON text components to the command sender.
	 * 
	 * @param text the text components to send
	 */
	public void sendMessageJson(ArrayList<TextComponent> text) {
		if (player instanceof Player) {
			AdvancedCoreUser user = plugin.getUserManager().getUser((Player) player);
			user.sendJson(text);
		} else {
			player.sendMessage(ArrayUtils.convert(ArrayUtils.comptoString(text)));
		}
	}

	/**
	 * Sets data in the data map.
	 * 
	 * @param str the key
	 * @param value the value to set
	 */
	public void setData(String str, Object value) {
		data.put(str, value);
	}
}
