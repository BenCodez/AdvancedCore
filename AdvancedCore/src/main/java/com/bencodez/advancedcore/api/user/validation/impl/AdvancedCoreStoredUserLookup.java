package com.bencodez.advancedcore.api.user.validation.impl;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.validation.interfaces.StoredUserLookup;

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
		return plugin.getUserManager().userExistStored(name);
	}
}