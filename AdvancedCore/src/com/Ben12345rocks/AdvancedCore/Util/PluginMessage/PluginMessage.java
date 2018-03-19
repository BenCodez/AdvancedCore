package com.Ben12345rocks.AdvancedCore.Util.PluginMessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

public class PluginMessage implements PluginMessageListener {

	private static PluginMessage instance = new PluginMessage();

	public static PluginMessage getInstance() {
		return instance;
	}

	public ArrayList<PluginMessageHandler> pluginMessages = new ArrayList<PluginMessageHandler>();

	public PluginMessage() {
	}

	public void add(PluginMessageHandler handle) {
		pluginMessages.add(handle);
	}

	public ArrayList<PluginMessageHandler> getPluginMessages() {
		return pluginMessages;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		// plugin.getLogger().info("Got plugin message " + channel + " : " + message);
		if (!channel.equals(AdvancedCoreHook.getInstance().getPlugin().getName())) {
			return;
		}
		ByteArrayDataInput in = ByteStreams.newDataInput(message);
		ArrayList<String> list = new ArrayList<String>();
		String subChannel = in.readUTF();
		int size = in.readInt();
		for (int i = 0; i < size; i++) {
			try {
				String str = in.readUTF();
				if (str != null) {
					list.add(str);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for (PluginMessageHandler handle : pluginMessages) {
			if (handle.getSubChannel().equalsIgnoreCase(subChannel)) {
				handle.onRecieve(subChannel, list);
			}
		}

	}

	public void sendPluginMessage(Player p, String channel, String... messageData) {
		if (p == null) {
			AdvancedCoreHook.getInstance().debug("Can't send plugin message, player == null");
			return;
		}
		ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(byteOutStream);
		try {
			out.writeUTF(channel);
			out.writeInt(messageData.length);
			for (String message : messageData) {
				out.writeUTF(message);
			}
			p.sendPluginMessage(AdvancedCoreHook.getInstance().getPlugin(),
					AdvancedCoreHook.getInstance().getPlugin().getName(), byteOutStream.toByteArray());
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
