package com.bencodez.advancedcore.api.misc.effects;

import java.util.ArrayList;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;

public class FireworkHandler implements Listener {

	/** The instance. */
	static FireworkHandler instance = new FireworkHandler();

	/**
	 * Gets the single instance of FireworkHandler.
	 *
	 * @return single instance of FireworkHandler
	 */
	public static FireworkHandler getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new FireworkHandler.
	 */
	private FireworkHandler() {
	}

	/**
	 * Launch firework.
	 *
	 * @param loc          the loc
	 * @param power        the power
	 * @param colors       the colors
	 * @param fadeOutColor the fade out color
	 * @param trail        the trail
	 * @param flicker      the flicker
	 * @param types        the types
	 */
	public void launchFirework(Location loc, int power, ArrayList<String> colors, ArrayList<String> fadeOutColor,
			boolean trail, boolean flicker, ArrayList<String> types, boolean detonate) {
		plugin.getBukkitScheduler().runTask(plugin, new Runnable() {

			@Override
			public void run() {
				Firework fw = (Firework) loc.getWorld().spawnEntity(loc,
						MiscUtils.getInstance().getEntityType("FIREWORK_ROCKET", "FIREWORK"));
				FireworkMeta fwmeta = fw.getFireworkMeta();
				FireworkEffect.Builder builder = FireworkEffect.builder();
				if (trail) {
					builder.withTrail();
				}
				if (flicker) {
					builder.withFlicker();
				}
				for (String color : colors) {
					if (color.startsWith("#")) {
						String hexColor = color.substring(1); // Remove the "#" symbol
						int rgb = Integer.parseInt(hexColor, 16);
						int red = (rgb >> 16) & 0xFF;
						int green = (rgb >> 8) & 0xFF;
						int blue = rgb & 0xFF;

						builder.withColor(Color.fromRGB(red, green, blue));
					} else {
						try {
							builder.withColor(DyeColor.valueOf(color).getColor());
						} catch (Exception ex) {
							plugin.getLogger().info(color
									+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Color.html");
						}
					}
				}
				for (String color : fadeOutColor) {
					if (color.startsWith("#")) {
						String hexColor = color.substring(1); // Remove the "#" symbol
						int rgb = Integer.parseInt(hexColor, 16);
						int red = (rgb >> 16) & 0xFF;
						int green = (rgb >> 8) & 0xFF;
						int blue = rgb & 0xFF;

						builder.withFade(Color.fromRGB(red, green, blue));
					} else {
						try {
							builder.withFade(DyeColor.valueOf(color).getColor());
						} catch (Exception ex) {
							plugin.getLogger().info(color
									+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/DyeColor.html");
						}
					}
				}
				for (String type : types) {
					try {
						builder.with(Type.valueOf(type));
					} catch (Exception ex) {
						plugin.getLogger().info(type
								+ " is not a valid Firework Effect, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/FireworkEffect.Type.html");
					}
				}
				fwmeta.addEffects(builder.build());
				fwmeta.setPower(power);
				fw.setFireworkMeta(fwmeta);
				fw.setCustomName("reward");
				fw.setCustomNameVisible(false);
				// fireWorks.add(fw);
				if (detonate) {
					fw.detonate();
				}
				// plugin.debug("Launched firework");
			}
		}, loc);

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFireworkDamage(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Firework) {
			Firework fw = (Firework) event.getDamager();
			if (fw.getCustomName() != null) {
				if (fw.getCustomName().equals("reward")) {
					event.setCancelled(true);
				}
			}
		}
	}
}
