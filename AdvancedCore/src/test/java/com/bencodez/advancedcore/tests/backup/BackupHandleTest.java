package com.bencodez.advancedcore.tests.backup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.bencodez.advancedcore.api.backup.BackupHandle;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.tests.BaseTest;

@TestInstance(Lifecycle.PER_CLASS)
public class BackupHandleTest {

	private Path tempDir;
	private BackupHandle backupHandle;
	private MiscUtils mockMiscUtils;
	private MockedStatic<BackupHandle> backupHandleMockedStatic;
	private MockedStatic<MiscUtils> miscUtilsMockedStatic;
	private BaseTest baseTest;

	@AfterAll
	public void tearDown() throws IOException {
		if (backupHandleMockedStatic != null && !backupHandleMockedStatic.isClosed()) {
			backupHandleMockedStatic.close();
			backupHandleMockedStatic = null;
		}
		if (miscUtilsMockedStatic != null && !miscUtilsMockedStatic.isClosed()) {
			miscUtilsMockedStatic.close();
			miscUtilsMockedStatic = null;
		}

		// Delete the temporary directory and its contents
		if (tempDir != null) {
			Files.walk(tempDir).map(Path::toFile).forEach(File::delete);
		}
	}

	@Test
	public void testCheckOldBackups() throws IOException {
		baseTest = BaseTest.getInstance();

		// Create a temporary directory
		tempDir = Files.createTempDirectory("testBackups");

		// Mock the MiscUtils instance
		mockMiscUtils = mock(MiscUtils.class);
		when(mockMiscUtils.getTime(anyLong())).thenAnswer(invocation -> {
			long millis = invocation.getArgument(0);
			return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
		});

		// Mock the BackupHandle instance
		backupHandleMockedStatic = Mockito.mockStatic(BackupHandle.class);
		miscUtilsMockedStatic = Mockito.mockStatic(MiscUtils.class);
		backupHandleMockedStatic.when(BackupHandle::getInstance).thenReturn(mock(BackupHandle.class));
		miscUtilsMockedStatic.when(MiscUtils::getInstance).thenReturn(mockMiscUtils);

		// Mock the dataFolder location
		File dataFolder = new File(tempDir.toFile(), "dataFolder");
		dataFolder.mkdir();
		when(baseTest.plugin.getDataFolder()).thenReturn(dataFolder);

		// Create a backup directory in the mocked dataFolder
		File backupDir = new File(dataFolder, "Backups");
		backupDir.mkdir();

		// Create a file older than 5 days
		File oldBackup = new File(backupDir, "oldBackup.zip");
		oldBackup.createNewFile();
		LocalDateTime oldTime = LocalDateTime.now().minusDays(6);
		Files.setLastModifiedTime(oldBackup.toPath(),
				FileTime.fromMillis(oldTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

		// Create a file newer than 5 days
		File newBackup = new File(backupDir, "newBackup.zip");
		newBackup.createNewFile();
		LocalDateTime newTime = LocalDateTime.now().minusDays(3);
		Files.setLastModifiedTime(newBackup.toPath(),
				FileTime.fromMillis(newTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

		// Ensure the backupHandle is not a mock and call the actual method
		backupHandleMockedStatic.close();
		backupHandle = BackupHandle.getInstance();
		backupHandle.checkOldBackups();

		// Verify that the old backup was deleted and the new one was not
		assertFalse(oldBackup.exists(), "Old backup should be deleted");
		assertTrue(newBackup.exists(), "New backup should not be deleted");
	}
}