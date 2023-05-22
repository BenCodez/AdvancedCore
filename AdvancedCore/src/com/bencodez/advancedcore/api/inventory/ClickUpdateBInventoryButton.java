package com.bencodez.advancedcore.api.inventory;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import lombok.Getter;

public abstract class ClickUpdateBInventoryButton extends BInventoryButton {
	private AdvancedCorePlugin plugin;

	public ClickUpdateBInventoryButton(AdvancedCorePlugin plugin, ItemBuilder item) {
		super(item);
		this.plugin = plugin;
	}

	public ClickUpdateBInventoryButton(AdvancedCorePlugin plugin, ItemStack item) {
		super(item);
		this.plugin = plugin;
	}

	public ClickUpdateBInventoryButton(AdvancedCorePlugin plugin, String name, String[] lore, ItemStack item) {
		super(name, lore, item);
		this.plugin = plugin;
	}

	@Getter
	private long mileseconds = 0;

	public ClickUpdateBInventoryButton delay(long mileseconds) {
		this.mileseconds = mileseconds;
		return this;
	}

	public void update(Player p) {
		if (!plugin.isLoadUserData() || plugin.getUserManager().getDataManager().isCached(p.getUniqueId())) {
			final ItemStack item = onUpdate(p).toItemStack(p);
			if (item != null) {
				if (plugin.isEnabled()) {
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
								plugin.debug(e);
							}

						}
					});
				}
			}
		}
	}

	@Override
	public void onClick(ClickEvent event, BInventory inv) {
		super.onClick(event, inv);
		if (mileseconds > 0) {
			plugin.getInventoryTimer().schedule(new Runnable() {

				@Override
				public void run() {
					update(event.getPlayer());
				}
			}, mileseconds, TimeUnit.MILLISECONDS);
		} else {
			update(event.getPlayer());
		}
	}

	public abstract ItemBuilder onUpdate(Player player);

}