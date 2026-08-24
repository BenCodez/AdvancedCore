package com.bencodez.advancedcore.tests.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
