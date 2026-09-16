package com.bencodez.advancedcore.tests.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserRuntimeBootstrap;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;

class BukkitUserRuntimeShutdownTest {
    @Test void runtimeShutdownWaitsForSharedRetirementBeforeClosingLegacyMysql() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
        MySQL mysql = mock(MySQL.class);
        UserManager users = mock(UserManager.class);
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getOptions()).thenReturn(options);
        when(options.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getMysql()).thenReturn(mysql);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        UserDataManager manager = new UserDataManager(plugin);
        when(users.getDataManager()).thenReturn(manager);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        try {
            BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(plugin, manager);
            new AdvancedCoreRuntime(new BukkitRuntimePlatform(plugin)).shutdown();
            verify(mysql).close();
        } finally {
            manager.getTimer().shutdownNow();
        }
    }
}
