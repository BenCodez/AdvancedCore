package com.bencodez.advancedcore.api.user;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.chat.ComponentSerializer;

class LiteralReplayPayloadTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void terminalComponentsPreserveLiteralTextAndExactRichActions() {
        var original = new TextComponent("%player_name% &a [Javascript=unexpected()] | 界");
        original.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "inspect %raw_action% &a"));
        original.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("%raw_hover% [Text=\"literal\"]")));
        var restored = LiteralReplayPayload.decode(LiteralReplayPayload.player(OWNER.toString(), 0, original));
        assertEquals(ComponentSerializer.toString(original), ComponentSerializer.toString(restored.components()[0]));
    }

    @Test void legitimateNumericItemHoverMetadataSurvivesTerminalDecoding() {
        var original = new TextComponent("Stone");
        original.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item("minecraft:stone", 2, null)));
        var restored = LiteralReplayPayload.decode(LiteralReplayPayload.player(OWNER.toString(), 0, original));
        assertEquals(ComponentSerializer.toString(original), ComponentSerializer.toString(restored.components()[0]));
    }

    @Test void nullAndEmptyPayloadsRetainTheirNoOpShape() {
        assertNull(LiteralReplayPayload.decode(LiteralReplayPayload.player(OWNER.toString(), 0, null)).components()[0]);
        assertEquals(0, LiteralReplayPayload.decode(LiteralReplayPayload.recipient(OWNER, 0, new TextComponent[0]))
                .components().length);
        assertNull(LiteralReplayPayload.decode(LiteralReplayPayload.console(null)).console());
        assertEquals("", LiteralReplayPayload.decode(LiteralReplayPayload.console("")).console());
    }

    @Test void consolePayloadPreservesColorsSeparatorsAndUninterpretedSyntax() {
        String console = "§a界%NewLine%[Text=\"literal\",Command=\"%raw%\"]";
        assertEquals(console, LiteralReplayPayload.decode(LiteralReplayPayload.console(console)).console());
    }

    @Test void componentPayloadRejectsDuplicateFieldsTrailingTokensAndLenientJson() {
        for (String json : List.of("{\"text\":\"one\",\"text\":\"two\"}", "{text:'one'}",
                "{\"text\":\"one\"} true", "{\"text\":\"one\",}", "{\"text\":\"\\q\"}",
                "{\"text\":\"\\'\"}", "{\"text\":\"unescaped\nline\"}", "{\"text\":01}",
                "{\"text\":+1}", "{\"text\":1.}", "{\"text\":1e}", "{\"text\":NaN}",
                "{\"text\":truefalse}", "{\"text\":\"\\uQQQQ\"}",
                "{\"text\":\"item\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"minecraft:stone\",\"count\":1e2000000000}}}",
                "{\"text\":\"item\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"minecraft:stone\",\"count\":1e-2000000000}}}"))
            assertThrows(RuntimeException.class, () -> LiteralReplayPayload.decode("P2|" + OWNER + "|0|" + json));
    }

    @Test void ordinalsAndOwnersAreCanonical() {
        for (String index : List.of("00", "-1", "+1", "4096"))
            assertThrows(RuntimeException.class,
                    () -> LiteralReplayPayload.decode("P2|" + OWNER + "|" + index + "|{\"text\":\"ok\"}"));
        assertThrows(RuntimeException.class,
                () -> LiteralReplayPayload.decode("P2|0-0-0-0-1|0|{\"text\":\"ok\"}"));
    }

    @Test void restoredComponentTextRetainsItsOriginalBoundary() {
        var atLimit = new TextComponent("界".repeat(262144));
        assertEquals(262144, LiteralReplayPayload.decode(LiteralReplayPayload.player(OWNER.toString(), 0, atLimit))
                .components()[0].toPlainText().length());
        assertThrows(RuntimeException.class, () -> LiteralReplayPayload.decode(
                LiteralReplayPayload.player(OWNER.toString(), 0, new TextComponent("x".repeat(262145)))));
    }

    @Test void frozenRecipientBatchRetains4096ComponentBoundary() {
        var components = new TextComponent[4096];
        for (int index = 0; index < components.length; index++) components[index] = new TextComponent("");
        assertEquals(4096, LiteralReplayPayload.decode(LiteralReplayPayload.recipient(OWNER, 0, components))
                .components().length);
        assertThrows(RuntimeException.class,
                () -> LiteralReplayPayload.recipient(OWNER, 0, new TextComponent[4097]));
    }
}
