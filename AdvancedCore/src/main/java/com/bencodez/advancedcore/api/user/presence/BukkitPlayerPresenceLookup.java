package com.bencodez.advancedcore.api.user.presence;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.attribute.FileTime;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Read-only retained server history, with owner-observed joins in this generation. */
public final class BukkitPlayerPresenceLookup implements PlayerPresenceLookup, Listener, AutoCloseable {
    private static final int MAX_DIRECTORY_ENTRIES = 1_000_000;
    private static final int MAX_RECOVERY_IDENTITIES = 65536;
    private record ScopeIdentity(Object fileKey, FileTime created) { }
    private record RecoveryIndex(ScopeIdentity scope, FileTime modified, Set<UUID> identities, boolean offlineHistory) { }
    private static final RecoveryIndex RETIRED = new RecoveryIndex(null, null, Set.of(), true);
    private final AtomicReference<RecoveryIndex> recoveryIndex = new AtomicReference<>();
    private final Object recoveryScan = new Object();
    @FunctionalInterface interface AttributeReader {
        BasicFileAttributes read(Path path) throws IOException;
    }
    private final AttributeReader attributeReader;
    private final Path historyDirectory;
    private final HashSet<UUID> joined = new HashSet<>();
    private boolean negativeUnavailable;
    private boolean observedOfflineHistory;
    private boolean closed;

    /** The caller must supply the actual server-owned playerdata directory. */
    public BukkitPlayerPresenceLookup(Path historyDirectory) {
        this(historyDirectory, path -> Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
    }

    BukkitPlayerPresenceLookup(Path historyDirectory, AttributeReader attributeReader) {
        this.attributeReader = java.util.Objects.requireNonNull(attributeReader);
        Path candidate = historyDirectory == null ? null : historyDirectory.toAbsolutePath();
        if (candidate != null) for (Path part : candidate) {
            if (part.toString().equals("..")) { candidate = null; break; }
        }
        this.historyDirectory = candidate == null ? null : candidate.normalize();
    }

    /** Metadata only, on the platform owner. Never infer storage from a dimension path. */
    public static Path capturePrimaryHistoryDirectory(Server server) {
        if (server == null || !server.getClass().getName().matches(
                "org\\.bukkit\\.craftbukkit(?:\\.v[0-9]+_[0-9]+_R[0-9]+)?\\.CraftServer")) return null;
        try {
            try {
                Object directory = server.getClass().getMethod("getLevelDirectory").invoke(server);
                return directory instanceof Path path ? path.resolve("playerdata") : null;
            } catch (NoSuchMethodException olderApi) {
                Object players = server.getClass().getMethod("getHandle").invoke(server);
                Object storage = players.getClass().getField("playerIo").get(players);
                Object directory = storage.getClass().getMethod("getPlayerDir").invoke(storage);
                return directory instanceof File file ? file.toPath() : null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** Capture UUID-only online metadata on the platform owner after registering this listener. */
    public void captureJoinedPlayers(Server server) {
        try {
            if (server == null) throw new IllegalStateException("Unavailable server");
            int examined = 0;
            for (var player : server.getOnlinePlayers()) {
                if (++examined > 65536) throw new IllegalStateException("Join evidence limit");
                observeJoined(player.getUniqueId());
            }
        } catch (RuntimeException | LinkageError unavailable) {
            synchronized (joined) { negativeUnavailable = true; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        try { observeJoined(event.getPlayer().getUniqueId()); }
        catch (RuntimeException | LinkageError unavailable) {
            synchronized (joined) { negativeUnavailable = true; }
        }
    }

    private void observeJoined(UUID identity) {
        synchronized (joined) {
            if (closed) return;
            if (identity != null && !isOnlineIdentity(identity)) observedOfflineHistory = true;
            if (identity == null || (!joined.contains(identity) && joined.size() >= 65536))
                negativeUnavailable = true;
            else joined.add(identity);
        }
    }

    private PlayerPresence liveEvidence(UUID identity) {
        synchronized (joined) {
            if (closed) return PlayerPresence.UNKNOWN;
            return joined.contains(identity) ? PlayerPresence.JOINED : null;
        }
    }

    @Override
    public PlayerPresence check(UUID identity) {
        return check(identity, false);
    }

    @Override
    public PlayerPresence checkOnlineHistory(UUID identity) {
        return check(identity, true);
    }

    private PlayerPresence check(UUID identity, boolean completeOnlineHistory) {
        if (identity == null) return PlayerPresence.UNKNOWN;
        PlayerPresence live = liveEvidence(identity);
        if (live != null) return live;
        if (historyDirectory == null) return PlayerPresence.UNKNOWN;
        try {
            if (!unlinkedScope()) return PlayerPresence.UNKNOWN;
            BasicFileAttributes before = attributes(historyDirectory);
            ScopeIdentity scope = identity(before);
            if (!before.isDirectory() || before.isOther() || scope == null) return PlayerPresence.UNKNOWN;
            boolean present = false, valid = false, failed = false;
            for (String suffix : new String[] { ".dat", ".dat_old", ".dat_new", ".dat.tmp",
                    ".dat.offline-read", ".dat_old.offline-read" }) {
                Path file = historyDirectory.resolve(identity + suffix);
                try {
                    BasicFileAttributes record = attributes(file);
                    present = true;
                    ScopeIdentity recordIdentity = identity(record);
                    if (!record.isRegularFile() || record.isOther() || recordIdentity == null
                            || record.size() > PlayerHistoryFormat.MAX_COMPRESSED) continue;
                    byte[] bytes;
                    try (var input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                        ByteBuffer buffer = ByteBuffer.allocate((int) record.size() + 1);
                        while (buffer.hasRemaining() && input.read(buffer) != -1) { }
                        if (buffer.position() != record.size()) continue;
                        bytes = java.util.Arrays.copyOf(buffer.array(), buffer.position());
                    }
                    BasicFileAttributes after = attributes(file);
                    if (!recordIdentity.equals(identity(after)) || !after.isRegularFile() || after.isOther()
                            || record.size() != after.size()
                            || !record.lastModifiedTime().equals(after.lastModifiedTime())) continue;
                    try {
                        if (PlayerHistoryFormat.valid(bytes)) { valid = true; break; }
                    } catch (IOException invalid) { /* Malformed history stays unknown, never absent. */ }
                } catch (NoSuchFileException absent) {
                    // Only a confirmed missing-file result can support a negative.
                } catch (IOException unavailable) {
                    failed = true;
                    // A sibling may still establish positive retained history.
                }
            }
            // Native saves use UUID-random.dat; their existence prevents absence
            // proof even if a crash left no canonical record. Never load or alter them.
            boolean recovery = !valid && !present && !failed && hasRecovery(identity, before, completeOnlineHistory);
            BasicFileAttributes after = attributes(historyDirectory);
            if (!unlinkedScope() || !after.isDirectory() || after.isOther() || !scope.equals(identity(after)))
                return PlayerPresence.UNKNOWN;
            live = liveEvidence(identity);
            if (live != null) return live;
            if (valid) return PlayerPresence.JOINED;
            synchronized (joined) {
                if (closed || negativeUnavailable || present || failed || recovery
                        || (completeOnlineHistory && (observedOfflineHistory || !isSupportedOnlineAlias(identity)))
                        || !before.lastModifiedTime().equals(after.lastModifiedTime())) return PlayerPresence.UNKNOWN;
                return joined.contains(identity) ? PlayerPresence.JOINED : PlayerPresence.NEVER_JOINED;
            }
        } catch (IOException | RuntimeException unavailable) {
            return PlayerPresence.UNKNOWN;
        }
    }

    private boolean hasRecovery(UUID identity, BasicFileAttributes scope, boolean completeOnlineHistory) throws IOException {
        // Even a cached absence index needs currently readable directory scope.
        try (var ignored = Files.newDirectoryStream(historyDirectory)) { }
        synchronized (recoveryScan) {
            ScopeIdentity scopeIdentity = identity(scope);
            if (scopeIdentity == null) return true;
            RecoveryIndex cached = recoveryIndex.get();
            if (cached == RETIRED) return true;
            // Cached retained evidence can preserve uncertainty. Cached absence
            // cannot certify current entries even with a key: times may collide.
            if (scopeIdentity.fileKey() != null && cached != null && cached.scope().equals(scopeIdentity)
                    && cached.modified().equals(scope.lastModifiedTime())
                    && (cached.identities().contains(identity) || (completeOnlineHistory && cached.offlineHistory())))
                return true;
            HashSet<UUID> identities = new HashSet<>();
            boolean offlineHistory = false;
            int examined = 0;
            try (var entries = Files.newDirectoryStream(historyDirectory)) {
                for (Path entry : entries) {
                    if (++examined > MAX_DIRECTORY_ENTRIES) return true;
                    String name = entry.getFileName().toString();
                    if (name.length() < 40) continue;
                    String suffix = name.substring(36).toLowerCase(java.util.Locale.ROOT);
                    boolean temporary = suffix.startsWith("-") && suffix.endsWith(".dat")
                            && suffix.length() > 5;
                    boolean corrupted = suffix.startsWith("_corrupted_")
                            && (suffix.endsWith(".dat") || suffix.endsWith(".dat_old"));
                    UUID record;
                    try { record = UUID.fromString(name.substring(0, 36)); }
                    catch (IllegalArgumentException unrelated) { continue; }
                    if (!record.toString().equalsIgnoreCase(name.substring(0, 36))) continue;
                    // Native offline UUIDs can retain history under an earlier name.
                    // Their ownership cannot be inferred from the current name cache.
                    if (!isOnlineIdentity(record) && (suffix.startsWith(".dat") || temporary || corrupted))
                        offlineHistory = true;
                    if (!temporary && !corrupted) continue;
                    identities.add(record);
                    if (identities.size() > MAX_RECOVERY_IDENTITIES) return true;
                }
            }
            BasicFileAttributes after = attributes(historyDirectory);
            if (!unlinkedScope() || !after.isDirectory() || after.isOther() || !scopeIdentity.equals(identity(after))
                    || !scope.lastModifiedTime().equals(after.lastModifiedTime())) return true;
            RecoveryIndex scanned = new RecoveryIndex(scopeIdentity, scope.lastModifiedTime(), Set.copyOf(identities), offlineHistory);
            // close never waits for the scan and a retired scan cannot repopulate it.
            if (!recoveryIndex.compareAndSet(cached, scanned)) return true;
            return identities.contains(identity) || (completeOnlineHistory && offlineHistory);
        }
    }

    private static ScopeIdentity identity(BasicFileAttributes attributes) {
        Object key = attributes.fileKey();
        FileTime created = attributes.creationTime();
        // Some providers (including Windows) omit keys. An unavailable birth time
        // cannot support the fallback scope check; retained results stay unknown.
        if (key == null && (created == null || created.equals(FileTime.fromMillis(0)))) return null;
        return new ScopeIdentity(key, created);
    }

    private static boolean isOnlineIdentity(UUID identity) {
        return identity.version() == 4 && identity.variant() == 2;
    }

    private static boolean isSupportedOnlineAlias(UUID identity) {
        return identity.variant() == 2 && (identity.version() == 3 || identity.version() == 4);
    }

    private boolean unlinkedScope() {
        for (Path part = historyDirectory; part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) return false;
        return true;
    }

    private BasicFileAttributes attributes(Path path) throws IOException {
        return attributeReader.read(path);
    }

    @Override public void close() {
        synchronized (joined) { closed = true; joined.clear(); }
        recoveryIndex.set(RETIRED);
    }
}
