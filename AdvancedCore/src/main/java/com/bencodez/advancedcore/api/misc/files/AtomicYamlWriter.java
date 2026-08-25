package com.bencodez.advancedcore.api.misc.files;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Writes YAML data to a sibling temporary file before committing it to the
 * destination. Serialization always completes before the existing file can be
 * modified. Existing files whose security metadata cannot be recreated safely
 * are committed through a preserved hard-link inode so the live path always
 * points at a complete old or new file.
 */
public final class AtomicYamlWriter {

	private static final int MAX_SYMLINK_DEPTH = 32;
	private static final String PRESERVED_SUFFIX = ".advancedcore-preserved-inode";

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

		Path preserved = preservedPath(target);
		recoverPreservedInode(target, preserved);

		Path temp;
		try {
			temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		} catch (IOException siblingCreationFailure) {
			if (Files.exists(target) && Files.isWritable(target)) {
				saveSerializedInPlace(target, data, siblingCreationFailure);
				return;
			}
			throw siblingCreationFailure;
		}

		boolean committed = false;
		try {
			data.save(temp.toFile());
			if (prepareReplacementMetadata(target, temp)) {
				moveReplacement(temp, target);
			} else {
				commitThroughPreservedInode(temp, target, preserved);
			}
			committed = true;
		} finally {
			if (!committed) {
				Files.deleteIfExists(temp);
			}
		}
	}

	static Path resolveWriteTarget(Path requestedTarget) throws IOException {
		return resolveWriteTarget(requestedTarget.toAbsolutePath(), new HashSet<>(), 0);
	}

	private static Path resolveWriteTarget(Path current, Set<Path> visited, int depth) throws IOException {
		if (depth > MAX_SYMLINK_DEPTH || !visited.add(current)) {
			throw new IOException("Cyclic or excessively deep symbolic link while resolving " + current);
		}

		// If the complete path exists, let the filesystem resolve every component.
		// This is important for paths such as dirlink/../target.yml: lexical
		// normalization before following dirlink can select a different file.
		if (Files.exists(current)) {
			return current.toRealPath();
		}

		// Files.exists follows links and therefore reports false for both a broken
		// final link and a symlink cycle. Inspect the directory entry itself before
		// recursing, while keeping a visited/depth guard for cycles.
		if (Files.isSymbolicLink(current)) {
			Path link = Files.readSymbolicLink(current);
			Path linkedTarget = link.isAbsolute() ? link : current.getParent().resolve(link);
			return resolveWriteTarget(linkedTarget, visited, depth + 1);
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
		// ACL entries. Use the preserved-inode commit path so those ACLs and other
		// inode metadata survive without truncating the live destination.
		PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
		if (targetPosix != null) {
			applyPosixMetadataBestEffort(target, temp);
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

	private static void applyPosixMetadataBestEffort(Path target, Path temp) {
		try {
			PosixFileAttributes attributes = Files.readAttributes(target, PosixFileAttributes.class);
			PosixFileAttributeView tempPosix = Files.getFileAttributeView(temp, PosixFileAttributeView.class);
			if (tempPosix == null) {
				return;
			}
			try {
				tempPosix.setPermissions(attributes.permissions());
			} catch (IOException | SecurityException ignored) {
			}
			try {
				tempPosix.setGroup(attributes.group());
			} catch (IOException | SecurityException ignored) {
			}
			try {
				if (!attributes.owner().equals(tempPosix.getOwner())) {
					tempPosix.setOwner(attributes.owner());
				}
			} catch (IOException | SecurityException ignored) {
			}
		} catch (IOException | SecurityException ignored) {
		}
	}

	private static Path preservedPath(Path target) {
		return target.getParent().resolve("." + target.getFileName() + PRESERVED_SUFFIX);
	}

	private static void recoverPreservedInode(Path target, Path preserved) throws IOException {
		if (!Files.exists(preserved)) {
			return;
		}
		if (Files.isSymbolicLink(preserved)) {
			throw new IOException("Refusing to follow preserved-inode symlink: " + preserved);
		}
		if (!Files.exists(target)) {
			moveReplacement(preserved, target);
			return;
		}
		if (Files.isSameFile(target, preserved)) {
			Files.delete(preserved);
			return;
		}

		copyCompleteContents(target, preserved);
		moveReplacement(preserved, target);
	}

	private static void commitThroughPreservedInode(Path temp, Path target, Path preserved) throws IOException {
		Files.deleteIfExists(preserved);
		Files.createLink(preserved, target);
		boolean livePathReplaced = false;
		try {
			// The fully serialized temp becomes the live path first. If any later copy
			// into the preserved inode fails, the live path still contains a complete
			// new YAML rather than a truncated file.
			moveReplacement(temp, target);
			livePathReplaced = true;

			// Refill the preserved original inode while it is off-path, then atomically
			// restore that inode to the live path so POSIX ACLs/ownership/xattrs survive.
			copyCompleteContents(target, preserved);
			moveReplacement(preserved, target);
		} catch (IOException | RuntimeException e) {
			if (!livePathReplaced) {
				Files.deleteIfExists(preserved);
			}
			// Once the live path has been replaced, leave the preserved inode in place
			// on failure. A later save will repair it from the complete live file before
			// attempting another commit.
			throw e;
		}
	}

	private static void saveSerializedInPlace(Path target, FileConfiguration data, IOException siblingCreationFailure)
			throws IOException {
		byte[] replacement = data.saveToString().getBytes(StandardCharsets.UTF_8);
		byte[] original = null;
		try {
			original = Files.readAllBytes(target);
		} catch (IOException | SecurityException ignored) {
			// The legacy save path required only write access. Preserve that compatibility
			// even when this process cannot read the old file for rollback.
		}

		try {
			writeCompleteBytes(target, replacement);
		} catch (IOException writeFailure) {
			writeFailure.addSuppressed(siblingCreationFailure);
			if (original != null) {
				try {
					writeCompleteBytes(target, original);
				} catch (IOException rollbackFailure) {
					writeFailure.addSuppressed(rollbackFailure);
				}
			}
			throw writeFailure;
		}
	}

	private static void writeCompleteBytes(Path target, byte[] bytes) throws IOException {
		try (FileChannel output = FileChannel.open(target, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			output.position(0L);
			while (buffer.hasRemaining()) {
				output.write(buffer);
			}
			output.truncate(output.position());
			output.force(true);
		}
	}

	private static void copyCompleteContents(Path source, Path destination) throws IOException {
		try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
				FileChannel output = FileChannel.open(destination, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
			output.position(0L);
			while (input.read(buffer) != -1) {
				buffer.flip();
				while (buffer.hasRemaining()) {
					output.write(buffer);
				}
				buffer.clear();
			}
			output.truncate(output.position());
			output.force(true);
		}
	}

	private static void moveReplacement(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
