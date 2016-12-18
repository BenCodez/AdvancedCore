package com.Ben12345rocks.AdvancedCore.sql.db;

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