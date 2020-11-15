package com.bencodez.advancedcore.serverhandle;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;

public class CraftBukkitHandle implements IServerHandle {

	@Override
	public void sendMessage(Player player, BaseComponent component) {
		player.sendMessage(component.toLegacyText());
	}

	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		for (BaseComponent comp : components) {
			sendMessage(player, comp);
		}
	}
}
