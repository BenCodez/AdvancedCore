package com.bencodez.advancedcore.api.messages;

import java.util.List;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Drops temporary WORD JOINER formatting guards only at a final player-facing
 * sink. The guards must survive placeholder, JavaScript, color, and interactive
 * message parsing; removing them earlier would make external identifiers active
 * formatting syntax again.
 */
public final class DisplayTextBoundaries {

    private DisplayTextBoundaries() {
    }

    public static String afterFormatting(String prepared) {
        return prepared == null ? null : prepared.replace("\u2060", "");
    }

    public static void afterFormatting(BaseComponent component) {
        if (component == null) return;
        if (component instanceof TextComponent text) {
            text.setText(afterFormatting(text.getText()));
        }
        List<BaseComponent> children = component.getExtra();
        if (children != null) {
            for (BaseComponent child : children) afterFormatting(child);
        }
    }
}
