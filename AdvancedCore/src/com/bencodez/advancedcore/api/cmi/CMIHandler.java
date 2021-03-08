package com.bencodez.advancedcore.api.cmi;

import com.Zrips.CMI.CMI;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public class CMIHandler {
	public boolean isVanished(AdvancedCoreUser user) {
		return CMI.getInstance().getPlayerManager().getUser(java.util.UUID.fromString(user.getUUID())).isVanished();
	}
}
