package com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries;

public interface Callback<V extends Object, T extends Throwable> {

	public void call(V result, T thrown);

}
