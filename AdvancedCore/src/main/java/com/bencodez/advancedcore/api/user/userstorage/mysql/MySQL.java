package com.bencodez.advancedcore.api.user.userstorage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.mysql.AbstractSqlTable;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfigSpigot;
import com.bencodez.simpleapi.sql.mysql.queries.Query;

import lombok.Getter;

public class MySQL extends AbstractSqlTable {

	private final AdvancedCorePlugin plugin;

	@Getter
	private long lastBackgroundCheck = 0;

	private boolean useBatchUpdates = true;

	// Keep these caches like the original class did
	private final Set<String> names = ConcurrentHashMap.newKeySet();
	private final Set<String> uuids = ConcurrentHashMap.newKeySet();

	public MySQL(AdvancedCorePlugin plugin, String tableName, ConfigurationSection section) {
		super(tableName, new MysqlConfigSpigot(section), plugin.getOptions().getDebug().isDebug(), true);
		this.plugin = plugin;

		init();

		loadData();
		plugin.debug("UseBatchUpdates: " + isUseBatchUpdates());

		// old behavior: ensure uuid type is best-case for db
		alterColumnType("uuid", bestUuidType());
	}

	// -------------------------
	// Required AbstractSqlTable hooks
	// -------------------------

	@Override
	public String getPrimaryKeyColumn() {
		return "uuid";
	}

	@Override
	public String buildCreateTableSql(DbType dbType) {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE IF NOT EXISTS ").append(qi(tableName)).append(" (");

		// best-case uuid type for Postgres
		sb.append(qi("uuid")).append(" ").append(bestUuidType()).append(", ");

		for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
			sb.append(qi(key.getKey())).append(" ").append(normaliseTypeForDb(key.getColumnType())).append(", ");
		}

		sb.append("PRIMARY KEY (").append(qi("uuid")).append("));");
		return sb.toString();
	}

	@Override
	public void logSevere(String msg) {
		plugin.getLogger().severe(msg);
	}

	@Override
	public void logInfo(String msg) {
		plugin.getLogger().info(msg);
	}

	@Override
	public void debug(Throwable t) {
		plugin.debug(t);
	}

	@Override
	public void debug(String msg) {
		plugin.debug(msg);
	}

	// -------------------------
	// Cache / lifecycle
	// -------------------------

	public boolean isUseBatchUpdates() {
		return useBatchUpdates;
	}

	public void loadData() {
		try (Connection con = mysql.getConnectionManager().getConnection()) {
			useBatchUpdates = con != null && con.getMetaData().supportsBatchUpdates();
		} catch (SQLException e) {
			debug(e);
		}

		// superclass caches (columns + primaryKeys)
		clearCaches();

		// keep local caches in sync (existing behavior)
		uuids.clear();
		uuids.addAll(getUuidsQuery());

		names.clear();
		names.addAll(getNamesQuery());
	}

	public void clearCacheBasic() {
		clearCaches();
		uuids.clear();
		uuids.addAll(getUuidsQuery());
		names.clear();
		names.addAll(getNamesQuery());
	}

	// -------------------------
	// Existence / contains
	// -------------------------

	public boolean containsKey(String uuid) {
		return getUuids().contains(uuid) || containsKeyQuery(uuid);
	}

	@Override
	public boolean containsKeyQuery(String uuid) {
		// AbstractSqlTable containsKeyQuery uses ps.setString.
		// For Postgres UUID column, best-case is to do an explicit cast here.
		if (dbType == DbType.POSTGRESQL) {
			try {
				UUID.fromString(uuid);
			} catch (Exception e) {
				return false;
			}
			String sql = "SELECT 1 FROM " + qi(tableName) + " WHERE " + qi("uuid") + " = ?::uuid LIMIT 1;";
			try (Connection conn = mysql.getConnectionManager().getConnection();
					PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, uuid);
				try (ResultSet rs = ps.executeQuery()) {
					return rs.next();
				}
			} catch (SQLException e) {
				debug(e);
				return false;
			}
		}

		return super.containsKeyQuery(uuid);
	}

	public boolean containsUUID(String uuid) {
		return uuids.contains(uuid);
	}

	// -------------------------
	// Keep existing methods (getUuids / getUUID / etc.)
	// -------------------------

	public Set<String> getUuids() {
		if (uuids == null || uuids.isEmpty()) {
			uuids.clear();
			uuids.addAll(getUuidsQuery());
		}
		return uuids;
	}

	public ArrayList<String> getUuidsQuery() {
		ArrayList<String> out = new ArrayList<>();

		ArrayList<Column> rows = getRowsQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					out.add(c.getValue().getString());
				}
			}
		} else {
			plugin.getLogger().severe("Failed to fetch uuids");
		}

		return out;
	}

	public Set<String> getNames() {
		if (names == null || names.isEmpty()) {
			names.clear();
			names.addAll(getNamesQuery());
		}
		return names;
	}

	public ArrayList<String> getNamesQuery() {
		ArrayList<String> out = new ArrayList<>();

		checkColumn("PlayerName", DataType.STRING);
		ArrayList<Column> rows = getRowsNameQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					String v = c.getValue().getString();
					if (v != null) {
						out.add(v);
					}
				}
			}
		}

		return out;
	}

	public String getUUID(String playerName) {
		String query = "SELECT " + qi("uuid") + " FROM " + qi(tableName) + " WHERE " + qi("PlayerName") + "='"
				+ playerName + "';";
		plugin.devDebug("DB QUERY: " + query);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query);
				ResultSet rs = sql.executeQuery()) {

			if (rs.next()) {
				if (dbType == DbType.POSTGRESQL) {
					Object obj = rs.getObject(1);
					if (obj instanceof java.util.UUID) {
						return ((java.util.UUID) obj).toString();
					}
				}
				String uuid = rs.getString(1);
				if (uuid != null && !uuid.isEmpty()) {
					return uuid;
				}
			}
		} catch (SQLException e) {
			debug(e);
		}
		return null;
	}

	public ArrayList<Integer> getNumbersInColumn(String column) {
		ArrayList<Integer> result = new ArrayList<>();
		String sqlStr = "SELECT " + qi(column) + " FROM " + qi(tableName) + ";";
		plugin.devDebug("DB QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				result.add(rs.getInt(1));
			}
		} catch (SQLException e) {
			debug(e);
		}

		return result;
	}

	public ArrayList<Column> getRowsNameQuery() {
		ArrayList<Column> result = new ArrayList<>();
		String sqlStr = "SELECT " + qi("PlayerName") + " FROM " + qi(tableName) + ";";
		plugin.devDebug("DB QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				result.add(new Column("PlayerName", new DataValueString(rs.getString(1))));
			}
		} catch (SQLException e) {
			debug(e);
		}

		return result;
	}

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<>();
		String sqlStr = "SELECT " + qi("uuid") + " FROM " + qi(tableName) + ";";
		plugin.devDebug("DB QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				if (dbType == DbType.POSTGRESQL) {
					Object obj = rs.getObject(1);
					if (obj instanceof java.util.UUID) {
						result.add(new Column("uuid", new DataValueString(((java.util.UUID) obj).toString())));
					} else {
						result.add(new Column("uuid", new DataValueString(rs.getString(1))));
					}
				} else {
					result.add(new Column("uuid", new DataValueString(rs.getString(1))));
				}
			}
		} catch (SQLException e) {
			debug(e);
			return null;
		}

		return result;
	}

	public ConcurrentHashMap<UUID, String> getRowsUUIDNameQuery() {
		ConcurrentHashMap<UUID, String> uuidNames = new ConcurrentHashMap<>();
		String sqlStr = "SELECT " + qi("uuid") + ", " + qi("PlayerName") + " FROM " + qi(tableName) + ";";
		plugin.devDebug("DB QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				String playerName = rs.getString(2);
				if (playerName == null || playerName.isEmpty()) {
					continue;
				}

				UUID uuid;
				if (dbType == DbType.POSTGRESQL) {
					Object obj = rs.getObject(1);
					if (obj instanceof java.util.UUID) {
						uuid = (java.util.UUID) obj;
					} else {
						String s = rs.getString(1);
						if (s == null || s.isEmpty() || "null".equalsIgnoreCase(s)) {
							continue;
						}
						uuid = UUID.fromString(s);
					}
				} else {
					String s = rs.getString(1);
					if (s == null || s.isEmpty() || "null".equalsIgnoreCase(s)) {
						continue;
					}
					uuid = UUID.fromString(s);
				}

				uuidNames.put(uuid, playerName);
			}
		} catch (SQLException e) {
			// ignore like original
		}

		return uuidNames;
	}

	// -------------------------
	// Other existing behavior
	// -------------------------

	public void deletePlayer(String uuid) {
		String q;
		if (dbType == DbType.POSTGRESQL) {
			q = "DELETE FROM " + qi(tableName) + " WHERE " + qi("uuid") + "='" + uuid + "'::uuid;";
		} else {
			q = "DELETE FROM " + qi(tableName) + " WHERE " + qi("uuid") + "='" + uuid + "';";
		}
		plugin.devDebug("DB QUERY: " + q);

		try {
			new Query(mysql, q).executeUpdate();
		} catch (SQLException e) {
			debug(e);
		}

		uuids.remove(uuid);
		names.remove(PlayerManager.getInstance()
				.getPlayerName(plugin.getUserManager().getUser(java.util.UUID.fromString(uuid)), uuid));
		clearCacheBasic();
	}

	public void executeQuery(String str) {
		try {
			Query q = new Query(mysql, PlaceholderUtils.replacePlaceHolder(str, "tablename", tableName));
			plugin.devDebug("DB QUERY: " + str);
			q.executeUpdate();
		} catch (SQLException e) {
			debug(e);
		}
	}

	public void executeQueryReturn(String str) {
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(str);
				ResultSet rs = sql.executeQuery()) {
			plugin.devDebug("DB QUERY: " + str);
		} catch (SQLException e) {
			debug(e);
		}
	}

	public HashMap<UUID, ArrayList<Column>> getAllQuery() {
		HashMap<UUID, ArrayList<Column>> result = new HashMap<>();
		String query = "SELECT * FROM " + qi(tableName) + ";";
		plugin.devDebug("DB QUERY: " + query);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				ArrayList<Column> cols = new ArrayList<>();
				UUID uuid = null;

				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol;

					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						rCol = new Column(columnName, DataType.INTEGER);
						try {
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
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
						if (columnName.equalsIgnoreCase("uuid")) {
							if (dbType == DbType.POSTGRESQL) {
								Object obj = rs.getObject(i);
								if (obj instanceof java.util.UUID) {
									uuid = (java.util.UUID) obj;
								} else {
									uuid = UUID.fromString(rs.getString(i));
								}
							} else {
								uuid = UUID.fromString(rs.getString(i));
							}
						}
					}
					cols.add(rCol);
				}

				if (uuid != null) {
					result.put(uuid, cols);
				}
			}
			return result;
		} catch (SQLException | ArrayIndexOutOfBoundsException e) {
			debug(e);
		}

		return result;
	}

	public ArrayList<Column> getExact(String uuid) {
		return getExactQuery(new Column("uuid", new DataValueString(uuid)));
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();

		String query;
		if (dbType == DbType.POSTGRESQL && "uuid".equalsIgnoreCase(column.getName())) {
			query = "SELECT * FROM " + qi(tableName) + " WHERE " + qi("uuid") + "='" + column.getValue().getString()
					+ "'::uuid;";
		} else {
			query = "SELECT * FROM " + qi(tableName) + " WHERE " + qi(column.getName()) + "='"
					+ column.getValue().getString() + "';";
		}

		plugin.devDebug("DB QUERY: " + query);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query);
				ResultSet rs = sql.executeQuery()) {

			if (rs.next()) {
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol;

					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						rCol = new Column(columnName, DataType.INTEGER);
						try {
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
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
					}
					result.add(rCol);
				}
			}
			return result;
		} catch (SQLException | ArrayIndexOutOfBoundsException e) {
			debug(e);
		}

		for (String col : getColumns()) {
			result.add(new Column(col, DataType.STRING));
		}
		return result;
	}

	public void insert(String index, String column, DataValue value) {
		insertQuery(index, Arrays.asList(new Column(column, value)));
	}

	public void insertQuery(String index, List<Column> cols) {
		DbType type = dbType;

		// Ensure columns exist
		for (Column col : cols) {
			checkColumn(col.getName(), col.getDataType());
		}

		if (type == DbType.POSTGRESQL) {
			// Best-case behavior: UPSERT
			StringBuilder sb = new StringBuilder();
			sb.append("INSERT INTO ").append(qi(tableName)).append(" (").append(qi("uuid"));

			for (Column col : cols) {
				sb.append(", ").append(qi(col.getName()));
			}

			sb.append(") VALUES ('").append(index).append("'::uuid");

			for (Column col : cols) {
				sb.append(", '").append(col.getValue().toString()).append("'");
			}

			sb.append(") ON CONFLICT (").append(qi("uuid")).append(") DO UPDATE SET ");

			for (int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);
				sb.append(qi(col.getName())).append(" = EXCLUDED.").append(qi(col.getName()));
				if (i != cols.size() - 1) {
					sb.append(", ");
				}
			}
			sb.append(";");

			String query = sb.toString();
			plugin.devDebug("DB QUERY: " + query);

			try {
				new Query(mysql, query).executeUpdate();
			} catch (Exception e) {
				debug(e);
				plugin.debug("Failed to insert/upsert player " + index);
			}
		} else {
			// MySQL/MariaDB: keep original INSERT IGNORE ... SET ...
			String query = "INSERT IGNORE " + qi(tableName) + " set " + qi("uuid") + "='" + index + "', ";

			for (int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);
				boolean last = (i == cols.size() - 1);

				if (col.getValue().isString()) {
					query += qi(col.getName()) + "='" + col.getValue().getString() + "'" + (last ? ";" : ", ");
				} else if (col.getValue().isBoolean()) {
					query += qi(col.getName()) + "='" + col.getValue().getBoolean() + "'" + (last ? ";" : ", ");
				} else if (col.getValue().isInt()) {
					query += qi(col.getName()) + "='" + col.getValue().getInt() + "'" + (last ? ";" : ", ");
				}
			}

			plugin.devDebug("DB QUERY: " + query);

			try {
				new Query(mysql, query).executeUpdate();
			} catch (Exception e) {
				debug(e);
				plugin.debug("Failed to insert player " + index);
			}
		}

		// Cache updates (shared)
		String playerName = "";
		for (Column col : cols) {
			if (col.getName().equalsIgnoreCase("playername")) {
				playerName = col.getValue().toString();
			}
		}
		if (playerName == null || playerName.isEmpty()) {
			names.add(PlayerManager.getInstance()
					.getPlayerName(plugin.getUserManager().getUser(java.util.UUID.fromString(index), false), index));
		} else {
			names.add(playerName);
		}

		uuids.add(index);
		plugin.devDebug("Inserting " + index + " into database");
	}

	public void update(String index, List<Column> cols, boolean runAsync) {
		for (Column col : cols) {
			checkColumn(col.getName(), col.getDataType());
		}

		synchronized (updateLock) {
			if (getUuids().contains(index) || containsKeyQuery(index)) {
				StringBuilder sb = new StringBuilder();
				sb.append("UPDATE ").append(qi(tableName)).append(" SET ");

				for (int i = 0; i < cols.size(); i++) {
					Column col = cols.get(i);
					boolean last = (i == cols.size() - 1);

					if (col.getValue().isString()) {
						sb.append(qi(col.getName())).append("='").append(col.getValue().getString()).append("'");
					} else if (col.getValue().isBoolean()) {
						sb.append(qi(col.getName())).append("='").append(col.getValue().getBoolean()).append("'");
					} else if (col.getValue().isInt()) {
						sb.append(qi(col.getName())).append("='").append(col.getValue().getInt()).append("'");
					}

					if (!last) {
						sb.append(", ");
					}
				}

				if (dbType == DbType.POSTGRESQL) {
					sb.append(" WHERE ").append(qi("uuid")).append("='").append(index).append("'::uuid;");
				} else {
					sb.append(" WHERE ").append(qi("uuid")).append("='").append(index).append("';");
				}

				String query = sb.toString();
				plugin.devDebug("DB QUERY: " + query);

				try {
					Query q = new Query(mysql, query);
					if (runAsync) {
						q.executeUpdateAsync();
					} else {
						q.executeUpdate();
					}
				} catch (SQLException e) {
					debug(e);
				}
			} else {
				insertQuery(index, cols);
			}
		}
	}

	public void update(String index, String column, DataValue value) {
		if (value == null) {
			plugin.extraDebug("Mysql value null: " + column);
			return;
		}

		checkColumn(column, value.getType());

		synchronized (updateLock) {
			if (getUuids().contains(index) || containsKeyQuery(index)) {
				String query = "UPDATE " + qi(tableName) + " SET ";

				if (value.isString()) {
					query += qi(column) + "='" + value.getString() + "'";
				} else if (value.isBoolean()) {
					query += qi(column) + "='" + value.getBoolean() + "'";
				} else if (value.isInt()) {
					query += qi(column) + "='" + value.getInt() + "'";
				}

				if (dbType == DbType.POSTGRESQL) {
					query += " WHERE " + qi("uuid") + "='" + index + "'::uuid;";
				} else {
					query += " WHERE " + qi("uuid") + "='" + index + "';";
				}

				plugin.devDebug("DB QUERY: " + query);

				try {
					new Query(mysql, query).executeUpdate();
				} catch (SQLException e) {
					debug(e);
				}
			} else {
				insert(index, column, value);
			}
		}
	}

	public void wipeColumnData(String columnName, DataType dataType) {
		checkColumn(columnName, dataType);

		String sql = "UPDATE " + qi(tableName) + " SET " + qi(columnName) + " = " + dataType.getNoValue() + ";";
		plugin.devDebug("DB QUERY: " + sql);

		try {
			new Query(mysql, sql).executeUpdate();
		} catch (SQLException e) {
			debug(e);
		}
	}
}
