package com.bencodez.advancedcore.api.inventory;

import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import lombok.Getter;

public abstract class UpdatingBInventoryButton extends BInventoryButton {
	@Getter
	private long delay;
	private Timer timer = new Timer();
	@Getter
	private long updateInterval;

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
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private void checkUpdate(Player p) {
		if (getInv().isOpen(p)) {
			final ItemStack item = onUpdate(p).toItemStack(p);
			if (item != null) {
				Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

					@Override
					public void run() {
						try {
							if (p != null && getInv().isOpen(p)) {
								if (getFillSlots() != null && getFillSlots().size() > 0) {
									for (Integer slot : getFillSlots()) {
										p.getOpenInventory().getTopInventory().setItem(slot.intValue(), item);
									}
								} else {
									p.getOpenInventory().getTopInventory().setItem(getSlot(), item);
								}
							}
						} catch (Exception e) {
							AdvancedCorePlugin.getInstance().debug(e);
							cancel();
						}

					}
				});
			} else {
				cancel();
			}
		} else {
			cancel();
		}
	}

	@Override
	public void load(Player p) {
		if (timer != null) {
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					checkUpdate(p);
				}
			}, delay, updateInterval);
		}
	}

	public abstract ItemBuilder onUpdate(Player player);

}
