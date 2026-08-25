package com.bencodez.advancedcore.tests.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
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

		assertTrue(Files.isSymbolicLink(link), "atomic replacement must not replace the symlink itself");
		assertTrue(YamlConfiguration.loadConfiguration(sharedTarget.toFile()).getBoolean("New"));
		assertFalse(YamlConfiguration.loadConfiguration(sharedTarget.toFile()).contains("Old"));
	}

	@Test
	public void preservesExistingPosixPermissions() throws Exception {
		Path target = tempDir.resolve("permissions.yml");
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX permissions are not available");

		YamlConfiguration original = new YamlConfiguration();
		original.set("Old", true);
		original.save(target.toFile());
		Set<PosixFilePermission> expected = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.GROUP_READ);
		Files.setPosixFilePermissions(target, expected);

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(target.toFile(), replacement);

		assertEquals(expected, Files.getPosixFilePermissions(target));
	}
}
