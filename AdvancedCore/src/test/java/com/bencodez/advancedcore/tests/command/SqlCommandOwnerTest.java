package com.bencodez.advancedcore.tests.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.command.CommandLoader;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import java.util.function.Function;

class SqlCommandOwnerTest {
    @Test void sqlCommandUsesOneProviderSnapshotAcrossStorageReload() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
        CommandSender sender = mock(CommandSender.class);
        UserTable sqlite = mock(UserTable.class);
        MySQL mysql = mock(MySQL.class);
        UserManager users = mock(UserManager.class);
        UserDataManager dataManager = mock(UserDataManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getOptions()).thenReturn(options);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(dataManager);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getMysql()).thenReturn(mysql);
        when(plugin.getNativeUserStorageOwner()).thenReturn(
                new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, sqlite));
        when(dataManager.withSharedNativeUserStorage(any())).thenAnswer(call -> {
            @SuppressWarnings("unchecked")
            Function<AdvancedCorePlugin.UserStorageOwner, Object> operation = call.getArgument(0);
            return operation.apply(plugin.getNativeUserStorageOwner());
        });
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));

        var command = new CommandLoader(plugin).getBasicAdminCommands("test").get(1);
        assertDoesNotThrow(() -> command.execute(sender, new String[] {"RunSQLQuery", "SELECT", "1"}));
        verify(sqlite).executeQuery("SELECT 1;");
        verify(mysql, never()).executeQuery(anyString());
        verify(dataManager).withSharedNativeUserStorage(any());
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }
}
