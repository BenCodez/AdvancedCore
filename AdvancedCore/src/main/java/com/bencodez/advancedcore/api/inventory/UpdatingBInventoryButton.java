package com.bencodez.advancedcore.api.inventory;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.simpleapi.player.PlayerUtils;

import lombok.Getter;

public abstract class UpdatingBInventoryButton extends BInventoryButton {
	@Getter
	private final long delay;
	@Getter
	private final long updateInterval;
	private final AdvancedCorePlugin plugin;
	@Getter
	private boolean updateOnClick = false;
	@Getter
	private long clickUpdateDelay = 0;

	public UpdatingBInventoryButton(AdvancedCorePlugin plugin, ItemBuilder item, long delay, long updateInterval) {
		super(item);
		this.plugin = plugin;
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public UpdatingBInventoryButton(AdvancedCorePlugin plugin, ItemStack item, long delay, long updateInterval) {
		super(item);
		this.plugin = plugin;
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public UpdatingBInventoryButton(AdvancedCorePlugin plugin, String name, String[] lore, ItemStack item, long delay,
			long updateInterval) {
		super(name, lore, item);
		this.plugin = plugin;
		this.updateInterval = updateInterval;
		this.delay = delay;
	}

	public UpdatingBInventoryButton delay(long milliseconds) {
		this.clickUpdateDelay = milliseconds;
		return this;
	}

	@Override
	public void load(Player player) {
		BInventory inventory = getInv();
		if (inventory == null) {
			return;
		}
		inventory.addUpdatingButton(player, plugin, delay, updateInterval, () -> scheduleUpdate(player, true));
	}

	@Override
	public void onClick(ClickEvent event, BInventory inventory) {
		super.onClick(event, inventory);
		if (!updateOnClick) {
			return;
		}

		if (clickUpdateDelay > 0) {
			inventory.addDelayedTask(event.getPlayer(), plugin, clickUpdateDelay, () -> update(event.getPlayer()));
		} else {
			update(event.getPlayer());
		}
	}

	public abstract ItemBuilder onUpdate(Player player);

	public void update(Player player) {
		scheduleUpdate(player, false);
	}

	public UpdatingBInventoryButton updateOnClick() {
		updateOnClick = true;
		return this;
	}

	private void scheduleUpdate(Player player, boolean cancelWhenUnavailable) {
		BInventory inventory = getInv();
		if (inventory == null) {
			return;
		}
		if (!plugin.isEnabled()) {
			cancelIfRequested(inventory, player, cancelWhenUnavailable);
			return;
		}

		plugin.getBukkitScheduler().runTask(plugin, () -> applyUpdate(player, cancelWhenUnavailable), player);
	}

	private void applyUpdate(Player player, boolean cancelWhenUnavailable) {
		BInventory inventory = getInv();
		if (inventory == null || player == null || !plugin.isEnabled()) {
			cancelIfRequested(inventory, player, cancelWhenUnavailable);
			return;
		}

		if (plugin.isLoadUserData() && !plugin.getUserManager().getDataManager().isCached(player.getUniqueId())) {
			return;
		}
		if (!inventory.isOpen(player)) {
			cancelIfRequested(inventory, player, cancelWhenUnavailable);
			return;
		}

		try {
			ItemBuilder builder = onUpdate(player);
			if (builder == null) {
				cancelIfRequested(inventory, player, cancelWhenUnavailable);
				return;
			}

			ItemStack item = builder.toItemStack(player);
			if (item == null) {
				cancelIfRequested(inventory, player, cancelWhenUnavailable);
				return;
			}

			Inventory topInventory = PlayerUtils.getTopInventory(player);
			if (topInventory == null) {
				cancelIfRequested(inventory, player, cancelWhenUnavailable);
				return;
			}

			List<Integer> fillSlots = getFillSlots();
			if (fillSlots != null && !fillSlots.isEmpty()) {
				for (Integer slot : fillSlots) {
					if (slot != null) {
						topInventory.setItem(slot.intValue(), item);
					}
				}
			} else {
				topInventory.setItem(getSlot(), item);
			}
		} catch (Exception exception) {
			plugin.debug(exception);
			cancelIfRequested(inventory, player, cancelWhenUnavailable);
		}
	}

	private void cancelIfRequested(BInventory inventory, Player player, boolean cancelWhenUnavailable) {
		if (cancelWhenUnavailable && inventory != null) {
			inventory.cancelTimer(player);
		}
	}
}
