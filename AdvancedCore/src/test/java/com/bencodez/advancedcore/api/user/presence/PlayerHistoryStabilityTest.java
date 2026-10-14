package com.bencodez.advancedcore.api.user.presence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerHistoryStabilityTest {
    private static final UUID USER = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");
    @TempDir Path directory;

    private byte[] history() throws Exception {
        var nbt = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(nbt)) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(1);
            out.writeByte(11); out.writeUTF("UUID"); out.writeInt(4);
            out.writeInt((int) (USER.getMostSignificantBits() >>> 32)); out.writeInt((int) USER.getMostSignificantBits());
            out.writeInt((int) (USER.getLeastSignificantBits() >>> 32)); out.writeInt((int) USER.getLeastSignificantBits());
            out.writeByte(10); out.writeUTF("bukkit");
            out.writeByte(8); out.writeUTF("lastKnownName"); out.writeUTF("Synthetic\r\n歴史"); out.writeByte(0);
            out.writeByte(0);
        }
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(nbt.toByteArray()); }
        return bytes.toByteArray();
    }

    private static BasicFileAttributes actual(Path path) throws java.io.IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static BasicFileAttributes withoutKey(BasicFileAttributes source) {
        return metadata(source, null, source.creationTime());
    }
    private static BasicFileAttributes metadata(BasicFileAttributes source, Object key, FileTime created) {
        return new BasicFileAttributes() {
            public FileTime lastModifiedTime() { return source.lastModifiedTime(); }
            public FileTime lastAccessTime() { return source.lastAccessTime(); }
            public FileTime creationTime() { return created; }
            public boolean isRegularFile() { return source.isRegularFile(); }
            public boolean isDirectory() { return source.isDirectory(); }
            public boolean isSymbolicLink() { return source.isSymbolicLink(); }
            public boolean isOther() { return source.isOther(); }
            public long size() { return source.size(); }
            public Object fileKey() { return key; }
        };
    }

    @Test void completeKeylessRecordCanSupplyPositiveHistoryWithoutMutation() throws Exception {
        byte[] record = history(); Path file = Files.write(directory.resolve(USER + ".dat"), record);
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertArrayEquals(record, Files.readAllBytes(file));
        }
    }

    @Test void keylessReadableUnchangedEmptyDirectoryCanSupplyNegativeHistory() {
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        }
    }

    @Test void readableValidCurrentRecordSurvivesUnreadableSibling() throws Exception {
        byte[] record = history(); Path file = Files.write(directory.resolve(USER + ".dat"), record);
        Path sibling = Files.write(directory.resolve(USER + ".dat_old"), new byte[] {0});
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            if (path.equals(sibling)) throw new AccessDeniedException("synthetic");
            return actual(path);
        })) {
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertArrayEquals(record, Files.readAllBytes(file));
        }
    }

    @Test void unreadableCurrentRecordDoesNotHideReadableBackupEvidence() throws Exception {
        Path current = Files.write(directory.resolve(USER + ".dat"), new byte[] {0});
        byte[] record = history(); Path backup = Files.write(directory.resolve(USER + ".dat_old"), record);
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            if (path.equals(current)) throw new AccessDeniedException("synthetic");
            return actual(path);
        })) {
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertArrayEquals(record, Files.readAllBytes(backup));
        }
    }
    @Test void absentKeysAndUnavailableCreationMetadataStayUnknown() throws Exception {
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> metadata(actual(path), null, FileTime.fromMillis(0)))) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
            Files.write(directory.resolve(USER + ".dat"), history());
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void changedKeylessDirectoryCreationCannotSupplyNegativeProof() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            var attrs = actual(path);
            FileTime created = attrs.creationTime();
            if (path.equals(directory) && reads.incrementAndGet() > 1)
                created = FileTime.fromMillis(created.toMillis() + 1000);
            return metadata(attrs, null, created);
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void changedKeylessRecordCreationCannotSupplyPositiveProof() throws Exception {
        Path file = Files.write(directory.resolve(USER + ".dat"), history());
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            var attrs = actual(path); FileTime created = attrs.creationTime();
            if (path.equals(file) && reads.incrementAndGet() > 1)
                created = FileTime.fromMillis(created.toMillis() + 1000);
            return metadata(attrs, null, created);
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void disappearingAvailableRecordKeyCannotSupplyPositiveProof() throws Exception {
        Path file = Files.write(directory.resolve(USER + ".dat"), history());
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            var attrs = actual(path);
            if (path.equals(file) && reads.incrementAndGet() > 1) return withoutKey(attrs);
            return attrs;
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void unreadableAbsentCandidateCannotSupplyNegativeProof() {
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> {
            if (path.equals(directory.resolve(USER + ".dat"))) throw new AccessDeniedException("synthetic");
            return actual(path);
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void keylessRecoveryRescansEvenWhenDirectoryTimestampsAreUnchanged() throws Exception {
        BasicFileAttributes unchangedScope = withoutKey(actual(directory));
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> path.equals(directory) ? unchangedScope : withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            Files.createTempFile(directory, USER + "-", ".dat");
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void keylessReplacementWithMatchingTimestampsCannotReuseAbsentRecovery() throws Exception {
        BasicFileAttributes unchangedScope = withoutKey(actual(directory));
        Path displaced = directory.resolveSibling(directory.getFileName() + "-displaced");
        try (var lookup = new BukkitPlayerPresenceLookup(directory,
                path -> path.equals(directory) ? unchangedScope : withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            Files.move(directory, displaced);
            Files.createDirectory(directory);
            Files.createTempFile(directory, USER + "-", ".dat");
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        } finally {
            Files.deleteIfExists(displaced);
        }
    }

    @Test void keylessCachedRecoveryIndexRefreshesAfterDirectoryChange() throws Exception {
        try (var lookup = new BukkitPlayerPresenceLookup(directory, path -> withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            Path retained = Files.createTempFile(directory, USER + "-", ".dat");
            Files.setLastModifiedTime(directory, FileTime.fromMillis(System.currentTimeMillis() + 2000));
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
            Files.delete(retained);
            Files.setLastModifiedTime(directory, FileTime.fromMillis(System.currentTimeMillis() + 4000));
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            lookup.close();
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

}
