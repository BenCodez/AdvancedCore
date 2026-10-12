package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import java.util.concurrent.atomic.AtomicInteger;
import com.bencodez.advancedcore.api.javascript.JavascriptTextTemplate;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

class MixedLiteralActionValuesTest {
    @Test void braceAliasesAreLiteralInVisibleHoverAndAllExactActions() {
        for (String attribute : List.of("Command", "URL", "Suggest_Command")) {
            TextComponent[] rendered = render("[Text=\"before{SITENAME}after {Legacy}\"," + attribute
                    + "=\"/{SiteName}/{lEgAcY}\",Hover=\"{SiteName} {Legacy}\"]", values("raw-legacy"));
            BaseComponent action = clickable(rendered).get(0);
            assertEquals("/raw-site/raw-legacy", action.getClickEvent().getValue());
            assertTrue(BaseComponent.toPlainText(rendered).contains("beforeR&D &aLiteralafter"));
            var hover = assertInstanceOf(Text.class, action.getHoverEvent().getContents().get(0));
            var hoverComponents = assertInstanceOf(BaseComponent[].class, hover.getValue());
            assertTrue(BaseComponent.toPlainText(hoverComponents).contains("R&D &aLiteral"));
            assertFalse(BaseComponent.toLegacyText(rendered).contains("VCORELITERAL"));
        }
    }

    @Test void braceAndPercentMarkersInsideInsertedLiteralDataAreNeverReplacedAgain() {
        HashMap<String, String> values = values("raw-legacy");
        String literal = "{Legacy}%Legacy%{SiteName}%SiteName%";
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", literal, "old-safe");
        assertEquals(literal + "/" + literal, BaseComponent.toPlainText(render("{SiteName}/%SiteName%", values)));
    }

    @Test void braceAliasInsideAuthoredScriptProtectsTheEntireScriptBeforeEvaluation() {
        HashMap<String, String> values = values("raw-legacy");
        String literal = "');[Javascript=danger()]";
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", literal, "old-safe");
        String source = "[Javascript='pre{SiteName}post']";
        AtomicInteger executable = new AtomicInteger();
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> {
                String text = call.getArgument(0);
                assertFalse(text.contains(literal));
                return JavascriptTextTemplate.parse(text).evaluate(java.util.function.Function.identity(), expression -> {
                    executable.incrementAndGet(); return "executed";
                });
            });
            TextComponent[] result = RewardDisplayPlaceholders.renderLiteralComponents(null, source, values);
            assertEquals(source.replace("{SiteName}", literal), BaseComponent.toPlainText(result));
            assertEquals(0, executable.get());
            assertTrue(clickable(result).isEmpty());
        }
    }

    @Test void legacyVisibleAndHoverValuesStayInertWithAnUnrelatedLiteralBinding() {
        for (String display : List.of("[Text=\"Click\",Command=\"/unexpected\"]", "shown\"],Command=\"/unexpected",
                "$1\\path\\\"quoted\"", "%vault_rank%", "[Javascript=danger()]")) {
            HashMap<String, String> legacy = legacyValues("raw-legacy", display);
            HashMap<String, String> mixed = new HashMap<>(legacy);
            RewardDisplayPlaceholders.putLiteral(mixed, "SiteName", "literal", "old-safe");
            for (String source : List.of("prefix{Legacy}suffix", "[Text=\"%Legacy%\",Command=\"/trusted\",Hover=\"{Legacy}\"]")) {
                try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
                    placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
                    BaseComponent[] previous = new BaseComponent[] {PlaceholderUtils.parseJson(
                            RewardDisplayPlaceholders.replaceFormattedMessage(source, legacy))};
                    BaseComponent[] current = RewardDisplayPlaceholders.renderLiteralComponents(null, source, mixed);
                    assertEquals(BaseComponent.toPlainText(previous), BaseComponent.toPlainText(current), display);
                    assertEquals(BaseComponent.toLegacyText(previous), BaseComponent.toLegacyText(current), display);
                    assertEquals(clickable(previous).size(), clickable(current).size(), display);
                    if (!clickable(current).isEmpty()) assertEquals("/trusted", clickable(current).get(0).getClickEvent().getValue());
                    assertFalse(BaseComponent.toLegacyText(current).contains("VCORELITERAL"));
                }
            }
        }
    }

    @Test void configuredLegacyColorsRenderInPlainTextAndHoverAlongsideLiteralValues() {
        HashMap<String, String> values = legacyValues("raw-legacy", "&cColored");
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", "&aLiteral", "old-safe");
        TextComponent[] plain = render("{Legacy} | %SiteName%", values);
        assertEquals("Colored | &aLiteral", BaseComponent.toPlainText(plain));
        assertTrue(BaseComponent.toLegacyText(plain).contains("§cColored"));
        TextComponent[] interactive = render("[Text=\"{Legacy} %SiteName%\",Command=\"/trusted\",Hover=\"{Legacy}\"]", values);
        assertEquals("Colored &aLiteral", BaseComponent.toPlainText(interactive));
        BaseComponent action = clickable(interactive).get(0);
        assertEquals("/trusted", action.getClickEvent().getValue());
        var hover = assertInstanceOf(Text.class, action.getHoverEvent().getContents().get(0));
        var content = assertInstanceOf(BaseComponent[].class, hover.getValue());
        assertEquals("Colored", BaseComponent.toPlainText(content));
        assertEquals(ChatColor.RED, content[0].getColor());
    }

    @Test void configuredLegacyColorsRenderInBroadcastPlainTextHoverAndExactActions() {
        HashMap<String, String> values = legacyValues("&aRaw", "&cColored");
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", "&aLiteral", "old-safe");
        var captured = RewardDisplayPlaceholders.prepareLiteralBroadcast(null,
                "[Text=\"{Legacy} %SiteName%\",Command=\"/trusted {Legacy}\",Hover=\"{Legacy}\"]", values);
        TextComponent[] rendered = captured.render(null);
        assertEquals("Colored &aLiteral", BaseComponent.toPlainText(rendered));
        BaseComponent action = clickable(rendered).get(0);
        assertEquals("/trusted &aRaw", action.getClickEvent().getValue());
        var hover = assertInstanceOf(Text.class, action.getHoverEvent().getContents().get(0));
        var content = assertInstanceOf(BaseComponent[].class, hover.getValue());
        assertEquals("Colored", BaseComponent.toPlainText(content));
        assertEquals(ChatColor.RED, content[0].getColor());
        assertFalse(BaseComponent.toLegacyText(rendered).contains("§aLiteral"));
    }

    @Test void legacyOpaqueValueDoesNotEnterProviderPassButOrdinaryPlaceholderStillDoes() {
        Player player = mock(Player.class);
        HashMap<String, String> legacy = legacyValues("raw-legacy", "%untrusted_provider%");
        legacy.put("rank", "%vault_rank%");
        HashMap<String, String> mixed = new HashMap<>(legacy);
        RewardDisplayPlaceholders.putLiteral(mixed, "SiteName", "literal", "old-safe");
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(eq(player), anyString())).thenAnswer(call -> call.getArgument(1));
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(player), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                assertFalse(text.contains("%untrusted_provider%"));
                return text.replace("%vault_rank%", "Admin");
            });
            String source = "%rank% {Legacy}";
            BaseComponent[] old = new BaseComponent[] {PlaceholderUtils.parseJson(
                    RewardDisplayPlaceholders.replaceFormattedMessage(player, source, legacy))};
            BaseComponent[] current = RewardDisplayPlaceholders.renderLiteralComponents(player, source, mixed);
            assertEquals("Admin %untrusted_provider%", BaseComponent.toPlainText(current));
            assertEquals(BaseComponent.toPlainText(old), BaseComponent.toPlainText(current));
        }
    }

    @Test void legacyAuthoredJavascriptKeepsItsExistingBoundDataPassWithMixedLiterals() {
        String display = "'); danger(); ('";
        HashMap<String, String> legacy = legacyValues("raw-legacy", display);
        HashMap<String, String> mixed = new HashMap<>(legacy);
        RewardDisplayPlaceholders.putLiteral(mixed, "SiteName", "literal", "old-safe");
        ArrayList<String> expressions = new ArrayList<>();
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> {
                String text = call.getArgument(0);
                assertFalse(text.contains(display));
                return JavascriptTextTemplate.parse(text).evaluate(java.util.function.Function.identity(), expression -> {
                    assertTrue(expression.contains("%__advancedcore_bound_"));
                    expressions.add(expression); return "evaluated";
                });
            });
            String source = "[Javascript='{Legacy}']";
            String previous = RewardDisplayPlaceholders.replaceFormattedMessage(source, legacy);
            BaseComponent[] current = RewardDisplayPlaceholders.renderLiteralComponents(null, source, mixed);
            assertEquals(previous, BaseComponent.toPlainText(current));
            assertEquals(2, expressions.size());
            assertEquals(expressions.get(0), expressions.get(1));
        }
    }

    @Test void mixedBroadcastKeepsLegacyValuesOpaqueAcrossSourceAndRecipientPasses() {
        Player source = mock(Player.class);
        Player recipient = mock(Player.class);
        HashMap<String, String> legacy = legacyValues("%untrusted_raw%", "%untrusted_provider%");
        legacy.put("rank", "%vault_rank%");
        HashMap<String, String> mixed = new HashMap<>(legacy);
        RewardDisplayPlaceholders.putLiteral(mixed, "SiteName", "literal", "old-safe");
        String template = "[Text=\"{Legacy} %rank%\",Command=\"/{Legacy}/%recipient_suffix%\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(source), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                assertFalse(text.contains("%untrusted_provider%")); assertFalse(text.contains("%untrusted_raw%"));
                return text.replace("%vault_rank%", "SourceRank");
            });
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                assertFalse(text.contains("%untrusted_provider%")); assertFalse(text.contains("%untrusted_raw%"));
                return text.replace("%recipient_suffix%", "recipient");
            });
            BaseComponent[] previous = new BaseComponent[] {PlaceholderUtils.parseJson(
                    RewardDisplayPlaceholders.replaceFormattedBroadcast(source, recipient, template, legacy))};
            var captured = RewardDisplayPlaceholders.prepareLiteralBroadcast(source, template, mixed);
            mixed.put("Legacy", "changed"); RewardDisplayPlaceholders.put(mixed, "Legacy", "changed");
            BaseComponent[] current = captured.render(recipient);
            assertEquals(BaseComponent.toPlainText(previous), BaseComponent.toPlainText(current));
            assertEquals("/%untrusted_raw%/recipient", clickable(current).get(0).getClickEvent().getValue());
            assertEquals(clickable(previous).get(0).getClickEvent().getValue(), clickable(current).get(0).getClickEvent().getValue());
            assertFalse(BaseComponent.toLegacyText(current).contains("VCORELITERAL"));
        }
    }

    @Test void legacyExactActionJavascriptUsesTheExistingRawBindingAndOnlyItsFinalOutputIsShielded() {
        String raw = "raw'$\\value";
        HashMap<String, String> legacy = legacyValues(raw, "&cDisplayed");
        HashMap<String, String> mixed = new HashMap<>(legacy);
        RewardDisplayPlaceholders.putLiteral(mixed, "SiteName", "literal", "old-safe");
        ArrayList<String> expressions = new ArrayList<>();
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> {
                String text = call.getArgument(0);
                assertFalse(text.contains(raw));
                return JavascriptTextTemplate.parse(text).evaluate(java.util.function.Function.identity(), expression -> {
                    assertTrue(expression.contains("%__advancedcore_bound_"));
                    expressions.add(expression); return "/computed-command";
                });
            });
            String source = "[Text=\"{Legacy}\",Command=\"[Javascript='/cmd/{Legacy}']\"]";
            BaseComponent[] previous = new BaseComponent[] {PlaceholderUtils.parseJson(
                    RewardDisplayPlaceholders.replaceFormattedMessage(source, legacy))};
            BaseComponent[] current = RewardDisplayPlaceholders.renderLiteralComponents(null, source, mixed);
            assertEquals("/computed-command", clickable(current).get(0).getClickEvent().getValue());
            assertEquals(clickable(previous).get(0).getClickEvent().getValue(), clickable(current).get(0).getClickEvent().getValue());
            assertEquals(2, expressions.size());
            assertEquals(expressions.get(0), expressions.get(1));
        }
    }

    @Test void escapedBraceMarkersKeepAuthoredDollarAndBackslashCharactersWithoutPadding() {
        HashMap<String, String> values = values("raw-legacy");
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", "$1\\label", "old-safe");
        assertEquals("$\\$1\\label|\\$1\\label", BaseComponent.toPlainText(render("$\\{SiteName}|\\%SiteName%", values)));
    }

    private HashMap<String, String> legacyValues(String raw, String display) {
        HashMap<String, String> values = new HashMap<>();
        values.put("Legacy", raw);
        RewardDisplayPlaceholders.put(values, "Legacy", display);
        return values;
    }

    @Test void legacyDisplayOverrideKeepsRawValueInEveryAuthoredClickAttribute() {
        String raw = "&aRaw §b%NewLine% [Text=\"unexpected\",Command=\"/bad\"]";
        for (String attribute : List.of("Command", "URL", "Suggest_Command")) {
            HashMap<String, String> values = values(raw);
            TextComponent[] rendered = render("[Text=\"%Legacy% %SiteName%\"," + attribute
                    + "=\"prefix:%LeGaCy%\",Hover=\"%Legacy%\"]", values);
            List<BaseComponent> clickable = clickable(rendered);
            assertEquals(1, clickable.size(), attribute);
            assertEquals("prefix:" + raw, clickable.get(0).getClickEvent().getValue(), attribute);
            assertTrue(BaseComponent.toLegacyText(rendered).contains("§cFormatted Name"));
            assertTrue(BaseComponent.toLegacyText(rendered).contains("R&D &aLiteral"));
            var hover = assertInstanceOf(Text.class, clickable.get(0).getHoverEvent().getContents().get(0));
            var hoverComponents = assertInstanceOf(BaseComponent[].class, hover.getValue());
            assertEquals(ChatColor.RED, hoverComponents[0].getColor());
            assertFalse(BaseComponent.toLegacyText(rendered).contains("VCORELITERAL"));
        }
    }

    @Test void authoredAmpersandAndSectionPrefixesCannotConsumeActionTokens() {
        for (String prefix : List.of("&", "§")) {
            TextComponent[] rendered = render("[Text=\"%SiteName%\",Command=\"" + prefix + "%Legacy%\"]", values("aRaw"));
            assertEquals(prefix + "aRaw", clickable(rendered).get(0).getClickEvent().getValue());
        }
    }

    @Test void protectedScriptTextIsNotRetokenizedAsAnAuthoredAction() {
        String source = "[Javascript='[Text=\"%SiteName%\",Command=\"%Legacy%\"]']";
        var prepared = LiteralMessageTemplate.prepare(source, java.util.Map.of("sitename", "literal"),
                java.util.Map.of("sitename", "raw-site", "legacy", "raw-legacy"));
        String restored = prepared.finishText(prepared.template());
        assertEquals(source.replace("%SiteName%", "literal"), restored);
        assertFalse(restored.contains("VCORELITERAL"));
    }

    private HashMap<String, String> values(String raw) {
        HashMap<String, String> values = new HashMap<>();
        values.put("SiteName", "raw-site");
        values.put("Legacy", raw);
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", "R&D &aLiteral", "old-safe");
        RewardDisplayPlaceholders.put(values, "Legacy", "&cFormatted Name");
        return values;
    }

    private TextComponent[] render(String source, HashMap<String, String> values) {
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            return RewardDisplayPlaceholders.renderLiteralComponents(null, source, values);
        }
    }

    private List<BaseComponent> clickable(BaseComponent[] components) {
        List<BaseComponent> result = new ArrayList<>();
        for (BaseComponent component : components) {
            if (component.getClickEvent() != null) result.add(component);
            if (component.getExtra() != null) result.addAll(clickable(component.getExtra().toArray(new BaseComponent[0])));
        }
        return result;
    }
}
