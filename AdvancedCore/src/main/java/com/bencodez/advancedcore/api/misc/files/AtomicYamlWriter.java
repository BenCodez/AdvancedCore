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
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Writes YAML data to a sibling temporary file before committing it to the
 * destination. Serialization always completes before the existing file can be
 * modified. Existing POSIX files keep their inode so extended ACLs and other
 * inode metadata are not discarded by replacement.
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

		Path requestedTarget = file.toPath().toAbsolutePath();
		Path requestedParent = requestedTarget.getParent();
		if (requestedParent != null) {
			Files.createDirectories(requestedParent);
		}

		Path target = resolveWriteTarget(requestedTarget);
		Path parent = target.getParent();
		if (parent == null) {
			parent = Path.of(".").toAbsolutePath();
		}
		Files.createDirectories(parent);

		Path temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		boolean committed = false;
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
			committed = true;
		} finally {
			if (!committed) {
				Files.deleteIfExists(temp);
			}
		}
	}

	static Path resolveWriteTarget(Path requestedTarget) throws IOException {
		Path current = requestedTarget.toAbsolutePath();

		// If the complete path exists, let the filesystem resolve every component.
		// This is important for paths such as dirlink/../target.yml: lexical
		// normalization before following dirlink can select a different file.
		if (Files.exists(current)) {
			return current.toRealPath();
		}

		// A broken final symlink still needs write-through semantics. Follow that
		// final link without normalizing its text, then resolve the longest existing
		// parent through the filesystem.
		if (Files.isSymbolicLink(current)) {
			Path link = Files.readSymbolicLink(current);
			Path linkedTarget = link.isAbsolute() ? link : current.getParent().resolve(link);
			return resolveWriteTarget(linkedTarget);
		}

		Path parent = current.getParent();
		if (parent != null && Files.exists(parent)) {
			return parent.toRealPath().resolve(current.getFileName());
		}
		return current;
	}

	private static boolean prepareReplacementMetadata(Path target, Path temp) throws IOException {
		if (!Files.exists(target)) {
			preserveNormalCreationPermissions(target, temp);
			return true;
		}

		// Java's POSIX view exposes owner/group/mode, but not Linux extended POSIX
		// ACL entries. Replacing the inode can therefore silently discard setfacl
		// grants. Commit into the existing inode after successful serialization.
		PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
		if (targetPosix != null) {
			return false;
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

	private static void preserveNormalCreationPermissions(Path target, Path temp) throws IOException {
		PosixFileAttributeView tempPosix = Files.getFileAttributeView(temp, PosixFileAttributeView.class);
		if (tempPosix == null) {
			return;
		}

		Path parent = target.getParent();
		if (parent == null) {
			return;
		}
		Path probe = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".mode-probe");
		try {
			Files.createFile(probe);
			Set<java.nio.file.attribute.PosixFilePermission> permissions = Files
					.readAttributes(probe, PosixFileAttributes.class).permissions();
			tempPosix.setPermissions(permissions);
		} finally {
			Files.deleteIfExists(probe);
		}
	}

	private static void writeInPlacePreservingMetadata(Path temp, Path target) throws IOException {
		try (InputStream input = Files.newInputStream(temp);
				OutputStream output = Files.newOutputStream(target, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
			input.transferTo(output);
		}
	}
}
