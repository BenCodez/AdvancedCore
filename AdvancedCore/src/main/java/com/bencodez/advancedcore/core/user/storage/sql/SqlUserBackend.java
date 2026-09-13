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
