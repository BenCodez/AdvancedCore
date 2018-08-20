package com.Ben12345rocks.AdvancedCore.ServerHandle;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.NMSManager.ReflectionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.md_5.bungee.api.chat.BaseComponent;

public class CraftBukkitHandle implements IServerHandle {

	@Override
	public void sendMessage(Player player, BaseComponent component) {
		try {
			String jsonMessage = component.toLegacyText();
			ReflectionUtils reflectChat = new ReflectionUtils(null,
					NMSManager.getInstance().getNMSClass("ChatSerializer"));
			Object chatComponent = ReflectionUtils.invokeMethod(reflectChat.getMethodDeclared("a", String.class), null,
					jsonMessage);
			Object packetPlayOutChat = ReflectionUtils
					.constructObject(
							new ReflectionUtils(null, NMSManager.getInstance().getNMSClass("PacketPlayOutChat"))
									.getConstructor(NMSManager.getInstance().getNMSClass("IChatBaseComponent")),
							chatComponent);
			Field playerConnection = new ReflectionUtils(null, NMSManager.getInstance().getNMSClass("EntityPlayer"))
					.getFieldDeclared("playerConnection");
			Method sendPacket = new ReflectionUtils(null, NMSManager.getInstance().getNMSClass("PlayerConnection"))
					.getMethodDeclared("sendPacket", NMSManager.getInstance().getNMSClass("Packet"));

			Object handle = ReflectionUtils.invokeMethod(
					new ReflectionUtils(player, player.getClass()).getMethodDeclared("getHandle"), player);
			Object connection = ReflectionUtils.getFieldValue(playerConnection, handle);
			ReflectionUtils.invokeMethod(sendPacket, connection, packetPlayOutChat);
		} catch (Exception e) {
			player.sendMessage(component.toPlainText());
		}

	}

	@Override
	public void sendMessage(Player player, BaseComponent... components) {
		for (BaseComponent comp : components) {
			sendMessage(player, comp);
		}
	}
}
