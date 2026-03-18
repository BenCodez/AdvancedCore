package com.bencodez.advancedcore.api.user.validation;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.validation.impl.AdvancedCoreBedrockPrecheck;
import com.bencodez.advancedcore.api.user.validation.impl.AdvancedCoreStoredUserLookup;
import com.bencodez.advancedcore.api.user.validation.impl.BukkitOnlinePlayerLookup;
import com.bencodez.advancedcore.api.user.validation.impl.BukkitServerHistoryLookup;

public class UserValidationFactory {

	private UserValidationFactory() {
	}

	public static UserValidationService create(AdvancedCorePlugin plugin) {
		return new UserValidationService(
				new BukkitOnlinePlayerLookup(),
				new AdvancedCoreStoredUserLookup(plugin),
				new BukkitServerHistoryLookup(),
				new AdvancedCoreBedrockPrecheck(plugin));
	}
}