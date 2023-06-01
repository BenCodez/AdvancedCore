package com.bencodez.advancedcore.serverhandle;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;

public class SpigotHandle implements IServerHandle {

	@SuppressWarnings("deprecation")
	@Override
	public void sendMessage(Player player, BaseComponent component) {
		// ComponentSender.sendMessage(player, component);
		player.spigot().sendMessage(component);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		// ComponentSender.sendMessage(player, components);
		player.spigot().sendMessage(components);
	}

}
