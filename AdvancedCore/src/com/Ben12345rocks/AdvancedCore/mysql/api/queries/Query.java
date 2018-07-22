package com.Ben12345rocks.AdvancedCore.mysql.api.queries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.Ben12345rocks.AdvancedCore.mysql.api.MySQL;
import com.sun.rowset.CachedRowSetImpl;

@SuppressWarnings("restriction")
public class Query {

	private MySQL mysql;
	private Connection connection;
	private String sql;
	private boolean addBatch = false;

	public Query(MySQL mysql, String sql) throws SQLException {
		this.mysql = mysql;
		this.sql = sql;
	}

	/**
	 * Add the current statement to the batch.
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public void addBatch() throws SQLException {
		if (connection.getAutoCommit()) {
			connection.setAutoCommit(false);
		}
		addBatch = true;
	}

	/**
	 * Execute a batch that does not return a ResultSet.
	 *
	 * @return an array with updates rows
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public int[] executeBatch() throws SQLException {
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(this.sql);) {

			for (Entry<Integer, Object> entry : paramters.entrySet()) {
				sql.setObject(entry.getKey(), entry.getValue());
			}
			if (addBatch) {
				sql.addBatch();
			}
			return sql.executeBatch();

		} catch (SQLException e) {
		}
		return new int[0];
	}

	/**
	 * Execute a batch that does not return a ResultSet asynchronously.
	 * <p>
	 * The query will be run in a separate thread.
	 */
	public void executeBatchAsync() {
		executeBatchAsync(null);
	}

	/**
	 * Execute a batch that does not return a ResultSet asynchronously.
	 * <p>
	 * The query will be run in a separate thread.
	 *
	 * @param callback
	 *            the callback to be executed once the query is done
	 */
	public void executeBatchAsync(final Callback<int[], SQLException> callback) {
		mysql.getThreadPool().submit(new Runnable() {

			@Override
			public void run() {
				try {
					int[] rowsChanged = executeBatch();
					if (callback != null) {
						callback.call(rowsChanged, null);
					}
				} catch (SQLException e) {
					if (callback != null) {
						callback.call(null, e);
					}
				}
			}

		});
	}

	/**
	 * Execute a SQL query that does return a ResultSet.
	 * <p>
	 * Uses a CachedRowSetImpl that is not connected to the database.
	 *
	 * @return the ResultSet
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public ResultSet executeQuery() throws SQLException {
		CachedRowSetImpl rowSet = new CachedRowSetImpl();
		ResultSet resultSet = null;

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(this.sql);) {

			for (Entry<Integer, Object> entry : paramters.entrySet()) {
				sql.setObject(entry.getKey(), entry.getValue());
			}
			if (addBatch) {
				sql.addBatch();
			}
			sql.executeQuery();
			rowSet.populate(resultSet);

		} catch (SQLException e) {
		}

		return rowSet;
	}

	/**
	 * Execute a SQL query that does return a ResultSet asynchronously.
	 * <p>
	 * The query will be run in a separate thread.
	 *
	 * @param callback
	 *            the callback to be executed once the query is done
	 */
	public void executeQueryAsync(final Callback<ResultSet, SQLException> callback) {
		mysql.getThreadPool().submit(new Runnable() {

			@Override
			public void run() {
				try {
					ResultSet rs = executeQuery();
					callback.call(rs, null);
				} catch (SQLException e) {
					callback.call(null, e);
				}
			}

		});
	}

	/**
	 * Execute a SQL query that does not return a ResultSet.
	 *
	 * @return number of rows changed
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public int executeUpdate() throws SQLException {
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(this.sql);) {
			for (Entry<Integer, Object> entry : paramters.entrySet()) {
				sql.setObject(entry.getKey(), entry.getValue());
			}
			if (addBatch) {
				sql.addBatch();
			}
			return sql.executeUpdate();

		} catch (SQLException e) {
		}

		return 0;

	}

	/**
	 * Execute a SQL query that does not return a ResultSet asynchronously.
	 * <p>
	 * The query will be run in a separate thread.
	 */
	public void executeUpdateAsync() {
		executeUpdateAsync(null);
	}

	/**
	 * Execute a SQL query that does not return a ResultSet asynchronously.
	 * <p>
	 * The query will be run in a seperate thread.
	 *
	 * @param callback
	 *            the callback to be executed once the query is done
	 */
	public void executeUpdateAsync(final Callback<Integer, SQLException> callback) {
		mysql.getThreadPool().submit(new Runnable() {

			@Override
			public void run() {
				try {
					int rowsChanged = executeUpdate();
					if (callback != null) {
						callback.call(rowsChanged, null);
					}
				} catch (SQLException e) {
					if (callback != null) {
						callback.call(0, e);
					} else {
						e.printStackTrace();
					}
				}
			}

		});
	}

	/**
	 * Rollback the transaction.
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public void rollback() throws SQLException {
		if (connection != null) {
			connection.rollback();
		}
	}

	private HashMap<Integer, Object> paramters = new HashMap<Integer, Object>();

	/**
	 * Set a parameter of the prepared statement.
	 * <p>
	 * Parameters are defined using ? in the SQL query.
	 *
	 * @param index
	 *            the index of the parameter to set (starts with 1)
	 * @param value
	 *            the value to set the parameter to
	 *
	 * @throws SQLException
	 *             SQLException
	 */
	public void setParameter(int index, Object value) throws SQLException {
		paramters.put(index, value);
	}

}
