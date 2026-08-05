package com.bencodez.advancedcore.tests.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

class UserTablePreparedStatementTest {

	private static final String UUID = "00000000-0000-0000-0000-000000000001";

	private Connection connection;
	private PreparedStatement statement;
	private SQLite sqlite;

	@BeforeEach
	void setUp() throws Exception {
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		sqlite = mock(SQLite.class);
		when(sqlite.getSQLConnection()).thenReturn(connection);
	}

	@Test
	void updateBindsValuesInsteadOfEmbeddingThemInSql() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = spy(new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey));
		table.setSqLite(sqlite);
		doNothing().when(table).checkColumn(any(Column.class));
		doReturn(true).when(table).containsKey(anyString());

		String sql = "UPDATE Users SET `LastVotes`=?, `Points`=? WHERE `uuid`=?";
		when(connection.prepareStatement(sql)).thenReturn(statement);

		String lastVotes = "Site's value with punctuation;//123";
		table.update(primaryKey, Arrays.asList(new Column("LastVotes", new DataValueString(lastVotes)),
				new Column("Points", new DataValueInt(25))));

		verify(connection).prepareStatement(sql);
		verify(statement).setString(1, lastVotes);
		verify(statement).setInt(2, 25);
		verify(statement).setString(3, UUID);
		verify(statement).executeUpdate();
	}

	@Test
	void getUuidBindsPlayerName() throws Exception {
		Column primaryKey = new Column("uuid", new DataValueString(UUID));
		UserTable table = new UserTable(mock(AdvancedCorePlugin.class), "Users",
				Collections.singletonList(primaryKey), primaryKey);
		table.setSqLite(sqlite);

		ResultSet resultSet = mock(ResultSet.class);
		String sql = "SELECT uuid FROM Users WHERE PlayerName=?;";
		when(connection.prepareStatement(sql)).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(false);

		table.getUUID("O'Brien");

		verify(statement).setString(1, "O'Brien");
		verify(statement).executeQuery();
	}
}
