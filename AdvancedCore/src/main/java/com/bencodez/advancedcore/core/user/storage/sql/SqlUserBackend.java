package com.bencodez.advancedcore.core.user.storage.sql;

import java.util.List;
import java.util.UUID;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;

public interface SqlUserBackend extends AutoCloseable {
    UserStorage storageType();

    SqlUserStorage user(UUID uuid);

    List<UUID> enumerateUsers();

    boolean isOpen();

    @Override
    void close();
}
