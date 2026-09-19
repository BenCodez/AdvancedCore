package com.bencodez.advancedcore.tests.user.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.validation.BedrockCheckResult;
import com.bencodez.advancedcore.api.user.validation.UserValidationService;
import com.bencodez.advancedcore.api.user.validation.impl.AdvancedCoreStoredUserLookup;
import com.bencodez.advancedcore.api.user.validation.interfaces.BedrockPrecheck;
import com.bencodez.advancedcore.api.user.validation.interfaces.OnlinePlayerLookup;
import com.bencodez.advancedcore.api.user.validation.interfaces.ServerHistoryLookup;

class SharedStoredValidationTest {
    @Test void synchronousPrimaryThreadValidationUsesCacheInsteadOfSharedSql() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager data = mock(UserDataManager.class);
        BedrockPrecheck bedrock = mock(BedrockPrecheck.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(data);
        when(data.hasSharedSqlBackend()).thenReturn(true);
        when(bedrock.check("UncachedPlayer")).thenReturn(
                new BedrockCheckResult(false, false, "UncachedPlayer", "not-bedrock"));
        UserValidationService validation = new UserValidationService(
                mock(OnlinePlayerLookup.class), new AdvancedCoreStoredUserLookup(plugin),
                mock(ServerHistoryLookup.class), bedrock);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertFalse(assertDoesNotThrow(() -> validation.validate("UncachedPlayer", false)).isValid());
        }
        verify(users, never()).userExistStored(anyString());
    }
}
