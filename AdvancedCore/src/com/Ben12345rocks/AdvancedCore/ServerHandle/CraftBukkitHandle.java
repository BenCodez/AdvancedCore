package com.Ben12345rocks.AdvancedCore.ServerHandle;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;

public class CraftBukkitHandle implements IServerHandle {

	@Override
	public void sendMessage(Player player, BaseComponent component) {
		player.sendMessage(component.toPlainText());
	}

	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		for (BaseComponent comp : components) {
			sendMessage(player, comp);
		}
	}
}
