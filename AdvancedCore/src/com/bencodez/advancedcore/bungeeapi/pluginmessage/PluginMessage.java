package com.bencodez.advancedcore.bungeeapi.pluginmessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.thread.Thread;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import lombok.Getter;
import lombok.Setter;

public class PluginMessage implements PluginMessageListener {

	@Getter
	@Setter
	private boolean debug = false;

	private AdvancedCorePlugin plugin;

	private Timer timer = new Timer();

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

		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				onReceive(subChannel, list1);

			}
		}, 0);

	}

	public void onReceive(String subChannel, ArrayList<String> list) {
		if (debug) {
			plugin.getLogger().info("BungeeDebug: Received plugin message: " + subChannel + ", "
					+ ArrayUtils.getInstance().makeStringList(list));
		}
		Thread.getInstance().run(new Runnable() {

			@Override
			public void run() {
				for (PluginMessageHandler handle : pluginMessages) {
					if (handle.getSubChannel().equalsIgnoreCase(subChannel)) {
						handle.onRecieve(subChannel, list);
					}
				}
			}
		});

	}

	public void sendPluginMessage(String channel, String... messageData) {
		ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(byteOutStream);
		try {
			out.writeUTF(channel);
			out.writeInt(messageData.length);
			for (String message : messageData) {
				out.writeUTF(message);
			}
			if (debug) {
				plugin.getLogger().info("BungeeDebug: Sending plugin message: " + channel + ", "
						+ ArrayUtils.getInstance().makeStringList(ArrayUtils.getInstance().convert(messageData)));
			}
			Bukkit.getServer().sendPluginMessage(plugin, plugin.getBungeeChannel(), byteOutStream.toByteArray());

			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
