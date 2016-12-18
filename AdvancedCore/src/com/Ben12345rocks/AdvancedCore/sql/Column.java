package com.Ben12345rocks.AdvancedCore.sql;

public class Column {

	public String name;
	public DataType dataType;
	public int limit = 0;
	private Object value;

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

	public Column(String name, DataType dataType, Object value) {
		this.name = name;
		this.dataType = dataType;
		limit = 0;
		this.value = value;
	}

	public DataType getDataType() {
		return dataType;
	}

	public int getLimit() {
		return limit;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setValue(Object value) {
		this.value = value;
	}

}
