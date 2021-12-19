package com.bencodez.advancedcore.api.user;

public abstract class UserDataChanged {
	public abstract void onChange(AdvancedCoreUser user, String... key);
}
