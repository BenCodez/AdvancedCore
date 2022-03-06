package com.bencodez.advancedcore.api.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;

import lombok.Getter;

public class Hologram {
	@Getter
	private ArmorStand armorStand;
	@Getter
	private Location loc;

	public Hologram(Location loc, String name) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					createHologram(name, true, false);
				}
			});

		} else {
			createHologram(name, true, false);
		}

		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	public Hologram(Location loc, String name, boolean marker) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					createHologram(name, marker, false);
				}
			});

		} else {
			createHologram(name, marker, false);
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	public Hologram(Location loc, String name, boolean marker, boolean glowing) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					createHologram(name, marker, glowing);
				}
			});

		} else {
			createHologram(name, marker, glowing);
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	public Hologram(Location loc, String name, boolean marker, boolean glowing, NamespacedKey key, int value) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					createHologram(name, marker, glowing, key, value);
				}
			});

		} else {
			createHologram(name, marker, glowing, key, value);
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	public PersistentDataContainer getPersistentDataHolder() {
		return armorStand.getPersistentDataContainer();
	}

	public boolean isCreated() {
		return armorStand != null;
	}

	private void createHologram(String name, boolean marker, boolean glowing) {
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		if (!name.isEmpty()) {
			armorStand.setCustomNameVisible(true);
		} else {
			armorStand.setCustomNameVisible(false);
		}
		armorStand.setCustomName(StringParser.getInstance().colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(marker);
		armorStand.setGlowing(glowing);
	}

	private void createHologram(String name, boolean marker, boolean glowing, NamespacedKey key, int value) {
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		if (!name.isEmpty()) {
			armorStand.setCustomNameVisible(true);
		} else {
			armorStand.setCustomNameVisible(false);
		}
		armorStand.setCustomName(StringParser.getInstance().colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(marker);
		armorStand.setGlowing(glowing);
		armorStand.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
	}

	public void glow(boolean value) {
		if (armorStand != null) {
			armorStand.setGlowing(value);
		}
	}

	public void kill() {
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					armorStand.setHealth(0);
					armorStand.remove();
				}
			});
		} else {
			armorStand.setHealth(0);
			armorStand.remove();
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().remove(this, false);
	}
}
