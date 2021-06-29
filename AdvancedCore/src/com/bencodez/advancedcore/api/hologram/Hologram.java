package com.bencodez.advancedcore.api.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;

import lombok.Getter;

public class Hologram {
	@Getter
	private Location loc;
	@Getter
	private ArmorStand armorStand;

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

	public void glow(boolean value) {
		if (armorStand != null) {
			armorStand.setGlowing(value);
		}
	}
}
