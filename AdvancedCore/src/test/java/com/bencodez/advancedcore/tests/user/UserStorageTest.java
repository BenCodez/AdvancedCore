package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserStorage;

/**
 * Tests for {@link UserStorage}.
 */
public class UserStorageTest {

	@Test
	public void testValue_caseInsensitiveMatches() {
		assertEquals(UserStorage.MYSQL, UserStorage.value("mysql"));
		assertEquals(UserStorage.SQLITE, UserStorage.value("SQLITE"));
	}

	@Test
	public void testValue_unknownReturnsNull() {
		assertNull(UserStorage.value("nope"));
		assertNull(UserStorage.value(""));
		assertNull(UserStorage.value("   "));
	}

	@Test
	public void flatIsNotAnAvailableStorageType() {
		assertArrayEquals(new UserStorage[] { UserStorage.MYSQL, UserStorage.SQLITE }, UserStorage.values());
		assertThrows(IllegalArgumentException.class, () -> UserStorage.valueOf("FLAT"));
	}

	@Test
	public void retiredFlatConfigurationFailsRatherThanSelectingAnEmptySqlDatabase() {
		for (String value : new String[] { "FLAT", "flat", "Flat", " FLAT " }) {
			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> UserStorage.value(value));
			assertTrue(failure.getMessage().contains("previous version"));
		}
	}
}
