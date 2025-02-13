package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.backup.ZipCreator;

public class ZipCreatorTest {

	private ZipCreator zipCreator;
	private AdvancedCorePlugin mockPlugin;
	private File tempDir;
	private File tempFile;

	@BeforeEach
	public void setUp() throws IOException {
		// Mock the plugin instance
		mockPlugin = mock(AdvancedCorePlugin.class);
		when(mockPlugin.getDataFolder()).thenReturn(new File("target/mockDataFolder"));
		when(mockPlugin.getName()).thenReturn("AdvancedCore");

		// Mock the logger
		java.util.logging.Logger mockLogger = mock(java.util.logging.Logger.class);
		when(mockPlugin.getLogger()).thenReturn(mockLogger);

		// Set the plugin instance in ZipCreator
		zipCreator = ZipCreator.getInstance();
		zipCreator.plugin = mockPlugin;

		// Create a temporary directory and file for testing within the target folder
		tempDir = new File("target/testDir");
		tempDir.mkdirs();
		tempFile = new File(tempDir, "testFile.txt");
		Files.write(tempFile.toPath(), "Sample content".getBytes());
	}

	@AfterEach
	public void tearDown() {
		// Clean up temporary files and directories
		tempFile.delete();
		tempDir.delete();
	}

	@Test
	public void testAddAllFiles() {
		List<File> fileList = new java.util.ArrayList<>();
		zipCreator.addAllFiles(tempDir, fileList);
		assertTrue(fileList.contains(tempFile), "File list should contain the test file");
	}

	@Test
	public void testCreate() throws IOException {
		File zipFile = new File(tempDir, "testArchive.zip");
		zipCreator.create(tempDir, zipFile);
		assertTrue(zipFile.exists(), "Zip file should be created");
		assertTrue(zipFile.length() > 0, "Zip file should not be empty");
	}
}
