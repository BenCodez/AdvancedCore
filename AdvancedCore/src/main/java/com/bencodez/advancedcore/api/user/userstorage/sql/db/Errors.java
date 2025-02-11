package com.bencodez.advancedcore.api.user.userstorage.sql.db;

public class Errors {
	public static String noSQLConnection() {
		return "Unable to retreive MYSQL connection: ";
	}

	public static String noTableFound() {
		return "DB Error: No Table Found";
	}

	public static String sqlConnectionClose() {
		return "Failed to close MySQL connection: ";
	}

	public static String sqlConnectionExecute() {
		return "Couldn't execute MySQL statement: ";
	}
}