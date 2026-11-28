package com.bencodez.advancedcore.api.misc;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/** Resolves configured sound keys without relying on registry objects' debug text. */
public final class SoundResolver {
    private SoundResolver() {
    }

    /** Accepts canonical keys, namespaced keys and legacy Minecraft enum-style names. */
    public static Sound resolve(String input) {
        return resolve(input, Registry.SOUNDS);
    }

    static Sound resolve(String input, Registry<Sound> registry) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String name = input.trim();
        NamespacedKey key = parseKey(name);
        if (key == null) {
            return null;
        }
        Sound exact = registry.get(key);
        if (exact != null) {
            return exact;
        }
        // Legacy names flatten dots, but underscores inside a key remain significant.
        // Never reinterpret an explicitly namespaced key as an enum constant.
        if (name.indexOf(':') < 0 && name.matches("[A-Za-z0-9_]+")) {
            for (Sound sound : registry) {
                NamespacedKey candidate = sound.getKey();
                if (NamespacedKey.MINECRAFT.equals(candidate.getNamespace())
                        && candidate.getKey().replace('.', '_').equalsIgnoreCase(name)) {
                    return sound;
                }
            }
        }
        return null;
    }

    /** Parses configuration input without propagating Bukkit's validation exceptions. */
    public static NamespacedKey parseKey(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return NamespacedKey.fromString(input.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Retains the GUI click option's historical mixed underscore/dot aliases. */
    public static Sound resolveClickSound(String input) {
        if ("none".equalsIgnoreCase(input)) {
            return null;
        }
        Sound sound = resolve(input);
        if (sound != null) {
            return sound;
        }
        if (parseKey(input) == null) {
            return resolve("ui.button.click");
        }
        // Explicit namespaced keys are exact, even if the registry has no match.
        if (input.indexOf(':') >= 0) {
            return null;
        }
        // This legacy transformation is click-only and bare-name-only; shared
        // resolution above keeps underscores in keys such as block.note_block.chime.
        NamespacedKey legacy = parseKey(input.replace('_', '.'));
        return legacy == null ? resolve("ui.button.click") : Registry.SOUNDS.get(legacy);
    }

    /** A stable value suitable for configuration and the reward editor. */
    public static String configurationKey(Sound sound) {
        NamespacedKey key = sound.getKey();
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.toString();
    }
}
