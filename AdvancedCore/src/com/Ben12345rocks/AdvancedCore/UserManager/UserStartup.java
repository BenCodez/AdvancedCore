package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;

public abstract class UserStartup {
	private ArrayList<User> users = new ArrayList<User>();

	/**
	 * @return the users
	 */
	public ArrayList<User> getUsers() {
		return users;
	}

	public abstract void onFinish();

	public abstract void onStart();

	public abstract void onStartUp(User user);

	/**
	 * @param users
	 *            the users to set
	 */
	public void setUsers(ArrayList<User> users) {
		this.users = users;
	}
}
