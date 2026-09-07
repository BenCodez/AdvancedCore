package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import me.clip.placeholderapi.PlaceholderAPI;

class JavascriptSecurityFindingRegressionTest {

	private static final String INJECTED_MARKER = "[Javascript=Bukkit.shutdown()]";

	@Test
	void displayNamePlaceholderCannotCreateExecutableJavascript() {
		Player player = mock(Player.class);
		org.mockito.Mockito.when(player.getDisplayName()).thenReturn(INJECTED_MARKER);
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("displayname", player.getDisplayName());
		assertEquals("Welcome [Javascript =Bukkit.shutdown()]",
				PlaceholderUtils.parseText("Welcome %displayname%", placeholders));
	}

	@Test
	void placeholderApiOutputCannotCreateExecutableJavascript() {
		org.bukkit.OfflinePlayer player = mock(org.bukkit.OfflinePlayer.class);
		try (MockedStatic<PlaceholderAPI> papi = mockStatic(PlaceholderAPI.class)) {
			papi.when(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()))
					.thenAnswer(invocation -> ((String) invocation.getArgument(1)).replace("%papi_value%", INJECTED_MARKER));
			String papiOutput = PlaceholderUtils.replaceExternalPlaceholders("Reward: %papi_value%",
					value -> PlaceholderAPI.setPlaceholders(player, value), false);
			assertEquals("Reward: [Javascript =Bukkit.shutdown()]", PlaceholderUtils.parseText(papiOutput));
		}
	}

	@Test
	void itemBuilderPlaceholderCannotCreateExecutableJavascript() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("conditional_value", INJECTED_MARKER);
		ItemStack stack = mock(ItemStack.class);
		ItemMeta meta = mock(ItemMeta.class);
		java.util.concurrent.atomic.AtomicReference<String> displayName = new java.util.concurrent.atomic.AtomicReference<>();
		org.mockito.Mockito.when(stack.clone()).thenReturn(stack);
		org.mockito.Mockito.when(stack.hasItemMeta()).thenReturn(true);
		org.mockito.Mockito.when(stack.getItemMeta()).thenReturn(meta);
		org.mockito.Mockito.when(meta.hasDisplayName()).thenReturn(true);
		org.mockito.Mockito.when(meta.getDisplayName()).thenAnswer(invocation -> displayName.get());
		org.mockito.Mockito.doAnswer(invocation -> { displayName.set(invocation.getArgument(0)); return null; })
				.when(meta).setDisplayName(anyString());
		ItemBuilder item = new ItemBuilder(stack).setName("Item: {conditional_value}")
				.setPlaceholders(placeholders).dontCheckLoreLength();
		item.toItemStack();
		assertEquals("Item: [Javascript =Bukkit.shutdown()]", item.getName());
	}
}
