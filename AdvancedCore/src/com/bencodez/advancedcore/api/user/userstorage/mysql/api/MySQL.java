package com.bencodez.advancedcore.api.user.userstorage.mysql.api;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bencodez.advancedcore.api.user.userstorage.mysql.api.config.MysqlConfig;

public abstract class MySQL {

	private ConnectionManager connectionManager;
	private int maxConnections;
	private ExecutorService threadPool;

	/**
	 * Create a new MySQL object with a default of 10 maximum threads.
	 */
	public MySQL() {
		threadPool = Executors.newFixedThreadPool(10);
		maxConnections = 1;
	}

	/**
	 * Create a new MySQL object.
	 *
	 * @param maxConnections Maxiumn number of connections
	 */
	public MySQL(int maxConnections) {
		threadPool = Executors.newFixedThreadPool(10);
		this.maxConnections = maxConnections;
	}

	public boolean connect(MysqlConfig config) {
		this.maxConnections = config.getMaxThreads();
		connectionManager = new ConnectionManager(config.getHostName(), "" + config.getPort(), config.getUser(),
				config.getPass(), config.getDatabase(), maxConnections, config.isUseSSL(), config.getLifeTime(),
				config.getLine(), config.isPublicKeyRetrieval(), config.isUseMariaDB());

		return connectionManager.open();
	}

	public abstract void debug(SQLException e);

	/**
	 * Close all connections and the data source.
	 */
	public void disconnect() {
		if (connectionManager != null) {
			connectionManager.close();
		}

		if (threadPool != null) {
			threadPool.shutdown();
		}
	}

	/**
	 * Get the connection manager.
	 *
	 * @return the connection manager
	 */
	public ConnectionManager getConnectionManager() {
		return connectionManager;
	}

	/**
	 * Get the thread pool.
	 *
	 * @return the thread pool
	 */
	public ExecutorService getThreadPool() {
		return threadPool;
	}

	public abstract void severe(String string);
}
