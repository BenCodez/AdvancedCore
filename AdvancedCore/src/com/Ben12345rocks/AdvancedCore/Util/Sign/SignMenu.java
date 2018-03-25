package com.Ben12345rocks.AdvancedCore.Util.Sign;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;

/*
*
*   Sign Menu v1.1
*
*   Developed by: FrostedSnowman
*   Inspired by: nisovin (https://bukkit.org/threads/sign-gui-use-the-sign-interface-to-get-user-input.177030/)
*
*
* FIXED: Sign block showing up next to the client
*
* */

public class SignMenu {

	public interface InputReceiver {

		void receive(Player player, String[] text);
	}

	private final Plugin plugin;

	private final Map<UUID, InputReceiver> inputReceivers;

	public SignMenu(Plugin plugin) {
		this.plugin = plugin;
		this.inputReceivers = new ConcurrentHashMap<>();
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null
				&& !NMSManager.getInstance().isVersion("1.8", "1.9", "1.10", "1.11")) {
			this.listen();
		}
	}

	private InputReceiver display(UUID uuid, InputReceiver inputReceiver, String... text) {
		Player player = Bukkit.getPlayer(uuid);
		Location location = player.getLocation();
		BlockPosition blockPosition = new BlockPosition(location.getBlockX(), 0, location.getBlockZ());

		PacketContainer fakeSign = ProtocolLibrary.getProtocolManager()
				.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
		PacketContainer openSign = ProtocolLibrary.getProtocolManager()
				.createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
		PacketContainer signData = ProtocolLibrary.getProtocolManager()
				.createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);

		fakeSign.getBlockPositionModifier().write(0, blockPosition);
		fakeSign.getBlockData().write(0, WrappedBlockData.createData(Material.SIGN_POST));

		openSign.getBlockPositionModifier().write(0, blockPosition);

		NbtCompound signNBT = (NbtCompound) signData.getNbtModifier().read(0);
		IntStream.range(0, text.length).forEach(
				v -> signNBT.put("Text" + (v + 1), "{\"extra\":[{\"text\":\"" + text[v] + "\"}],\"text\":\"\"}"));

		signData.getBlockPositionModifier().write(0, blockPosition);
		signData.getIntegers().write(0, 9);
		signData.getNbtModifier().write(0, signNBT);

		try {
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, fakeSign);
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, openSign);
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, signData);
		} catch (InvocationTargetException exception) {
			exception.printStackTrace();
		}
		return inputReceiver;
	}

	private void listen() {
		if (!NMSManager.getInstance().getVersion().contains("1.8")
				&& !NMSManager.getInstance().getVersion().contains("1.9")
				&& !NMSManager.getInstance().getVersion().contains("1.10")
				&& !NMSManager.getInstance().getVersion().contains("1.11")) {
			ProtocolLibrary.getProtocolManager()
					.addPacketListener(new PacketAdapter(this.plugin, PacketType.Play.Client.UPDATE_SIGN) {
						@Override
						public void onPacketReceiving(PacketEvent event) {
							try {
								PacketContainer packet = event.getPacket();
								Player player = event.getPlayer();
								String[] text = packet.getStringArrays().read(0);
								if (!inputReceivers.containsKey(player.getUniqueId())) {
									return;
								}
								event.setCancelled(true);
								inputReceivers.remove(player.getUniqueId()).receive(player, text);
							} catch (Exception e) {
								
							}
						}
					});
		}

	}

	public void open(Player player, List<String> text, InputReceiver inputReceiver) {
		open(player.getUniqueId(), text, inputReceiver);
	}

	public void open(Player player, String[] text, InputReceiver inputReceiver) {
		open(player.getUniqueId(), text, inputReceiver);
	}

	public void open(UUID uuid, List<String> text, InputReceiver inputReceiver) {
		open(uuid, text.toArray(new String[text.size()]), inputReceiver);
	}

	public void open(UUID uuid, String[] text, InputReceiver inputReceiver) {
		this.inputReceivers.putIfAbsent(uuid, this.display(uuid, inputReceiver,
				Arrays.stream(Arrays.copyOf(text, 4))
						.map(s -> ChatColor.translateAlternateColorCodes('&', Optional.ofNullable(s).orElse("")))
						.toArray(String[]::new)));
	}
}