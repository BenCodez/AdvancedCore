package com.Ben12345rocks.AdvancedCore.mysql.api;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class ConnectionManager {

	private HikariDataSource dataSource;
	private String host;
	private String port;
	private String username;
	private String password;
	private String database;
	private int connectionTimeout;
	private int maximumPoolsize;
	private int maxConnections;

	public ConnectionManager(String host, String port, String username, String password, String database) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.database = database;
		connectionTimeout = 5000;
		maximumPoolsize = 10;
		maxConnections = 1;
	}

	public ConnectionManager(String host, String port, String username, String password, String database,
			int maxConnections) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.database = database;
		connectionTimeout = 5000;
		maximumPoolsize = 10;
		this.maxConnections = maxConnections;
	}

	public ConnectionManager(String host, String port, String username, String password, String database,
			int connectionTimeout, int maximumPoolsize, int maxConnections) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.database = database;
		this.connectionTimeout = connectionTimeout;
		this.maximumPoolsize = maximumPoolsize;
		this.maxConnections = maxConnections;
	}

	public void close() {
		if (isClosed()) {
			throw new IllegalStateException("Connection is not open.");
		}

		dataSource.close();
	}

	public Connection getConnection() {
		if (isClosed()) {
			throw new IllegalStateException("Connection is not open.");
		}

		try {
			return dataSource.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean isClosed() {
		return dataSource == null || dataSource.isClosed();
	}

	public boolean open() {
		try {
			Class.forName("com.mysql.jdbc.Driver");

			HikariConfig config = new HikariConfig();
			config.setDriverClassName("com.mysql.jdbc.Driver");
			config.setUsername(username);
			config.setPassword(password);
			config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, database));
			config.setConnectionTimeout(connectionTimeout);
			config.setMaximumPoolSize(maximumPoolsize);
			config.setMinimumIdle(maxConnections);
			dataSource = new HikariDataSource(config);
			return true;
		} catch (Exception e) {
			System.out.println(e.toString());
			return false;
		}
	}

}
