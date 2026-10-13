package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;
import net.md_5.bungee.api.chat.BaseComponent;

class LiteralBatchOutputBudgetTest {
    private HashMap<String, String> bindings(String text) {
        var map = new HashMap<String, String>();
        map.put("A", "raw-action");
        RewardDisplayPlaceholders.putLiteral(map, "A", text, "safe");
        return map;
    }

    @Test void validMultilineBatchRetainsMaximumRestoredTextAndLiteralBoundaries() {
        var map = bindings("a".repeat(8192));
        try (var parsing = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            parsing.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            var lines = RewardDisplayPlaceholders.renderLiteralComponents(null,
                    "%A%%NewLine%".repeat(32), map);
            assertEquals(32, lines.length);
            assertEquals(262144, BaseComponent.toPlainText(lines).length());
        }
    }

    @Test void oversizedPlayerBatchRejectsBeforeAnyComponentParsing() {
        var parsed = new AtomicInteger();
        try (var parsing = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            parsing.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            parsing.when(() -> PlaceholderUtils.parseJson(anyString())).thenAnswer(call -> {
                parsed.incrementAndGet(); return call.callRealMethod();
            });
            assertThrows(IllegalArgumentException.class, () -> RewardDisplayPlaceholders.renderLiteralComponents(null,
                    "%A%%NewLine%".repeat(4000), bindings("a".repeat(8192))));
            assertEquals(0, parsed.get(), "reject aggregate literal expansion before allocating its component batch");
        }
    }

    @Test void authoredBroadcastLinesShareOneRestoredOutputBudget() {
        var parsed = new AtomicInteger();
        try (var parsing = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            parsing.when(() -> PlaceholderUtils.parseJson(anyString())).thenAnswer(call -> {
                parsed.incrementAndGet(); return call.callRealMethod();
            });
            var prepared = RewardDisplayPlaceholders.prepareLiteralBroadcast(null,
                    "%A%%NewLine%".repeat(40), bindings("a".repeat(8192)));
            assertThrows(IllegalArgumentException.class, () -> prepared.render(null));
            assertTrue(parsed.get() <= 32, "stop parsing when the recipient's aggregate output budget is exhausted");
        }
    }

    @Test void recipientExpandedFragmentsRejectBeforeComponentParsing() {
        Player recipient = mock(Player.class);
        var parsed = new AtomicInteger();
        try (var parsing = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            parsing.when(() -> PlaceholderUtils.parseJson(anyString())).thenAnswer(call -> {
                parsed.incrementAndGet(); return call.callRealMethod();
            });
            parsing.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString()))
                    .thenAnswer(call -> call.<String>getArgument(1).repeat(100));
            var prepared = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "%A%", bindings("a".repeat(8192)));
            assertThrows(IllegalArgumentException.class, () -> prepared.renderRecipientLineFragments(recipient, 0));
            assertEquals(0, parsed.get(), "recipient expansion must be budgeted before rich-text parsing");
        }
    }

    @Test void componentTreesAcrossPlayerLinesShareOneNodeBudget() {
        try (var parsing = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            parsing.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            assertThrows(IllegalArgumentException.class, () -> RewardDisplayPlaceholders.renderLiteralComponents(null,
                    "%A%%NewLine%".repeat(4096), bindings("short")));
        }
    }
}
