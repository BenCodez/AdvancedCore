package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;

public abstract class UserStartup {
	public abstract void onStartUp(User user);

	public abstract void onFinish();
	
	public abstract void onStart();

	private ArrayList<User> users = new ArrayList<User>();

	/**
	 * @return the users
	 */
	public ArrayList<User> getUsers() {
		return users;
	}

	/**
	 * @param users the users to set
	 */
	public void setUsers(ArrayList<User> users) {
		this.users = users;
	}
}
