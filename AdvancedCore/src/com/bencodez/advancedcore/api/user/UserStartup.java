package com.bencodez.advancedcore.api.user;

public abstract class UserStartup {
	public abstract void onFinish();

	public abstract void onStart();

	public abstract void onStartUp(AdvancedCoreUser user);

}
