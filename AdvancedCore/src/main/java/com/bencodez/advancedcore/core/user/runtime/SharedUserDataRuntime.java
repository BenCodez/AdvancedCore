package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * Platform-neutral user lifecycle coordinator. Cache and queue ownership stays
 * with the injected {@link UserCacheOwner}; this class never allocates a second
 * user cache or parallel user record.
 */
public final class SharedUserDataRuntime implements AutoCloseable {
    private final UserCacheOwner cacheOwner;
    private volatile SqlUserBackend backend;
    private volatile boolean closed;

    public SharedUserDataRuntime(SqlUserBackend backend, UserCacheOwner cacheOwner) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cacheOwner = Objects.requireNonNull(cacheOwner, "cacheOwner");
    }

    public DataValue read(UUID uuid, String key, UserDataFetchMode mode,
            HashMap<String, DataValue> temporaryCache, DataValue defaultValue) {
        requireOpen();
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(mode, "mode");
        if (key == null || key.isEmpty()) {
            return defaultValue;
        }

        if (mode.allowTempCache() && temporaryCache != null) {
            DataValue temporary = temporaryCache.get(key);
            if (temporary != null) {
                return temporary;
            }
            if (!mode.allowUserCache() && !mode.allowStorageLookup()) {
                return defaultValue;
            }
        }

        if (mode.allowUserCache()) {
            DataValue cached = cacheOwner.getIfPresent(uuid, key);
            if (cached != null) {
                return cached;
            }
            if (mode.waitForCache() && !cacheOwner.isCached(uuid)) {
                populate(uuid);
                cached = cacheOwner.getIfPresent(uuid, key);
                if (cached != null) {
                    return cached;
                }
            }
            if (!mode.allowStorageLookup()) {
                return defaultValue;
            }
        } else if (!mode.allowStorageLookup()) {
            return defaultValue;
        }

        return find(readStorageRow(uuid), key, defaultValue);
    }

    public HashMap<String, DataValue> populate(UUID uuid) {
        requireOpen();
        HashMap<String, DataValue> values = SqlUserDataAccess.convert(readStorageRow(uuid));
        cacheOwner.populate(uuid, values);
        return values;
    }

    public int startupForEach(BiConsumer<UUID, HashMap<String, DataValue>> consumer, boolean populateCache) {
        requireOpen();
        Objects.requireNonNull(consumer, "consumer");
        int count = 0;
        for (UUID uuid : backend.enumerateUsers()) {
            HashMap<String, DataValue> values = SqlUserDataAccess.convert(readStorageRow(uuid));
            if (populateCache) {
                cacheOwner.populate(uuid, values);
            }
            consumer.accept(uuid, values);
            count++;
        }
        return count;
    }

    public void queueChange(UUID uuid, String key, DataValue value) {
        requireOpen();
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(key, "key");
        if (!cacheOwner.isCached(uuid)) {
            populate(uuid);
        }
        cacheOwner.queueChange(uuid, key, value);
    }

    public void flush(UUID uuid) {
        requireOpen();
        Objects.requireNonNull(uuid, "uuid");
        cacheOwner.flush(uuid, backend.user(uuid));
    }

    public void flushAll() {
        requireOpen();
        Set<UUID> users = Set.copyOf(cacheOwner.cachedUsers());
        for (UUID uuid : users) {
            cacheOwner.flush(uuid, backend.user(uuid));
        }
    }

    public synchronized void replaceBackend(SqlUserBackend replacement) {
        requireOpen();
        Objects.requireNonNull(replacement, "replacement");
        if (!replacement.isOpen()) {
            throw new IllegalArgumentException("replacement backend is closed");
        }
        flushAll();
        cacheOwner.clearAfterFlush();
        SqlUserBackend previous = backend;
        backend = replacement;
        previous.close();
    }

    public void remove(UUID uuid) {
        requireOpen();
        Objects.requireNonNull(uuid, "uuid");
        flush(uuid);
        backend.user(uuid).delete(backend.storageType());
        cacheOwner.remove(uuid);
    }

    public SqlUserBackend backend() {
        return backend;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        flushAll();
        cacheOwner.clearAfterFlush();
        cacheOwner.shutdown();
        backend.close();
        closed = true;
    }

    private List<Column> readStorageRow(UUID uuid) {
        SqlUserStorage storage = backend.user(uuid);
        return storage.readRow(backend.storageType());
    }

    private DataValue find(List<Column> row, String key, DataValue defaultValue) {
        if (row != null) {
            for (Column column : row) {
                if (column.getName().equals(key)) {
                    return column.getValue() == null ? defaultValue : column.getValue();
                }
            }
        }
        return defaultValue;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Shared user data runtime is closed");
        }
        if (!backend.isOpen()) {
            throw new IllegalStateException("SQL user backend is closed");
        }
    }
}
