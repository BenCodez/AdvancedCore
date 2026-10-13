package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.serverhandle.IServerHandle;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class LiteralBroadcastSchedulingTest {
    /** Test stand-in for the supported native EntityScheduler capability. */
    public interface EntityOwner { OwnerScheduler getScheduler(); }
    public interface OwnerScheduler {
        boolean execute(org.bukkit.plugin.Plugin plugin, Runnable action, Runnable retired, long delay);
    }

    @Test void singleConsolePayloadUsesGlobalOwnerAndDoesNotReparseFinalText() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String message = "§7R&D &aLiteral%NewLine%[Text=\"literal\",Command=\"run\"]";
            var completion = fixture.misc.dispatchPreparedConsoleMessageAsync(fixture.plugin, () -> {
                assertTrue(fixture.globalOwner.get());
                return message;
            });
            verifyNoInteractions(fixture.console);
            fixture.global.get(0).run();
            fixture.global.get(0).run();
            completion.toCompletableFuture().join();
            verify(fixture.console).sendMessage(message);
            verifyNoMoreInteractions(fixture.console);
        }
    }

    @Test void cancelledConsolePayloadFencesQueuedGlobalCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var rendered = new AtomicBoolean();
            var completion = fixture.misc.dispatchPreparedConsoleMessageAsync(fixture.plugin, () -> {
                rendered.set(true); return "Original";
            }).toCompletableFuture();
            completion.cancel(false);
            fixture.global.get(0).run();
            assertFalse(rendered.get());
            verifyNoInteractions(fixture.console);
        }
    }

    @Test void disabledQueuedConsoleLineFailsBeforeRenderingOrConsoleLookup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var rendered = new AtomicBoolean();
            var completion = fixture.misc.dispatchPreparedConsoleLineAsync(fixture.plugin, () -> {
                rendered.set(true);
                return new TextComponent("Original");
            }).toCompletableFuture();
            assertFalse(completion.isDone());
            assertEquals(1, fixture.global.size());
            when(fixture.plugin.isEnabled()).thenReturn(false);
            fixture.global.get(0).run();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion::join).getCause());
            assertFalse(rendered.get());
            fixture.bukkit.verify(Bukkit::getConsoleSender, never());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void enabledConsoleLineRendersOnGlobalOwnerAndDeliversOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var rendered = new ArrayList<TextComponent>();
            TextComponent line = new TextComponent("R&D &aLiteral");
            var completion = fixture.misc.dispatchPreparedConsoleLineAsync(fixture.plugin, () -> {
                assertTrue(fixture.globalOwner.get());
                rendered.add(line);
                return line;
            }).toCompletableFuture();
            assertFalse(completion.isDone());
            assertTrue(rendered.isEmpty());
            verifyNoInteractions(fixture.console);
            fixture.global.get(0).run();
            fixture.global.get(0).run();
            completion.join();
            assertEquals(List.of(line), rendered);
            verify(fixture.console).sendMessage(line.toLegacyText());
            verifyNoMoreInteractions(fixture.console);
        }
    }

    @Test void disableDuringConsoleLineRenderingFailsBeforeConsoleLookup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var rendered = new AtomicBoolean();
            var completion = fixture.misc.dispatchPreparedConsoleLineAsync(fixture.plugin, () -> {
                assertTrue(fixture.globalOwner.get());
                rendered.set(true);
                when(fixture.plugin.isEnabled()).thenReturn(false);
                return new TextComponent("Late");
            }).toCompletableFuture();
            fixture.global.get(0).run();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion::join).getCause());
            assertTrue(rendered.get());
            fixture.bukkit.verify(Bukkit::getConsoleSender, never());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void disableDuringRecipientAdmissionFencesBroadcastConsoleRenderer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var rendered = new AtomicBoolean();
            doAnswer(call -> {
                Runnable task = call.getArgument(1);
                Player player = call.getArgument(2);
                fixture.owners.add(() -> {
                    fixture.currentOwner.set(player);
                    try { task.run(); } finally { fixture.currentOwner.set(null); }
                });
                when(fixture.plugin.isEnabled()).thenReturn(false);
                return null;
            }).when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), any(Player.class));
            var completion = fixture.misc.broadcastPreparedMessageAsync(fixture.renderer, () -> {
                rendered.set(true);
                return "Late";
            }).toCompletableFuture();
            fixture.global.get(0).run();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion::join).getCause());
            assertFalse(rendered.get());
            fixture.owners.forEach(Runnable::run);
            assertTrue(fixture.rendered.isEmpty());
            fixture.bukkit.verify(Bukkit::getConsoleSender, never());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void audienceCaptureDeadlineFencesLateGlobalCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var capture = fixture.misc.captureOnlinePlayerIdsAsync(fixture.plugin, fixture.deadline::set);
            fixture.deadline.get().run();
            assertThrows(CompletionException.class, capture.toCompletableFuture()::join);
            fixture.global.get(0).run();
            fixture.bukkit.verify(Bukkit::getOnlinePlayers, never());
        }
    }

    @Test void audienceCaptureClaimsDuplicateGlobalCallbacksOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var capture = fixture.misc.captureOnlinePlayerIdsAsync(fixture.plugin, fixture.deadline::set);
            fixture.global.get(0).run();
            fixture.global.get(0).run();
            assertEquals(List.of(fixture.first.getUniqueId(), fixture.second.getUniqueId()),
                    capture.toCompletableFuture().join());
        }
    }

    @Test void disabledPluginRejectsAudienceCaptureBeforeInspectingPlayers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.plugin.isEnabled()).thenReturn(false);
            var capture = fixture.misc.captureOnlinePlayerIdsAsync(fixture.plugin, fixture.deadline::set);
            fixture.global.get(0).run();
            assertThrows(CompletionException.class, capture.toCompletableFuture()::join);
            fixture.bukkit.verify(Bukkit::getOnlinePlayers, never());
        }
    }

    @Test void foliaRetirementSettlesWithoutWaitingForTheDispatchDeadline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.global.get(0).run();
            nativeScheduler.retired.get(0).run();
            fixture.owners.get(1).run();
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of(fixture.second), fixture.rendered);
            verify(fixture.transport, never()).sendMessage(eq(fixture.first), any(BaseComponent.class));
            fixture.deadline.get().run();
            assertFalse(completion.toCompletableFuture().isCompletedExceptionally());
        }
    }

    @Test void retiredRecipientCompletionContinuationRunsOutsideNativeRetirementCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var insideRetirement = new AtomicBoolean();
            var completion = fixture.withControlledDeadline(fixture.renderer);
            completion.whenComplete((ignored, failure) -> assertFalse(insideRetirement.get()));
            fixture.global.get(0).run();
            insideRetirement.set(true);
            nativeScheduler.retired.get(0).run();
            insideRetirement.set(false);
            fixture.owners.get(1).run();
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test void alreadyRetiredFoliaEntityIsSkippedAndOtherRecipientStillCompletes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.nativeSchedulers();
            when(((EntityOwner) fixture.first).getScheduler().execute(any(), any(), any(), anyLong())).thenReturn(false);
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.global.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            fixture.owners.get(0).run();
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of(fixture.second), fixture.rendered);
        }
    }

    @Test void repeatedRetirementAndLateRunCannotSettleOrDeliverTheRecipientTwice() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.global.get(0).run();
            nativeScheduler.retired.get(0).run();
            nativeScheduler.retired.get(0).run();
            fixture.owners.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            fixture.owners.get(1).run();
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of(fixture.second), fixture.rendered);
        }
    }

    @Test void nativeSchedulingExceptionFailsAndFencesQueuedRecipients() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.nativeSchedulers();
            IllegalStateException failure = new IllegalStateException("native scheduler rejected");
            when(((EntityOwner) fixture.second).getScheduler().execute(any(), any(), any(), anyLong())).thenThrow(failure);
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.global.get(0).run();
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.get(0).run();
            assertTrue(fixture.rendered.isEmpty());
        }
    }

    @Test void completionWaitsForGlobalConsoleAndEveryRecipientDelivery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CompletionStage<Void> completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            assertFalse(completion.toCompletableFuture().isDone());
            verifyNoInteractions(fixture.console, fixture.transport);
            fixture.global.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            verify(fixture.console).sendMessage(contains("R&D &aLiteral"));
            fixture.owners.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            fixture.owners.get(1).run();
            completion.toCompletableFuture().join();
            assertEquals(List.of(fixture.first, fixture.second), fixture.rendered);
            verify(fixture.transport).sendMessage(eq(fixture.first), any(BaseComponent.class));
            verify(fixture.transport).sendMessage(eq(fixture.second), any(BaseComponent.class));
        }
    }

    @Test void globalSchedulingFailureIsExceptionalBeforeAnyDelivery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("global scheduler rejected");
            doThrow(failure).when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class));
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            assertTrue(fixture.global.isEmpty());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void recipientSchedulingFailureFencesAlreadyQueuedRecipients() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("entity retired");
            doThrow(failure).when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), eq(fixture.second));
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.get(0).run();
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void renderingFailureIsExceptionalAndRemainingRecipientsCannotDeliverLate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalArgumentException failure = new IllegalArgumentException("invalid component");
            var completion = fixture.misc.broadcastPreparedComponentsAsync(player -> { throw failure; }, fixture.consoleLines());
            fixture.global.get(0).run();
            fixture.owners.get(0).run();
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.get(1).run();
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void transportFailureNeverReportsSuccessfulBroadcast() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("transport failed");
            doThrow(failure).when(fixture.transport).sendMessage(eq(fixture.first), any(BaseComponent.class));
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            fixture.owners.get(0).run();
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.get(1).run();
            verify(fixture.transport, never()).sendMessage(eq(fixture.second), any(BaseComponent.class));
        }
    }

    @Test void disabledGlobalCallbackFailsWithoutInspectingPlayers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            when(fixture.plugin.isEnabled()).thenReturn(false);
            fixture.global.get(0).run();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.bukkit.verify(Bukkit::getOnlinePlayers, never());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void disconnectedRecipientIsSkippedAndOtherRecipientsStillGateCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            when(fixture.first.isOnline()).thenReturn(false);
            fixture.owners.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            fixture.owners.get(1).run();
            completion.toCompletableFuture().join();
            assertEquals(List.of(fixture.second), fixture.rendered);
            verify(fixture.transport, never()).sendMessage(eq(fixture.first), any(BaseComponent.class));
        }
    }

    @Test void disabledRecipientCallbackFailsAndFencesOtherRecipients() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            when(fixture.plugin.isEnabled()).thenReturn(false);
            fixture.owners.get(0).run(); fixture.owners.get(1).run();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void consoleFailureIsExceptionalAndFencesPendingRecipients() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("console unavailable");
            doThrow(failure).when(fixture.console).sendMessage(anyString());
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            assertSame(failure, assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.forEach(Runnable::run);
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void cancellingCompletionFencesQueuedRecipientCallbacks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run();
            assertTrue(completion.toCompletableFuture().cancel(false));
            fixture.owners.forEach(Runnable::run);
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void deadlineFencesGlobalCallbackBeforeAnyGameStateAccess() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.deadline.get().run();
            assertInstanceOf(TimeoutException.class,
                    assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.global.get(0).run();
            fixture.bukkit.verify(Bukkit::getOnlinePlayers, never());
            verifyNoInteractions(fixture.console, fixture.transport);
        }
    }

    @Test void deadlineFencesRecipientCallbacksAfterGlobalDispatch() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.withControlledDeadline(fixture.renderer);
            fixture.global.get(0).run();
            assertFalse(completion.toCompletableFuture().isDone());
            fixture.deadline.get().run();
            assertInstanceOf(TimeoutException.class,
                    assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            fixture.owners.forEach(Runnable::run);
            assertTrue(fixture.rendered.isEmpty());
            verify(fixture.first, never()).isOnline();
            verify(fixture.second, never()).isOnline();
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void deadlineDuringRenderingPreventsSubsequentNativeSend() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.withControlledDeadline(player -> {
                fixture.deadline.get().run();
                return new TextComponent[] {new TextComponent("late")};
            });
            fixture.global.get(0).run(); fixture.owners.get(0).run();
            assertInstanceOf(TimeoutException.class,
                    assertThrows(CompletionException.class, completion.toCompletableFuture()::join).getCause());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void duplicateSchedulerCallbacksCannotRepeatDelivery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var completion = fixture.misc.broadcastPreparedComponentsAsync(fixture.renderer, fixture.consoleLines());
            fixture.global.get(0).run(); fixture.global.get(0).run();
            fixture.owners.forEach(task -> {task.run(); task.run();});
            completion.toCompletableFuture().join();
            assertEquals(List.of(fixture.first, fixture.second), fixture.rendered);
            verify(fixture.console, times(1)).sendMessage(anyString());
            verify(fixture.transport, times(1)).sendMessage(eq(fixture.first), any(BaseComponent.class));
            verify(fixture.transport, times(1)).sendMessage(eq(fixture.second), any(BaseComponent.class));
        }
    }

    @Test void recipientCaptureUsesItsOwnerBeforeReturningComponentsAndDoesNotSend() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            var capture = fixture.misc.capturePreparedRecipientAsync(fixture.plugin, id, fixture.renderer);
            assertFalse(capture.toCompletableFuture().isDone());
            fixture.global.get(0).run(); fixture.global.get(0).run();
            assertTrue(fixture.rendered.isEmpty());
            fixture.owners.get(0).run(); fixture.owners.get(0).run();
            var prepared = capture.toCompletableFuture().join();
            assertSame(fixture.first, prepared.player());
            assertEquals(List.of(fixture.first), fixture.rendered);
            assertEquals("R&D &aLiteral", prepared.components()[0].toPlainText());
            var returned = prepared.components(); returned[0] = null;
            assertNotNull(prepared.components()[0], "component-array ownership must not escape");
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void cancelledRecipientCaptureFencesQueuedCallbacks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var capture = fixture.misc.capturePreparedRecipientAsync(fixture.plugin,
                    fixture.first.getUniqueId(), fixture.renderer).toCompletableFuture();
            capture.cancel(false);
            fixture.global.get(0).run();
            assertTrue(fixture.rendered.isEmpty()); assertTrue(fixture.owners.isEmpty());
            assertTrue(capture.isCancelled()); verifyNoInteractions(fixture.transport);
        }
    }

    @Test void disabledRecipientCaptureDoesNotInvokeIntegrationRendering() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            var capture = fixture.misc.capturePreparedRecipientAsync(fixture.plugin, id, fixture.renderer).toCompletableFuture();
            fixture.global.get(0).run();
            when(fixture.plugin.isEnabled()).thenReturn(false);
            fixture.owners.get(0).run();
            assertThrows(CompletionException.class, capture::join);
            assertTrue(fixture.rendered.isEmpty()); verifyNoInteractions(fixture.transport);
        }
    }

    @Test void disabledQueuedAbsentRecipientFailsBeforeLookupAndReplayCheckpoints() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
            var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
            assertEquals(2, checkpoints.size(), "outer and fragment snapshots were admitted before dispatch");
            assertFalse(completion.isDone());
            when(fixture.plugin.isEnabled()).thenReturn(false);
            assertDoesNotThrow(() -> fixture.global.get(0).run());
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(CompletionException.class, completion::join).getCause());
            assertEquals(2, checkpoints.size(), "shutdown must not persist fragment or outer completion");
            fixture.bukkit.verify(() -> Bukkit.getPlayer(id), never());
            assertTrue(fixture.owners.isEmpty());
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport, fixture.console);
        }
    }

    @Test void disabledRetiredRecipientDeliveryFailsWithoutAdvancingReplayCheckpoints() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
            var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
            fixture.global.get(0).run();
            assertEquals(2, checkpoints.size());
            when(fixture.plugin.isEnabled()).thenReturn(false);
            assertDoesNotThrow(() -> nativeScheduler.retired.get(0).run());
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> completion.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(2, checkpoints.size(), "retirement after disable is failure, not completed delivery");
            fixture.owners.get(0).run();
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void disabledRetiredRecipientCaptureFailsInsteadOfPublishingAbsence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            var capture = fixture.misc.capturePreparedRecipientAsync(fixture.plugin, id, fixture.renderer)
                    .toCompletableFuture();
            fixture.global.get(0).run();
            when(fixture.plugin.isEnabled()).thenReturn(false);
            assertDoesNotThrow(() -> nativeScheduler.retired.get(0).run());
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> capture.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            fixture.owners.get(0).run();
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void disabledRetiredOrdinaryBroadcastFailsAndFencesLateOwners() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var nativeScheduler = fixture.nativeSchedulers();
            var completion = fixture.withControlledDeadline(fixture.renderer).toCompletableFuture();
            fixture.global.get(0).run();
            when(fixture.plugin.isEnabled()).thenReturn(false);
            nativeScheduler.retired.forEach(Runnable::run);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> completion.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            fixture.owners.forEach(Runnable::run);
            assertTrue(fixture.rendered.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void enabledAbsentAndOfflineRecipientsStillAdvanceTheirReplayCheckpointsOnce() throws Exception {
        for (boolean absent : List.of(true, false)) {
            try (Fixture fixture = new Fixture()) {
                var id = fixture.first.getUniqueId();
                if (!absent) {
                    fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
                    when(fixture.first.isOnline()).thenReturn(false);
                }
                var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
                var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
                fixture.global.get(0).run(); fixture.global.get(0).run();
                if (!absent) { assertEquals(1, fixture.owners.size()); fixture.owners.get(0).run(); }
                completion.join();
                assertEquals(4, checkpoints.size(), "live skip completes the fragment and its outer recipient once");
                fixture.bukkit.verify(() -> Bukkit.getPlayer(id), times(1));
                assertTrue(fixture.rendered.isEmpty());
                verifyNoInteractions(fixture.transport);
            }
        }
    }

    @Test void enabledRetirementStillSkipsRecipientCaptureAndDelivery() throws Exception {
        for (boolean capture : List.of(true, false)) {
            try (Fixture fixture = new Fixture()) {
                var nativeScheduler = fixture.nativeSchedulers();
                var id = fixture.first.getUniqueId();
                fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
                var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
                var completion = capture ? fixture.misc.capturePreparedRecipientAsync(fixture.plugin, id, fixture.renderer)
                        : fixture.replayLiteralFragment(id, checkpoints);
                fixture.global.get(0).run();
                nativeScheduler.retired.get(0).run(); nativeScheduler.retired.get(0).run();
                Object value = completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
                if (capture) assertNull(((MiscUtils.PreparedRecipient) value).player());
                else assertEquals(4, checkpoints.size());
                fixture.owners.get(0).run();
                assertTrue(fixture.rendered.isEmpty());
                verifyNoInteractions(fixture.transport);
            }
        }
    }

    @Test void cancelledLiteralDeliveryFencesQueuedGlobalLookup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            var completion = fixture.misc.dispatchPreparedComponentLineAsync(fixture.plugin, id,
                    player -> new TextComponent("Late")).toCompletableFuture();
            assertTrue(completion.cancel(false));
            fixture.global.get(0).run(); fixture.global.get(0).run();
            fixture.bukkit.verify(() -> Bukkit.getPlayer(id), never());
            assertTrue(fixture.owners.isEmpty());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void recipientLookupFailureCompletesExceptionallyWithoutEscapingQueuedCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            var failure = new IllegalStateException("synthetic player lookup failure");
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenThrow(failure);
            var completion = fixture.misc.dispatchPreparedComponentLineAsync(fixture.plugin, id,
                    player -> new TextComponent("Late")).toCompletableFuture();
            assertDoesNotThrow(() -> fixture.global.get(0).run());
            assertSame(failure, assertThrows(CompletionException.class, completion::join).getCause());
            assertTrue(fixture.owners.isEmpty());
        }
    }

    @Test void disableDuringAbsentLookupFailsBeforeSuccessfulSkip() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenAnswer(call -> {
                when(fixture.plugin.isEnabled()).thenReturn(false); return null;
            });
            var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
            var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
            fixture.global.get(0).run();
            assertThrows(CompletionException.class, completion::join);
            assertEquals(2, checkpoints.size());
        }
    }

    @Test void disableDuringOfflineCheckFailsBeforeSuccessfulSkip() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            when(fixture.first.isOnline()).thenAnswer(call -> {
                when(fixture.plugin.isEnabled()).thenReturn(false); return false;
            });
            var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
            var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
            fixture.global.get(0).run(); fixture.owners.get(0).run();
            assertThrows(CompletionException.class, completion::join);
            assertEquals(2, checkpoints.size());
            verifyNoInteractions(fixture.transport);
        }
    }

    @Test void disableAfterNativeSendRetainsPendingReplayCheckpoints() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var id = fixture.first.getUniqueId();
            fixture.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(fixture.first);
            doAnswer(call -> {
                assertSame(fixture.first, fixture.currentOwner.get());
                when(fixture.plugin.isEnabled()).thenReturn(false);
                return null;
            }).when(fixture.transport).sendMessage(eq(fixture.first), any(BaseComponent.class));
            var checkpoints = new ArrayList<Reward.ReplayCheckpoint>();
            var completion = fixture.replayLiteralFragment(id, checkpoints).toCompletableFuture();
            fixture.global.get(0).run(); fixture.owners.get(0).run();
            assertThrows(CompletionException.class, completion::join);
            assertEquals(2, checkpoints.size(), "the ambiguous native effect must not be acknowledged after disable");
            verify(fixture.transport, times(1)).sendMessage(eq(fixture.first), any(BaseComponent.class));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Player first = mock(Player.class, withSettings().extraInterfaces(EntityOwner.class));
        final Player second = mock(Player.class, withSettings().extraInterfaces(EntityOwner.class));
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        final IServerHandle transport = mock(IServerHandle.class);
        final ArrayList<Runnable> global = new ArrayList<>();
        final ArrayList<Runnable> owners = new ArrayList<>();
        final ArrayList<Player> rendered = new ArrayList<>();
        final AtomicReference<Player> currentOwner = new AtomicReference<>();
        final AtomicBoolean globalOwner = new AtomicBoolean();
        final AtomicReference<Runnable> deadline = new AtomicReference<>();
        final MiscUtils misc = MiscUtils.getInstance();
        final java.lang.reflect.Field pluginField;
        final Object previousPlugin;
        final org.mockito.MockedStatic<Bukkit> bukkit;
        final org.mockito.MockedStatic<PlayerUtils> utility;
        final Function<Player, TextComponent[]> renderer = player -> {
            assertSame(player, currentOwner.get()); rendered.add(player);
            return new TextComponent[] {new TextComponent("R&D &aLiteral")};
        };

        Fixture() throws Exception {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(first.isOnline()).thenReturn(true); when(second.isOnline()).thenReturn(true);
            when(first.getUniqueId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
            when(second.getUniqueId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"));
            doAnswer(call -> {
                Runnable task = call.getArgument(1);
                global.add(() -> {globalOwner.set(true); try {task.run();} finally {globalOwner.set(false);}});
                return null;
            }).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> {
                Runnable task = call.getArgument(1); Player player = call.getArgument(2);
                owners.add(() -> {currentOwner.set(player); try {task.run();} finally {currentOwner.set(null);}});
                return null;
            }).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            doAnswer(call -> {assertTrue(globalOwner.get()); return null;}).when(console).sendMessage(anyString());
            doAnswer(call -> {assertSame(call.getArgument(0), currentOwner.get()); return null;})
                    .when(transport).sendMessage(any(Player.class), any(BaseComponent.class));
            pluginField = MiscUtils.class.getDeclaredField("plugin"); pluginField.setAccessible(true);
            previousPlugin = pluginField.get(misc); pluginField.set(misc, plugin);
            bukkit = mockStatic(Bukkit.class); utility = mockStatic(PlayerUtils.class);
            bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> {assertTrue(globalOwner.get()); return List.of(first, second);});
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            utility.when(PlayerUtils::getServerHandle).thenReturn(transport);
        }

        TextComponent[] consoleLines() { return new TextComponent[] {new TextComponent("R&D &aLiteral")}; }

        CompletionStage<Void> replayLiteralFragment(java.util.UUID id, List<Reward.ReplayCheckpoint> checkpoints) {
            var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
                    .when(timer).execute(any(Runnable.class));
            var options = new RewardOptions();
            options.setAsyncReplayCheckpointConsumer(checkpoints::add);
            var state = Reward.replayStateFor(options);
            var metadata = new java.util.HashMap<String, String>();
            return Reward.replayNestedRewardSequence(plugin, metadata, "shutdown-outer", List.of("R|" + id + "|0"),
                    state, "shutdown-occurrence", (entry, ignored) -> Reward.replayNestedRewardSequence(plugin,
                            metadata, "shutdown-fragments", List.of("F|0"), state, "shutdown-occurrence",
                            (fragment, unused) -> misc.dispatchPreparedComponentLineAsync(plugin, id, player -> {
                                assertSame(player, currentOwner.get());
                                rendered.add(player);
                                return new TextComponent("Original");
                            }), fragment -> fragment.equals("F|0"), 4096, 16, 1048576L),
                    entry -> entry.equals("R|" + id + "|0"), 8192, 96, 1048576L);
        }

        private static final class NativeCallbacks {
            final ArrayList<Runnable> retired = new ArrayList<>();
        }

        NativeCallbacks nativeSchedulers() {
            var library = mock(com.bencodez.simpleapi.folialib.FoliaLib.class);
            when(library.isFolia()).thenReturn(true);
            when(scheduler.getFoliaLib()).thenReturn(library);
            NativeCallbacks callbacks = new NativeCallbacks();
            for (Player player : List.of(first, second)) {
                OwnerScheduler nativeScheduler = mock(OwnerScheduler.class);
                when(((EntityOwner) player).getScheduler()).thenReturn(nativeScheduler);
                when(nativeScheduler.execute(eq(plugin), any(), any(), eq(1L))).thenAnswer(call -> {
                    Runnable action = call.getArgument(1);
                    owners.add(() -> {currentOwner.set(player); try {action.run();} finally {currentOwner.set(null);}});
                    callbacks.retired.add(call.getArgument(2));
                    return true;
                });
            }
            return callbacks;
        }

        @SuppressWarnings("unchecked")
        CompletionStage<Void> withControlledDeadline(Function<Player, TextComponent[]> render) throws Exception {
            var method = MiscUtils.class.getDeclaredMethod("broadcastPreparedComponentsAsync", Function.class,
                    TextComponent[].class, Consumer.class);
            method.setAccessible(true);
            return (CompletionStage<Void>) method.invoke(misc, render, consoleLines(), (Consumer<Runnable>) deadline::set);
        }

        @Override public void close() throws Exception {
            utility.close(); bukkit.close(); pluginField.set(misc, previousPlugin);
        }
    }

    @Test
    void eachRecipientRendersOnItsOwnerAndConsoleNeverReformatsLiteralAmpersands() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        Player first = mock(Player.class);
        Player second = mock(Player.class);
        when(first.isOnline()).thenReturn(true);
        when(second.isOnline()).thenReturn(true);
        ArrayList<Runnable> global = new ArrayList<>();
        ArrayList<Runnable> owners = new ArrayList<>();
        doAnswer(call -> { global.add(call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        AtomicBoolean onOwner = new AtomicBoolean();
        doAnswer(call -> {
            Runnable work = call.getArgument(1);
            owners.add(() -> { onOwner.set(true); try { work.run(); } finally { onOwner.set(false); } });
            return null;
        }).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        IServerHandle transport = mock(IServerHandle.class);
        var field = MiscUtils.class.getDeclaredField("plugin");
        field.setAccessible(true);
        MiscUtils misc = MiscUtils.getInstance();
        Object previous = field.get(misc);
        field.set(misc, plugin);
        ArrayList<Player> rendered = new ArrayList<>();
        try (var bukkit = mockStatic(Bukkit.class); var utility = mockStatic(PlayerUtils.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, second));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            utility.when(PlayerUtils::getServerHandle).thenReturn(transport);
            misc.broadcastPreparedComponents(player -> {
                assertTrue(onOwner.get());
                rendered.add(player);
                return new TextComponent[] { new TextComponent("R&D &aLiteral") };
            }, new TextComponent[] { new TextComponent("R&D &aLiteral") });
            assertTrue(rendered.isEmpty());
            assertEquals(1, global.size());
            global.get(0).run();
            assertTrue(rendered.isEmpty());
            verify(console).sendMessage(contains("R&D &aLiteral"));
            assertEquals(2, owners.size());
            owners.forEach(Runnable::run);
            assertEquals(List.of(first, second), rendered);
            verify(transport).sendMessage(eq(first), any(BaseComponent.class));
            verify(transport).sendMessage(eq(second), any(BaseComponent.class));
        } finally {
            field.set(misc, previous);
        }
    }
}
