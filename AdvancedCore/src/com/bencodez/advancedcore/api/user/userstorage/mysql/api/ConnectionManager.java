package com.bencodez.advancedcore.api.user.userstorage.mysql.api;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class ConnectionManager {

	private int connectionTimeout;
	private String database;
	private HikariDataSource dataSource;
	private String host;
	private int maximumPoolsize;
	// private int maxConnections;
	private long maxLifetimeMs;
	private String password;
	private String port;
	private boolean publicKeyRetrieval;
	private String str = "";
	private String username;
	private boolean useSSL = false;
	private boolean useMariaDB = false;

	public ConnectionManager(String host, String port, String username, String password, String database) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.database = database;
		connectionTimeout = 50000;
		maximumPoolsize = 5;
		// maxConnections = 1;

	}

	public ConnectionManager(String host, String port, String username, String password, String database,
			int maxConnections, boolean useSSL, long lifeTime, String str, boolean publicKeyRetrieval,
			boolean useMariaDB) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.database = database;
		connectionTimeout = 50000;
		maximumPoolsize = maxConnections;
		this.useSSL = useSSL;
		this.maxLifetimeMs = lifeTime;
		// this.maxConnections = maxConnections;
		this.str = str;
		this.publicKeyRetrieval = publicKeyRetrieval;
		this.useMariaDB = useMariaDB;
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
		// this.maxConnections = maxConnections;
	}

	public void close() {
		if (isClosed()) {
			throw new IllegalStateException("Connection is not open.");
		}

		dataSource.close();
	}

	public Connection getConnection() {
		try {
			if (isClosed()) {
				open();
			}
			return dataSource.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
			open();
		}
		return null;
	}

	/**
	 * @return the connectionTimeout
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * @return the database
	 */
	public String getDatabase() {
		return database;
	}

	/**
	 * @return the dataSource
	 */
	public HikariDataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @return the maximumPoolsize
	 */
	public int getMaximumPoolsize() {
		return maximumPoolsize;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @return the port
	 */
	public String getPort() {
		return port;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	public boolean isClosed() {
		return dataSource == null || dataSource.isClosed();
	}

	/**
	 * @return the useSSL
	 */
	public boolean isUseSSL() {
		return useSSL;
	}

	private String mysqlDriver = "";

	public String getMysqlDriverName() {
		String className = "org.mariadb.jdbc.Driver";
		if (!useMariaDB) {
			className = "com.mysql.cj.jdbc.Driver";
		}

		try {
			Class.forName(className);
		} catch (ClassNotFoundException ignored) {
			className = "com.mysql.cj.jdbc.Driver";
			try {
				Class.forName(className);
			} catch (ClassNotFoundException ignored1) {
				try {
					className = "com.mysql.jdbc.Driver";
					Class.forName(className);
				} catch (ClassNotFoundException ignored2) {
				}
			}
		}
		return className;
	}

	public boolean open() {
		if (mysqlDriver.isEmpty()) {
			mysqlDriver = getMysqlDriverName();
		}

		try {
			HikariConfig config = new HikariConfig();
			config.setDriverClassName(mysqlDriver);
			config.setUsername(username);
			config.setPassword(password);
			if (mysqlDriver.equals("org.mariadb.jdbc.Driver")) {
				config.setJdbcUrl(String.format("jdbc:mariadb://%s:%s/%s", host, port, database) + "?useSSL=" + useSSL
						+ "&allowMultiQueries=true&rewriteBatchedStatements=true&useDynamicCharsetInfo=false&allowPublicKeyRetrieval="
						+ publicKeyRetrieval + str);
			} else {
				config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, database) + "?useSSL=" + useSSL
						+ "&allowMultiQueries=true&rewriteBatchedStatements=true&useDynamicCharsetInfo=false&allowPublicKeyRetrieval="
						+ publicKeyRetrieval + str);
			}
			config.setConnectionTimeout(connectionTimeout);
			config.setMaximumPoolSize(maximumPoolsize);
			config.setMinimumIdle(maximumPoolsize);
			if (maxLifetimeMs > -1) {
				config.setMaxLifetime(maxLifetimeMs);
			}
			config.addDataSourceProperty("cachePrepStmts", true);
			config.addDataSourceProperty("prepStmtCacheSize", 500);
			config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
			config.addDataSourceProperty("useServerPrepStmts", true);
			config.setAutoCommit(true);
			dataSource = new HikariDataSource(config);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * @param connectionTimeout the connectionTimeout to set
	 */
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	/**
	 * @param database the database to set
	 */
	public void setDatabase(String database) {
		this.database = database;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(HikariDataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @param host the host to set
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @param maximumPoolsize the maximumPoolsize to set
	 */
	public void setMaximumPoolsize(int maximumPoolsize) {
		this.maximumPoolsize = maximumPoolsize;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @param port the port to set
	 */
	public void setPort(String port) {
		this.port = port;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @param useSSL the useSSL to set
	 */
	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

}
