package com.bencodez.advancedcore.tests.user.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.validation.BedrockCheckResult;
import com.bencodez.advancedcore.api.user.validation.UserValidationResult;
import com.bencodez.advancedcore.api.user.validation.UserValidationService;
import com.bencodez.advancedcore.api.user.validation.ValidationSource;
import com.bencodez.advancedcore.api.user.validation.ValidationStatus;
import com.bencodez.advancedcore.api.user.validation.interfaces.BedrockPrecheck;
import com.bencodez.advancedcore.api.user.validation.interfaces.OnlinePlayerLookup;
import com.bencodez.advancedcore.api.user.validation.interfaces.ServerHistoryLookup;
import com.bencodez.advancedcore.api.user.validation.interfaces.StoredUserLookup;

public class UserValidationServiceTest {

	private OnlinePlayerLookup onlinePlayerLookup;
	private StoredUserLookup storedUserLookup;
	private ServerHistoryLookup serverHistoryLookup;
	private BedrockPrecheck bedrockPrecheck;

	private UserValidationService service;

	@BeforeEach
	public void setUp() {
		onlinePlayerLookup = mock(OnlinePlayerLookup.class);
		storedUserLookup = mock(StoredUserLookup.class);
		serverHistoryLookup = mock(ServerHistoryLookup.class);
		bedrockPrecheck = mock(BedrockPrecheck.class);

		service = new UserValidationService(onlinePlayerLookup, storedUserLookup, serverHistoryLookup, bedrockPrecheck);
	}

	@Test
	public void testNullName() {
		UserValidationResult result = service.validate(null, true);

		assertFalse(result.isValid());
		assertEquals(ValidationStatus.INVALID, result.getStatus());
		assertEquals("", result.getNormalizedName());
		assertEquals(ValidationSource.UNKNOWN, result.getSource());
		assertEquals("null-name", result.getReason());
		assertFalse(result.isBedrock());

		verify(onlinePlayerLookup, never()).isOnlineExact(anyString());
		verify(storedUserLookup, never()).userExistsStored(anyString());
		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testEmptyName() {
		UserValidationResult result = service.validate("   ", true);

		assertFalse(result.isValid());
		assertEquals(ValidationStatus.INVALID, result.getStatus());
		assertEquals("", result.getNormalizedName());
		assertEquals(ValidationSource.UNKNOWN, result.getSource());
		assertEquals("empty-name", result.getReason());
		assertFalse(result.isBedrock());

		verify(onlinePlayerLookup, never()).isOnlineExact(anyString());
		verify(storedUserLookup, never()).userExistsStored(anyString());
		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testOnlinePlayerValid() {
		when(onlinePlayerLookup.isOnlineExact("Ben")).thenReturn(true);

		UserValidationResult result = service.validate("Ben", true);

		assertTrue(result.isValid());
		assertEquals(ValidationStatus.VALID, result.getStatus());
		assertEquals("Ben", result.getNormalizedName());
		assertEquals(ValidationSource.ONLINE_PLAYER, result.getSource());
		assertEquals("online-player", result.getReason());
		assertFalse(result.isBedrock());

		verify(onlinePlayerLookup).isOnlineExact("Ben");
		verify(storedUserLookup, never()).userExistsStored(anyString());
		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testStoredUserValid() {
		when(onlinePlayerLookup.isOnlineExact("Ben")).thenReturn(false);
		when(storedUserLookup.userExistsStored("Ben")).thenReturn(true);

		UserValidationResult result = service.validate("Ben", true);

		assertTrue(result.isValid());
		assertEquals(ValidationStatus.VALID, result.getStatus());
		assertEquals("Ben", result.getNormalizedName());
		assertEquals(ValidationSource.STORAGE, result.getSource());
		assertEquals("stored-user", result.getReason());
		assertFalse(result.isBedrock());

		verify(onlinePlayerLookup).isOnlineExact("Ben");
		verify(storedUserLookup).userExistsStored("Ben");
		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testTrustedBedrockValid() {
		when(onlinePlayerLookup.isOnlineExact("BedrockUser")).thenReturn(false);
		when(storedUserLookup.userExistsStored("BedrockUser")).thenReturn(false);
		when(bedrockPrecheck.check("BedrockUser"))
				.thenReturn(new BedrockCheckResult(true, true, ".BedrockUser", "online-uuid-bedrock"));

		UserValidationResult result = service.validate("BedrockUser", true);

		assertTrue(result.isValid());
		assertEquals(ValidationStatus.VALID, result.getStatus());
		assertEquals(".BedrockUser", result.getNormalizedName());
		assertEquals(ValidationSource.BEDROCK_TRUSTED, result.getSource());
		assertEquals("online-uuid-bedrock", result.getReason());
		assertTrue(result.isBedrock());

		verify(onlinePlayerLookup).isOnlineExact("BedrockUser");
		verify(storedUserLookup).userExistsStored("BedrockUser");
		verify(bedrockPrecheck).check("BedrockUser");
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testServerHistoryValidWhenEnabled() {
		when(onlinePlayerLookup.isOnlineExact("JoinedBefore")).thenReturn(false);
		when(storedUserLookup.userExistsStored("JoinedBefore")).thenReturn(false);
		when(bedrockPrecheck.check("JoinedBefore"))
				.thenReturn(new BedrockCheckResult(false, false, "JoinedBefore", "unknown-no-db"));
		when(serverHistoryLookup.hasJoinedBefore("JoinedBefore")).thenReturn(true);

		UserValidationResult result = service.validate("JoinedBefore", true);

		assertTrue(result.isValid());
		assertEquals(ValidationStatus.VALID, result.getStatus());
		assertEquals("JoinedBefore", result.getNormalizedName());
		assertEquals(ValidationSource.SERVER_HISTORY, result.getSource());
		assertEquals("server-history", result.getReason());
		assertFalse(result.isBedrock());

		verify(serverHistoryLookup).hasJoinedBefore("JoinedBefore");
	}

	@Test
	public void testServerHistoryIgnoredWhenDisabled() {
		when(onlinePlayerLookup.isOnlineExact("JoinedBefore")).thenReturn(false);
		when(storedUserLookup.userExistsStored("JoinedBefore")).thenReturn(false);
		when(bedrockPrecheck.check("JoinedBefore"))
				.thenReturn(new BedrockCheckResult(false, false, "JoinedBefore", "unknown-no-db"));

		UserValidationResult result = service.validate("JoinedBefore", false);

		assertFalse(result.isValid());
		assertEquals(ValidationStatus.INVALID, result.getStatus());
		assertEquals("JoinedBefore", result.getNormalizedName());
		assertEquals(ValidationSource.UNKNOWN, result.getSource());
		assertEquals("unknown-user", result.getReason());
		assertFalse(result.isBedrock());

		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testUntrustedBedrockInvalid() {
		when(onlinePlayerLookup.isOnlineExact("FakeBedrock")).thenReturn(false);
		when(storedUserLookup.userExistsStored("FakeBedrock")).thenReturn(false);
		when(bedrockPrecheck.check("FakeBedrock"))
				.thenReturn(new BedrockCheckResult(true, false, ".FakeBedrock", "prefixed-only"));
		when(serverHistoryLookup.hasJoinedBefore("FakeBedrock")).thenReturn(false);

		UserValidationResult result = service.validate("FakeBedrock", true);

		assertFalse(result.isValid());
		assertEquals(ValidationStatus.INVALID, result.getStatus());
		assertEquals(".FakeBedrock", result.getNormalizedName());
		assertEquals(ValidationSource.BEDROCK_UNTRUSTED, result.getSource());
		assertEquals("untrusted-bedrock", result.getReason());
		assertTrue(result.isBedrock());
	}

	@Test
	public void testUnknownUserInvalid() {
		when(onlinePlayerLookup.isOnlineExact("RandomUser123")).thenReturn(false);
		when(storedUserLookup.userExistsStored("RandomUser123")).thenReturn(false);
		when(bedrockPrecheck.check("RandomUser123"))
				.thenReturn(new BedrockCheckResult(false, false, "RandomUser123", "unknown-no-db"));
		when(serverHistoryLookup.hasJoinedBefore("RandomUser123")).thenReturn(false);

		UserValidationResult result = service.validate("RandomUser123", true);

		assertFalse(result.isValid());
		assertEquals(ValidationStatus.INVALID, result.getStatus());
		assertEquals("RandomUser123", result.getNormalizedName());
		assertEquals(ValidationSource.UNKNOWN, result.getSource());
		assertEquals("unknown-user", result.getReason());
		assertFalse(result.isBedrock());
	}

	@Test
	public void testTrimmedInputUsesTrimmedName() {
		when(onlinePlayerLookup.isOnlineExact("Ben")).thenReturn(false);
		when(storedUserLookup.userExistsStored("Ben")).thenReturn(true);

		UserValidationResult result = service.validate("  Ben  ", true);

		assertTrue(result.isValid());
		assertEquals("Ben", result.getNormalizedName());

		verify(onlinePlayerLookup).isOnlineExact("Ben");
		verify(storedUserLookup).userExistsStored("Ben");
	}

	@Test
	public void testLookupOrderStopsAfterOnlineMatch() {
		when(onlinePlayerLookup.isOnlineExact("Ben")).thenReturn(true);

		UserValidationResult result = service.validate("Ben", true);

		assertTrue(result.isValid());
		assertEquals(ValidationSource.ONLINE_PLAYER, result.getSource());

		verify(storedUserLookup, never()).userExistsStored(anyString());
		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}

	@Test
	public void testLookupOrderStopsAfterStoredMatch() {
		when(onlinePlayerLookup.isOnlineExact("Ben")).thenReturn(false);
		when(storedUserLookup.userExistsStored("Ben")).thenReturn(true);

		UserValidationResult result = service.validate("Ben", true);

		assertTrue(result.isValid());
		assertEquals(ValidationSource.STORAGE, result.getSource());

		verify(bedrockPrecheck, never()).check(anyString());
		verify(serverHistoryLookup, never()).hasJoinedBefore(anyString());
	}
}