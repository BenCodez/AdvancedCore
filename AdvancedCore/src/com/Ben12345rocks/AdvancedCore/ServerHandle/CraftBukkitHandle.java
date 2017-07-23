package com.Ben12345rocks.AdvancedCore.ServerHandle;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;
import net.pl3x.bukkit.chatapi.ComponentSender;

public class CraftBukkitHandle implements IServerHandle {

	@Override
	public void sendMessage(Player player, BaseComponent component) {
		try {
			ComponentSender.sendMessage(player, component);
		} catch (Exception e) {
			player.sendMessage(component.toPlainText());
		}

	}

	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		try {
			ComponentSender.sendMessage(player, components);
		} catch (Exception e) {
			for (BaseComponent comp : components) {
				sendMessage(player, comp);
			}
		}
	}
}
