package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserStartup;

/**
 * Tests for {@link UserStartup}.
 */
public class UserStartupTest {

	@Test
	public void testProcess_defaultTrue_andSettable() {
		UserStartup s = new UserStartup() {
			@Override
			public void onFinish() {
			}

			@Override
			public void onStart() {
			}

			@Override
			public void onStartUp(AdvancedCoreUser user) {
			}
		};

		assertTrue(s.isProcess());
		s.setProcess(false);
		assertFalse(s.isProcess());
	}

	@Test
	public void testOnPostFinish_defaultNoThrow() {
		UserStartup s = new UserStartup() {
			@Override
			public void onFinish() {
			}

			@Override
			public void onStart() {
			}

			@Override
			public void onStartUp(AdvancedCoreUser user) {
			}
		};

		assertDoesNotThrow(s::onPostFinish);
	}
}
