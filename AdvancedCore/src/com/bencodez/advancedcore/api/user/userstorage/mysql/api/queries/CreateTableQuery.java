package com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries;

import java.util.ArrayList;
import java.util.List;

import com.bencodez.advancedcore.api.user.userstorage.mysql.api.utils.QueryUtils;

public class CreateTableQuery {

	private List<String> columns = new ArrayList<>();
	private boolean ifNotExists = false;
	private String primaryKey;
	private String table;

	/**
	 * Create a create table query.
	 *
	 * @param table the table to be created
	 */
	public CreateTableQuery(String table) {
		this.table = table;
	}

	/**
	 * Build the query as a String.
	 *
	 * @return the query as a String
	 */
	public String build() {
		StringBuilder builder = new StringBuilder();
		builder.append("CREATE TABLE ");

		if (ifNotExists) {
			builder.append("IF NOT EXISTS ");
		}

		builder.append(table).append(" (").append(QueryUtils.separate(columns, ","));

		if (primaryKey != null) {
			builder.append(",PRIMARY KEY(");
			builder.append(primaryKey);
			builder.append(")");
		}

		builder.append(")");

		return builder.toString();
	}

	/**
	 * Add a column with settings.
	 *
	 * @param column   the column
	 * @param settings the column settings
	 * @return the CreateTableQuery object
	 */
	public CreateTableQuery column(String column, String settings) {
		columns.add(column + " " + settings);
		return this;
	}

	/**
	 * Add if not exists to the query.
	 *
	 * @return the CreateTableQuery object
	 */
	public CreateTableQuery ifNotExists() {
		ifNotExists = true;
		return this;
	}

	/**
	 * Set the primary key to column.
	 *
	 * @param column the column to be the primary key
	 * @return the CreateTableQuery object
	 */
	public CreateTableQuery primaryKey(String column) {
		primaryKey = column;
		return this;
	}

}
