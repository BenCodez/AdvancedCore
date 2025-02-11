package com.bencodez.advancedcore.api.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

public class Hologram {
	@Getter
	private ArmorStand armorStand;
	@Getter
	private Location loc;

	public Hologram(Location loc, String name) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

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
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

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
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

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
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

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

	public Hologram(Location loc, String name, boolean marker, boolean glowing, NamespacedKey key, int value,
			String str, Object value1) {
		this.loc = loc;
		if (!Bukkit.isPrimaryThread()) {
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

						@Override
						public void run() {
							createHologram(name, marker, glowing, key, value, str, value1);
						}
					});

		} else {
			createHologram(name, marker, glowing, key, value, str, value1);
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	private void createHologram(String name, boolean marker, boolean glowing) {
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		if (!name.isEmpty()) {
			armorStand.setCustomNameVisible(true);
		} else {
			armorStand.setCustomNameVisible(false);
		}
		armorStand.setCustomName(MessageAPI.colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(marker);
		armorStand.setGlowing(glowing);
		armorStand.setInvulnerable(true);
	}

	private void createHologram(String name, boolean marker, boolean glowing, NamespacedKey key, int value) {
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		if (!name.isEmpty()) {
			armorStand.setCustomNameVisible(true);
		} else {
			armorStand.setCustomNameVisible(false);
		}
		armorStand.setCustomName(MessageAPI.colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(marker);
		armorStand.setGlowing(glowing);
		armorStand.setInvulnerable(true);
		armorStand.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
	}

	private void createHologram(String name, boolean marker, boolean glowing, NamespacedKey key, int value, String str,
			Object object) {
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		if (!name.isEmpty()) {
			armorStand.setCustomNameVisible(true);
		} else {
			armorStand.setCustomNameVisible(false);
		}
		armorStand.setCustomName(MessageAPI.colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(marker);
		armorStand.setGlowing(glowing);
		armorStand.setInvulnerable(true);
		armorStand.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
		MiscUtils.getInstance().setEntityMeta(armorStand, str, object);
	}

	public PersistentDataContainer getPersistentDataHolder() {
		return armorStand.getPersistentDataContainer();
	}

	public void glow(boolean value) {
		if (armorStand != null) {
			armorStand.setGlowing(value);
		}
	}

	public boolean isAlive() {
		return !armorStand.isDead();
	}

	public boolean isCreated() {
		return armorStand != null;
	}

	public void kill() {
		if (!Bukkit.isPrimaryThread()) {
			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.executeOrScheduleSync(AdvancedCorePlugin.getInstance(), new Runnable() {

						@Override
						public void run() {
							armorStand.setHealth(0);
							armorStand.remove();
							armorStand = null;
						}
					});
		} else {
			armorStand.setHealth(0);
			armorStand.remove();
			armorStand = null;
		}
		AdvancedCorePlugin.getInstance().getHologramHandler().remove(this, false);
	}
}
