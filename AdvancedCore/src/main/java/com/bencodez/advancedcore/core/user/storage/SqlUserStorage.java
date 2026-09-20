package com.bencodez.advancedcore.core.user.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * does not add retries or a second write queue. JDBC implementations also
 * expose a caller extension of their existing user-row transaction.
 */
public interface SqlUserStorage {
    List<Column> readRow(UserStorage storage);

    boolean contains(UserStorage storage);

    void delete(UserStorage storage);

    void write(UserStorage storage, String key, DataValue value);

    void writeValues(UserStorage storage, HashMap<String, DataValue> values);

    /**
     * Run caller-owned SQL and user writes in one backend-owned transaction.
     * The user row exists and is locked before the callback runs. A callback
     * must use only this scope for user writes; ordinary methods open another
     * connection. Cache-backed callers must enter through
     * UserDataManager.withAtomicUserTransaction so cached writes are flushed and the
     * committed snapshot is reconciled. This blocking method belongs on a
     * storage worker. Deadlock/busy retries are the caller's responsibility.
     */
    default <T> T transaction(UserStorage storage, TransactionWork<T> work) {
        return transaction(storage, Map.of(), work);
    }

    /**
     * Initial values are inserted only when the user row does not yet exist.
     * Supply any required non-default columns here; existing rows retain their
     * values until the callback explicitly writes them.
     */
    default <T> T transaction(UserStorage storage, Map<String, DataValue> initialValues, TransactionWork<T> work) {
        throw new UnsupportedOperationException("Atomic JDBC user transactions are unavailable");
    }

    @FunctionalInterface
    interface TransactionWork<T> {
        T run(TransactionScope scope) throws SQLException;
    }

    interface TransactionScope {
        /**
         * Active connection for caller-owned tables. Never commit, roll back,
         * close, or change auto-commit. AdvancedCore owns its lifecycle. Do not
         * retain the connection or scope after the callback returns.
         */
        Connection connection();

        /** Read the locked user row on the active transaction connection. */
        List<Column> readRow() throws SQLException;

        /** Write registered user values on the active transaction connection. */
        void writeValues(Map<String, DataValue> values) throws SQLException;
    }
}
