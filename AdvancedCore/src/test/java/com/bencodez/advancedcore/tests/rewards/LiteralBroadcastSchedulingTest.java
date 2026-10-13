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
