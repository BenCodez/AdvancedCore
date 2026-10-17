package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import com.bencodez.advancedcore.lifecycle.ServerDataOwnership;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ServerDataProcessHookTest {
    @TempDir Path directory;

    @Test void earlyRegistrationAndConfirmedReleaseRemoveTheExactHook() {
        Runtime runtime = mock(Runtime.class);
        var hook = org.mockito.ArgumentCaptor.forClass(Thread.class);
        try (var scoped = mockStatic(Runtime.class)) {
            scoped.when(Runtime::getRuntime).thenReturn(runtime);
            var owner = ServerDataOwnership.acquire(directory.resolve("ServerData.yml"));
            verify(runtime).addShutdownHook(hook.capture());
            var completion = new CompletableFuture<Void>(); owner.releaseAfter(completion);
            assertFalse(owner.isReleased());
            completion.complete(null);
            assertTrue(owner.isReleased());
            verify(runtime, times(1)).removeShutdownHook(hook.getValue());
        }
    }

    @Test void repeatedDisableDoesNotRenewTheSpentDeadline() throws Exception {
        Runtime runtime = mock(Runtime.class);
        var removed = new CountDownLatch(1);
        when(runtime.removeShutdownHook(any())).thenAnswer(call -> { removed.countDown(); return true; });
        try (var scoped = mockStatic(Runtime.class)) {
            scoped.when(Runtime::getRuntime).thenReturn(runtime);
            var owner = ServerDataOwnership.acquire(directory.resolve("ServerData.yml"));
            try {
                owner.beginShutdown(System.nanoTime() - TimeUnit.SECONDS.toNanos(15) + TimeUnit.MILLISECONDS.toNanos(20));
                owner.beginShutdown(System.nanoTime());
                assertTrue(removed.await(2, TimeUnit.SECONDS));
                assertFalse(owner.isReleased(), "deadline expiry is not confirmed publication");
            } finally { owner.releaseAfter(CompletableFuture.completedFuture(null)); }
            verify(runtime, times(1)).addShutdownHook(any());
            verify(runtime, times(1)).removeShutdownHook(any());
        }
    }

    @Test void failedRegistrationRollsBackOnlyItsUnpublishedAdmission() {
        Runtime runtime = mock(Runtime.class);
        doThrow(new IllegalStateException("shutdown already started")).when(runtime).addShutdownHook(any());
        try (var scoped = mockStatic(Runtime.class)) {
            scoped.when(Runtime::getRuntime).thenReturn(runtime);
            assertThrows(IllegalStateException.class,
                    () -> ServerDataOwnership.acquire(directory.resolve("ServerData.yml")));
            doNothing().when(runtime).addShutdownHook(any());
            var recovered = ServerDataOwnership.acquire(directory.resolve("ServerData.yml"));
            recovered.releaseAfter(CompletableFuture.completedFuture(null));
            assertTrue(recovered.isReleased());
        }
    }
}
