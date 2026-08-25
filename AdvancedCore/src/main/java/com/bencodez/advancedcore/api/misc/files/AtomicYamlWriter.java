package com.bencodez.advancedcore.api.misc.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
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
			if (prepareReplacementMetadata(target, temp)) {
				try {
					Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} else {
				writeInPlacePreservingMetadata(temp, target);
				Files.deleteIfExists(temp);
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

	private static boolean prepareReplacementMetadata(Path target, Path temp) {
		if (!Files.exists(target)) {
			return true;
		}

		PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
		PosixFileAttributeView tempPosix = Files.getFileAttributeView(temp, PosixFileAttributeView.class);
		if (targetPosix != null && tempPosix != null) {
			try {
				PosixFileAttributes attributes = targetPosix.readAttributes();
				tempPosix.setPermissions(attributes.permissions());
				tempPosix.setGroup(attributes.group());
				UserPrincipal tempOwner = tempPosix.getOwner();
				if (!attributes.owner().equals(tempOwner)) {
					tempPosix.setOwner(attributes.owner());
				}
				return true;
			} catch (IOException | SecurityException e) {
				return false;
			}
		}

		AclFileAttributeView targetAcl = Files.getFileAttributeView(target, AclFileAttributeView.class);
		AclFileAttributeView tempAcl = Files.getFileAttributeView(temp, AclFileAttributeView.class);
		if (targetAcl != null && tempAcl != null) {
			try {
				tempAcl.setAcl(targetAcl.getAcl());
				UserPrincipal targetOwner = targetAcl.getOwner();
				if (!targetOwner.equals(tempAcl.getOwner())) {
					tempAcl.setOwner(targetOwner);
				}
				return true;
			} catch (IOException | SecurityException e) {
				return false;
			}
		}

		return true;
	}

	private static void writeInPlacePreservingMetadata(Path temp, Path target) throws IOException {
		try (InputStream input = Files.newInputStream(temp);
				OutputStream output = Files.newOutputStream(target, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
			input.transferTo(output);
		}
	}
}
