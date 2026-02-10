package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;

/**
 * Tests for {@link UserDataFetchMode}.
 */
public class UserDataFetchModeTest {

	@Test
	public void testFlags_default() {
		UserDataFetchMode m = UserDataFetchMode.DEFAULT;
		assertTrue(m.allowTempCache());
		assertTrue(m.allowUserCache());
		assertTrue(m.allowStorageLookup());
		assertTrue(m.waitForCache());
	}

	@Test
	public void testFlags_noCache() {
		UserDataFetchMode m = UserDataFetchMode.NO_CACHE;
		assertTrue(m.allowTempCache());
		assertFalse(m.allowUserCache());
		assertTrue(m.allowStorageLookup());
		assertTrue(m.waitForCache());
	}

	@Test
	public void testFlags_noDbLookup() {
		UserDataFetchMode m = UserDataFetchMode.NO_DB_LOOKUP;
		assertTrue(m.allowTempCache());
		assertTrue(m.allowUserCache());
		assertFalse(m.allowStorageLookup());
		assertTrue(m.waitForCache());
	}

	@Test
	public void testFlags_tempOnly() {
		UserDataFetchMode m = UserDataFetchMode.TEMP_ONLY;
		assertTrue(m.allowTempCache());
		assertFalse(m.allowUserCache());
		assertFalse(m.allowStorageLookup());
		assertFalse(m.waitForCache());
	}

	@Test
	public void testFlags_cacheOnly() {
		UserDataFetchMode m = UserDataFetchMode.CACHE_ONLY;
		assertFalse(m.allowTempCache());
		assertTrue(m.allowUserCache());
		assertFalse(m.allowStorageLookup());
		assertTrue(m.waitForCache());
	}

	@Test
	public void testFlags_noWait() {
		UserDataFetchMode m = UserDataFetchMode.NO_WAIT;
		assertTrue(m.allowTempCache());
		assertTrue(m.allowUserCache());
		assertTrue(m.allowStorageLookup());
		assertFalse(m.waitForCache());
	}

	@Test
	public void testFromBooleans_mapping() {
		assertEquals(UserDataFetchMode.DEFAULT, UserDataFetchMode.fromBooleans(true, true));
		assertEquals(UserDataFetchMode.NO_WAIT, UserDataFetchMode.fromBooleans(true, false));

		// if cache isn't used, wait flag doesn't matter per implementation
		assertEquals(UserDataFetchMode.NO_CACHE, UserDataFetchMode.fromBooleans(false, true));
		assertEquals(UserDataFetchMode.NO_CACHE, UserDataFetchMode.fromBooleans(false, false));
	}
}
