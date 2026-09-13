package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
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

    interface PopulationToken {}

    default PopulationToken beginPopulation(UUID uuid) { return null; }

    default HashMap<String, DataValue> completePopulation(UUID uuid, HashMap<String, DataValue> values,
            PopulationToken token) {
        populate(uuid, values);
        return values;
    }

    void queueChange(UUID uuid, String key, DataValue value);
    void flush(UUID uuid, SqlUserStorage storage);

    default void flush(UUID uuid, UserStorage type, SqlUserStorage storage) { flush(uuid, storage); }

    default void bindFlushGate(Consumer<Runnable> gate) {}
    default void bindUserGate(BiConsumer<UUID, Runnable> gate) {}
    default void bindBackend(SqlUserBackend backend) {}

    default void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate) {
        bindFlushGate(gate);
        bindBackend(backend);
    }

    /** Publish the global and per-user lifecycle routes as one logical binding. */
    default void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate,
            BiConsumer<UUID, Runnable> userGate) {
        bindUserGate(userGate);
        bindLifecycle(backend, gate);
    }

    default void requireBlockingAllowed() {}

    Set<UUID> cachedUsers();
    void remove(UUID uuid);
    void clearAfterFlush();
    void shutdown();
}
