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

/**
 * Represents a hologram using armor stands.
 */
public class Hologram {
	/**
	 * @return the armor stand entity
	 */
	@Getter
	private ArmorStand armorStand;
	/**
	 * @return the location of the hologram
	 */
	@Getter
	private Location loc;

	/**
	 * Creates a new hologram at the specified location with the given name.
	 * 
	 * @param loc the location
	 * @param name the hologram name
	 */
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

	/**
	 * Creates a new hologram with marker setting.
	 * 
	 * @param loc the location
	 * @param name the hologram name
	 * @param marker whether to set as marker
	 */
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

	/**
	 * Creates a new hologram with marker and glowing settings.
	 * 
	 * @param loc the location
	 * @param name the hologram name
	 * @param marker whether to set as marker
	 * @param glowing whether the hologram should glow
	 */
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

	/**
	 * Creates a new hologram with persistent data.
	 * 
	 * @param loc the location
	 * @param name the hologram name
	 * @param marker whether to set as marker
	 * @param glowing whether the hologram should glow
	 * @param key the namespaced key for persistent data
	 * @param value the persistent data value
	 */
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

	/**
	 * Creates a new hologram with persistent data and entity metadata.
	 * 
	 * @param loc the location
	 * @param name the hologram name
	 * @param marker whether to set as marker
	 * @param glowing whether the hologram should glow
	 * @param key the namespaced key for persistent data
	 * @param value the persistent data value
	 * @param str the metadata key
	 * @param value1 the metadata value
	 */
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

	/**
	 * Gets the persistent data container of the armor stand.
	 * 
	 * @return the persistent data container
	 */
	public PersistentDataContainer getPersistentDataHolder() {
		return armorStand.getPersistentDataContainer();
	}

	/**
	 * Sets the glowing state of the hologram.
	 * 
	 * @param value whether the hologram should glow
	 */
	public void glow(boolean value) {
		if (armorStand != null) {
			armorStand.setGlowing(value);
		}
	}

	/**
	 * Checks if the hologram is alive.
	 * 
	 * @return true if the armor stand is not dead, false otherwise
	 */
	public boolean isAlive() {
		return !armorStand.isDead();
	}

	/**
	 * Checks if the hologram has been created.
	 * 
	 * @return true if the armor stand exists, false otherwise
	 */
	public boolean isCreated() {
		return armorStand != null;
	}

	/**
	 * Kills and removes the hologram.
	 */
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
