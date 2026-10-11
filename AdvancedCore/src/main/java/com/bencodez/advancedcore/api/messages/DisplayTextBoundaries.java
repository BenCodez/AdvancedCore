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
        if (prepared == null || prepared.indexOf('\u2060') < 0) return prepared;
        StringBuilder visible = new StringBuilder(prepared.length());
        for (int i = 0; i < prepared.length(); i++) {
            char next = prepared.charAt(i);
            if (next != '\u2060') {
                visible.append(next);
                continue;
            }
            // Some legacy String sinks (inventory titles, item metadata, boss
            // bars) still parse § codes on the client. Prevent an incomplete
            // § in a trusted template from joining the next site-name letter
            // into an active legacy color code when dropping this guard.
            if (i > 0 && prepared.charAt(i - 1) == '\u00A7'
                    && i + 1 < prepared.length() && isLegacyColorCode(prepared.charAt(i + 1))) {
                visible.append(' ');
            }
        }
        return visible.toString();
    }

    private static boolean isLegacyColorCode(char code) {
        return "0123456789abcdefklmnorx".indexOf(Character.toLowerCase(code)) >= 0;
    }

    public static void afterFormatting(BaseComponent component) {
        if (component == null) return;
        if (component instanceof TextComponent text) {
            // A component already passed legacy parsing, so it can retain the
            // exact literal text without inserting a String-sink separator.
            text.setText(text.getText() == null ? null : text.getText().replace("\u2060", ""));
        }
        List<BaseComponent> children = component.getExtra();
        if (children != null) {
            for (BaseComponent child : children) afterFormatting(child);
        }
    }
}
