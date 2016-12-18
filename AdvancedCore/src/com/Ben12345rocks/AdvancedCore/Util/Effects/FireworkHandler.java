package com.Ben12345rocks.AdvancedCore.Util.Effects;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

public class FireworkHandler {

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
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Instantiates a new FireworkHandler.
	 */
	private FireworkHandler() {
	}

	/**
	 * Launch firework.
	 *
	 * @param loc
	 *            the loc
	 * @param power
	 *            the power
	 * @param colors
	 *            the colors
	 * @param fadeOutColor
	 *            the fade out color
	 * @param trail
	 *            the trail
	 * @param flicker
	 *            the flicker
	 * @param types
	 *            the types
	 */
	public void launchFirework(Location loc, int power, ArrayList<String> colors, ArrayList<String> fadeOutColor,
			boolean trail, boolean flicker, ArrayList<String> types) {
		Bukkit.getScheduler().runTask(plugin.getPlugin(), new Runnable() {

			@Override
			public void run() {
				Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
				FireworkMeta fwmeta = fw.getFireworkMeta();
				FireworkEffect.Builder builder = FireworkEffect.builder();
				if (trail) {
					builder.withTrail();
				}
				if (flicker) {
					builder.withFlicker();
				}
				for (String color : colors) {
					try {
						builder.withColor(DyeColor.valueOf(color).getColor());
					} catch (Exception ex) {
						plugin.getPlugin().getLogger().info(color
								+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Color.html");
					}
				}
				for (String color : fadeOutColor) {
					try {
						builder.withFade(DyeColor.valueOf(color).getColor());
					} catch (Exception ex) {
						plugin.getPlugin().getLogger().info(color
								+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Color.html");
					}
				}
				for (String type : types) {
					try {
						builder.with(Type.valueOf(type));
					} catch (Exception ex) {
						plugin.getPlugin().getLogger().info(type
								+ " is not a valid Firework Effect, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/FireworkEffect.Type.html");
					}
				}
				fwmeta.addEffects(builder.build());
				fwmeta.setPower(power);
				fw.setFireworkMeta(fwmeta);
				// plugin.debug("Launched firework");
			}
		});

	}
}
