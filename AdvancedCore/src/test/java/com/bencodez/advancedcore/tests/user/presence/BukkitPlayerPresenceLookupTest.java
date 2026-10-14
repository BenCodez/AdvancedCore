package com.bencodez.advancedcore.tests.user.presence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import com.bencodez.advancedcore.api.user.presence.BukkitPlayerPresenceLookup;
import com.bencodez.advancedcore.api.user.presence.PlayerPresence;

class BukkitPlayerPresenceLookupTest {
    private static final UUID USER = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");
    @TempDir Path directory;

    static byte[] gzip(byte[] payload) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(payload); }
        return bytes.toByteArray();
    }

    static byte[] completeHistory() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF("");
            for (int type = 1; type <= 12; type++) {
                out.writeByte(type); out.writeUTF("entry-" + type);
                switch (type) {
                    case 1 -> out.writeByte(1);
                    case 2 -> out.writeShort(2);
                    case 3 -> out.writeInt(3);
                    case 4 -> out.writeLong(4);
                    case 5 -> out.writeFloat(5);
                    case 6 -> out.writeDouble(6);
                    case 7 -> { out.writeInt(2); out.write(new byte[] {1, 2}); }
                    case 8 -> out.writeUTF("déjà-joined-歴史 🎉\r\n adjacent text");
                    case 9 -> { out.writeByte(3); out.writeInt(2); out.writeInt(1); out.writeInt(2); }
                    case 10 -> { out.writeByte(8); out.writeUTF("name"); out.writeUTF("nested"); out.writeByte(0); }
                    case 11 -> { out.writeInt(1); out.writeInt(1); }
                    case 12 -> { out.writeInt(1); out.writeLong(1); }
                }
            }
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    @Test void completeAllTagHistoryIsReadOnlyAndDoesNotNeedServerLoader() throws Exception {
        byte[] record = gzip(completeHistory());
        Path file = Files.write(directory.resolve(USER + ".dat"), record);
        assertEquals(PlayerPresence.JOINED, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertArrayEquals(record, Files.readAllBytes(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void missingRecordNeedsReadableScopeAndNeverCreatesState() {
        assertEquals(PlayerPresence.NEVER_JOINED, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory.resolve("missing")).check(USER));
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(null).check(USER));
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(null));
    }

    @Test void everyRecoveryOrMigrationSuffixIsRetainedAndMalformedIsUnknown() throws Exception {
        for (String suffix : new String[] {".dat", ".dat_old", ".dat_new", ".dat.tmp",
                ".dat.offline-read", ".dat_old.offline-read"}) {
            Path file = directory.resolve(USER + suffix);
            Files.write(file, new byte[] {0});
            assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER));
            byte[] valid = gzip(completeHistory()); Files.write(file, valid);
            assertEquals(PlayerPresence.JOINED, new BukkitPlayerPresenceLookup(directory).check(USER));
            assertArrayEquals(valid, Files.readAllBytes(file)); Files.delete(file);
        }
    }

    @Test void unrelatedIdentityAndMigrationFilesAreNotClaimedOrRenamed() throws Exception {
        UUID historical = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        byte[] record = gzip(completeHistory()); Path old = Files.write(directory.resolve(historical + ".dat"), record);
        var lookup = new BukkitPlayerPresenceLookup(directory);
        assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        assertEquals(PlayerPresence.JOINED, lookup.check(historical));
        assertArrayEquals(record, Files.readAllBytes(old));
        assertFalse(Files.exists(directory.resolve(historical + ".dat.offline-read")));
    }

    @Test void migratedBackupCannotBeMistakenForMissingHistory() throws Exception {
        Path retained = directory.resolve(USER + ".dat_old.offline-read");
        byte[] valid = gzip(completeHistory()); Files.write(retained, valid);
        assertEquals(PlayerPresence.JOINED, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertArrayEquals(valid, Files.readAllBytes(retained));
        byte[] malformed = {0}; Files.write(retained, malformed);
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertArrayEquals(malformed, Files.readAllBytes(retained));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void interruptedNativeFirstSaveIsUnknownAndRetainedVerbatim() throws Exception {
        Path file = Files.createTempFile(directory, USER + "-", ".dat");
        byte[] record = gzip(completeHistory()); Files.write(file, record);
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertArrayEquals(record, Files.readAllBytes(file));
        Files.write(file, new byte[] {0});
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER));
        assertArrayEquals(new byte[] {0}, Files.readAllBytes(file));
    }

    @Test void recoveryEvidenceIsScopedToItsIdentityAndSurvivesMalformedContent() throws Exception {
        UUID other = UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381");
        Path temp = Files.createTempFile(directory, other + "-", ".dat");
        Files.write(temp, new byte[] {0});
        var lookup = new BukkitPlayerPresenceLookup(directory);
        assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(other));
        Files.write(directory.resolve(USER + "_corrupted_2026-10-14_01-00-00.dat_old"), new byte[] {0});
        Files.setLastModifiedTime(directory, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        assertArrayEquals(new byte[] {0}, Files.readAllBytes(temp));
        Files.write(directory.resolve(USER + ".dat"), gzip(completeHistory()));
        assertEquals(PlayerPresence.JOINED, lookup.check(USER));
        lookup.close();
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
    }

    @Test void recoveryIndexRefreshesAfterCreationDeletionAndDirectoryReplacement() throws Exception {
        Path scope = Files.createDirectory(directory.resolve("playerdata"));
        var lookup = new BukkitPlayerPresenceLookup(scope);
        assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        Path pending = Files.createTempFile(scope, USER + "-", ".dat");
        Files.setLastModifiedTime(scope, java.nio.file.attribute.FileTime.fromMillis(1000));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        Files.delete(pending);
        Files.setLastModifiedTime(scope, java.nio.file.attribute.FileTime.fromMillis(2000));
        assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        Files.move(scope, directory.resolve("previous"));
        Files.createDirectory(scope);
        Path replacementPending = Files.createTempFile(scope, USER + "-", ".dat");
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        Files.delete(replacementPending);
        Files.delete(scope);
        Files.createSymbolicLink(scope, directory.resolve("previous"));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
    }

    @Test void normalLargeHistoryDirectoryStillSupportsNegativeProof() throws Exception {
        for (int i = 0; i < 1200; i++)
            Files.write(directory.resolve(new UUID(0, i) + ".dat"), new byte[] {0});
        Files.write(directory.resolve("unrelated-random.dat"), new byte[] {0});
        var lookup = new BukkitPlayerPresenceLookup(directory);
        assertEquals(PlayerPresence.NEVER_JOINED, lookup.check(USER));
        assertEquals(PlayerPresence.NEVER_JOINED,
                lookup.check(UUID.fromString("b3f2bd99-5325-4a51-a642-12674e147381")));
        try (var files = Files.list(directory)) { assertEquals(1201, files.count()); }
    }

    @Test void symbolicFileScopeAndDotDotCannotSupplyNegativeProof() throws Exception {
        Path real = Files.createDirectory(directory.resolve("déjà-joined-歴史"));
        Path alias = Files.createSymbolicLink(directory.resolve("alias"), real);
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(alias).check(USER));
        assertEquals(PlayerPresence.UNKNOWN,
                new BukkitPlayerPresenceLookup(alias.resolve("..").resolve(real.getFileName())).check(USER));
        Path file = Files.write(directory.resolve("record"), gzip(completeHistory()));
        Files.createSymbolicLink(real.resolve(USER + ".dat"), file);
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(real).check(USER));
        assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(file).check(USER));
    }

    @Test void joinedSnapshotAndEventsCoverUnsavedPlayersAndRetirement() {
        Player live = mock(Player.class); when(live.getUniqueId()).thenReturn(USER);
        Server server = mock(Server.class); doReturn(java.util.List.of(live)).when(server).getOnlinePlayers();
        var lookup = new BukkitPlayerPresenceLookup(directory); lookup.captureJoinedPlayers(server);
        assertEquals(PlayerPresence.JOINED, lookup.check(USER));
        verify(server).getOnlinePlayers(); verifyNoMoreInteractions(server);
        lookup.close(); lookup.onJoin(new PlayerJoinEvent(live, "synthetic"));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        var replacement = new BukkitPlayerPresenceLookup(null);
        replacement.onJoin(new PlayerJoinEvent(live, "synthetic"));
        assertEquals(PlayerPresence.JOINED, replacement.check(USER));
    }

    @Test void failedOnlineSnapshotDisablesNegativeEvidenceButKeepsValidHistoryPositive() throws Exception {
        Server server = mock(Server.class); when(server.getOnlinePlayers()).thenThrow(new IllegalStateException("synthetic"));
        var lookup = new BukkitPlayerPresenceLookup(directory); lookup.captureJoinedPlayers(server);
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
        Files.write(directory.resolve(USER + ".dat"), gzip(completeHistory()));
        assertEquals(PlayerPresence.JOINED, lookup.check(USER));
    }

    @Test void unsupportedServerCannotSupplyNegativeScope() {
        Server unsupported = mock(Server.class);
        assertNull(BukkitPlayerPresenceLookup.capturePrimaryHistoryDirectory(unsupported));
        verifyNoInteractions(unsupported);
        assertNull(BukkitPlayerPresenceLookup.capturePrimaryHistoryDirectory(null));
    }

    @TestFactory java.util.stream.Stream<DynamicTest> everyTruncatedCompleteFrameStaysUnknownAndUnchanged() throws Exception {
        byte[] record = gzip(completeHistory());
        return java.util.stream.IntStream.range(0, record.length).mapToObj(length ->
                DynamicTest.dynamicTest("truncated gzip bytes=" + length, () -> {
                    byte[] truncated = java.util.Arrays.copyOf(record, length);
                    Path file = Files.write(directory.resolve(USER + ".dat"), truncated);
                    assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER));
                    assertArrayEquals(truncated, Files.readAllBytes(file));
                }));
    }

    @Test void malformedTypedAndAdjacentPayloadsCannotBecomePositiveOrNegativeHistory() throws Exception {
        var bad = new java.util.ArrayList<byte[]>();
        byte[] payload = completeHistory(); byte[] valid = gzip(payload);
        bad.add(gzip(java.util.Arrays.copyOf(payload, payload.length - 1)));
        bad.add(gzip(java.util.Arrays.copyOf(payload, payload.length + 1)));
        bad.add(gzip(new byte[] {10, 0, 0, 13, 0, 0, 0})); // Unknown named tag.
        bad.add(gzip(new byte[] {10, 0, 0, 7, 0, 0, -1, -1, -1, -1, 0})); // Negative array length.
        bad.add(gzip(new byte[] {10, 0, 0, 9, 0, 0, 0, 0, 0, 0, 1, 0})); // Nonempty END list.
        bad.add(gzip(new byte[] {10, 0, 0, 8, 0, 0, 0, 1, (byte) 0xc2, 0})); // Truncated modified UTF.
        bad.add(gzip(new byte[] {10, 0, 0, 1, 0, 0, 1, 1, 0, 0, 2, 0})); // Duplicate name.
        byte[] checksum = valid.clone(); checksum[checksum.length - 8] ^= 1; bad.add(checksum);
        byte[] reserved = valid.clone(); reserved[3] = (byte) 128; bad.add(reserved);
        bad.add(java.util.Arrays.copyOf(valid, valid.length + 1));
        byte[] doubleFrame = new byte[valid.length * 2];
        System.arraycopy(valid, 0, doubleFrame, 0, valid.length);
        System.arraycopy(valid, 0, doubleFrame, valid.length, valid.length); bad.add(doubleFrame);
        var depthBytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(depthBytes)) {
            out.writeByte(10); out.writeUTF("");
            for (int depth = 0; depth < 70; depth++) { out.writeByte(10); out.writeUTF("nested"); }
            for (int depth = 0; depth <= 70; depth++) out.writeByte(0);
        }
        bad.add(gzip(depthBytes.toByteArray()));
        var nodes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(nodes)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("list");
            out.writeByte(1); out.writeInt(100001); out.write(new byte[100001]); out.writeByte(0);
        }
        bad.add(gzip(nodes.toByteArray()));
        byte[] expanded = new byte[8 * 1024 * 1024 + 1]; bad.add(gzip(expanded));
        bad.add(new byte[1024 * 1024 + 1]);
        for (int index = 0; index < bad.size(); index++) {
            Path file = Files.write(directory.resolve(USER + ".dat"), bad.get(index));
            assertEquals(PlayerPresence.UNKNOWN, new BukkitPlayerPresenceLookup(directory).check(USER), "case=" + index);
            assertArrayEquals(bad.get(index), Files.readAllBytes(file));
        }
    }

    @Test void optionalGzipFilenameAndHeaderChecksumRemainValidData() throws Exception {
        byte[] original = gzip(completeHistory());
        var header = new ByteArrayOutputStream();
        header.write(original, 0, 10);
        byte[] first = header.toByteArray(); first[3] = 10; header.reset(); header.write(first);
        header.write("synthetic.dat".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); header.write(0);
        var crc = new java.util.zip.CRC32(); crc.update(header.toByteArray());
        header.write((int) crc.getValue() & 255); header.write((int) (crc.getValue() >> 8) & 255);
        header.write(original, 10, original.length - 10);
        byte[] framed = header.toByteArray(); Files.write(directory.resolve(USER + ".dat"), framed);
        assertEquals(PlayerPresence.JOINED, new BukkitPlayerPresenceLookup(directory).check(USER));
    }

    @Test void validExactByteDepthAndNodeLimitsRemainAccepted() throws Exception {
        var records = new java.util.ArrayList<byte[]>();
        byte[] original = gzip(completeHistory());
        byte[] compressedLimit = new byte[1024 * 1024];
        System.arraycopy(original, 0, compressedLimit, 0, 10); compressedLimit[3] = 16;
        int padding = compressedLimit.length - original.length;
        java.util.Arrays.fill(compressedLimit, 10, 10 + padding - 1, (byte) 'a');
        System.arraycopy(original, 10, compressedLimit, 10 + padding, original.length - 10);
        records.add(compressedLimit);
        var expanded = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(expanded)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(7); out.writeUTF("");
            int count = 8 * 1024 * 1024 - 11;
            out.writeInt(count); out.write(new byte[count]); out.writeByte(0);
        }
        assertEquals(8 * 1024 * 1024, expanded.size()); records.add(gzip(expanded.toByteArray()));
        var depth = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(depth)) {
            out.writeByte(10); out.writeUTF("");
            for (int index = 0; index < 64; index++) { out.writeByte(10); out.writeUTF("nested"); }
            for (int index = 0; index <= 64; index++) out.writeByte(0);
        }
        records.add(gzip(depth.toByteArray()));
        var nodes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(nodes)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("list");
            out.writeByte(1); out.writeInt(99998); out.write(new byte[99998]); out.writeByte(0);
        }
        records.add(gzip(nodes.toByteArray()));
        for (int index = 0; index < records.size(); index++) {
            Files.write(directory.resolve(USER + ".dat"), records.get(index));
            assertEquals(PlayerPresence.JOINED, new BukkitPlayerPresenceLookup(directory).check(USER), "limit=" + index);
        }
    }

    @Test void joinEvidenceSaturationKeepsUnknownVisibleAndPreservesKnownPositive() {
        var lookup = new BukkitPlayerPresenceLookup(directory);
        Player live = mock(Player.class);
        var index = new java.util.concurrent.atomic.AtomicLong();
        when(live.getUniqueId()).thenAnswer(call -> new UUID(0, index.incrementAndGet()));
        PlayerJoinEvent event = new PlayerJoinEvent(live, "synthetic");
        for (int count = 0; count < 65537; count++) lookup.onJoin(event);
        assertEquals(PlayerPresence.JOINED, lookup.check(new UUID(0, 1)));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(new UUID(0, 65537)));
        assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
    }
    @Test void savingPolicyDefaultsTypesAndFailuresOnlyRestrictNegativeEvidence() throws Exception {
        for (Object disabled : new Object[] {null, false, true, "false", 0}) {
            var config = new org.bukkit.configuration.file.YamlConfiguration(); config.set("players.disable-saving", disabled);
            var server = mock(Server.class); var spigot = mock(Server.Spigot.class);
            when(server.spigot()).thenReturn(spigot); when(spigot.getConfig()).thenReturn(config);
            try (var lookup = new BukkitPlayerPresenceLookup(directory)) {
                lookup.capturePlayerSaving(server);
                PlayerPresence expected = disabled == null || Boolean.FALSE.equals(disabled)
                        ? PlayerPresence.NEVER_JOINED : PlayerPresence.UNKNOWN;
                assertEquals(expected, lookup.check(USER)); assertEquals(expected, lookup.checkOnlineHistory(USER));
            }
        }
        for (Server server : new Server[] {null, mock(Server.class), failingSavingServer()}) {
            try (var lookup = new BukkitPlayerPresenceLookup(directory)) {
                lookup.capturePlayerSaving(server);
                assertEquals(PlayerPresence.UNKNOWN, lookup.check(USER));
                assertEquals(PlayerPresence.UNKNOWN, lookup.checkOnlineHistory(USER));
            }
        }
    }

    private Server failingSavingServer() {
        var server = mock(Server.class); var spigot = mock(Server.Spigot.class);
        when(server.spigot()).thenReturn(spigot);
        when(spigot.getConfig()).thenThrow(new UnsupportedOperationException("synthetic unavailable"));
        return server;
    }

    @Test void disabledOrUnknownSavingPreservesRetainedPositivesAndCannotReenableNegatives() throws Exception {
        byte[] record = gzip(completeHistory()); Path file = Files.write(directory.resolve(USER + ".dat"), record);
        var server = mock(Server.class); var spigot = mock(Server.Spigot.class);
        var config = new org.bukkit.configuration.file.YamlConfiguration(); config.set("players.disable-saving", true);
        when(server.spigot()).thenReturn(spigot); when(spigot.getConfig()).thenReturn(config);
        try (var lookup = new BukkitPlayerPresenceLookup(directory)) {
            lookup.capturePlayerSaving(server);
            assertEquals(PlayerPresence.JOINED, lookup.check(USER));
            assertEquals(PlayerPresence.JOINED, lookup.checkOnlineHistory(USER));
            config.set("players.disable-saving", false); lookup.capturePlayerSaving(server);
            assertEquals(PlayerPresence.UNKNOWN, lookup.check(new UUID(0, 2)));
            assertArrayEquals(record, Files.readAllBytes(file));
        }
    }

}
