package com.bencodez.advancedcore.api.user.usercache.change;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;

import lombok.Getter;
import lombok.Setter;

public abstract class UserDataChange {
	@Getter
	@Setter
	private String key;

	public UserDataChange(String key) {
		this.key = key;
	}
	
	public abstract DataValue toUserDataValue();

	public abstract void dump();
}
