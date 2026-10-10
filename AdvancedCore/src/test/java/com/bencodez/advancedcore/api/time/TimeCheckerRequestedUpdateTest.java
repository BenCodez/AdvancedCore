package com.bencodez.advancedcore.api.time;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;

class TimeCheckerRequestedUpdateTest {
    private AdvancedCorePlugin plugin() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }
    @Test void repeatedRequestsCoalesceAndNeverRunInline() {
        var plugin = plugin(); var checker = spy(new TimeChecker(plugin));
        var timer = mock(ScheduledExecutorService.class); Queue<Runnable> tasks = new ArrayDeque<>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
        checker.setTimer(timer); doNothing().when(checker).update();
        for (int i = 0; i < 100; i++) checker.requestUpdate();
        assertEquals(1, tasks.size()); verify(checker, never()).update();
        tasks.remove().run(); verify(checker).update();
        checker.requestUpdate(); assertEquals(1, tasks.size());
        tasks.remove().run(); verify(checker, times(2)).update();
    }
    @Test void admittedButNotStartedCheckIsSkippedAndDrainedOnShutdown() {
        var checker = spy(new TimeChecker(plugin())); var timer = mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
        checker.setTimer(timer); checker.requestUpdate();
        var stopped = checker.beginShutdown().toCompletableFuture(); assertFalse(stopped.isDone());
        task.get().run(); assertTrue(stopped.isDone()); verify(checker, never()).update();
        checker.requestUpdate(); verify(timer).execute(any(Runnable.class));
    }
    @Test void rejectedSubmissionReleasesAdmissionForRetry() {
        var checker = spy(new TimeChecker(plugin())); var timer = mock(ScheduledExecutorService.class);
        doThrow(new RejectedExecutionException()).doNothing().when(timer).execute(any(Runnable.class));
        checker.setTimer(timer); checker.requestUpdate(); checker.requestUpdate();
        verify(timer, times(2)).execute(any(Runnable.class)); verify(checker, never()).update();
    }
    @Test void retiredTimerAndDisabledPluginCannotExecuteChecks() {
        for (boolean disabled : new boolean[] { false, true }) {
            var plugin = plugin(); var checker = spy(new TimeChecker(plugin));
            var timer = mock(ScheduledExecutorService.class); AtomicReference<Runnable> task = new AtomicReference<>();
            doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
            checker.setTimer(timer); checker.requestUpdate();
            if (disabled) when(plugin.isEnabled()).thenReturn(false); else checker.setTimer(mock(ScheduledExecutorService.class));
            task.get().run(); verify(checker, never()).update();
            assertTrue(checker.beginShutdown().toCompletableFuture().isDone());
        }
    }
    @Test void backgroundExecutionRemainsOwnedUntilRunningCheckReturns() throws Exception {
        var plugin = plugin(); var checker = spy(new TimeChecker(plugin));
        var timer = Executors.newSingleThreadScheduledExecutor(); checker.setTimer(timer);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        Thread caller = Thread.currentThread(); AtomicReference<Thread> worker = new AtomicReference<>();
        doAnswer(call -> { worker.set(Thread.currentThread()); entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS)); return null; }).when(checker).update();
        try {
            checker.requestUpdate(); assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNotSame(caller, worker.get());
            var stopped = checker.beginShutdown().toCompletableFuture(); assertFalse(stopped.isDone());
            release.countDown(); stopped.get(5, TimeUnit.SECONDS);
            verify(checker).update();
        } finally { release.countDown(); timer.shutdownNow(); assertTrue(timer.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    @Test void failureDoesNotStrandShutdownOrFutureRequests() {
        var checker = spy(new TimeChecker(plugin())); var timer = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
        checker.setTimer(timer); doThrow(new IllegalStateException("fixture failure")).doNothing().when(checker).update();
        checker.requestUpdate(); tasks.remove().run(); checker.requestUpdate(); tasks.remove().run();
        verify(checker, times(2)).update(); assertTrue(checker.beginShutdown().toCompletableFuture().isDone());
    }
}
