package com.bencodez.advancedcore.core.user;

import java.util.List;
import java.util.Map;

import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Existing user/cache/storage access, supplied lazily without owning a second cache. */
public interface UserDataReadContext {
    interface Cache {
        boolean isCached(String key);
        DataValue get(String key);
    }

    Map<String, DataValue> tempCache();
    Cache userCache();
    void cacheIfNeeded();
    void cache();
    List<Column> sqliteRow();
    List<Column> mysqlRow();
    int flatInt(String key, int fallback);
    String flatString(String key);
}
