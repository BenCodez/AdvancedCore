package com.bencodez.advancedcore.tests.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.bencodez.advancedcore.core.platform.ConsoleCommandDispatcher;
import com.bencodez.advancedcore.core.platform.PlatformPlayer;
import com.bencodez.advancedcore.core.platform.PlatformScheduler;
import com.bencodez.advancedcore.core.platform.PlatformServices;

/** JDK-only behavioral fixture, also executed in a no-server-API class loader. */
public final class PlatformServicesFixture {
    private PlatformServicesFixture() { }

    public static void main(String[] args) {
        run();
        System.out.println("PlatformServicesFixture passed");
    }

    public static void run() {
        FakePlatform platform = new FakePlatform();
        ConsoleCommandDispatcher dispatcher = new ConsoleCommandDispatcher(platform);
        String[] input = {"/one", "//two", " /three", "[Javascript=literal]", "", null};
        String[] expected = {"one", "/two", " /three", "[Javascript=literal]", "", null};
        for (int i = 0; i < input.length; i++) dispatcher.dispatch(input[i], i, true);
        require(platform.commands.isEmpty(), "dispatch happened before handoff");
        require(platform.delays.equals(List.of(0L, 1L, 2L, 3L, 4L, 5L)), "stagger changed");
        platform.pending.forEach(Runnable::run);
        require(platform.commands.equals(java.util.Arrays.asList(expected)), "command text changed");

        FakePlatform immediate = new FakePlatform();
        ConsoleCommandDispatcher other = new ConsoleCommandDispatcher(immediate);
        other.dispatch("one", 7, false);
        other.dispatch("two", -2, true);
        other.dispatch("three", 0, true);
        require(immediate.delays.equals(List.of(0L, 0L, 0L)), "immediate branch changed");

        FakePlayer player = new FakePlayer();
        platform.player = player;
        require(platform.findOnlinePlayer(player.id).orElseThrow() == player, "UUID lookup");
        require(platform.findOnlinePlayer(UUID.randomUUID()).isEmpty(), "unknown UUID");
        require(!platform.runPlayer(UUID.randomUUID(), p -> { throw new AssertionError("offline"); }), "offline handoff");
        platform.runPlayer(player.id, p -> {
            require(p.getUniqueId().equals(player.id) && p.getName().equals("Ben"), "identity");
            require(!p.hasPermission("denied"), "permission denial");
            if (p.hasPermission("allowed")) p.sendMessage("already rendered");
            require(p.performCommand("say literal"), "player command result");
        });
        platform.pending.get(platform.pending.size() - 1).run();
        require(player.messages.equals(List.of("already rendered")), "message");
        require(player.commands.equals(List.of("say literal")), "player command");

        FakePlatform rejected = new FakePlatform();
        RuntimeException failure = new IllegalStateException("scheduler stopped");
        rejected.failure = failure;
        try {
            new ConsoleCommandDispatcher(rejected).dispatch("one", 0, false);
            throw new AssertionError("rejection hidden");
        } catch (IllegalStateException actual) {
            require(actual == failure, "rejection replaced");
        }
        require(rejected.commands.isEmpty(), "rejection dispatched work");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakePlatform implements PlatformServices, PlatformScheduler {
        private final List<Runnable> pending = new ArrayList<>();
        private final List<Long> delays = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
        private FakePlayer player;
        private RuntimeException failure;
        @Override public Optional<PlatformPlayer> findOnlinePlayer(UUID id) {
            return player != null && player.id.equals(id) ? Optional.of(player) : Optional.empty();
        }
        @Override public PlatformScheduler scheduler() { return this; }
        @Override public boolean dispatchConsoleCommand(String command) { commands.add(command); return true; }
        @Override public void sendConsoleMessage(String message) { }
        @Override public void runServer(Runnable task) { runServerLater(task, 0); }
        @Override public void runServerLater(Runnable task, long seconds) {
            if (failure != null) throw failure;
            pending.add(task); delays.add(seconds);
        }
        @Override public boolean runPlayer(UUID id, Consumer<PlatformPlayer> task) {
            Optional<PlatformPlayer> target = findOnlinePlayer(id);
            target.ifPresent(p -> pending.add(() -> task.accept(p)));
            return target.isPresent();
        }
    }

    private static final class FakePlayer implements PlatformPlayer {
        private final UUID id = UUID.randomUUID();
        private final List<String> messages = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
        @Override public UUID getUniqueId() { return id; }
        @Override public String getName() { return "Ben"; }
        @Override public boolean isOnline() { return true; }
        @Override public boolean hasPermission(String permission) { return "allowed".equals(permission); }
        @Override public void sendMessage(String message) { messages.add(message); }
        @Override public boolean performCommand(String command) { commands.add(command); return true; }
    }
}
