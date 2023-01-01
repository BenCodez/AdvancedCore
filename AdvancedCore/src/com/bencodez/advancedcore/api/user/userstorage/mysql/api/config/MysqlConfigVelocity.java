package com.bencodez.advancedcore.api.user.userstorage.mysql.api.config;

import com.bencodez.advancedcore.bungeeapi.velocity.VelocityYMLFile;

public class MysqlConfigVelocity extends MysqlConfig {
	public MysqlConfigVelocity(VelocityYMLFile config) {
		setTablePrefix(config.getString(config.getNode("Prefix"), ""));
		setHostName(config.getString(config.getNode("Host"), ""));
		setPort(config.getInt(config.getNode("Port"), 0));
		setUser(config.getString(config.getNode("Username"), ""));
		setPass(config.getString(config.getNode("Password"), ""));
		setDatabase(config.getString(config.getNode("Database"), ""));
		setLifeTime(config.getLong(config.getNode("MaxLifeTime"), -1));
		setMaxThreads(config.getInt(config.getNode("MaxConnections"), 1));
		if (getMaxThreads() < 1) {
			setMaxThreads(1);
		}
		setUseSSL(config.getBoolean(config.getNode("UseSSL"), false));
		setPublicKeyRetrieval(config.getBoolean(config.getNode("PublicKeyRetrieval"), false));
		setUseMariaDB(config.getBoolean(config.getNode("UseMariaDB"), false));
		if (!config.getString(config.getNode("Name"), "").isEmpty()) {
			setTableName(config.getString(config.getNode("Name"), ""));
		}

		setLine(config.getString(config.getNode("Line"), ""));
	}

	public MysqlConfigVelocity(String prePath, VelocityYMLFile config) {
		setTablePrefix(config.getString(config.getNode(prePath, "Prefix"), ""));
		setHostName(config.getString(config.getNode(prePath, "Host"), ""));
		setPort(config.getInt(config.getNode(prePath, "Port"), 0));
		setUser(config.getString(config.getNode(prePath, "Username"), ""));
		setPass(config.getString(config.getNode(prePath, "Password"), ""));
		setDatabase(config.getString(config.getNode(prePath, "Database"), ""));
		setLifeTime(config.getLong(config.getNode(prePath, "MaxLifeTime"), -1));
		setMaxThreads(config.getInt(config.getNode(prePath, "MaxConnections"), 1));
		if (getMaxThreads() < 1) {
			setMaxThreads(1);
		}
		setUseSSL(config.getBoolean(config.getNode(prePath, "UseSSL"), false));
		setPublicKeyRetrieval(config.getBoolean(config.getNode(prePath, "PublicKeyRetrieval"), false));
		setUseMariaDB(config.getBoolean(config.getNode(prePath, "UseMariaDB"), false));
		if (!config.getString(config.getNode(prePath, "Name"), "").isEmpty()) {
			setTableName(config.getString(config.getNode(prePath, "Name"), ""));
		}

		setLine(config.getString(config.getNode(prePath, "Line"), ""));
	}
}
