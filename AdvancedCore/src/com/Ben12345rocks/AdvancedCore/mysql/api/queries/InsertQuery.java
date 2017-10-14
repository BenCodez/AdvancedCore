package com.Ben12345rocks.AdvancedCore.mysql.api.queries;

import java.util.LinkedHashMap;
import java.util.Map;

import com.Ben12345rocks.AdvancedCore.mysql.api.utils.QueryUtils;

public class InsertQuery {

	private String table;
	private LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
	private LinkedHashMap<String, String> duplicateValues = new LinkedHashMap<String, String>();
	private boolean onDuplicateKey = false;

	/**
	 * Create an insert query.
	 *
	 * @param table
	 *            the table to be updated
	 */
	public InsertQuery(String table) {
		this.table = table;
	}

	/**
	 * Build the query as a String.
	 *
	 * @return the query as a String
	 */
	public String build() {
		StringBuilder builder = new StringBuilder();
		builder.append("INSERT INTO ").append(table).append(" (").append(QueryUtils.separate(values.keySet(), ","))
				.append(")").append(" VALUES (").append(QueryUtils.separate(values.values(), ",")).append(")");

		if (onDuplicateKey) {
			builder.append(" ON DUPLICATE KEY UPDATE ");
			String separator = "";
			for (Map.Entry<String, String> entry : duplicateValues.entrySet()) {
				String column = entry.getKey();
				String value = entry.getValue();
				builder.append(separator).append(column).append("=").append(value);
				separator = ",";
			}
		}

		return builder.toString();
	}

	/**
	 * Add on duplicate key update clause.
	 *
	 * @return the InsertQuery object
	 */
	public InsertQuery onDuplicateKeyUpdate() {
		onDuplicateKey = true;
		return this;
	}

	/**
	 * Update a column to value in case of a duplicate key.
	 * <p>
	 * Automatically inserts values(column).
	 *
	 * @param column
	 *            column
	 * @return InsertQuery
	 */
	public InsertQuery set(String column) {
		set(column, "VALUES(" + column + ")");
		return this;
	}

	/**
	 * Update a column to value in case of a duplicate key.
	 *
	 * @param column
	 *            the column to update
	 * @param value
	 *            the new value
	 * @return the InsertQuery object
	 */
	public InsertQuery set(String column, String value) {
		duplicateValues.put(column, value);
		return this;
	}

	/**
	 * Set a column to insert to. Automatically sets the value to ? to be used with
	 * prepared statements.
	 *
	 * @param column
	 *            the column to insert to
	 * @return the InsertQuery object
	 */
	public InsertQuery value(String column) {
		value(column, "?");
		return this;
	}

	/**
	 * Set a column to insert to and the value to be inserted.
	 *
	 * @param column
	 *            the column to insert to
	 * @param value
	 *            the value to be inserted
	 * @return the InsertQuery object
	 */
	public InsertQuery value(String column, String value) {
		values.put(column, value);
		return this;
	}

}
