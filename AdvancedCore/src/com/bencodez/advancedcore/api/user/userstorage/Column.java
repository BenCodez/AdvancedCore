package com.bencodez.advancedcore.api.user.userstorage;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;

import lombok.Getter;
import lombok.Setter;

public class Column {

	@Getter
	@Setter
	private DataType dataType;
	@Getter
	@Setter
	private int limit = 0;
	@Getter
	@Setter
	private String name;
	@Getter
	@Setter
	private DataValue value;

	public Column(String name, DataType dataType) {
		this.name = name;
		this.dataType = dataType;
		limit = 0;
	}

	public Column(String name, DataType dataType, int limit) {
		this.name = name;
		this.dataType = dataType;
		this.limit = limit;
	}

	public Column(String name, DataValue value) {
		this.name = name;
		limit = 0;
		this.value = value;
		dataType = value.getType();
	}
}
