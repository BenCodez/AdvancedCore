package com.bencodez.advancedcore.api.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PlayerManagerDamageItemTest {
	private final PlayerManager manager = PlayerManager.getInstance();

	@Test
	void missingItemAndMetaAreSafe() {
		assertFalse(manager.damageItemInHand(null, 1));
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		assertFalse(manager.damageItemInHand(player, 1));
		ItemStack item = mock(ItemStack.class);
		when(inventory.getItemInMainHand()).thenReturn(item);
		when(item.getType()).thenReturn(Material.AIR);
		assertFalse(manager.damageItemInHand(player, 1));
		when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
		assertFalse(manager.damageItemInHand(player, 1));
	}

	@Test
	void nonPositiveDamageAndUnbreakableItemDoNotChangeDurability() {
		Fixture fixture = new Fixture(3);
		assertTrue(manager.damageItemInHand(fixture.player, 0));
		assertTrue(manager.damageItemInHand(fixture.player, -100));
		verify(fixture.meta, never()).setDamage(anyInt());
		when(fixture.meta.isUnbreakable()).thenReturn(true);
		assertFalse(manager.damageItemInHand(fixture.player, 100));
		verify(fixture.item, never()).setItemMeta(any());
	}

	@Test
	void appliesDamageAndRemovesItemPastMaximum() {
		Fixture fixture = new Fixture(56);
		try (MockedStatic<MiscUtils> misc = mockStatic(MiscUtils.class)) {
			MiscUtils utils = mock(MiscUtils.class);
			misc.when(MiscUtils::getInstance).thenReturn(utils);
			when(fixture.item.getEnchantmentLevel(null)).thenReturn(0);
			assertTrue(manager.damageItemInHand(fixture.player, 2));
			verify(fixture.meta).setDamage(58);
			verify(fixture.item).setItemMeta(fixture.meta);
			Fixture breaking = new Fixture(59);
			assertFalse(manager.damageItemInHand(breaking.player, 1));
			verify(breaking.inventory).setItemInMainHand(any(ItemStack.class));
			Fixture overflow = new Fixture(Integer.MAX_VALUE);
			assertFalse(manager.damageItemInHand(overflow.player, Integer.MAX_VALUE));
			verify(overflow.inventory).setItemInMainHand(any(ItemStack.class));
		}
	}

	@Test
	void extremeDamageFinishesPromptlyWithUnbreaking() {
		Fixture fixture = new Fixture(0);
		try (MockedStatic<MiscUtils> misc = mockStatic(MiscUtils.class)) {
			MiscUtils utils = mock(MiscUtils.class);
			misc.when(MiscUtils::getInstance).thenReturn(utils);
			when(fixture.item.getEnchantmentLevel(null)).thenReturn(1);
			assertTimeout(Duration.ofSeconds(2), () ->
					assertFalse(manager.damageItemInHand(fixture.player, Integer.MAX_VALUE)));
			verify(fixture.inventory).setItemInMainHand(any(ItemStack.class));
		}
	}

	@Test
	void boundedSamplerRetainsBinomialMoments() {
		Random random = new Random(47291);
		int count = 20_000;
		double sum = 0;
		double squared = 0;
		for (int i = 0; i < count; i++) {
			int hits = PlayerManager.sampleDamage(1_000, 50, 1_001, random);
			sum += hits;
			squared += (double) hits * hits;
		}
		double mean = sum / count;
		double variance = squared / count - mean * mean;
		assertEquals(500, mean, 1);
		assertEquals(250, variance, 10);
		assertEquals(0, PlayerManager.sampleDamage(100, 0, 10, random));
		assertEquals(10, PlayerManager.sampleDamage(Integer.MAX_VALUE, 100, 10, random));
	}

	private static final class Fixture {
		final Player player = mock(Player.class);
		final PlayerInventory inventory = mock(PlayerInventory.class);
		final ItemStack item = mock(ItemStack.class);
		final Damageable meta = mock(Damageable.class);

		Fixture(int currentDamage) {
			when(player.getInventory()).thenReturn(inventory);
			when(inventory.getItemInMainHand()).thenReturn(item);
			when(item.getType()).thenReturn(Material.LEGACY_WOOD_SWORD);
			when(item.getItemMeta()).thenReturn(meta);
			when(meta.getDamage()).thenReturn(currentDamage);
		}
	}
}
