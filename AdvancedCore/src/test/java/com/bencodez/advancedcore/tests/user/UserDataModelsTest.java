package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeBoolean;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyBoolean;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

/**
 * Unit tests for simple user-cache model classes:
 * <ul>
 * <li>UserDataChangeBoolean / UserDataChangeInt / UserDataChangeString</li>
 * <li>UserDataKeyBoolean / UserDataKeyInt / UserDataKeyString</li>
 * </ul>
 *
 * These tests intentionally avoid Bukkit/plugin dependencies and focus on pure
 * data behavior: constructors, default values, toUserDataValue(), and dump()
 * behavior.
 */
public class UserDataModelsTest {

	@Test
	@DisplayName("UserDataChangeInt stores key/value and converts to DataValueInt")
	public void testUserDataChangeInt_toUserDataValue() {
		UserDataChangeInt change = new UserDataChangeInt("votes", 42);

		assertEquals("votes", change.getKey());
		assertEquals(42, change.getValue());

		DataValue dv = change.toUserDataValue();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueInt);
		assertEquals(42, ((DataValueInt) dv).getInt());
	}

	@Test
	@DisplayName("UserDataChangeBoolean stores key/value and converts to DataValueBoolean")
	public void testUserDataChangeBoolean_toUserDataValue() {
		UserDataChangeBoolean change = new UserDataChangeBoolean("bedrock", true);

		assertEquals("bedrock", change.getKey());
		assertTrue(change.isValue());

		DataValue dv = change.toUserDataValue();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueBoolean);
		assertTrue(((DataValueBoolean) dv).getBoolean());
	}

	@Test
	@DisplayName("UserDataChangeString stores key/value and converts to DataValueString")
	public void testUserDataChangeString_toUserDataValue() {
		UserDataChangeString change = new UserDataChangeString("name", "Ben");

		assertEquals("name", change.getKey());
		assertEquals("Ben", change.getValue());

		DataValue dv = change.toUserDataValue();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueString);
		assertEquals("Ben", ((DataValueString) dv).getString());
	}

	@Test
	@DisplayName("dump() nulls key for all change types; string change also nulls value")
	public void testUserDataChange_dump() {
		UserDataChangeInt ci = new UserDataChangeInt("k1", 1);
		UserDataChangeBoolean cb = new UserDataChangeBoolean("k2", true);
		UserDataChangeString cs = new UserDataChangeString("k3", "v");

		ci.dump();
		cb.dump();
		cs.dump();

		assertNull(ci.getKey(), "Int change should null key on dump()");
		assertNull(cb.getKey(), "Boolean change should null key on dump()");
		assertNull(cs.getKey(), "String change should null key on dump()");
		assertNull(cs.getValue(), "String change should null value on dump()");
	}

	@Test
	@DisplayName("UserDataKeyInt defaults: columnType INT DEFAULT '0' and default value 0")
	public void testUserDataKeyInt_defaults() {
		UserDataKeyInt key = new UserDataKeyInt("votes");

		assertEquals("votes", key.getKey());
		assertNotNull(key.getColumnType());
		assertTrue(key.getColumnType().toUpperCase().contains("INT"));
		assertTrue(key.getColumnType().contains("0"));

		DataValue dv = key.getDefault();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueInt);
		assertEquals(0, ((DataValueInt) dv).getInt());
	}

	@Test
	@DisplayName("UserDataKeyBoolean defaults: columnType VARCHAR(5) and default false")
	public void testUserDataKeyBoolean_defaults() {
		UserDataKeyBoolean key = new UserDataKeyBoolean("bedrock");

		assertEquals("bedrock", key.getKey());
		assertEquals("VARCHAR(5)", key.getColumnType());

		DataValue dv = key.getDefault();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueBoolean);
		assertFalse(((DataValueBoolean) dv).getBoolean());
	}

	@Test
	@DisplayName("UserDataKeyString defaults: TEXT -> default empty string")
	public void testUserDataKeyString_defaultText() {
		UserDataKeyString key = new UserDataKeyString("name");

		assertEquals("name", key.getKey());
		assertEquals("TEXT", key.getColumnType());

		DataValue dv = key.getDefault();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueString);
		assertEquals("", ((DataValueString) dv).getString());
	}

	@Test
	@DisplayName("UserDataKeyString special-case: if columnType is VARCHAR(5), default becomes boolean false")
	public void testUserDataKeyString_varchar5DefaultBooleanFalse() {
		UserDataKey key = new UserDataKeyString("someFlag").setColumnType("VARCHAR(5)");

		assertEquals("someFlag", key.getKey());
		assertEquals("VARCHAR(5)", key.getColumnType());

		DataValue dv = key.getDefault();
		assertNotNull(dv);
		assertTrue(dv instanceof DataValueBoolean, "VARCHAR(5) should return DataValueBoolean(false)");
		assertFalse(((DataValueBoolean) dv).getBoolean());
	}
}
