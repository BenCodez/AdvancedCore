package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserDataChanged;

/**
 * Tests for {@link UserDataChanged}.
 */
public class UserDataChangedTest {

	@Test
	public void testOnChange_receivesKeys() {
		AtomicReference<String[]> captured = new AtomicReference<>();

		UserDataChanged changed = new UserDataChanged() {
			@Override
			public void onChange(AdvancedCoreUser user, String... key) {
				captured.set(key);
			}
		};

		changed.onChange(null, "a", "b", "c");

		assertNotNull(captured.get());
		assertArrayEquals(new String[] { "a", "b", "c" }, captured.get());
	}
	
	
}
