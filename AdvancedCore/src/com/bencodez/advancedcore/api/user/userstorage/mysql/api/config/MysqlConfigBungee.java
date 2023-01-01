package com.bencodez.advancedcore.api.user.userstorage.mysql.api.config;

import net.md_5.bungee.config.Configuration;

public class MysqlConfigBungee extends MysqlConfig {
	public MysqlConfigBungee(Configuration section) {
		setTablePrefix(section.getString("Prefix"));
		setHostName(section.getString("Host"));
		setPort(section.getInt("Port"));
		setUser(section.getString("Username"));
		setPass(section.getString("Password"));
		setDatabase(section.getString("Database"));
		setLifeTime(section.getLong("MaxLifeTime", -1));
		setMaxThreads(section.getInt("MaxConnections", 1));
		if (getMaxThreads() < 1) {
			setMaxThreads(1);
		}
		setUseSSL(section.getBoolean("UseSSL", false));
		setPublicKeyRetrieval(section.getBoolean("PublicKeyRetrieval", false));
		setUseMariaDB(section.getBoolean("UseMariaDB", false));
		if (!section.getString("Name", "").isEmpty()) {
			setTableName(section.getString("Name", ""));
		}

		setLine(section.getString("Line", ""));
	}
}
