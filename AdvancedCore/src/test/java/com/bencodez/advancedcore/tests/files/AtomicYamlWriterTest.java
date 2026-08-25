package com.bencodez.advancedcore.tests.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.misc.files.AtomicYamlWriter;

public class AtomicYamlWriterTest {

	@TempDir
	Path tempDir;

	@Test
	public void replacesYamlWithoutLeavingTemporaryFiles() throws Exception {
		File file = tempDir.resolve("ServerData.yml").toFile();
		YamlConfiguration oldData = new YamlConfiguration();
		oldData.set("Old", true);
		oldData.save(file);

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New.Value", 42);
		AtomicYamlWriter.save(file, replacement);

		YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
		assertFalse(loaded.contains("Old"));
		assertEquals(42, loaded.getInt("New.Value"));
		assertEquals(1L, Files.list(tempDir).count());
	}

	@Test
	public void createsMissingParentDirectory() throws Exception {
		File file = tempDir.resolve("nested/data.yml").toFile();
		YamlConfiguration data = new YamlConfiguration();
		data.set("Ready", true);

		AtomicYamlWriter.save(file, data);

		assertTrue(file.isFile());
		assertTrue(YamlConfiguration.loadConfiguration(file).getBoolean("Ready"));
	}

	@Test
	public void newPosixFileUsesNormalCreationPermissions() throws Exception {
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX attributes are not available");

		Path normalCreate = tempDir.resolve("normal-create.yml");
		Files.createFile(normalCreate);
		Set<PosixFilePermission> expected = Files.getPosixFilePermissions(normalCreate);
		Files.delete(normalCreate);

		Path target = tempDir.resolve("atomic-create.yml");
		YamlConfiguration data = new YamlConfiguration();
		data.set("Ready", true);
		AtomicYamlWriter.save(target.toFile(), data);

		assertEquals(expected, Files.getPosixFilePermissions(target),
				"first-time saves should honor the same process umask as a normal file creation");
	}

	@Test
	public void preservesDestinationSymlink() throws Exception {
		Path sharedDir = tempDir.resolve("shared");
		Path localDir = tempDir.resolve("local");
		Files.createDirectories(sharedDir);
		Files.createDirectories(localDir);
		Path sharedTarget = sharedDir.resolve("ServerData.yml");
		YamlConfiguration original = new YamlConfiguration();
		original.set("Old", true);
		original.save(sharedTarget.toFile());

		Path link = localDir.resolve("ServerData.yml");
		try {
			Files.createSymbolicLink(link, localDir.relativize(sharedTarget));
		} catch (UnsupportedOperationException | SecurityException e) {
			assumeTrue(false, "symbolic links are not supported by this test environment");
		}

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(link.toFile(), replacement);

		assertTrue(Files.isSymbolicLink(link), "replacement must not replace the symlink itself");
		assertTrue(YamlConfiguration.loadConfiguration(sharedTarget.toFile()).getBoolean("New"));
		assertFalse(YamlConfiguration.loadConfiguration(sharedTarget.toFile()).contains("Old"));
	}

	@Test
	public void resolvesDotDotAfterIntermediateSymlinkUsingFilesystemSemantics() throws Exception {
		Path local = tempDir.resolve("local");
		Path elsewhere = tempDir.resolve("elsewhere");
		Path nested = elsewhere.resolve("nested");
		Files.createDirectories(local);
		Files.createDirectories(nested);

		Path directoryLink = local.resolve("dirlink");
		Path fileLink = local.resolve("ServerData.yml");
		try {
			Files.createSymbolicLink(directoryLink, local.relativize(nested));
			Files.createSymbolicLink(fileLink, Path.of("dirlink/../target.yml"));
		} catch (UnsupportedOperationException | SecurityException e) {
			assumeTrue(false, "symbolic links are not supported by this test environment");
		}

		Path expectedTarget = elsewhere.resolve("target.yml");
		YamlConfiguration original = new YamlConfiguration();
		original.set("Old", true);
		original.save(expectedTarget.toFile());

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(fileLink.toFile(), replacement);

		assertTrue(YamlConfiguration.loadConfiguration(expectedTarget.toFile()).getBoolean("New"));
		assertFalse(Files.exists(local.resolve("target.yml")),
				"lexical normalization must not redirect a symlink containing an intermediate link plus ..");
	}

	@Test
	public void rejectsCyclicSymlinkChains() throws Exception {
		Path first = tempDir.resolve("first.yml");
		Path second = tempDir.resolve("second.yml");
		try {
			Files.createSymbolicLink(first, second.getFileName());
			Files.createSymbolicLink(second, first.getFileName());
		} catch (UnsupportedOperationException | SecurityException e) {
			assumeTrue(false, "symbolic links are not supported by this test environment");
		}

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);

		IOException error = assertThrows(IOException.class, () -> AtomicYamlWriter.save(first.toFile(), replacement));
		assertTrue(error.getMessage().contains("Cyclic") || error.getMessage().contains("deep"));
	}

	@Test
	public void writableExistingFileSavesWhenParentDirectoryIsNotWritable() throws Exception {
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX attributes are not available");

		Path lockedDir = tempDir.resolve("locked");
		Files.createDirectory(lockedDir);
		Path target = lockedDir.resolve("ServerData.yml");
		Files.writeString(target, "Old: true\n");

		Set<PosixFilePermission> originalDirPermissions = Files.getPosixFilePermissions(lockedDir);
		Files.setPosixFilePermissions(target,
				EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		Files.setPosixFilePermissions(lockedDir,
				EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

		try {
			assumeTrue(!Files.isWritable(lockedDir),
					"test environment still permits sibling creation in a non-writable directory");
			assumeTrue(Files.isWritable(target),
					"existing target must remain writable for compatibility fallback");

			YamlConfiguration replacement = new YamlConfiguration();
			replacement.set("New", true);
			AtomicYamlWriter.save(target.toFile(), replacement);

			YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target.toFile());
			assertTrue(loaded.getBoolean("New"));
			assertFalse(loaded.contains("Old"));
		} finally {
			Files.setPosixFilePermissions(lockedDir, originalDirPermissions);
		}
	}

	@Test
	public void preservesExistingPosixPermissionsOwnershipAndInode() throws Exception {
		Path target = tempDir.resolve("permissions.yml");
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX attributes are not available");

		YamlConfiguration original = new YamlConfiguration();
		original.set("Old", true);
		original.save(target.toFile());
		Set<PosixFilePermission> expected = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.GROUP_READ);
		Files.setPosixFilePermissions(target, expected);
		PosixFileAttributes before = Files.readAttributes(target, PosixFileAttributes.class);
		Object beforeFileKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(target.toFile(), replacement);

		PosixFileAttributes after = Files.readAttributes(target, PosixFileAttributes.class);
		Object afterFileKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();
		assertEquals(expected, after.permissions());
		assertEquals(before.owner(), after.owner(), "save must preserve the file owner");
		assertEquals(before.group(), after.group(), "save must preserve the configured POSIX group");
		if (beforeFileKey != null && afterFileKey != null) {
			assertEquals(beforeFileKey, afterFileKey,
					"existing POSIX files must retain their inode so extended POSIX ACLs are not discarded");
		}
		assertFalse(Files.exists(tempDir.resolve(".permissions.yml.advancedcore-preserved-inode")));
	}

	@Test
	public void recoversPreservedInodeAfterInterruptedPosixCommit() throws Exception {
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX attributes are not available");

		Path target = tempDir.resolve("recovery.yml");
		YamlConfiguration original = new YamlConfiguration();
		original.set("Old", true);
		original.save(target.toFile());
		Object originalFileKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();

		Path preserved = tempDir.resolve(".recovery.yml.advancedcore-preserved-inode");
		try {
			Files.createLink(preserved, target);
		} catch (UnsupportedOperationException | SecurityException e) {
			assumeTrue(false, "hard links are not supported by this test environment");
		}

		Path simulatedLiveReplacement = tempDir.resolve("simulated-new.yml");
		YamlConfiguration alreadyCommitted = new YamlConfiguration();
		alreadyCommitted.set("Committed", true);
		alreadyCommitted.save(simulatedLiveReplacement.toFile());
		Files.move(simulatedLiveReplacement, target, StandardCopyOption.REPLACE_EXISTING);

		YamlConfiguration nextSave = new YamlConfiguration();
		nextSave.set("Final", true);
		AtomicYamlWriter.save(target.toFile(), nextSave);

		assertTrue(YamlConfiguration.loadConfiguration(target.toFile()).getBoolean("Final"));
		assertFalse(Files.exists(preserved));
		Object finalFileKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();
		if (originalFileKey != null && finalFileKey != null) {
			assertEquals(originalFileKey, finalFileKey,
					"recovery should restore the preserved inode before the next successful commit");
		}
	}
}
