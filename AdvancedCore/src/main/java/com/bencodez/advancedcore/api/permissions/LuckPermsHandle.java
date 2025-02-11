package com.bencodez.advancedcore.api.permissions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;

public class LuckPermsHandle {
	@Getter
	LuckPerms api = null;

	public boolean hasPermission(UUID uuid, String permission) {
		User user = api.getUserManager().getUser(uuid);
		if (user == null) {
			CompletableFuture<User> loadedUser = api.getUserManager().loadUser(uuid);
			try {
				user = loadedUser.get(10, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
				return false;
			}
		}
		return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
	}

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
}
