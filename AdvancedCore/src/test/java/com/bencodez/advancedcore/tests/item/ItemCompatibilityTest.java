package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.item.NexoItemHandle;
import com.bencodez.advancedcore.api.misc.effects.EffectCompatibility;

public class ItemCompatibilityTest {

	@Test
	public void nexoReflectionBuildsExpectedItemType() {
		NexoItemHandle handle = new TestNexoItemHandle(ItemCompatibilityTest.class.getClassLoader());

		ItemStack item = handle.getItem("test");

		assertEquals(Material.STONE, item.getType());
		assertNull(handle.getItem("missing"));
	}

	@Test
	public void nexoReflectionCacheIsSharedAcrossHandles() {
		CountingClassLoader loader = new CountingClassLoader(ItemCompatibilityTest.class.getClassLoader());
		NexoItemHandle first = new TestNexoItemHandle(loader);
		NexoItemHandle second = new TestNexoItemHandle(loader);

		assertEquals(Material.STONE, first.getItem("test").getType());
		int afterFirst = loader.getNexoLoads();
		assertEquals(Material.STONE, second.getItem("test").getType());

		assertEquals(2, afterFirst, "the API and builder classes should each be resolved once");
		assertEquals(afterFirst, loader.getNexoLoads(), "a second handle must reuse the shared reflection cache");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void defaultNexoCacheRetainsReflectionStateBetweenTransientHandles() throws Exception {
		NexoItemHandle handle = new TestNexoItemHandle(NexoItemHandle.class.getClassLoader());
		assertEquals(Material.STONE, handle.getItem("test").getType());

		Field defaultCachesField = NexoItemHandle.class.getDeclaredField("DEFAULT_CACHES");
		defaultCachesField.setAccessible(true);
		Map<String, ?> defaultCaches = (Map<String, ?>) defaultCachesField.get(null);

		assertFalse(defaultCaches.isEmpty());
		assertTrue(defaultCaches.values().stream().noneMatch(WeakReference.class::isInstance),
				"the production/default loader must retain resolved reflection state between temporary handles");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void sharedNexoCacheDoesNotStronglyRetainDisposableClassLoaders() throws Exception {
		CountingClassLoader loader = new CountingClassLoader(ItemCompatibilityTest.class.getClassLoader());
		new TestNexoItemHandle(loader);

		Field cachesField = NexoItemHandle.class.getDeclaredField("CACHES");
		cachesField.setAccessible(true);
		Map<ClassLoader, Map<String, ?>> caches = (Map<ClassLoader, Map<String, ?>>) cachesField.get(null);
		Map<String, ?> loaderCache = caches.get(loader);

		assertTrue(loaderCache.values().stream().allMatch(WeakReference.class::isInstance),
				"disposable class-loader keys must not have values that strongly retain reflection state");
	}

	@Test
	public void bossBarCompatibilityAcceptsCaseAndClampsProgress() {
		assertEquals(BarColor.BLUE, EffectCompatibility.parseBarColor("blue"));
		assertEquals(BarStyle.SOLID, EffectCompatibility.parseBarStyle(" solid "));
		assertEquals(1D, EffectCompatibility.clampProgress(2D));
		assertEquals(0D, EffectCompatibility.clampProgress(-1D));
	}

	private static final class TestNexoItemHandle extends NexoItemHandle {
		private TestNexoItemHandle(ClassLoader classLoader) {
			super(classLoader, FakeNexoItems.class.getName(), FakeBuilder.class.getName());
		}
	}

	private static final class CountingClassLoader extends ClassLoader {
		private final AtomicInteger nexoLoads = new AtomicInteger();

		private CountingClassLoader(ClassLoader parent) {
			super(parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.equals(FakeNexoItems.class.getName()) || name.equals(FakeBuilder.class.getName())) {
				nexoLoads.incrementAndGet();
			}
			return super.loadClass(name, resolve);
		}

		private int getNexoLoads() {
			return nexoLoads.get();
		}
	}

	public static final class FakeNexoItems {
		public static FakeBuilder itemFromId(String id) {
			return "test".equals(id) ? new FakeBuilder() : null;
		}
	}

	public static final class FakeBuilder {
		public ItemStack build() {
			return new ItemStack(Material.STONE);
		}
	}
}
