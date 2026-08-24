package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
		NexoItemHandle handle = new TestNexoItemHandle();

		ItemStack item = handle.getItem("test");

		assertEquals(Material.STONE, item.getType());
		assertNull(handle.getItem("missing"));
	}

	@Test
	public void bossBarCompatibilityAcceptsCaseAndClampsProgress() {
		assertEquals(BarColor.BLUE, EffectCompatibility.parseBarColor("blue"));
		assertEquals(BarStyle.SOLID, EffectCompatibility.parseBarStyle(" solid "));
		assertEquals(1D, EffectCompatibility.clampProgress(2D));
		assertEquals(0D, EffectCompatibility.clampProgress(-1D));
	}

	private static final class TestNexoItemHandle extends NexoItemHandle {
		private TestNexoItemHandle() {
			super(ItemCompatibilityTest.class.getClassLoader(), FakeNexoItems.class.getName(), FakeBuilder.class.getName());
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
