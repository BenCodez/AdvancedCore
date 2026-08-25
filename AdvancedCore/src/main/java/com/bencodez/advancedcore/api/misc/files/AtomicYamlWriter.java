package com.bencodez.advancedcore.api.misc.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Writes YAML data to a sibling temporary file before replacing the destination.
 * This prevents a failed/partial serialization from truncating the last known-good
 * configuration file.
 */
public final class AtomicYamlWriter {

	private static final int MAX_SYMLINK_DEPTH = 32;

	private AtomicYamlWriter() {
	}

	public static void save(File file, FileConfiguration data) throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("file cannot be null");
		}
		if (data == null) {
			throw new IllegalArgumentException("data cannot be null");
		}

		Path requestedTarget = file.toPath().toAbsolutePath().normalize();
		Path requestedParent = requestedTarget.getParent();
		if (requestedParent != null) {
			Files.createDirectories(requestedParent);
		}

		Path target = resolveWriteTarget(requestedTarget);
		Path parent = target.getParent();
		if (parent == null) {
			parent = Path.of(".").toAbsolutePath().normalize();
		}
		Files.createDirectories(parent);

		Path temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		boolean replaced = false;
		try {
			data.save(temp.toFile());
			preservePosixAttributes(target, temp);
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

	static Path resolveWriteTarget(Path requestedTarget) throws IOException {
		Path current = requestedTarget.toAbsolutePath().normalize();
		Set<Path> visited = new HashSet<>();
		for (int depth = 0; Files.isSymbolicLink(current); depth++) {
			if (depth >= MAX_SYMLINK_DEPTH || !visited.add(current)) {
				throw new IOException("Too many symbolic links while resolving " + requestedTarget);
			}
			Path link = Files.readSymbolicLink(current);
			current = link.isAbsolute() ? link.normalize() : current.getParent().resolve(link).normalize();
		}
		return current;
	}

	private static void preservePosixAttributes(Path target, Path temp) throws IOException {
		if (!Files.exists(target)) {
			return;
		}
		PosixFileAttributeView targetView = Files.getFileAttributeView(target, PosixFileAttributeView.class);
		PosixFileAttributeView tempView = Files.getFileAttributeView(temp, PosixFileAttributeView.class);
		if (targetView == null || tempView == null) {
			return;
		}

		PosixFileAttributes attributes = targetView.readAttributes();
		// The temp inode becomes the destination inode after the move. Preserve all
		// access-relevant POSIX metadata, not just mode bits.
		tempView.setOwner(attributes.owner());
		tempView.setGroup(attributes.group());
		tempView.setPermissions(attributes.permissions());
	}
}
