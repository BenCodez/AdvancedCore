package com.bencodez.advancedcore.bukkit.user.runtime;

import java.util.Objects;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;

/** Creates the shared route only after Bukkit has initialized its native user storage. */
public final class BukkitUserRuntimeBootstrap {
    private BukkitUserRuntimeBootstrap() {}

    public static void bindAfterStorageInitialization(AdvancedCorePlugin plugin, UserDataManager manager) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(manager, "manager");
        if (manager.hasSharedRuntime()) return;
        if (manager.hasSharedRuntimeLifecycle()) {
            throw new IllegalStateException("Shared user storage retirement is still in progress");
        }
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(new BukkitSqlUserBackend(plugin),
                new BukkitUserCacheOwner(manager));
        try {
            manager.bindSharedRuntime(runtime);
        } catch (RuntimeException | Error failure) {
            try { runtime.closeAsync(manager.getTimer()); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }
}
