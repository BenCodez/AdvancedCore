package com.bencodez.advancedcore.api.user.userstorage.mysql.api.config;

import org.bukkit.configuration.ConfigurationSection;

public class MysqlConfigSpigot extends MysqlConfig {
	public MysqlConfigSpigot(ConfigurationSection section) {
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
		setTableName(section.getString("Name", ""));

		setLine(section.getString("Line", ""));
	}
}
