package com.bencodez.advancedcore.tests.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.command.CommandLoader;

class SqlCommandOwnerTest {
    @Test void sqlCommandUsesOneProviderSnapshotAcrossStorageReload() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
        CommandSender sender = mock(CommandSender.class);
        UserTable sqlite = mock(UserTable.class);
        MySQL mysql = mock(MySQL.class);
        when(plugin.getOptions()).thenReturn(options);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getMysql()).thenReturn(mysql);
        when(plugin.getNativeUserStorageOwner()).thenReturn(
                new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, sqlite));

        var command = new CommandLoader(plugin).getBasicAdminCommands("test").get(1);
        assertDoesNotThrow(() -> command.execute(sender, new String[] {"RunSQLQuery", "SELECT", "1"}));
        verify(sqlite).executeQuery("SELECT 1;");
        verify(mysql, never()).executeQuery(anyString());
    }
}
