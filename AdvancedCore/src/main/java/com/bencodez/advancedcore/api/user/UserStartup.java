package com.bencodez.advancedcore.api.user;

import lombok.Getter;
import lombok.Setter;

public abstract class UserStartup {
	@Getter
	@Setter
	private boolean process = true;
	
	public abstract void onFinish();

	public abstract void onStart();

	public abstract void onStartUp(AdvancedCoreUser user);
	
	public void onPostFinish() {
		
	}

}
