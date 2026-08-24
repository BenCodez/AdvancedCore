package com.bencodez.advancedcore.api.misc.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Writes YAML data to a sibling temporary file before replacing the destination.
 * This prevents a failed/partial serialization from truncating the last known-good
 * configuration file.
 */
public final class AtomicYamlWriter {

	private AtomicYamlWriter() {
	}

	public static void save(File file, FileConfiguration data) throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("file cannot be null");
		}
		if (data == null) {
			throw new IllegalArgumentException("data cannot be null");
		}

		File parentFile = file.getAbsoluteFile().getParentFile();
		if (parentFile != null) {
			Files.createDirectories(parentFile.toPath());
		}

		Path target = file.toPath();
		Path parent = target.toAbsolutePath().getParent();
		if (parent == null) {
			parent = Path.of(".").toAbsolutePath();
		}
		Path temp = Files.createTempFile(parent, "." + file.getName() + ".", ".tmp");
		boolean replaced = false;
		try {
			data.save(temp.toFile());
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			replaced = true;
		} finally {
			if (!replaced) {
				Files.deleteIfExists(temp);
			}
		}
	}
}
