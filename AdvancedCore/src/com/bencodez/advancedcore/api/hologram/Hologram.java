package com.bencodez.advancedcore.api.hologram;

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

	public Hologram(Location loc, String name) {
		this.loc = loc;
		armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
		armorStand.setVisible(false);
		armorStand.setCustomNameVisible(true);
		armorStand.setCustomName(StringParser.getInstance().colorize(name));
		armorStand.setGravity(false);
		armorStand.setAI(false);
		armorStand.setMarker(true);
		AdvancedCorePlugin.getInstance().getHologramHandler().add(this);
	}

	public void kill() {
		armorStand.setHealth(0);
		armorStand.remove();
		AdvancedCorePlugin.getInstance().getHologramHandler().remove(this, false);
	}
}
