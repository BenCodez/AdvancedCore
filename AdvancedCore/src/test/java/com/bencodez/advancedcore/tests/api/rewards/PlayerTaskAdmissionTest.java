package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
import com.bencodez.simpleapi.folialib.FoliaLib;
import com.bencodez.simpleapi.folialib.enums.EntityTaskResult;
import com.bencodez.simpleapi.folialib.impl.PlatformScheduler;
import com.bencodez.simpleapi.folialib.wrapper.task.WrappedTask;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class PlayerTaskAdmissionTest {
    private enum RelocatedResult { SUCCESS, ENTITY_RETIRED, SCHEDULER_RETIRED, UNEXPECTED }

    @Test
    void successDispatchesOnlyOnTheOwnerCallback() {
        Fixture fixture = new Fixture();
        fixture.submit();
        verifyNoInteractions(fixture.action, fixture.retired, fixture.rejected);
        fixture.owner.accept(null);
        fixture.result.complete(EntityTaskResult.SUCCESS);
        verify(fixture.action).run();
        verifyNoInteractions(fixture.retired, fixture.rejected);
    }

    @Test
    void fallbackAndFutureRetirementNotifyOnlyOnceAndPreventLateDispatch() {
        Fixture fixture = new Fixture();
        fixture.submit();
        fixture.fallback.run();
        fixture.result.complete(EntityTaskResult.ENTITY_RETIRED);
        fixture.fallback.run();
        fixture.owner.accept(null);
        verify(fixture.retired).run();
        verifyNoInteractions(fixture.action, fixture.rejected);
    }

    @Test
    void immediateSchedulerRetirementDoesNotRequireTheFallback() {
        Fixture fixture = new Fixture();
        fixture.result.complete(EntityTaskResult.SCHEDULER_RETIRED);
        fixture.submit();
        fixture.fallback.run();
        verify(fixture.retired).run();
        verifyNoInteractions(fixture.action, fixture.rejected);
    }

    @Test
    void actionClaimPreventsLateRetirementOrExceptionalRejection() {
        Fixture fixture = new Fixture();
        fixture.submit();
        fixture.owner.accept(null);
        fixture.owner.accept(null);
        fixture.fallback.run();
        fixture.result.completeExceptionally(new IllegalStateException("Synthetic late failure"));
        verify(fixture.action).run();
        verifyNoInteractions(fixture.retired, fixture.rejected);
    }

    @Test
    void exceptionalCompletionRejectsOnceAndPreventsLateCallbacks() {
        Fixture fixture = new Fixture();
        RuntimeException failure = new IllegalStateException("Synthetic admission failure");
        fixture.submit();
        fixture.result.completeExceptionally(failure);
        fixture.fallback.run();
        fixture.owner.accept(null);
        verify(fixture.rejected).accept(failure);
        verifyNoInteractions(fixture.action, fixture.retired);
    }

    @Test
    void nullStatusRejectsInsteadOfLosingThePendingAction() {
        Fixture fixture = new Fixture();
        fixture.submit();
        fixture.result.complete(null);
        verify(fixture.rejected).accept(isA(IllegalStateException.class));
        verifyNoInteractions(fixture.action, fixture.retired);
    }

    @Test
    void enumNamesRemainCompatibleAfterRelocationAndUnknownStatusRejects() {
        for (RelocatedResult status : RelocatedResult.values()) {
            Fixture fixture = new Fixture();
            CompletableFuture<RelocatedResult> relocated = new CompletableFuture<>();
            fixture.returnRelocated(relocated);
            fixture.submit();
            relocated.complete(status);
            if (status == RelocatedResult.SUCCESS) {
                fixture.owner.accept(null);
                verify(fixture.action).run();
                verifyNoInteractions(fixture.retired, fixture.rejected);
            } else if (status == RelocatedResult.UNEXPECTED) {
                verify(fixture.rejected).accept(isA(IllegalStateException.class));
                verifyNoInteractions(fixture.action, fixture.retired);
            } else {
                verify(fixture.retired).run();
                verifyNoInteractions(fixture.action, fixture.rejected);
            }
        }
    }

    @Test
    void missingFutureAndSynchronousRejectionKeepTheCallerFailureContract() {
        Fixture missing = new Fixture();
        when(missing.platform.runAtEntityWithFallback(any(), any(), any())).thenReturn(null);
        assertThrows(IllegalStateException.class, missing::submit);
        Fixture rejected = new Fixture();
        RejectedExecutionException failure = new RejectedExecutionException("Synthetic rejection");
        when(rejected.platform.runAtEntityWithFallback(any(), any(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(RejectedExecutionException.class, rejected::submit));
        verifyNoInteractions(rejected.action, rejected.retired, rejected.rejected);
    }

    @Test
    void nonFoliaKeepsTheEstablishedSchedulerPath() {
        Fixture fixture = new Fixture();
        when(fixture.folia.isFolia()).thenReturn(false);
        fixture.submit();
        verify(fixture.scheduler).runTask(fixture.plugin, fixture.action, fixture.player);
        verifyNoInteractions(fixture.platform, fixture.action, fixture.retired, fixture.rejected);
    }

    private static final class Fixture {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final Player player = mock(Player.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final FoliaLib folia = mock(FoliaLib.class);
        final PlatformScheduler platform = mock(PlatformScheduler.class);
        final Runnable action = mock(Runnable.class);
        final Runnable retired = mock(Runnable.class);
        final Consumer<Throwable> rejected = mock();
        final CompletableFuture<EntityTaskResult> result = new CompletableFuture<>();
        Consumer<WrappedTask> owner;
        Runnable fallback;

        Fixture() {
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(scheduler.getFoliaLib()).thenReturn(folia);
            when(folia.isFolia()).thenReturn(true);
            when(folia.getImpl()).thenReturn(platform);
            when(folia.getScheduler()).thenReturn(platform);
            when(platform.runAtEntityWithFallback(any(), any(), any())).thenAnswer(call -> {
                owner = call.getArgument(1);
                fallback = call.getArgument(2);
                return result;
            });
        }

        void submit() {
            RewardEligibility.runPlayerTask(plugin, player, action, retired, rejected);
        }

        // Emulate a dependency relocation at the scheduler boundary only. Production
        // code must handle enum names without casting to a particular vendor enum.
        void returnRelocated(CompletableFuture<RelocatedResult> relocated) {
            when(platform.runAtEntityWithFallback(any(), any(), any())).thenAnswer(call -> {
                owner = call.getArgument(1);
                fallback = call.getArgument(2);
                return relocated;
            });
        }
    }
}
