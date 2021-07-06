package com.bencodez.advancedcore.api.user.userstorage.mysql.api;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

	public boolean connect(String host, String port, String username, String password, String database, boolean useSSL,
			long lifeTime, String str, boolean publicKeyRetrieval) {
		connectionManager = new ConnectionManager(host, port, username, password, database, maxConnections, useSSL,
				lifeTime, str, publicKeyRetrieval);
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
