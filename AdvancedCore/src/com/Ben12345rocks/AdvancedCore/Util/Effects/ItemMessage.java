package com.Ben12345rocks.AdvancedCore.Util.Effects;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.PriorityQueue;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

/**
 * Minecraft displays the item tooltip to the player briefly when it's changed.
 * Let's (ab)use that to send a "popup" message to the player via one of these
 * tooltips. This is nice for temporary messages where you don't want to clutter
 * up the chat window.
 */
public class ItemMessage {
	public class MessageRecord implements Comparable<Object> {
		private final String message;
		private final int duration;
		private final int priority;
		private final long id;

		public MessageRecord(String message, int duration, int priority, long id) {
			this.message = message;
			this.duration = duration;
			this.priority = priority;
			this.id = id;
		}

		@Override
		public int compareTo(Object other) {
			MessageRecord rec = importOtherMessageRecord(other);
			if (rec != null) {
				if (this.priority == rec.getPriority()) {
					return (Long.valueOf(this.id)).compareTo(rec.getId());
				} else {
					return (Integer.valueOf(this.priority)).compareTo(rec.getPriority());
				}
			} else {
				return 0;
			}
		}

		public int getDuration() {
			return duration;
		}

		public long getId() {
			return id;
		}

		public String getMessage() {
			return message;
		}

		public int getPriority() {
			return priority;
		}
	}

	private class NamerTask extends BukkitRunnable implements Listener {
		private final WeakReference<Player> playerRef;
		private final String message;
		private int slot;
		private int iterations;

		public NamerTask(Player player, MessageRecord rec) {
			this.playerRef = new WeakReference<Player>(player);
			this.iterations = Math.max(1, (rec.getDuration() * 20) / interval);
			this.slot = player.getInventory().getHeldItemSlot();
			this.message = rec.getMessage();
			Bukkit.getPluginManager().registerEvents(this, plugin);
		}

		private void cleanup() {
			cancel();
			HandlerList.unregisterAll(this);
		}

		private void finish(Player player) {
			sendItemSlotChange(player, slot, player.getInventory().getItem(slot));
			notifyDone(player);
			cleanup();
		}

		private ItemStack makeStack(Player player) {
			ItemStack stack0 = player.getInventory().getItem(slot);
			ItemStack stack;
			if (stack0 == null || stack0.getType() == Material.AIR) {
				// an empty slot can't display any custom item name, so we need to fake an item
				// a snow layer is a good choice, since it's visually quite unobtrusive
				stack = new ItemStack(emptyHandReplacement, 1);
			} else {
				stack = new ItemStack(stack0);
			}
			ItemMeta meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
			// fool the client into thinking the item name has changed, so it actually
			// (re)displays it
			meta.setDisplayName(String.format(formats[iterations % formats.length], message));
			stack.setItemMeta(meta);
			return stack;
		}

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onItemHeldChange(PlayerItemHeldEvent event) {
			Player player = event.getPlayer();
			if (player.equals(playerRef.get())) {
				sendItemSlotChange(player, event.getPreviousSlot(),
						player.getInventory().getItem(event.getPreviousSlot()));
				slot = event.getNewSlot();
				refresh(event.getPlayer());
			}
		}

		@EventHandler
		public void onPluginDisable(PluginDisableEvent event) {
			Player player = playerRef.get();
			if (event.getPlugin() == plugin && player != null) {
				getMessageQueue(player).clear();
				finish(playerRef.get());
			}
		}

		private void refresh(Player player) {
			sendItemSlotChange(player, slot, makeStack(player));
		}

		@Override
		public void run() {
			Player player = playerRef.get();
			if (player != null) {
				if (iterations-- <= 0) {
					// finished - restore the previous item data and tidy up
					finish(player);
				} else {
					// refresh the item data
					refresh(player);
				}
			} else {
				// player probably disconnected - whatever, we're done here
				cleanup();
			}
		}

		private void sendItemSlotChange(Player player, int slot, ItemStack stack) {
			PacketContainer setSlot = new PacketContainer(PacketType.Play.Server.SET_SLOT);
			// int field 0: window id (0 = player inventory)
			// int field 1: slot number (36 - 44 for player hotbar)
			setSlot.getIntegers().write(0, 0).write(1, slot + 36);
			setSlot.getItemModifier().write(0, stack);
			try {
				ProtocolLibrary.getProtocolManager().sendServerPacket(player, setSlot);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}

	private static final int DEFAULT_DURATION = 2; // seconds
	private static final int DEFAULT_PRIORITY = 0;
	private static final String DEF_FORMAT_1 = "%s";
	private static final String DEF_FORMAT_2 = " %s ";
	private static final String METADATA_Q_KEY = "item-message:msg-queue";
	private static final String METADATA_ID_KEY = "item-message:id";

	private int interval = 20; // ticks
	private final Plugin plugin;

	private String[] formats = new String[] { DEF_FORMAT_1, DEF_FORMAT_2 };

	private Material emptyHandReplacement = Material.SNOW;

	public ItemMessage(Plugin plugin) {
		this.plugin = plugin;
	}

	@SuppressWarnings("unchecked")
	private PriorityQueue<MessageRecord> getMessageQueue(Player player) {
		if (!player.hasMetadata(METADATA_Q_KEY)) {
			player.setMetadata(METADATA_Q_KEY, new FixedMetadataValue(plugin, new PriorityQueue<MessageRecord>()));
		}
		for (MetadataValue v : player.getMetadata(METADATA_Q_KEY)) {
			if (v.value() instanceof PriorityQueue<?>) {
				return (PriorityQueue<MessageRecord>) v.value();
			}
		}
		return null;
	}

	private long getNextId(Player player) {
		long id;
		if (player.hasMetadata(METADATA_ID_KEY)) {
			List<MetadataValue> l = player.getMetadata(METADATA_ID_KEY);
			id = l.size() >= 1 ? l.get(0).asLong() : 1L;
		} else {
			id = 1L;
		}
		player.setMetadata(METADATA_ID_KEY, new FixedMetadataValue(plugin, id + 1));
		return id;
	}

	/**
	 * Import a foreign MessageRecord object, if possible. Why is this necessary?
	 * There may be multiple plugins putting message records into a player's
	 * metadata, and objects from different plugins are likely to be (should be!) in
	 * different packages, and will not be castable to one another. So we use
	 * reflection to convert the foreign MessageRecord's data into our local object.
	 *
	 * @param other the foreign message record
	 * @return a MessageRecord with the imported data, or null if there was a
	 *         problem
	 */
	private MessageRecord importOtherMessageRecord(Object other) {
		if (other instanceof MessageRecord) {
			return (MessageRecord) other;
		} else if (other.getClass().getName().endsWith(".ItemMessage$MessageRecord")) {
			// looks like the same class as us - we make no assumptions about what package
			// it's in, though
			try {
				Method m1 = other.getClass().getMethod("getId");
				Method m2 = other.getClass().getMethod("getPriority");
				Method m3 = other.getClass().getMethod("getMessage");
				Method m4 = other.getClass().getMethod("getDuration");
				long otherId = (Long) m1.invoke(other);
				int otherPriority = (Integer) m2.invoke(other);
				String otherMessage = (String) m3.invoke(other);
				int otherDuration = (Integer) m4.invoke(other);
				return new MessageRecord(otherMessage, otherDuration, otherPriority, otherId);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}

	private void notifyDone(Player player) {
		PriorityQueue<MessageRecord> msgQueue = getMessageQueue(player);
		msgQueue.poll();
		if (!msgQueue.isEmpty()) {
			MessageRecord rec = importOtherMessageRecord(msgQueue.peek());
			new NamerTask(player, rec).runTaskTimer(plugin, 1L, interval);
		}
	}

	public void sendMessage(Player player, String message) {
		sendMessage(player, message, DEFAULT_DURATION, DEFAULT_PRIORITY);
	}

	public void sendMessage(Player player, String message, int duration) {
		sendMessage(player, message, duration, DEFAULT_PRIORITY);
	}

	public void sendMessage(Player player, String message, int duration, int priority) {

		PriorityQueue<MessageRecord> msgQueue = getMessageQueue(player);
		msgQueue.add(new MessageRecord(message, duration, priority, getNextId(player)));
		if (msgQueue.size() == 1) {
			// there was nothing in the queue previously - kick off a NamerTask
			// (if there was already something in the queue, a new NamerTask will be kicked
			// off
			// when the current task completes - see notifyDone())
			new NamerTask(player, msgQueue.peek()).runTaskTimer(plugin, 1L, interval);
		}

	}

	/**
	 * Set which item the player should held if he receives a message without having
	 * something in his hand. Default is a snow layer
	 *
	 * @param material Material
	 * @throws IllegalArgumentException if material is null
	 */
	public void setEmptyHandReplacement(Material material) {
		Validate.notNull(material, "There must be a replacement for an empty hand!");
		this.emptyHandReplacement = material;
	}

	/**
	 * Set the alternating format strings for message display. The strings must be
	 * different and must each contain one (and only one) occurrence of '%s'.
	 *
	 * @param formats the format strings
	 * @throws IllegalArgumentException if the strings are the same, or do not
	 *                                  contain a %s
	 */
	public void setFormats(String... formats) {
		Validate.isTrue(formats.length > 1, "Two formats are minimum!");
		for (String format : formats) {
			Validate.isTrue(format.contains("%s"), "format string \"" + format + "\" must contain a %s");
		}
		this.formats = formats;
	}

	/**
	 * set the interval the player will receive packets with the formatted message
	 * Default is 20 for every second
	 *
	 * @param interval in ticks
	 * @throws IllegalArgumentException if interval is below 1
	 */
	public void setInterval(int interval) {
		Validate.isTrue(interval > 0, "Interval can't be below 1!");
		this.interval = interval;
	}
}