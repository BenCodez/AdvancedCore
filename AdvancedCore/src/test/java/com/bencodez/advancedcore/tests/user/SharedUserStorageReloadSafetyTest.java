package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;

class SharedUserStorageReloadSafetyTest {
    @Test
    void loadAndReloadRejectBeforeAnyNativeStorageMutationWhenSharedRuntimeOwnsStorage() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        when(runtime.isClosed()).thenReturn(false);
        manager.bindSharedRuntime(runtime);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        try {
            assertThrows(IllegalStateException.class, () -> plugin.loadUserAPI(UserStorage.MYSQL));
            assertThrows(IllegalStateException.class, () -> plugin.reloadAdvancedCore(true));
            verify(plugin, never()).getOptions();
            verify(plugin, never()).getServerDataFile();
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void retirementRemainsAnUnsafeReloadWindowUntilBlockingCloseCompletes() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        CompletableFuture<Void> retirement = new CompletableFuture<>();
        CountDownLatch afterRetirement = new CountDownLatch(1);
        when(runtime.isClosed()).thenReturn(false);
        when(runtime.closeAsync(any())).thenReturn(retirement);
        manager.bindSharedRuntime(runtime);
        try {
            assertTrue(manager.closeSharedRuntimeAsync(afterRetirement::countDown));
            assertTrue(manager.hasSharedRuntimeLifecycle());
            assertTrue(manager.closeSharedRuntimeAsync(() -> {
                throw new AssertionError("A second caller must join the existing retirement");
            }));
            retirement.complete(null);
            assertTrue(afterRetirement.await(5, TimeUnit.SECONDS));
            assertFalse(manager.hasSharedRuntimeLifecycle());
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void conversionRunsBehindTheRuntimeMaintenanceBarrierInsteadOfTheReloadGuard() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(users.getAllKeys(UserStorage.SQLITE)).thenReturn(new HashMap<>());
        doNothing().when(plugin).loadUserAPI(any(UserStorage.class));
        doNothing().when(plugin).debug(any(String.class));
        doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(runtime).runStorageMaintenance(any(Runnable.class));
        manager.bindSharedRuntime(runtime);
        try {
            plugin.convertDataStorage(UserStorage.SQLITE, UserStorage.MYSQL);

            verify(runtime).runStorageMaintenance(any(Runnable.class));
            verify(plugin).loadUserAPI(UserStorage.SQLITE);
            verify(plugin).loadUserAPI(UserStorage.MYSQL);
        } finally {
            manager.getTimer().shutdownNow();
        }
    }
}
