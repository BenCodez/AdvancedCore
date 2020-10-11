package com.Ben12345rocks.AdvancedCore.gui;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import lombok.Getter;

public abstract class GUIHandler {
	public abstract void onChat(CommandSender player);

	public abstract void onBook(Player player);

	public abstract void onChest(Player player);

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
