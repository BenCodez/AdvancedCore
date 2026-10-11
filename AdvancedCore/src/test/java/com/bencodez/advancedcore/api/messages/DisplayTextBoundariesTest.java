package com.bencodez.advancedcore.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

class DisplayTextBoundariesTest {

    @Test
    void chatRemovesTemporaryMarkersOnlyAfterLegacyParsing() {
        TextComponent text = PlaceholderUtils.parseJson("\u00A7eSite: \u2060Minecraft Menu\u2060");

        assertEquals("Site: Minecraft Menu", text.toPlainText());
        assertFalse(text.toPlainText().contains("\u2060"));
        assertEquals("\u00A7eSite: Minecraft Menu", text.toLegacyText());
    }

    @Test
    void guardedColorSequenceRemainsLiteralAfterLastParsingPass() {
        TextComponent text = PlaceholderUtils.parseJson("&\u2060a");

        assertEquals("&a", text.toPlainText());
        assertFalse(text.toPlainText().contains("\u2060"));
    }

    @Test
    void interactiveActionsStayExactWhileVisibleTextIsClean() {
        TextComponent text = PlaceholderUtils.parseJson(
                "[Text=\"\u2060Site name\u2060\",Url=\"https://example.com/vote\"]");

        assertEquals("Site name", text.toPlainText());
        TextComponent link = (TextComponent) text.getExtra().get(1);
        ClickEvent click = link.getClickEvent();
        assertNotNull(click);
        assertEquals("https://example.com/vote", click.getValue());
    }
}
