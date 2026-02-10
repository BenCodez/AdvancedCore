package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * Tests for {@link UserData}.
 *
 * <p>
 * Only tests methods that are safe without storage/Bukkit.
 * </p>
 */
public class UserDataTest {

	@Test
	public void testClearTempCache_nullSafe() {
		UserData data = new UserData(mock(AdvancedCoreUser.class));
		assertDoesNotThrow(data::clearTempCache);
	}

	@Test
	public void testClearTempCache_clearsAndNullsInternalCache() throws Exception {
		UserData data = new UserData(mock(AdvancedCoreUser.class));

		// Set tempCache via reflection
		Field f = UserData.class.getDeclaredField("tempCache");
		f.setAccessible(true);

		@SuppressWarnings("unchecked")
		HashMap<String, Object> tempCache = new HashMap<>();
		tempCache.put("k", "v");
		f.set(data, tempCache);

		// Call method
		data.clearTempCache();

		// Must be nulled
		Object after = f.get(data);
		assertNull(after);
	}

	@Test
	public void testConvert_nullList_returnsEmptyMap() {
		UserData data = new UserData(mock(AdvancedCoreUser.class));
		HashMap<String, DataValue> out = data.convert(null);
		assertNotNull(out);
		assertTrue(out.isEmpty());
	}

	@Test
	public void testConvert_mapsColumnNameToValue() {
		UserData data = new UserData(mock(AdvancedCoreUser.class));

		Column c1 = mock(Column.class);
		DataValue v1 = mock(DataValue.class);
		when(c1.getName()).thenReturn("a");
		when(c1.getValue()).thenReturn(v1);

		Column c2 = mock(Column.class);
		DataValue v2 = mock(DataValue.class);
		when(c2.getName()).thenReturn("b");
		when(c2.getValue()).thenReturn(v2);

		List<Column> cols = Arrays.asList(c1, c2);

		HashMap<String, DataValue> out = data.convert(cols);

		assertEquals(2, out.size());
		assertSame(v1, out.get("a"));
		assertSame(v2, out.get("b"));
	}
}
