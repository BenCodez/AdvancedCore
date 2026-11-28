package com.bencodez.advancedcore.tests.sound;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.SoundResolver;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public class SoundResolutionFixture {
    private Registry<Sound> registry;
    private Sound levelup;
    private Sound chime;
    private Sound custom;
    private Method resolve;

    // The real API initializes all registry constants through Bukkit. Supply a
    // synthetic server registry only during that initialization; no server is run.
    @BeforeAll
    static void initializeRegistry() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getRegistry(any())).thenAnswer(call -> new EmptyRegistry<>());
            Class.forName("org.bukkit.Registry");
        }
    }

    private static final class EmptyRegistry<T extends Keyed> implements Registry<T> {
        public T get(NamespacedKey key) { return entries.stream().filter(e -> e.getKey().equals(key)).findFirst().orElse(null); }
        public T getOrThrow(NamespacedKey key) { return null; }
        public java.util.stream.Stream<T> stream() { return java.util.stream.Stream.empty(); }
        private List<T> entries = List.of();
        public java.util.Iterator<T> iterator() { return entries.iterator(); }
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() throws Exception {
        registry = mock(Registry.class);
        levelup = sound("minecraft:entity.player.levelup");
        chime = sound("minecraft:block.note_block.chime");
        custom = sound("example:custom.note_block");
        when(registry.iterator()).thenAnswer(ignored -> List.of(levelup, chime, custom).iterator());
        when(registry.get(any())).thenAnswer(call -> {
            NamespacedKey key = call.getArgument(0);
            return List.of(levelup, chime, custom).stream().filter(s -> s.getKey().equals(key)).findFirst().orElse(null);
        });
        resolve = SoundResolver.class.getDeclaredMethod("resolve", String.class, Registry.class);
        resolve.setAccessible(true);
    }

    private Sound sound(String key) {
        Sound sound = mock(Sound.class);
        when(sound.getKey()).thenReturn(NamespacedKey.fromString(key));
        when(sound.toString()).thenReturn("CraftSound{holder=synthetic-debug-text}");
        return sound;
    }

    private Sound resolve(String name) throws Exception {
        return (Sound) resolve.invoke(null, name, registry);
    }

    @Test void editorKeysIgnoreRegistryDebugTextAndRoundTrip() throws Exception {
        assertEquals("block.note_block.chime", SoundResolver.configurationKey(chime));
        assertEquals("example:custom.note_block", SoundResolver.configurationKey(custom));
        for (Sound sound : List.of(levelup, chime, custom)) {
            assertSame(sound, resolve(SoundResolver.configurationKey(sound)));
        }
    }

    @Test void actualEditorOffersStableKeysInsteadOfDebugStrings() {
        var reward = mock(com.bencodez.advancedcore.api.rewards.RewardEditData.class);
        var editor = mock(com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditSound.class, CALLS_REAL_METHODS);
        doReturn(null).when(editor).getBooleanButton(anyString(), any());
        doReturn(null).when(editor).getDoubleButton(anyString(), any());
        doReturn(null).when(editor).getBackButton(any());
        doReturn(null).when(editor).getStringButton(anyString(), any(), any(String[].class));
        @SuppressWarnings("unchecked")
        var sounds = (EmptyRegistry<Sound>) Registry.SOUNDS;
        sounds.entries = List.of(chime, custom);
        try (var gui = mockConstruction(com.bencodez.advancedcore.api.inventory.editgui.EditGUI.class)) {
            editor.open(mock(Player.class), reward);
            verify(editor).getStringButton("Sound.Sound", reward,
                    new String[] {"block.note_block.chime", "example:custom.note_block"});
        } finally {
            sounds.entries = List.of();
        }
    }

    @Test void canonicalNamespacedAndLegacyInputsResolve() throws Exception {
        for (String name : List.of("entity.player.levelup", "minecraft:entity.player.levelup", "ENTITY_PLAYER_LEVELUP")) {
            assertSame(levelup, resolve(name));
        }
        for (String name : List.of("block.note_block.chime", "minecraft:block.note_block.chime", "BLOCK_NOTE_BLOCK_CHIME")) {
            assertSame(chime, resolve(name));
        }
        assertSame(custom, resolve("example:custom.note_block"));
    }

    @Test void invalidAndUnrelatedNamesDoNotInventRegistryEntries() throws Exception {
        for (String name : new String[] {null, "", " ", "unknown", "bad:key:extra", "bad sound", "CraftSound{holder=debug}",
                "minecraft:BLOCK_NOTE_BLOCK_CHIME", "example:BLOCK_NOTE_BLOCK_CHIME", "CUSTOM_NOTE_BLOCK", "block.note.block.chime", "a".repeat(300), "minecraft:" + "a".repeat(300)}) {
            assertNull(assertDoesNotThrow(() -> resolve(name)), String.valueOf(name));
        }
    }

    @Test void playbackPreservesLocationVolumePitchAndOfflineNoop() throws Exception {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class, CALLS_REAL_METHODS);
        var pluginField = AdvancedCoreUser.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(user, mock(AdvancedCorePlugin.class));
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        doReturn(player).when(user).getPlayer();
        when(player.getLocation()).thenReturn(location);
        try (MockedStatic<SoundResolver> resolver = mockStatic(SoundResolver.class)) {
            resolver.when(() -> SoundResolver.resolve("BLOCK_NOTE_BLOCK_CHIME")).thenReturn(chime);
            user.playSound("BLOCK_NOTE_BLOCK_CHIME", 0.4f, 1.6f);
            verify(player).playSound(location, chime, 0.4f, 1.6f);
            doReturn(null).when(user).getPlayer();
            user.playSound("offline", 1f, 1f);
            resolver.verify(() -> SoundResolver.resolve("offline"), never());
        }
    }

    @Test void invalidRewardWarnsNormallyWithBoundedTextAndRepetition() throws Exception {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class, CALLS_REAL_METHODS);
        doReturn(mock(Player.class)).when(user).getPlayer();
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        var field = AdvancedCoreUser.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(user, plugin);
        assertDoesNotThrow(() -> user.playSound("x".repeat(500), 1f, 1f));
        user.playSound(null, 1f, 1f);
        var lastWarning = AdvancedCoreUser.class.getDeclaredField("lastInvalidSoundWarningAt");
        lastWarning.setAccessible(true);
        lastWarning.setLong(user, 0L);
        user.playSound("bad\nsound", 1f, 1f);
        var warning = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger, times(2)).warning(warning.capture());
        for (String message : warning.getAllValues()) {
            assertTrue(message.contains("Invalid reward sound:"));
            assertFalse(message.contains("\n"));
            assertTrue(message.length() < 300);
        }
    }

    @Test void namespacedClickKeysNeverUseLegacyRewrite() {
        @SuppressWarnings("unchecked")
        var sounds = (EmptyRegistry<Sound>) Registry.SOUNDS;
        Sound click = sound("minecraft:ui.button.click");
        Sound dottedNamespace = sound("example.sounds:ui.button.click");
        Sound exactUnderscore = sound("example_sounds:custom_note");
        sounds.entries = List.of(click, dottedNamespace, exactUnderscore);
        try {
            AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class, CALLS_REAL_METHODS);
            assertAll(
                () -> {
                    when(options.getClickSoundSoundStr()).thenReturn("minecraft:ui_button.click");
                    assertNull(options.getClickSoundSound());
                },
                () -> {
                    when(options.getClickSoundSoundStr()).thenReturn("example_sounds:ui_button.click");
                    assertNull(options.getClickSoundSound());
                },
                () -> {
                    when(options.getClickSoundSoundStr()).thenReturn("example.sounds:ui.button.click");
                    assertSame(dottedNamespace, options.getClickSoundSound());
                },
                () -> {
                    when(options.getClickSoundSoundStr()).thenReturn("example_sounds:custom_note");
                    assertSame(exactUnderscore, options.getClickSoundSound());
                });
        } finally { sounds.entries = List.of(); }
    }

    @Test void mixedLegacyClickAliasRemainsSupported() {
        @SuppressWarnings("unchecked")
        var sounds = (EmptyRegistry<Sound>) Registry.SOUNDS;
        Sound click = sound("minecraft:ui.button.click");
        sounds.entries = List.of(click);
        try {
            AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class, CALLS_REAL_METHODS);
            when(options.getClickSoundSoundStr()).thenReturn("UI_BUTTON.CLICK");
            assertSame(click, options.getClickSoundSound());
        } finally { sounds.entries = List.of(); }
    }

    @Test void clickSoundUsesSameResolverAndRetainsNoneAndFallback() {
        @SuppressWarnings("unchecked")
        var sounds = (EmptyRegistry<Sound>) Registry.SOUNDS;
        Sound click = sound("minecraft:ui.button.click");
        sounds.entries = List.of(chime, levelup, click);
        try {
            // Use the actual Spigot parser and shared resolver, not static stubs.
            AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class, CALLS_REAL_METHODS);
            for (String invalid : new String[] {null, "", " ", "a".repeat(300), "bad:key:extra"}) {
                when(options.getClickSoundSoundStr()).thenReturn(invalid);
                assertSame(click, assertDoesNotThrow(options::getClickSoundSound), String.valueOf(invalid));
            }
            for (String valid : List.of("BLOCK_NOTE_BLOCK_CHIME", "block.note_block.chime", "minecraft:block.note_block.chime")) {
                when(options.getClickSoundSoundStr()).thenReturn(valid);
                assertSame(chime, options.getClickSoundSound());
            }
            when(options.getClickSoundSoundStr()).thenReturn("none");
            assertNull(options.getClickSoundSound());
            when(options.getClickSoundSoundStr()).thenReturn("unknown.valid");
            assertNull(options.getClickSoundSound());
            when(options.getClickSoundSoundStr()).thenReturn("UI_BUTTON.CLICK");
            assertSame(click, options.getClickSoundSound());
        } finally {
            sounds.entries = List.of();
        }
    }
}
