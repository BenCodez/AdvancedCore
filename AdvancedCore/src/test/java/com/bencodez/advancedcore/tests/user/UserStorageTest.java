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
		assertEquals(UserStorage.FLAT, UserStorage.value("flat"));
	}

	@Test
	public void testValue_unknownReturnsNull() {
		assertNull(UserStorage.value("nope"));
		assertNull(UserStorage.value(""));
		assertNull(UserStorage.value("   "));
	}
}
