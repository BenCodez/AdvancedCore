package com.bencodez.advancedcore.api.user.userstorage.mysql.api.config;

import lombok.Getter;
import lombok.Setter;

public class MysqlConfig {
	@Getter
	@Setter
	private String tablePrefix;
	@Getter
	@Setter
	private String tableName;

	@Getter
	@Setter
	private String hostName;
	@Getter
	@Setter
	private int port;
	@Getter
	@Setter
	private String user;
	@Getter
	@Setter
	private String pass;
	@Getter
	@Setter
	private String database;
	@Getter
	@Setter
	private long lifeTime;
	@Getter
	@Setter
	private int maxThreads = 1;
	@Getter
	@Setter
	private boolean useSSL;
	@Getter
	@Setter
	private boolean publicKeyRetrieval;
	@Getter
	@Setter
	private boolean useMariaDB;
	@Getter
	@Setter
	private String line = "";
	
	public boolean hasTableNameSet() {
		if (tableName == null) {
			return false;
		}
		return !tableName.isEmpty();
	}
}
