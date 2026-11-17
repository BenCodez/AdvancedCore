package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.*;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.*;

class UserDataNotificationDispatcherTest {
    private UserManager users;
    private final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
    private final Map<String, Thread> deliveries = new ConcurrentHashMap<>();

    @BeforeEach void setup() {
        users = new UserManager(mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS));
        users.getDataManager().getTimer().shutdownNow();
        users.getUserDataChange().add(new UserDataChanged() {
            @Override public void onChange(AdvancedCoreUser ignored, String... keys) {
                for (String key : keys) deliveries.put(key, Thread.currentThread());
            }
        });
    }

    @Test void nestedFailureRestoresOuterDispatcherAndDefaultInlineContract() {
        List<Runnable> outer = new ArrayList<>();
        List<Runnable> inner = new ArrayList<>();
        users.withUserDataChangeDispatcher(outer::add, () -> {
            users.onChange(user, "outer-before");
            assertThrows(IllegalStateException.class, () ->
                    users.withUserDataChangeDispatcher(inner::add, () -> {
                        users.onChange(user, "inner");
                        throw new IllegalStateException("synthetic failure");
                    }));
            users.onChange(user, "outer-after");
        });
        assertTrue(deliveries.isEmpty());
        users.onChange(user, "outside");
        assertEquals(Set.of("outside"), deliveries.keySet());
        assertEquals(2, outer.size());
        assertEquals(1, inner.size());
        inner.forEach(Runnable::run);
        outer.forEach(Runnable::run);
        assertEquals(Set.of("outer-before", "outer-after", "inner", "outside"), deliveries.keySet());
    }

    @Test void dispatchScopeIsThreadLocalAndSnapshotsTheKeyArray() throws Exception {
        List<Runnable> ownerQueue = new ArrayList<>();
        Thread caller = Thread.currentThread();
        users.withUserDataChangeDispatcher(ownerQueue::add, () -> {
            String[] keys = {"original"};
            users.onChange(user, keys);
            keys[0] = "mutated";
            Thread other = new Thread(() -> users.onChange(user, "other"), "synthetic-unrelated-worker");
            other.start();
            try { other.join(5000); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            assertFalse(other.isAlive());
            assertNotNull(deliveries.get("other"));
            assertNotSame(caller, deliveries.get("other"));
        });
        assertEquals(1, ownerQueue.size());
        ownerQueue.get(0).run();
        assertSame(caller, deliveries.get("original"));
        assertFalse(deliveries.containsKey("mutated"));
    }
}
