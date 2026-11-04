package com.bencodez.advancedcore.core.user.storage.sql;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;

public interface SqlUserBackend extends AutoCloseable {
    /** Materialized compatibility API is intentionally bounded by concrete SQL backends. */
    int MAX_MATERIALIZED_USERS = 100_000;

    /** True only when a successful row read contains every persisted column. */
    default boolean hasAuthoritativeRowReads() { return false; }

    /** Explicit opt-in: user transactions atomically commit caller-owned SQL and user values. */
    default boolean supportsAtomicUserTransactions() { return false; }

    /** Column identifier folding, or null when this provider cannot guarantee it. */
    default Boolean caseInsensitiveColumnNames() { return null; }

    UserStorage storageType();

    SqlUserStorage user(UUID uuid);

    List<UUID> enumerateUsers();

    /**
     * Streaming enumeration for large historical user tables. Concrete SQL backends
     * override this to avoid first materializing every UUID in heap.
     */
    default void forEachUser(Consumer<UUID> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        enumerateUsers().forEach(consumer);
    }

    boolean isOpen();

    @Override
    void close();
}
