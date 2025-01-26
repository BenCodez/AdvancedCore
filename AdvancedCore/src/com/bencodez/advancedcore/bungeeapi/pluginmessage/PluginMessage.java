package com.bencodez.advancedcore.bungeeapi.pluginmessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import lombok.Getter;
import lombok.Setter;

public class PluginMessage implements PluginMessageListener {

	@Getter
	@Setter
	private boolean debug = false;

	@Getter
	@Setter
	private EncryptionHandler encryptionHandler;

	private AdvancedCorePlugin plugin;

	private ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

	public ArrayList<PluginMessageHandler> pluginMessages = new ArrayList<>();

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
		if (!channel.equals(plugin.getBungeeChannel())) {
			return;
		}

		ByteArrayDataInput in = ByteStreams.newDataInput(message);
		String data = "";
		String subChannel1 = "";
		if (encryptionHandler != null) {
			try {
				subChannel1 = encryptionHandler.decrypt(in.readUTF());
			} catch (Exception e) {
				plugin.debug(e);
				plugin.getLogger().warning("Error reading plugin message: " + e.getMessage());
				return;
			}
		} else {
			subChannel1 = in.readUTF();
		}
		final String subChannel = subChannel1;
		int size = in.readInt();

		// Ensure the size is within a reasonable range to prevent reading too much data
		if (size < 0 || size > message.length) {
			plugin.getLogger().warning("Invalid message size: " + size);
			return;
		}

		try {

			if (encryptionHandler != null) {
				data = encryptionHandler.decrypt(in.readUTF());
			} else {
				data = in.readUTF();
			}

			String[] list = data.split("/a/");
			ArrayList<String> list1 = new ArrayList<String>();
			for (String s : list) {
				list1.add(s);
			}

			timer.submit(new Runnable() {
				@Override
				public void run() {
					onReceive(subChannel, list1);
				}
			});
		} catch (Exception e) {
			plugin.debug(e);
			plugin.getLogger().warning("Error reading plugin message: " + e.getMessage());
		}
	}

	public void onReceive(String subChannel, ArrayList<String> list) {
		if (debug) {
			plugin.getLogger().info(
					"BungeeDebug: Received plugin message: " + subChannel + ", " + ArrayUtils.makeStringList(list));
		}
		for (PluginMessageHandler handle : pluginMessages) {
			if (handle.getSubChannel().equalsIgnoreCase(subChannel)) {
				handle.onRecieve(subChannel, list);
			}
		}

	}

	public void sendPluginMessage(String channel, String... messageData) {
		ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(byteOutStream);
		try {
			if (encryptionHandler != null) {
				out.writeUTF(encryptionHandler.encrypt(channel));
			} else {
				out.writeUTF(channel);
			}
			out.writeInt(messageData.length);
			String data = "";

			for (String message : messageData) {
				data += message + "/a/";

			}
			if (encryptionHandler != null) {
				out.writeUTF(encryptionHandler.encrypt(data));
			} else {
				out.writeUTF(data);
			}
			if (debug) {
				plugin.getLogger().info("BungeeDebug: Sending plugin message: " + channel + ", "
						+ ArrayUtils.makeStringList(ArrayUtils.convert(messageData)));
			}
			Bukkit.getServer().sendPluginMessage(plugin, plugin.getBungeeChannel(), byteOutStream.toByteArray());

			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
