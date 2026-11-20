package com.bencodez.advancedcore.api.user.userstorage.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

public class UserTable extends com.bencodez.simpleapi.sql.sqlite.Table {
	/** A rejected insert whose original row identity is no longer safe to recreate. */
	public static final class StaleRowWriteException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public StaleRowWriteException(String message) { super(message); }
	}
	private static final ThreadLocal<Boolean> STRICT_ROW_READ = new ThreadLocal<>();
	static String quoteIdentifier(String identifier) {
		if (identifier == null) {
			throw new IllegalArgumentException("SQL identifier cannot be null");
		}
		return "`" + identifier.replace("`", "``") + "`";
	}

	private List<Column> columns = new ArrayList<>();
	private String name;
	private Object object = new Object();

	private Column primaryKey;

	private SQLite sqLite;
	private AdvancedCorePlugin plugin;
	private volatile java.sql.Connection guardedConnection;
	private final String directGuardName = "advancedcore_direct_" + Integer.toHexString(System.identityHashCode(this));
	private final org.sqlite.SQLiteCommitListener directCommitListener = new org.sqlite.SQLiteCommitListener() {
		@Override public void onCommit() { com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectCommit(directCommitListener); }
		@Override public void onRollback() { com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectRollback(directCommitListener); }
	};
	/** Identity of this table's SQLite commit observation, without opening storage. */
	public Object legacyDirectProviderIdentity() { return directCommitListener; }
	/** Subclasses must explicitly opt in before replay transactions bypass their overrides. */
	public boolean supportsAtomicReplayTransactions() { return getClass() == UserTable.class; }

	private boolean ensureDirectGuard(java.sql.Connection connection) throws java.sql.SQLException {
		java.sql.Connection nativeConnection = connection;
		if (!(nativeConnection instanceof org.sqlite.SQLiteConnection)) {
			try { nativeConnection = connection.unwrap(org.sqlite.SQLiteConnection.class); }
			catch (java.sql.SQLException unavailable) { return false; }
			if (nativeConnection == null) return false;
		}
		if (guardedConnection == nativeConnection && !nativeConnection.isClosed()) return true;
		org.sqlite.Function.create(nativeConnection, directGuardName, new org.sqlite.Function() {
			@Override protected void xFunc() throws java.sql.SQLException {
				result(com.bencodez.advancedcore.api.user.usercache.UserDataCache.observeSqliteDirectKey(value_text(0), value_text(1)) ? 1 : 0);
			}
		});
		((org.sqlite.SQLiteConnection) nativeConnection).addCommitListener(directCommitListener);
		guardedConnection = nativeConnection;
		return true;
	}

	private static String sqlLiteral(String value) { return "'" + value.replace("'", "''") + "'"; }
	private String directGuard(String uuid, String key) { return directGuardName + "(" + sqlLiteral(uuid) + "," + sqlLiteral(key) + ")"; }
	private void appendBalancedGuard(StringBuilder result, List<Column> columns, String uuid, String operator, int start, int end) {
		if (end - start == 1) { result.append(directGuard(uuid, columns.get(start).getName())); return; }
		int middle = start + (end - start) / 2;
		result.append('('); appendBalancedGuard(result, columns, uuid, operator, start, middle);
		result.append(operator); appendBalancedGuard(result, columns, uuid, operator, middle, end); result.append(')');
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Collection<Column> columns) {
		this.name = name;
		this.columns.addAll(columns);
		primaryKey = this.columns.get(0);
		this.plugin = plugin;
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Collection<Column> columns, Column primaryKey) {
		this.name = name;
		this.primaryKey = primaryKey;
		this.columns.addAll(columns);
		this.plugin = plugin;
	}

	/**
	 * Streams every user row from SQLite without building a giant in-memory map.
	 *
	 * @param perUser    called once per row with (uuid, columns)
	 * @param onFinished called once at the end with the number of rows processed
	 */
	public void forEachUser(BiConsumer<UUID, ArrayList<Column>> perUser, Consumer<Integer> onFinished) {
		forEachUser(perUser, onFinished, failure -> {
			throw new IllegalStateException("Failed to enumerate SQLite users", failure);
		});
	}

	/** Streams users and reports a terminal SQL failure without claiming completion. */
	public void forEachUser(BiConsumer<UUID, ArrayList<Column>> perUser, Consumer<Integer> onFinished,
			Consumer<Throwable> onFailure) {
		java.util.Objects.requireNonNull(perUser, "perUser");
		java.util.Objects.requireNonNull(onFailure, "onFailure");
		int processed = 0;
		final int pageSize = 500;
		String cursor = null;
		while (true) {
			ArrayList<EnumerationEntry> page = new ArrayList<>(pageSize);
			int rowsThisPage = 0;
			String nextCursor = cursor;
			String query = "SELECT * FROM " + getName() + " WHERE uuid IS NOT NULL"
					+ (cursor == null ? "" : " AND uuid > ?") + " ORDER BY uuid ASC LIMIT ?;";
			try (PreparedStatement ps = sqLite.getSQLConnection().prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY)) {
				int parameter = 1;
				if (cursor != null) ps.setString(parameter++, cursor);
				ps.setInt(parameter, pageSize);
				try (ResultSet rs = ps.executeQuery()) {
					ResultSetMetaData meta = rs.getMetaData();
					int colCount = meta.getColumnCount();
					String[] colNames = new String[colCount + 1];
					byte[] kind = new byte[colCount + 1];
					int uuidIndex = -1;
					for (int i = 1; i <= colCount; i++) {
						String name = meta.getColumnLabel(i);
						colNames[i] = name;
						if ("uuid".equalsIgnoreCase(name)) uuidIndex = i;
						if (plugin.getUserManager().getDataManager().isInt(name)) kind[i] = 1;
						else if (plugin.getUserManager().getDataManager().isBoolean(name)) kind[i] = 2;
					}
					if (uuidIndex < 1) throw new IllegalStateException("SQLite user table has no UUID column");
					while (rs.next()) {
						rowsThisPage++;
						String uuidString = rs.getString(uuidIndex);
						nextCursor = uuidString;
						UUID uuid = null;
						if (uuidString != null && !uuidString.isEmpty() && !"null".equalsIgnoreCase(uuidString)) {
							try { uuid = UUID.fromString(uuidString); }
							catch (IllegalArgumentException ignored) { }
						}
						if (uuid == null) continue;
						ArrayList<Column> columns = new ArrayList<>(colCount);
						for (int i = 1; i <= colCount; i++) {
							String columnName = colNames[i];
							Column column;
							if (kind[i] == 1) {
								column = new Column(columnName, DataType.INTEGER);
								int value;
								try { value = rs.getInt(i); if (rs.wasNull()) value = 0; }
								catch (Exception failure) {
									String data = rs.getString(i);
									try { value = data == null ? 0 : Integer.parseInt(data); }
									catch (NumberFormatException invalid) { value = 0; }
								}
								column.setValue(new DataValueInt(value));
							} else if (kind[i] == 2) {
								column = new Column(columnName, DataType.BOOLEAN);
								column.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
							} else {
								column = new Column(columnName, DataType.STRING);
								column.setValue(new DataValueString(rs.getString(i)));
							}
							columns.add(column);
						}
						page.add(new EnumerationEntry(uuid, columns));
					}
				}
			} catch (SQLException | RuntimeException failure) {
				onFailure.accept(failure);
				return;
			}
			for (EnumerationEntry entry : page) {
				try { perUser.accept(entry.uuid(), entry.columns()); processed++; }
				catch (Throwable failure) { onFailure.accept(failure); return; }
			}
			if (rowsThisPage < pageSize) break;
			if (nextCursor == null || java.util.Objects.equals(cursor, nextCursor)) {
				onFailure.accept(new IllegalStateException("SQLite user enumeration did not advance its UUID cursor"));
				return;
			}
			cursor = nextCursor;
		}
		if (onFinished != null) {
			onFinished.accept(processed);
		}
	}

	private record EnumerationEntry(UUID uuid, ArrayList<Column> columns) { }

	public UserTable(AdvancedCorePlugin plugin, String name, Column... columns) {
		this.name = name;
		for (Column column : columns) {
			this.columns.add(column);
		}
		primaryKey = this.columns.get(0);
		this.plugin = plugin;
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Column primaryKey, Column... columns) {
		this.name = name;
		this.primaryKey = primaryKey;
		for (Column column : columns) {
			this.columns.add(column);
		}
		this.plugin = plugin;
	}

	public void addColoumn(Column column) {
		if (hasColumn(column)) {
			return;
		}
		try {
			String query = "ALTER TABLE " + quoteIdentifier(getName()) + " ADD COLUMN " + quoteIdentifier(column.getName()) + " "
					+ column.getDataType().toString();
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			s.executeUpdate();
			s.close();
			columns.add(column);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addColoumn(UserDataKey column) {
		if (getTableColumns().contains(column.getKey())) {
			return;
		}
		try {
			String query = "ALTER TABLE " + quoteIdentifier(getName()) + " ADD COLUMN " + quoteIdentifier(column.getKey()) + " "
					+ column.getColumnType();
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			s.executeUpdate();
			s.close();
			if (column instanceof UserDataKeyInt) {
				columns.add(new Column(column.getKey(), DataType.INTEGER));
			} else {
				columns.add(new Column(column.getKey(), DataType.STRING));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addCustomColumns() {
		// add custom column types
		for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
			addColoumn(key);
		}
	}

	public void checkColumn(Column c) {
		if (!hasColumn(c)) {
			Column col = new Column(c.getName(), c.getDataType());
			addColoumn(col);
		}
	}

	public void checkColumns() {
		for (String col : getTableColumns()) {
			boolean has = false;
			for (Column column : columns) {
				if (col != null) {
					if (col.equals(column.getName())) {
						has = true;
					}
				}
			}
			if (!has) {
				columns.add(new Column(col, DataType.STRING));
			}
		}
	}

	public boolean containsKey(String index) {
		String query = "SELECT uuid FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */
			while (rs.next()) {
				String str = rs.getString("uuid");
				if (str != null && str.equals(index)) {
					rs.close();
					s.close();
					return true;
				}
			}
			rs.close();

		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public void copyColumnData(String columnFromName, String columnToName, DataType dataType) {
		checkColumn(new Column(columnToName, dataType));
		checkColumn(new Column(columnFromName, dataType));
		String sql = "UPDATE " + quoteIdentifier(getName()) + " SET " + quoteIdentifier(columnToName) + " = "
				+ quoteIdentifier(columnFromName) + ";";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sql);
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void delete(Column column) {
		if (column.getName().equalsIgnoreCase(primaryKey.getName())) {
			String query = "DELETE FROM " + getName() + " WHERE `" + column.getName() + "`=?";
			try {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				if (column.getValue().isString()) {
					s.setString(1, column.getValue().getString());
				} else if (column.getValue().isInt()) {
					s.setInt(1, column.getValue().getInt());
				} else {
					s.setBoolean(1, column.getValue().getBoolean());
				}
				s.executeUpdate();
				s.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Primary key must be used!");
		}
	}

	public void executeQuery(String str) {
		try {
			PreparedStatement s = sqLite.getSQLConnection()
					.prepareStatement(PlaceholderUtils.replacePlaceHolder(str, "tablename", getName()));
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public List<List<Column>> getAll() {
		List<List<Column>> results = new ArrayList<>();
		String query = "SELECT * FROM " + getName();
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				List<Column> result = new ArrayList<>();
				for (int i = 0; i < getColumns().size(); i++) {
					Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).getDataType(),
							getColumns().get(i).getLimit());
					if (getColumns().get(i).getValue().isString()) {
						s.setString(1, getColumns().get(i).getValue().getString());
					} else if (getColumns().get(i).getValue().isInt()) {
						s.setInt(1, getColumns().get(i).getValue().getInt());
					} else {
						s.setBoolean(1, getColumns().get(i).getValue().getBoolean());
					}
					result.add(rCol);
				}
				results.add(result);
			}
			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}

	public HashMap<UUID, ArrayList<Column>> getAllQuery() {
		HashMap<UUID, ArrayList<Column>> result = new HashMap<>();
		String query = "SELECT * FROM " + getName() + ";";

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();

			while (rs.next()) {
				ArrayList<Column> cols = new ArrayList<>();
				UUID uuid = null;
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;

					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						try {
							rCol = new Column(columnName, DataType.INTEGER);
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
							rCol = new Column(columnName, DataType.INTEGER);
							String data = rs.getString(i);
							if (data != null) {
								try {
									rCol.setValue(new DataValueInt(Integer.parseInt(data)));
								} catch (NumberFormatException ex) {
									rCol.setValue(new DataValueInt(0));
								}
							} else {
								rCol.setValue(new DataValueInt(0));
							}
						}
					} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
						rCol = new Column(columnName, DataType.BOOLEAN);
						rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
					} else {
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
						if (columnName.equals("uuid")) {
							uuid = UUID.fromString(rs.getString(i));
						}
					}
					cols.add(rCol);
				}
				result.put(uuid, cols);
			}
			rs.close();
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		return result;
	}

	public List<Column> getColumns() {
		return columns;
	}

	public List<String> getColumnsString() {
		List<Column> column = getColumns();
		ArrayList<String> list = new ArrayList<>();
		for (Column col : column) {
			list.add(col.getName());
		}
		return list;
	}

	public ArrayList<Column> getExact(Column column) {
		return getExact(column, Boolean.TRUE.equals(STRICT_ROW_READ.get()));
	}

	/** Shared storage must not turn a failed row read into an empty snapshot. */
	public ArrayList<Column> getExactStrict(Column column) {
		Boolean previous = STRICT_ROW_READ.get();
		STRICT_ROW_READ.set(Boolean.TRUE);
		try { return getExact(column); }
		finally { if (previous == null) STRICT_ROW_READ.remove(); else STRICT_ROW_READ.set(previous); }
	}

	private ArrayList<Column> getExact(Column column, boolean strict) {
		ArrayList<Column> result = new ArrayList<>();

		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		try {
			synchronized (object) {
				try (PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query)) {
					if (column.getValue().isString()) {
						s.setString(1, column.getValue().getString());
					} else if (column.getValue().isInt()) {
						s.setInt(1, column.getValue().getInt());
					} else {
						s.setBoolean(1, column.getValue().getBoolean());
					}
					try (ResultSet rs = s.executeQuery()) {
						if (rs.next()) {
							for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
								String columnName = rs.getMetaData().getColumnLabel(i);
								Column rCol = null;
								if (plugin.getUserManager().getDataManager().isInt(columnName)) {
									rCol = new Column(columnName, DataType.INTEGER);
									rCol.setValue(new DataValueInt(rs.getInt(i)));
								} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
									rCol = new Column(columnName, DataType.BOOLEAN);
									rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
								} else {
									rCol = new Column(columnName, DataType.STRING);
									rCol.setValue(new DataValueString(rs.getString(i)));
								}
								result.add(rCol);
							}
						}
					}
				}
				return result;
			}

		} catch (SQLException e) {
			if (strict) throw new IllegalStateException("Failed to read SQLite user row", e);
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			if (strict) throw new IllegalStateException("Failed to read SQLite user row", e);
		}

		for (Column col : getColumns()) {
			result.add(new Column(col.getName(), col.getDataType()));
		}
		return result;
	}

	@Override
	public String getName() {
		return name;
	}

	public ArrayList<String> getNames() {
		ArrayList<String> names = new ArrayList<>();
		for (Column col : getRowsNames()) {
			if (col.getValue() != null && col.getValue().isString()) {
				names.add(col.getValue().getString());
			}
		}
		return names;
	}

	public ArrayList<Integer> getNumbersInColumn(String column) {
		ArrayList<Integer> result = new ArrayList<>();
		String sqlStr = "SELECT " + quoteIdentifier(column) + " FROM " + quoteIdentifier(getName()) + ";";

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sqlStr);
			ResultSet rs = s.executeQuery();

			while (rs.next()) {
				result.add(rs.getInt(column));
			}

			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public Column getPrimaryKey() {
		return primaryKey;
	}

	@Override
	public String getQuery() {
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37), ";
		// add custom column types
		for (UserDataKey key : AdvancedCorePlugin.getInstance().getUserManager().getDataManager().getKeys()) {
			sql += key.getKey() + " " + key.getColumnType() + ", ";
		}
		sql += "PRIMARY KEY ( uuid ));";
		return sql;
	}

	public List<Column> getRows() {
		List<Column> result = new ArrayList<>();
		String query = "SELECT uuid FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("uuid", new DataValueString(rs.getString("uuid")));
					result.add(rCol);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				s.close();
				rs.close();
				e.printStackTrace();
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public List<Column> getRowsNames() {
		checkColumn(new Column("PlayerName", DataType.STRING));
		List<Column> result = new ArrayList<>();
		String query = "SELECT PlayerName FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("PlayerName", new DataValueString(rs.getString("PlayerName")));
					result.add(rCol);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				s.close();
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public ArrayList<String> getTableColumns() {
		ArrayList<String> columns = new ArrayList<>();
		String query = "SELECT * FROM " + getName();
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			ResultSetMetaData metadata = rs.getMetaData();
			int columnCount = metadata.getColumnCount();

			for (int i = 1; i <= columnCount; i++) {
				String columnName = metadata.getColumnName(i);

				columns.add(columnName);
			}

			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return columns;
	}

	public String getUUID(String playerName) {
		try {
			return getUUIDOrThrow(playerName);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} catch (ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}

	/** Query a persisted UUID while allowing authoritative lookup failures to propagate. */
	public String getUUIDOrThrow(String playerName) throws SQLException {
		String query = "SELECT uuid FROM " + getName() + " WHERE PlayerName=?;";

		try (PreparedStatement sql = sqLite.getSQLConnection().prepareStatement(query)) {
			sql.setString(1, playerName);
			ResultSet rs = sql.executeQuery();
			/*
			 * Query sql = new Query(mysql, query); ResultSet rs = sql.executeQuery();
			 */
			if (rs.next()) {
				String uuid = rs.getString("uuid");
				if (uuid != null && !uuid.isEmpty()) {
					rs.close();
					return uuid;
				}
			}
			rs.close();
		}
		return null;
	}

	public boolean hasColumn(Column column) {
		return getTableColumns().contains(column.getName());
	}

	private static boolean sameSqliteIdentifier(String left, String right) {
		return com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey(left)
				.equals(com.bencodez.advancedcore.api.user.usercache.UserDataCache.sqliteDirectKey(right));
	}

	public void insert(List<Column> columns) {
		for (Column c : columns) {
			checkColumn(c);
		}
		String targetId = null;
		for (Column column : columns) {
			if (targetId == null && sameSqliteIdentifier(column.getName(), primaryKey.getName()) && column.getValue() != null && column.getValue().isString())
				targetId = column.getValue().getString();
		}
		final String rowId = targetId;
		boolean legacyBatch = com.bencodez.advancedcore.api.user.usercache.UserDataCache.hasLegacyBatchPersistence(targetId);
		boolean direct = com.bencodez.advancedcore.api.user.usercache.UserDataCache.hasLegacyDirectPersistence();
		if (direct && targetId != null) com.bencodez.advancedcore.api.user.usercache.UserDataCache.bindLegacyDirectRow(targetId, true);
		if (direct) com.bencodez.advancedcore.api.user.usercache.UserDataCache.classifySqliteDirectStatement(true);
		if (direct) com.bencodez.advancedcore.api.user.usercache.UserDataCache.setSqliteMoveDestination(targetId, null);
		try {
			java.sql.Connection connection = sqLite.getSQLConnection();
			boolean guarded = direct && targetId != null && ensureDirectGuard(connection);
			boolean directStatement = guarded;
			java.util.List<Column> mutable = new ArrayList<>();
			for (Column column : columns) if (!sameSqliteIdentifier(column.getName(), primaryKey.getName())) mutable.add(column);
			guarded = guarded && !mutable.isEmpty();
			boolean guardedPrimaryOnly = directStatement && mutable.isEmpty();
			StringBuilder query = new StringBuilder(guarded ? "INSERT INTO " : legacyBatch || (directStatement && mutable.isEmpty()) ? "INSERT OR IGNORE INTO " : "INSERT OR REPLACE INTO ");
			query.append(quoteIdentifier(getName())).append(" (");
			for (int i = 0; i < columns.size(); i++) { if (i != 0) query.append(", "); query.append(quoteIdentifier(columns.get(i).getName())); }
			query.append(guarded || guardedPrimaryOnly ? ") SELECT " : ") VALUES (");
			for (int i = 0; i < columns.size(); i++) { if (i != 0) query.append(", "); query.append("?"); }
			if (guarded) {
				query.append(" WHERE ").append(directGuard(rowId, primaryKey.getName())).append(" AND ("); appendBalancedGuard(query, mutable, rowId, " OR ", 0, mutable.size());
				query.append(") AND (("); appendBalancedGuard(query, mutable, rowId, " AND ", 0, mutable.size());
				query.append(") OR EXISTS (SELECT 1 FROM ")
					.append(quoteIdentifier(getName())).append(" WHERE ").append(quoteIdentifier(primaryKey.getName())).append("=").append(sqlLiteral(targetId)).append("))")
					.append(" ON CONFLICT(").append(quoteIdentifier(primaryKey.getName())).append(") DO UPDATE SET ");
				for (int i = 0; i < mutable.size(); i++) {
					if (i != 0) query.append(", ");
					Column column = mutable.get(i); String name = quoteIdentifier(column.getName());
					query.append(name).append("=CASE WHEN ").append(directGuard(rowId, column.getName()))
						.append(" THEN excluded.").append(name).append(" ELSE ").append(quoteIdentifier(getName())).append(".").append(name).append(" END");
				}
			} else if (guardedPrimaryOnly) query.append(" WHERE ").append(directGuard(rowId, primaryKey.getName()));
			else query.append(")");
			try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
				for (int i = 0; i < columns.size(); i++) {
					if (columns.get(i).getValue() == null) statement.setString(i + 1, "");
					else bindValue(statement, i + 1, columns.get(i));
				}
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectStaleLegacyBatch(targetId);
				int inserted;
				if (directStatement) com.bencodez.advancedcore.api.user.usercache.UserDataCache.beginSqliteDirectExecution(true, legacyDirectProviderIdentity());
				try { inserted = statement.executeUpdate(); }
				finally { if (directStatement) com.bencodez.advancedcore.api.user.usercache.UserDataCache.endSqliteDirectExecution(); }
				if (legacyBatch && inserted == 0) com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectLegacyBatchInsertConflict(targetId);
				if (legacyBatch && inserted > 0) com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyBatchProviderCommit(targetId, columns.stream().map(Column::getName).toList(), com.bencodez.advancedcore.api.user.UserStorage.SQLITE, false);
				if (directStatement && !guarded && inserted == 0 && com.bencodez.advancedcore.api.user.usercache.UserDataCache.hasAdmittedSqliteDirectKey(primaryKey.getName())
						&& com.bencodez.advancedcore.api.user.usercache.UserDataCache.isCurrentLegacyDirectKey(rowId, primaryKey.getName()))
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(targetId, new IllegalStateException("SQLite primary-only insert encountered an existing row"));
				if (guarded && inserted == 0 && mutable.stream().anyMatch(column -> com.bencodez.advancedcore.api.user.usercache.UserDataCache.isCurrentLegacyDirectKey(rowId, column.getName()))) {
					StaleRowWriteException failure = new StaleRowWriteException("SQLite direct write could not safely recreate a partially superseded row");
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(targetId, failure);
					throw failure;
				}
			}
		} catch (SQLException e) {
			com.bencodez.advancedcore.api.user.usercache.UserDataCache.propagateLegacyBatchWriteFailure(targetId, e);
			com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(targetId, e);
			e.printStackTrace();
		}
	}

	public List<List<Column>> search(Column column) {
		List<List<Column>> results = new ArrayList<>();
		if (column.getName().equalsIgnoreCase(primaryKey.getName())) {
			return null;
		}
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			if (column.getValue().isString()) {
				s.setString(1, column.getValue().getString());
			} else if (column.getValue().isInt()) {
				s.setInt(1, column.getValue().getInt());
			} else {
				s.setBoolean(1, column.getValue().getBoolean());
			}

			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				List<Column> result = new ArrayList<>();
				for (int i = 0; i < getColumns().size(); i++) {
					Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).getDataType(),
							getColumns().get(i).getLimit());

					if (getColumns().get(i).getValue().isString()) {
						rCol.setValue(new DataValueString(rs.getString(i + 1)));
					} else if (getColumns().get(i).getValue().isInt()) {
						rCol.setValue(new DataValueInt(rs.getInt(i + 1)));
					} else if (getColumns().get(i).getValue().isBoolean()) {
						rCol.setValue(new DataValueBoolean(rs.getBoolean(i + 1)));
					}
					result.add(rCol);
				}
				results.add(result);
			}
			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}

	public void setColumns(List<Column> columns) {
		this.columns = columns;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPrimaryKey(Column primaryKey) {
		this.primaryKey = primaryKey;
	}

	@Override
	public void setSqLite(SQLite sqLite) {
		this.sqLite = sqLite;
	}

	public void update(Column primaryKey, List<Column> columns) {
		for (Column c : columns) {
			checkColumn(c);
		}
		if (columns.isEmpty()) {
			return;
		}
		if (containsKey(primaryKey.getValue().toString())) {
			synchronized (object) {
				columns = com.bencodez.advancedcore.api.user.usercache.UserDataCache.filterConfirmedLegacyBatchColumns(primaryKey.getValue().getString(), columns, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, legacyDirectProviderIdentity());
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectStaleLegacyBatch(primaryKey.getValue().getString());
				if (columns.isEmpty()) return;
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.classifySqliteDirectStatement(false);
				java.sql.Connection connection = sqLite.getSQLConnection();
				boolean guarded = false;
				try { guarded = com.bencodez.advancedcore.api.user.usercache.UserDataCache.hasLegacyDirectPersistence() && ensureDirectGuard(connection); }
				catch (SQLException failure) {
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(primaryKey.getValue().getString(), failure);
					failure.printStackTrace();
					return;
				}
				StringBuilder query = new StringBuilder("UPDATE ").append(getName()).append(" SET ");
				String moveDestination = null;
				for (Column column : columns) if (sameSqliteIdentifier(column.getName(), primaryKey.getName()) && column.getValue() != null && column.getValue().isString()) moveDestination = column.getValue().getString();
				if (guarded) com.bencodez.advancedcore.api.user.usercache.UserDataCache.setSqliteMoveDestination(primaryKey.getValue().getString(), moveDestination);
				for (int i = 0; i < columns.size(); i++) {
					String name = quoteIdentifier(columns.get(i).getName());
					query.append(name).append("=");
					if (guarded)
						query.append("CASE WHEN ").append(directGuard(primaryKey.getValue().getString(), columns.get(i).getName())).append(" THEN ? ELSE ").append(name).append(" END");
					else query.append("?");
					if (i != columns.size() - 1) {
						query.append(", ");
					}
				}
				query.append(" WHERE `").append(primaryKey.getName()).append("`=?");
				if (guarded) query.append(" AND ").append(directGuard(primaryKey.getValue().getString(), primaryKey.getName()));
				try (PreparedStatement s = connection.prepareStatement(query.toString())) {
					int parameter = 1;
					for (Column column : columns) {
						bindValue(s, parameter++, column);
					}
					bindValue(s, parameter, primaryKey);
					if (guarded) com.bencodez.advancedcore.api.user.usercache.UserDataCache.beginSqliteDirectExecution(false, legacyDirectProviderIdentity());
					int updated;
					try { updated = s.executeUpdate(); }
					finally { if (guarded) com.bencodez.advancedcore.api.user.usercache.UserDataCache.endSqliteDirectExecution(); }
					if (updated == 0) {
						String row = primaryKey.getValue().getString();
						StaleRowWriteException failure = new StaleRowWriteException("SQLite update target disappeared before the write");
						com.bencodez.advancedcore.api.user.usercache.UserDataCache.propagateLegacyBatchWriteFailure(row, failure);
						com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(row, failure);
					}
					else com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyBatchProviderCommit(primaryKey.getValue().getString(), columns.stream().map(Column::getName).toList(), com.bencodez.advancedcore.api.user.UserStorage.SQLITE, false);
				} catch (SQLException e) {
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.propagateLegacyBatchWriteFailure(primaryKey.getValue().getString(), e);
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.recordLegacyDirectWriteFailure(primaryKey.getValue().getString(), e);
					e.printStackTrace();
				}
			}
		} else {
			String row = primaryKey.getValue().getString();
			long claim = com.bencodez.advancedcore.api.user.usercache.UserDataCache.confirmedLegacyBatchClaimSerial(row);
			columns = com.bencodez.advancedcore.api.user.usercache.UserDataCache.filterConfirmedLegacyBatchColumns(row, columns, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, legacyDirectProviderIdentity());
			com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectStaleLegacyBatch(row);
			if (claim != com.bencodez.advancedcore.api.user.usercache.UserDataCache.confirmedLegacyBatchClaimSerial(row)) {
				if (!containsKey(row)) com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectLegacyBatchInsertConflict(row);
				if (!columns.isEmpty()) update(primaryKey, columns);
				return;
			}
			boolean addPrimary = true;
			for (Column col : columns) {
				if (col.getName().equals("uuid")) {
					addPrimary = false;
				}
			}
			List<Column> insertColumns = new ArrayList<>(columns);
			if (addPrimary) insertColumns.add(primaryKey);
			try {
				insert(insertColumns);
			} catch (com.bencodez.advancedcore.api.user.usercache.UserDataCache.StaleLegacyBatchException stale) {
				claim = com.bencodez.advancedcore.api.user.usercache.UserDataCache.confirmedLegacyBatchClaimSerial(row);
				List<Column> remaining = com.bencodez.advancedcore.api.user.usercache.UserDataCache.filterConfirmedLegacyBatchColumns(row, columns, com.bencodez.advancedcore.api.user.UserStorage.SQLITE, legacyDirectProviderIdentity());
				if (claim == com.bencodez.advancedcore.api.user.usercache.UserDataCache.confirmedLegacyBatchClaimSerial(row)) throw stale;
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.rejectStaleLegacyBatch(row);
				if (!containsKey(row)) throw stale;
				if (!remaining.isEmpty()) update(primaryKey, remaining);
			}
		}
	}

	private void bindValue(PreparedStatement statement, int parameter, Column column) throws SQLException {
		if (column.getValue() == null) {
			statement.setObject(parameter, null);
		} else if (column.getValue().isString()) {
			statement.setString(parameter, column.getValue().getString());
		} else if (column.getValue().isBoolean()) {
			statement.setBoolean(parameter, column.getValue().getBoolean());
		} else if (column.getValue().isInt()) {
			statement.setInt(parameter, column.getValue().getInt());
		} else {
			statement.setObject(parameter, column.getValue().toString());
		}
	}

	public void wipeColumnData(String columnName, DataType dataType) {
		checkColumn(new Column(columnName, dataType));
		String sql = "UPDATE " + quoteIdentifier(getName()) + " SET " + quoteIdentifier(columnName) + " = "
				+ dataType.getNoValue() + ";";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sql);
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SQLite getSqLite() {
		return sqLite;
	}

}
