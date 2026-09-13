package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * Port implemented by the existing cache owner. The shared runtime deliberately
 * does not allocate a second cache or queue.
 */
public interface UserCacheOwner {
    boolean isCached(UUID uuid);

    DataValue getIfPresent(UUID uuid, String key);

    void populate(UUID uuid, HashMap<String, DataValue> values);

    void queueChange(UUID uuid, String key, DataValue value);

    void flush(UUID uuid, SqlUserStorage storage);

    Set<UUID> cachedUsers();

    void remove(UUID uuid);

    void clearAfterFlush();

    void shutdown();
}
