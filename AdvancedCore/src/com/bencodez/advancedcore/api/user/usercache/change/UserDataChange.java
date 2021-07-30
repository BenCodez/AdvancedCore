package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;

import lombok.Getter;

public abstract class UserDataChange {
	@Getter
	private String key;

	public UserDataChange(String key) {
		this.key = key;
	}
	
	public abstract DataValue toUserDataValue();
}
