package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.usercache.UserDataManager;

class SharedPlatformThreadStorageGuardTest {

	public static final class GlobalTickProbe {
		public boolean isGlobalTickThread() { return true; }
	}

	public static final class WorkerProbe {
		public boolean isGlobalTickThread() { return false; }
	}

	@Test
	void foliaGlobalTickProbeRecognizesPlatformThread() throws Exception {
		Method probe = UserDataManager.class.getDeclaredMethod("isFoliaTickThread", Object.class);
		probe.setAccessible(true);
		assertTrue((boolean) probe.invoke(null, new GlobalTickProbe()));
	}

	@Test
	void ordinaryWorkerProbeIsNotPlatformOwned() throws Exception {
		Method probe = UserDataManager.class.getDeclaredMethod("isFoliaTickThread", Object.class);
		probe.setAccessible(true);
		assertFalse((boolean) probe.invoke(null, new WorkerProbe()));
	}
	@Test
	void deferralHelpersUsePlatformOwnedThreadGuard() throws Exception {
		java.nio.file.Path source = java.nio.file.Path.of("src/main/java/com/bencodez/advancedcore/api/user/usercache/UserDataManager.java");
		String text = java.nio.file.Files.readString(source);
		assertTrue(text.contains("if (!hasSharedSqlBackend() || !isPlatformOwnedThread()) return false;"));
		assertFalse(text.contains("!hasSharedSqlBackend() || Bukkit.getServer() == null || !Bukkit.isPrimaryThread()"));
	}

}
