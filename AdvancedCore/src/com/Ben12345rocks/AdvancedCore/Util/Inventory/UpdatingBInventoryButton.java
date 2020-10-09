package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

import lombok.Getter;

public abstract class UpdatingBInventoryButton extends BInventoryButton {
	@Getter
	private long updateInterval;
	@Getter
	private long delay;
	private Timer timer = new Timer();

	public UpdatingBInventoryButton(ItemBuilder item, long delay, long updateInterval) {
		super(item);
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public UpdatingBInventoryButton(ItemStack item, long delay, long updateInterval) {
		super(item);
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public UpdatingBInventoryButton(String name, String[] lore, ItemStack item, long delay, long updateInterval) {
		super(name, lore, item);
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public void cancel() {
		timer.cancel();
		timer = null;
	}

	private void checkUpdate(Player p) {
		if (getInv().isOpen(p)) {
			final ItemStack item = onUpdate(p).toItemStack(p);
			if (item != null) {
				Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

					@Override
					public void run() {
						p.getOpenInventory().getTopInventory().setItem(getSlot(), item);
					}
				});
			} else {
				cancel();
			}
		} else {
			cancel();
		}
	}

	public void loadTimer(Player p) {
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				checkUpdate(p);
			}
		}, delay, updateInterval);
	}

	public abstract ItemBuilder onUpdate(Player player);

}
