package com.bencodez.advancedcore.tests.api.user.presence;

import com.bencodez.advancedcore.api.user.presence.BukkitPlayerPresenceLookup;
import com.bencodez.advancedcore.api.user.presence.PlayerPresence;

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
        return history(USER);
    }

    private byte[] history(UUID recordIdentity) throws Exception {
        var nbt = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(nbt)) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(1);
            out.writeByte(11); out.writeUTF("UUID"); out.writeInt(4);
            out.writeInt((int) (recordIdentity.getMostSignificantBits() >>> 32)); out.writeInt((int) recordIdentity.getMostSignificantBits());
            out.writeInt((int) (recordIdentity.getLeastSignificantBits() >>> 32)); out.writeInt((int) recordIdentity.getLeastSignificantBits());
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

    @Test void legacyUuidAbsenceAlsoRescansKeyedScopesWithCollidingTimes() throws Exception {
        BasicFileAttributes attrs = actual(directory);
        BasicFileAttributes fixed = metadata(attrs, new Object(), attrs.creationTime());
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> path.equals(directory) ? fixed : actual(path))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            Files.createTempFile(directory, USER + "-", ".dat");
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void cachedRecoveryForOneUuidCannotCertifyAnotherUuidAbsentWithCollidingTimes() throws Exception {
        BasicFileAttributes attrs = actual(directory);
        BasicFileAttributes fixed = metadata(attrs, new Object(), attrs.creationTime());
        UUID other = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        Files.createTempFile(directory, USER + "-", ".dat");
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> path.equals(directory) ? fixed : actual(path))) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
            Files.createTempFile(directory, other + "-", ".dat");
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(other));
        }
    }

    @Test void keyedScopeWithCollidingDirectoryTimesMustDiscoverNewOfflineHistory() throws Exception {
        BasicFileAttributes attrs = actual(directory);
        BasicFileAttributes fixed = metadata(attrs, new Object(), attrs.creationTime());
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> path.equals(directory) ? fixed : actual(path))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.checkOnlineHistory(USER));
            UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(directory.resolve(former + ".dat"), history(former));
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
        }
    }

    @Test void versionFourWithNonOnlineVariantRecordCannotCertifyOnlineAbsence() throws Exception {
        UUID legacy = UUID.fromString("00000000-0000-4000-0000-000000000001");
        assertEquals(4, legacy.version()); assertNotEquals(2, legacy.variant());
        Files.write(directory.resolve(legacy + ".dat"), history(legacy));
        try (var lookup = PresenceLookupAccess.create(directory)) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
        }
    }

    @Test void versionFourNonOnlineVariantJoinCannotCertifyOnlineAbsenceBeforeSave() {
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-4000-0000-000000000001"));
        var event = org.mockito.Mockito.mock(org.bukkit.event.player.PlayerJoinEvent.class);
        org.mockito.Mockito.when(event.getPlayer()).thenReturn(player);
        try (var lookup = PresenceLookupAccess.create(directory)) {
            lookup.onJoin(event);
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
        }
    }

    @Test void unsupportedOnlineAliasRetainsPositiveButCannotCertifyAbsentHistory() throws Exception {
        UUID legacy = UUID.fromString("00000000-0000-4000-0000-000000000001");
        try (var lookup = PresenceLookupAccess.create(directory)) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(legacy));
            Files.write(directory.resolve(legacy + ".dat"), history(legacy));
            assertEquals(PlayerPresence.JOINED, lookup.checkOnlineHistory(legacy));
        }
    }

    @Test void completeOnlineOnlyScopeCanProveAbsenceWithoutChangingLegacyApi() throws Exception {
        UUID anotherOnline = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        Files.write(directory.resolve(anotherOnline + ".dat"), history(anotherOnline));
        try (var lookup = PresenceLookupAccess.create(directory)) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.checkOnlineHistory(USER));
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        }
    }

    @Test void formerOfflineNameBlocksOnlineNegativeWithoutGuessingOwnership() throws Exception {
        UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] retained = history(former); Path record = Files.write(directory.resolve(former + ".dat"), retained);
        try (var lookup = PresenceLookupAccess.create(directory)) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
            assertArrayEquals(retained, Files.readAllBytes(record));
            Files.write(directory.resolve(USER + ".dat"), history());
            assertEquals(PlayerPresence.JOINED, lookup.checkOnlineHistory(USER));
        }
    }

    @Test void unmappedCorruptBackupAndInterruptedOfflineSavesBlockOnlineNegatives() throws Exception {
        UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String suffix : new String[] { ".dat_old", ".dat.offline-read", "-temporary.dat", "_corrupted_1.dat_old" }) {
            Path retained = Files.write(directory.resolve(former + suffix), new byte[] {0});
            try (var lookup = PresenceLookupAccess.create(directory)) {
                assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER), suffix);
                assertArrayEquals(new byte[] {0}, Files.readAllBytes(retained));
            } finally { Files.delete(retained); }
        }
    }

    @Test void nonOnlineUuidAndCaseInsensitiveRecoveryScopeCannotBecomeOnlineAbsence() throws Exception {
        UUID legacy = new UUID(0, 1);
        Path retained = Files.write(directory.resolve(legacy.toString().toUpperCase(java.util.Locale.ROOT) + "-SAVE.DAT"), new byte[] {0});
        try (var lookup = PresenceLookupAccess.create(directory)) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(legacy));
            assertArrayEquals(new byte[] {0}, Files.readAllBytes(retained));
        }
    }

    @Test void keylessScopeWithUnchangedTimesRescansNewOfflineRecords() throws Exception {
        BasicFileAttributes fixed = withoutKey(actual(directory));
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> path.equals(directory) ? fixed : withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.checkOnlineHistory(USER));
            UUID former = UUID.nameUUIDFromBytes("OfflinePlayer:FormerSynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(directory.resolve(former + ".dat"), history(former));
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
            lookup.close(); assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
        }
    }

    @Test void observedNonOnlineJoinPreventsUnmappedAbsenceBeforeFirstSave() {
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(new UUID(0, 1));
        var event = org.mockito.Mockito.mock(org.bukkit.event.player.PlayerJoinEvent.class);
        org.mockito.Mockito.when(event.getPlayer()).thenReturn(player);
        try (var lookup = PresenceLookupAccess.create(directory)) {
            lookup.onJoin(event);
            assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
            assertEquals(PlayerPresence.JOINED, lookup.checkOnlineHistory(player.getUniqueId()));
        }
    }

    @Test void completeKeylessRecordCanSupplyPositiveHistoryWithoutMutation() throws Exception {
        byte[] record = history(); Path file = Files.write(directory.resolve(USER + ".dat"), record);
        try (var lookup = PresenceLookupAccess.create(directory, path -> withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertArrayEquals(record, Files.readAllBytes(file));
        }
    }

    @Test void keylessReadableUnchangedEmptyDirectoryCanSupplyNegativeHistory() {
        try (var lookup = PresenceLookupAccess.create(directory, path -> withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        }
    }

    @Test void readableValidCurrentRecordSurvivesUnreadableSibling() throws Exception {
        byte[] record = history(); Path file = Files.write(directory.resolve(USER + ".dat"), record);
        Path sibling = Files.write(directory.resolve(USER + ".dat_old"), new byte[] {0});
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
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
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
            if (path.equals(current)) throw new AccessDeniedException("synthetic");
            return actual(path);
        })) {
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertArrayEquals(record, Files.readAllBytes(backup));
        }
    }
    @Test void absentKeysAndUnavailableCreationMetadataStayUnknown() throws Exception {
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> metadata(actual(path), null, FileTime.fromMillis(0)))) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
            Files.write(directory.resolve(USER + ".dat"), history());
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void changedKeylessDirectoryCreationCannotSupplyNegativeProof() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
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
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
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
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
            var attrs = actual(path);
            if (path.equals(file) && reads.incrementAndGet() > 1) return withoutKey(attrs);
            return attrs;
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void unreadableAbsentCandidateCannotSupplyNegativeProof() {
        try (var lookup = PresenceLookupAccess.create(directory, path -> {
            if (path.equals(directory.resolve(USER + ".dat"))) throw new AccessDeniedException("synthetic");
            return actual(path);
        })) {
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void keylessRecoveryRescansEvenWhenDirectoryTimestampsAreUnchanged() throws Exception {
        BasicFileAttributes unchangedScope = withoutKey(actual(directory));
        try (var lookup = PresenceLookupAccess.create(directory,
                path -> path.equals(directory) ? unchangedScope : withoutKey(actual(path)))) {
            assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
            Files.createTempFile(directory, USER + "-", ".dat");
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        }
    }

    @Test void keylessReplacementWithMatchingTimestampsCannotReuseAbsentRecovery() throws Exception {
        BasicFileAttributes unchangedScope = withoutKey(actual(directory));
        Path displaced = directory.resolveSibling(directory.getFileName() + "-displaced");
        try (var lookup = PresenceLookupAccess.create(directory,
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
        try (var lookup = PresenceLookupAccess.create(directory, path -> withoutKey(actual(path)))) {
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
