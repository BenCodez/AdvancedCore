package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

public abstract class UserStartup {
	@Getter
	@Setter
	private ArrayList<AdvancedCoreUser> users = new ArrayList<AdvancedCoreUser>();

	public abstract void onFinish();

	public abstract void onStart();

	public abstract void onStartUp(AdvancedCoreUser user);

}
