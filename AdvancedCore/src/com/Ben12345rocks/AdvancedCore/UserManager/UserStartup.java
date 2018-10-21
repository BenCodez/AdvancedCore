package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

public abstract class UserStartup {
	@Getter
	@Setter
	private ArrayList<User> users = new ArrayList<User>();

	public abstract void onFinish();

	public abstract void onStart();

	public abstract void onStartUp(User user);

}
