package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;

class SharedBindingAdmissionTest {
    @Test
    void bindingTransitionBlocksAnUnmappedCacheFromStartingANewLegacyBatch() {
        UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        try {
            UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            manager.beginSharedBindingTransition();
            try {
                assertThrows(IllegalStateException.class, cache::processChanges);
                assertTrue(cache.hasChangesToProcess());
            } finally {
                manager.endSharedBindingTransition();
            }
        } finally {
            manager.getTimer().shutdownNow();
        }
    }
}
