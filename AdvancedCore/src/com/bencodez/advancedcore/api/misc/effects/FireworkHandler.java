package com.bencodez.advancedcore.api.misc.effects;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.scheduler.BukkitScheduler;

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

	private ConcurrentLinkedQueue<Firework> fireWorks = new ConcurrentLinkedQueue<Firework>();

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
			boolean trail, boolean flicker, ArrayList<String> types) {
		BukkitScheduler.runTask(plugin, new Runnable() {

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
						plugin.getLogger().info(color
								+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Color.html");
					}
				}
				for (String color : fadeOutColor) {
					try {
						builder.withFade(DyeColor.valueOf(color).getColor());
					} catch (Exception ex) {
						plugin.getLogger().info(color
								+ " is not a valid color, see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/DyeColor.html");
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
				fireWorks.add(fw);
				// plugin.debug("Launched firework");
			}
		}, loc);

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFireworkDamage(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Firework) {
			Firework fw = (Firework) event.getDamager();
			if (fireWorks.contains(fw)) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFireworkExplode(FireworkExplodeEvent event) {
		if (event.getEntity() instanceof Firework) {
			Firework fw = event.getEntity();
			if (fireWorks.contains(fw)) {
				BukkitScheduler.runTaskLaterAsynchronously(plugin, new Runnable() {

					@Override
					public void run() {
						fireWorks.remove(fw);
					}
				}, 10l);
			}
		}
	}
}
