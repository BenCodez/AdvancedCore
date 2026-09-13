package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Port for the existing cache/queue owner. No parallel cache is allocated. */
public interface UserCacheOwner {
    boolean isCached(UUID uuid);
    DataValue getIfPresent(UUID uuid, String key);
    void populate(UUID uuid, HashMap<String, DataValue> values);
    void queueChange(UUID uuid, String key, DataValue value);
    void flush(UUID uuid, SqlUserStorage storage);

    /** Explicit type avoids inferring the destination from a platform plugin's current settings. */
    default void flush(UUID uuid, UserStorage type, SqlUserStorage storage) { flush(uuid, storage); }

    /** Include scheduled cache batches in the runtime admission/drain barrier. */
    default void bindFlushGate(Consumer<Runnable> gate) {}

    /** Bind scheduled writes to this provider; invoked before access and after a drained replacement. */
    default void bindBackend(SqlUserBackend backend) {}

    /** Bind initial lifecycle state together. Stateful adapters override for atomic publication. */
    default void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate) {
        bindFlushGate(gate);
        bindBackend(backend);
    }

    /** Platform adapters reject blocking work on server/entity threads before acquiring any barrier. */
    default void requireBlockingAllowed() {}

    Set<UUID> cachedUsers();
    void remove(UUID uuid);
    void clearAfterFlush();
    void shutdown();
}
