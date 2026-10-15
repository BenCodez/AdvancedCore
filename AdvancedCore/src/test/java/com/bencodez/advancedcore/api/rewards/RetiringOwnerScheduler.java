package com.bencodez.advancedcore.api.rewards;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.folialib.FoliaLib;
import com.bencodez.simpleapi.folialib.enums.EntityTaskResult;
import com.bencodez.simpleapi.folialib.impl.ServerImplementation;
import com.bencodez.simpleapi.folialib.wrapper.task.WrappedTask;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

/** Models the pinned Folia boundary without replacing reward completion or eligibility. */
final class RetiringOwnerScheduler {
    private enum Admission { NORMAL, IMMEDIATE_RETIREMENT, LATER_RETIREMENT, REJECTED }

    private final Consumer<Runnable> ownerQueue;
    private final Consumer<Runnable> retirementQueue;
    private Admission next = Admission.NORMAL;
    private Runnable beforeRetirement = () -> { };
    private Consumer<WrappedTask> staleOwner;
    private Runnable staleRetirement;
    private CompletableFuture<EntityTaskResult> staleResult;

    RetiringOwnerScheduler(AdvancedCorePlugin plugin, BukkitScheduler scheduler, Consumer<Runnable> ownerQueue,
            Consumer<Runnable> retirementQueue) {
        this.ownerQueue = ownerQueue;
        this.retirementQueue = retirementQueue;
        FoliaLib folia = mock(FoliaLib.class);
        ServerImplementation implementation = mock(ServerImplementation.class);
        when(scheduler.getFoliaLib()).thenReturn(folia);
        when(folia.isFolia()).thenReturn(true);
        when(folia.getImpl()).thenReturn(implementation);
        // Exercise the real wrapper that discards runAtEntity's returned future.
        doCallRealMethod().when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Entity.class));
        when(implementation.runAtEntity(any(Entity.class), any())).thenAnswer(call ->
                admit(call.getArgument(1), null));
        when(implementation.runAtEntityWithFallback(any(Entity.class), any(), any(Runnable.class))).thenAnswer(call ->
                admit(call.getArgument(1), call.getArgument(2)));
    }

    void retireNext(boolean immediate) {
        next = immediate ? Admission.IMMEDIATE_RETIREMENT : Admission.LATER_RETIREMENT;
    }

    void rejectNext() {
        next = Admission.REJECTED;
    }

    void beforeRetirement(Runnable callback) {
        beforeRetirement = callback;
    }

    void enqueueStaleOwnerCallback() {
        if (staleOwner == null) throw new IllegalStateException("No retired owner callback was captured");
        ownerQueue.accept(() -> staleOwner.accept(null));
    }

    void repeatRetirementSignal() {
        if (staleRetirement != null) staleRetirement.run();
        if (staleResult != null) staleResult.complete(EntityTaskResult.ENTITY_RETIRED);
    }

    private CompletableFuture<EntityTaskResult> admit(Consumer<WrappedTask> action, Runnable retired) {
        CompletableFuture<EntityTaskResult> result = new CompletableFuture<>();
        Admission admission = next;
        next = Admission.NORMAL;
        if (admission == Admission.NORMAL) {
            ownerQueue.accept(() -> {
                action.accept(null);
                result.complete(EntityTaskResult.SUCCESS);
            });
        } else {
            staleOwner = action;
            staleRetirement = retired;
            staleResult = result;
            if (admission == Admission.REJECTED) {
                throw new RejectedExecutionException("Synthetic owner scheduler rejection");
            }
            if (admission == Admission.IMMEDIATE_RETIREMENT) {
                beforeRetirement.run();
                // The pinned API returns this status without calling the fallback.
                result.complete(EntityTaskResult.SCHEDULER_RETIRED);
            } else {
                retirementQueue.accept(() -> {
                    beforeRetirement.run();
                    if (retired != null) retired.run();
                    result.complete(EntityTaskResult.ENTITY_RETIRED);
                });
            }
        }
        return result;
    }
}
