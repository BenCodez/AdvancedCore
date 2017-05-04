package com.Ben12345rocks.AdvancedCore.ServerHandle;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;
import net.pl3x.bukkit.chatapi.ComponentSender;

public class CraftBukkitHandle implements IServerHandle {

	@Override
	public void sendMessage(Player player, BaseComponent component) {
		ComponentSender.sendMessage(player, component);

	}

	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		ComponentSender.sendMessage(player, components);
	}
}
