package com.bencodez.advancedcore.core.user.storage;

import java.util.HashMap;
import java.util.List;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * SQL operations for one caller-owned user identity. No game API, user cache,
 * executor, connection pool, or lifecycle is owned here. A platform supplies its
 * existing backend implementations. Calls may block; use the owner's established
 * storage execution context, never a game/region thread for database work.
 *
 * These operations retain the backing provider's error/commit semantics. A void
 * return is not an additional durable reward acknowledgement. This boundary does
 * not add retries, transactions, or a second write queue.
 */
public interface SqlUserStorage {
    List<Column> readRow(UserStorage storage);

    boolean contains(UserStorage storage);

    void delete(UserStorage storage);

    void write(UserStorage storage, String key, DataValue value);

    void writeValues(UserStorage storage, HashMap<String, DataValue> values);
}
