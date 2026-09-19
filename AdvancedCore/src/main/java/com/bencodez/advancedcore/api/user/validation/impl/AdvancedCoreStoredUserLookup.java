package com.bencodez.advancedcore.api.user.validation.impl;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.validation.interfaces.StoredUserLookup;
import org.bukkit.Bukkit;

public class AdvancedCoreStoredUserLookup implements StoredUserLookup {

	private final AdvancedCorePlugin plugin;

	public AdvancedCoreStoredUserLookup(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean userExistsStored(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()
				&& plugin.getUserManager().getDataManager().hasSharedSqlBackend()) {
			return UuidLookup.getInstance().getAllCachedNames().stream().anyMatch(name::equalsIgnoreCase);
		}
		return plugin.getUserManager().userExistStored(name);
	}
}
