package com.bencodez.advancedcore.bukkit.platform;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.core.platform.PlatformPlayer;
import com.bencodez.advancedcore.core.platform.PlatformScheduler;
import com.bencodez.advancedcore.core.platform.PlatformServices;

/**
 * Uses the existing plugin-owned SimpleAPI Bukkit/Folia scheduler. Creates no
 * executor, listener, task registry, persistent user, or additional player cache.
 * The supplier is lazy, including during construction, for legacy facade owners.
 */
public final class BukkitPlatformServices implements PlatformServices {
    private final Supplier<AdvancedCorePlugin> plugin;
    private final PlatformScheduler scheduler = new Scheduler();

    public BukkitPlatformServices(Supplier<AdvancedCorePlugin> plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Optional<PlatformPlayer> findOnlinePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return Optional.of(new OnlinePlayer(playerId, player));
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public boolean dispatchConsoleCommand(String command) {
        return Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(Objects.requireNonNull(message, "message"));
    }

    private AdvancedCorePlugin owner() {
        return Objects.requireNonNull(plugin.get(), "plugin is not initialized");
    }

    private final class Scheduler implements PlatformScheduler {
        @Override
        public void runServer(Runnable task) {
            Objects.requireNonNull(task, "task");
            AdvancedCorePlugin owner = owner();
            owner.getBukkitScheduler().runTask(owner, task);
        }

        @Override
        public void runServerLater(Runnable task, long delaySeconds) {
            Objects.requireNonNull(task, "task");
            if (delaySeconds < 0) {
                throw new IllegalArgumentException("delaySeconds must not be negative");
            }
            AdvancedCorePlugin owner = owner();
            // This existing overload explicitly uses TimeUnit.SECONDS, not ticks.
            owner.getBukkitScheduler().runTaskLater(owner, task, delaySeconds);
        }

        @Override
        public boolean runPlayer(UUID playerId, Consumer<PlatformPlayer> task) {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(task, "task");
            // Async callers may locate the entity, but must not read player
            // state until the entity scheduler transfers us to its owning thread.
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return false;
            }
            AdvancedCorePlugin owner = owner();
            OnlinePlayer session = new OnlinePlayer(playerId, player);
            // Never pass null: SimpleAPI's entity overload otherwise falls back
            // to global scheduling. Capture this login, not a future UUID lookup.
            owner.getBukkitScheduler().runTask(owner, () -> {
                if (owner.isEnabled() && session.isOnline()) {
                    task.accept(session);
                }
            }, player);
            return true;
        }
    }

    private static final class OnlinePlayer implements PlatformPlayer {
        private final UUID playerId;
        private final Player player;

        private OnlinePlayer(UUID playerId, Player player) {
            this.playerId = playerId;
            this.player = player;
        }

        @Override
        public UUID getUniqueId() {
            return playerId;
        }

        @Override
        public String getName() {
            return player.getName();
        }

        @Override
        public boolean isOnline() {
            return player.isOnline() && Bukkit.getPlayer(playerId) == player;
        }

        @Override
        public boolean hasPermission(String permission) {
            Objects.requireNonNull(permission, "permission");
            return isOnline() && player.hasPermission(permission);
        }

        @Override
        public void sendMessage(String message) {
            Objects.requireNonNull(message, "message");
            requireOnline();
            player.sendMessage(message);
        }

        @Override
        public boolean performCommand(String command) {
            Objects.requireNonNull(command, "command");
            requireOnline();
            return player.performCommand(command);
        }

        private void requireOnline() {
            if (!isOnline()) {
                throw new IllegalStateException("Player session is no longer online");
            }
        }
    }
}
