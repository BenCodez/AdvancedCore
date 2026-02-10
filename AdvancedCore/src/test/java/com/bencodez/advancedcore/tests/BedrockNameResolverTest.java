package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;

/**
 * Unit tests for {@link BedrockNameResolver}.
 *
 * <p>
 * These tests do not require Floodgate/Geyser jars. We replace the internal BedrockDetect
 * instance with a deterministic stub via reflection and mock Bukkit.getOnlinePlayers()
 * using Mockito static mocking.
 * </p>
 *
 * <p>
 * Requires test dependencies:
 * <ul>
 *   <li>org.junit.jupiter:junit-jupiter</li>
 *   <li>org.mockito:mockito-core</li>
 *   <li>org.mockito:mockito-inline (for static mocking)</li>
 * </ul>
 * </p>
 */
public class BedrockNameResolverTest {

	private MockedStatic<Bukkit> bukkitStatic;

	@AfterEach
	public void tearDown() {
		if (bukkitStatic != null) {
			bukkitStatic.close();
			bukkitStatic = null;
		}
	}

	@Test
	public void testIsBedrockName_nullAndEmpty_false() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		DetectStub detect = new DetectStub();
		setDetect(resolver, detect);

		assertFalse(resolver.isBedrockName(null));
		assertFalse(resolver.isBedrockName(""));
	}

	@Test
	public void testIsBedrockName_prefixFallback_trueWhenNameStartsWithPrefix() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		setDetect(resolver, new DetectStub());

		mockNoOnlinePlayers();

		assertTrue(resolver.isBedrockName(".SomeBedrock"));
		assertFalse(resolver.isBedrockName("SomeJava"));
	}

	@Test
	public void testLearn_userCachesCaseInsensitive() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		setDetect(resolver, new DetectStub());
		mockNoOnlinePlayers();

		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlayerName()).thenReturn("TeStUser");
		when(user.isBedrockUser()).thenReturn(true);

		resolver.learn(user);

		assertTrue(resolver.isBedrockName("testuser"));
		assertTrue(resolver.isBedrockName("TESTUSER"));
	}

	@Test
	public void testResolve_cacheBedrock_addsPrefixAndRationale() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		setDetect(resolver, new DetectStub());
		mockNoOnlinePlayers();

		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlayerName()).thenReturn("Steve");
		when(user.isBedrockUser()).thenReturn(true);

		resolver.learn(user);

		BedrockNameResolver.Result r = resolver.resolve("Steve");
		assertTrue(r.isBedrock);
		assertEquals(".Steve", r.finalName);
		assertEquals("cache-bedrock", r.rationale);
	}

	@Test
	public void testResolve_cachePrefixedVariant_creditsPrefixedName() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		setDetect(resolver, new DetectStub());
		mockNoOnlinePlayers();

		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlayerName()).thenReturn(".Alex");
		when(user.isBedrockUser()).thenReturn(true);

		resolver.learn(user);

		BedrockNameResolver.Result r = resolver.resolve("Alex");
		assertTrue(r.isBedrock);
		assertEquals(".Alex", r.finalName);
		assertTrue(r.rationale.toLowerCase(Locale.ROOT).contains("prefixed-variant"));
	}

	@Test
	public void testResolve_onlineStrippedMatch_usesOnlineUuidBedrock() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		DetectStub detect = new DetectStub();
		setDetect(resolver, detect);

		UUID bedrockUuid = UUID.randomUUID();
		detect.set(bedrockUuid, true);

		Player online = mock(Player.class);
		when(online.getName()).thenReturn(".DarkAshley");
		when(online.getUniqueId()).thenReturn(bedrockUuid);

		mockOnlinePlayers(online);

		BedrockNameResolver.Result r = resolver.resolve("darkashley");
		assertTrue(r.isBedrock);
		assertEquals(".DarkAshley", r.finalName);
		assertEquals("online-uuid-bedrock", r.rationale);
	}

	@Test
	public void testIsBedrock_uuidAuthoritative_true() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		DetectStub detect = new DetectStub();
		setDetect(resolver, detect);
		mockNoOnlinePlayers();

		UUID uuid = UUID.randomUUID();
		detect.set(uuid, true);

		assertTrue(resolver.isBedrock(uuid, "SomeName"));
	}

	@Test
	public void testIsBedrock_uuidFalseFallsBackToCache() throws Exception {
		BedrockNameResolver resolver = newResolverWithPrefix(".");
		DetectStub detect = new DetectStub();
		setDetect(resolver, detect);
		mockNoOnlinePlayers();

		UUID uuid = UUID.randomUUID();
		detect.set(uuid, false);

		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlayerName()).thenReturn("TeSt");
		when(user.isBedrockUser()).thenReturn(false);
		resolver.learn(user);

		assertFalse(resolver.isBedrock(uuid, "test"));
	}

	@Test
	public void testIsBedrock_dbFlag_true() throws Exception {
		UserManager userManager = mock(UserManager.class);
		AdvancedCorePlugin plugin = mockPlugin(".", userManager);

		BedrockNameResolver resolver = new BedrockNameResolver(plugin);
		setDetect(resolver, new DetectStub());
		mockNoOnlinePlayers();

		AdvancedCoreUser dbUser = mock(AdvancedCoreUser.class);
		when(dbUser.isBedrockUser()).thenReturn(true);

		when(userManager.getUser("NotCached")).thenReturn(dbUser);

		assertTrue(resolver.isBedrockName("NotCached"));
	}

	@Test
	public void testLearn_playerBedrock_updatesCacheAndSetsUserFlag() throws Exception {
		UserManager userManager = mock(UserManager.class);
		AdvancedCorePlugin plugin = mockPlugin(".", userManager);

		BedrockNameResolver resolver = new BedrockNameResolver(plugin);

		DetectStub detect = new DetectStub();
		setDetect(resolver, detect);

		UUID uuid = UUID.randomUUID();
		detect.set(uuid, true);

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getName()).thenReturn(".BedrockDude");

		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(userManager.getUser(player)).thenReturn(user);

		resolver.learn(player);

		// IMPORTANT: isBedrockName() calls Bukkit.getOnlinePlayers() first
		mockNoOnlinePlayers();

		assertTrue(resolver.isBedrockName(".BedrockDude"));
		assertTrue(resolver.isBedrockName(".bedrockdude"));

		verify(user, times(1)).setBedrockUser(true);
	}

	// ------------------------------------------------------------
	// Bukkit static mocking helpers
	// ------------------------------------------------------------

	private void mockNoOnlinePlayers() {
		if (bukkitStatic != null) {
			bukkitStatic.close();
		}
		bukkitStatic = mockStatic(Bukkit.class);
		bukkitStatic.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());
	}

	private void mockOnlinePlayers(Player... players) {
		if (bukkitStatic != null) {
			bukkitStatic.close();
		}
		bukkitStatic = mockStatic(Bukkit.class);
		bukkitStatic.when(Bukkit::getOnlinePlayers).thenReturn(Arrays.asList(players));
	}

	// ------------------------------------------------------------
	// Resolver/plugin helpers
	// ------------------------------------------------------------

	private BedrockNameResolver newResolverWithPrefix(String prefix) throws Exception {
		UserManager userManager = mock(UserManager.class);
		AdvancedCorePlugin plugin = mockPlugin(prefix, userManager);
		BedrockNameResolver resolver = new BedrockNameResolver(plugin);
		resolver.clearCache();
		return resolver;
	}

	/**
	 * Mocks AdvancedCorePlugin enough for BedrockNameResolver construction.
	 *
	 * @param bedrockPrefix prefix returned by plugin.getOptions().getBedrockPlayerPrefix()
	 * @param userManager user manager returned by plugin.getUserManager()
	 * @return plugin mock
	 */
	private AdvancedCorePlugin mockPlugin(String bedrockPrefix, UserManager userManager) {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);

		doAnswer(inv -> null).when(plugin).debug(anyString());
		when(plugin.getUserManager()).thenReturn(userManager);
		when(plugin.getOptions().getBedrockPlayerPrefix()).thenReturn(bedrockPrefix);
		doAnswer(inv -> null).when(plugin).addUserStartup(any());

		return plugin;
	}

	// ------------------------------------------------------------
	// Reflection helper for bedrockDetect replacement
	// ------------------------------------------------------------

	/**
	 * Replace the private bedrockDetect field with a deterministic stub.
	 *
	 * @param resolver resolver instance
	 * @param detectReplacement replacement instance (must be assignable to the field type)
	 */
	private static void setDetect(BedrockNameResolver resolver, Object detectReplacement) throws Exception {
		Field f = BedrockNameResolver.class.getDeclaredField("bedrockDetect");
		f.setAccessible(true);

		Class<?> fieldType = f.getType();
		assertTrue(fieldType.isInstance(detectReplacement),
				"detectReplacement must be instance of " + fieldType.getName() + " but was "
						+ detectReplacement.getClass().getName());

		f.set(resolver, detectReplacement);
	}

	/**
	 * Deterministic BedrockDetect stub that allows per-UUID answers.
	 */
	private static final class DetectStub extends BedrockNameResolver.BedrockDetect {
		private final java.util.concurrent.ConcurrentHashMap<UUID, Boolean> map =
				new java.util.concurrent.ConcurrentHashMap<>();

		public void set(UUID uuid, boolean bedrock) {
			map.put(uuid, bedrock);
		}

		@Override
		public boolean isBedrock(UUID uuid) {
			Boolean v = map.get(uuid);
			return v != null && v.booleanValue();
		}

		@Override
		public boolean isFloodgateAvailable() {
			return false;
		}

		@Override
		public boolean isGeyserAvailable() {
			return false;
		}
	}
}
