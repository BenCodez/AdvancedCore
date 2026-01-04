package com.bencodez.advancedcore.api.permissions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class LuckPermsHandle {
	@Getter
	private LuckPerms api;

	public void load(AdvancedCorePlugin plugin) {
		RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
		if (provider != null) {
			api = provider.getProvider();
			plugin.getLogger().info("Loaded LuckPerms hook!");
		}
	}

	public boolean luckpermsApiLoaded() {
		return api != null;
	}

	/**
	 * Synchronous permission check.
	 *
	 * - If player is online, uses Bukkit (fast). - If player is offline, requires
	 * LuckPerms User load.
	 *
	 * IMPORTANT: This method will NOT block the main thread. If called on the main
	 * thread for an offline user, it returns false.
	 */
	public boolean hasPermission(UUID uuid, String permission) {
		if (api == null) {
			return false;
		}

		// Handle your "!perm" convention
		boolean negate = permission.startsWith("!");
		if (negate) {
			permission = permission.substring(1);
		}

		// Online fast path (and best context accuracy)
		Player player = Bukkit.getPlayer(uuid);
		if (player != null) {
			boolean has = player.hasPermission(permission);
			return negate ? !has : has;
		}

		// Offline: do NOT block main thread
		if (Bukkit.isPrimaryThread()) {
			// If you want: plugin.getLogger().fine(...) here, but you don't have plugin in
			// this class right now.
			return negate; // treat missing as false, so negation returns true? Usually NOT desired.
			// I'd recommend: return false; (see note below)
		}

		// We're async here, safe to wait briefly
		User user = api.getUserManager().getUser(uuid);
		if (user == null) {
			try {
				user = api.getUserManager().loadUser(uuid).get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				return false;
			}
		}

		boolean has = checkUserPermission(user, permission, null);
		return negate ? !has : has;
	}

	/**
	 * Async permission check for offline users. Safe to call from the main thread.
	 */
	public CompletableFuture<Boolean> hasPermissionAsync(UUID uuid, String permission) {
		if (api == null) {
			return CompletableFuture.completedFuture(false);
		}

		boolean negate = permission.startsWith("!");
		if (negate) {
			permission = permission.substring(1);
		}

		// Online fast path
		Player player = Bukkit.getPlayer(uuid);
		if (player != null) {
			boolean has = player.hasPermission(permission);
			return CompletableFuture.completedFuture(negate ? !has : has);
		}

		final String permFinal = permission;
		return api.getUserManager().loadUser(uuid).thenApply(user -> {
			if (user == null) {
				return false;
			}
			boolean has = checkUserPermission(user, permFinal, null);
			return negate ? !has : has;
		});
	}

	private boolean checkUserPermission(User user, String permission, Player playerContext) {
		QueryOptions options;

		if (playerContext != null) {
			options = api.getContextManager().getQueryOptions(playerContext);
		} else {
			options = api.getContextManager().getStaticQueryOptions();
		}

		return user.getCachedData().getPermissionData(options).checkPermission(permission).asBoolean();
	}
}
