package com.bencodez.advancedcore.tests.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.misc.files.AtomicYamlWriter;

public class AtomicYamlWriterSecurityMetadataTest {

	@TempDir
	Path tempDir;

	@Test
	public void preservedInodeCommitKeepsExistingPosixMetadata() throws Exception {
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("posix"), "POSIX attributes are not available");

		Path target = tempDir.resolve("fallback.yml");
		Files.writeString(target, "Old: true\n");
		Set<PosixFilePermission> expected = EnumSet.of(PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ);
		Files.setPosixFilePermissions(target, expected);
		PosixFileAttributes before = Files.readAttributes(target, PosixFileAttributes.class);
		Object fileKeyBefore = Files.readAttributes(target, BasicFileAttributes.class).fileKey();

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(target.toFile(), replacement);

		PosixFileAttributes after = Files.readAttributes(target, PosixFileAttributes.class);
		Object fileKeyAfter = Files.readAttributes(target, BasicFileAttributes.class).fileKey();
		assertEquals(expected, after.permissions());
		assertEquals(before.owner(), after.owner());
		assertEquals(before.group(), after.group());
		if (fileKeyBefore != null && fileKeyAfter != null) {
			assertEquals(fileKeyBefore, fileKeyAfter,
					"the completed save must restore the original metadata-bearing inode");
		}
		assertTrue(YamlConfiguration.loadConfiguration(target.toFile()).getBoolean("New"));
		assertFalse(Files.exists(tempDir.resolve(".fallback.yml.advancedcore-preserved-inode")));
	}

	@Test
	public void savePreservesAclMetadataWhenProviderSupportsIt() throws Exception {
		FileStore store = Files.getFileStore(tempDir);
		assumeTrue(store.supportsFileAttributeView("acl"), "ACL attributes are not available");

		Path target = tempDir.resolve("acl.yml");
		Files.writeString(target, "Old: true\n");
		AclFileAttributeView beforeView = Files.getFileAttributeView(target, AclFileAttributeView.class);
		assumeTrue(beforeView != null, "ACL view is unavailable");
		var aclBefore = beforeView.getAcl();
		var ownerBefore = beforeView.getOwner();

		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("New", true);
		AtomicYamlWriter.save(target.toFile(), replacement);

		AclFileAttributeView afterView = Files.getFileAttributeView(target, AclFileAttributeView.class);
		assertEquals(ownerBefore, afterView.getOwner());
		assertEquals(aclBefore, afterView.getAcl());
	}
}
