package com.bencodez.advancedcore.bungeeapi.pluginmessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

public class PluginMessage implements PluginMessageListener {

	private AdvancedCorePlugin plugin;

	public ArrayList<PluginMessageHandler> pluginMessages = new ArrayList<PluginMessageHandler>();

	public PluginMessage(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
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
		if (!channel.equals(plugin.getBungeeChannel())) {
			return;
		}
		ByteArrayDataInput in = ByteStreams.newDataInput(message);
		ArrayList<String> list = new ArrayList<String>();
		final String subChannel = in.readUTF();
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

		final ArrayList<String> list1 = list;

		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				for (PluginMessageHandler handle : pluginMessages) {
					if (handle.getSubChannel().equalsIgnoreCase(subChannel)) {
						handle.onRecieve(subChannel, list1);
					}
				}
			}
		});

	}

	public void sendPluginMessage(Player p, String channel, String... messageData) {
		if (p == null) {
			plugin.debug("Can't send plugin message, player == null, " + channel + " data: "
					+ ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(messageData)));
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
			p.sendPluginMessage(plugin, plugin.getBungeeChannel(),
					byteOutStream.toByteArray());
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendPluginMessage(String channel, String... messageData) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			sendPluginMessage(p, channel, messageData);
			return;
		}

		plugin.debug("Can't send plugin message, player == null, " + channel + " data: "
				+ ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(messageData)));
	}

}
