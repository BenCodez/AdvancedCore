package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.builtin.RewardMessages;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.simpleapi.serverhandle.IServerHandle;
import com.bencodez.simpleapi.array.ArrayUtils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

class RewardLiteralDisplayTest {
    @TempDir File directory;
    private HashMap<String, String> values(String literal) {
        HashMap<String, String> values = new HashMap<>();
        values.put("SiteName", "raw-site");
        RewardDisplayPlaceholders.putLiteral(values, "SiteName", literal, " legacy-safe ");
        return values;
    }

    private TextComponent[] render(String template, HashMap<String, String> values) {
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
                    .thenAnswer(call -> call.getArgument(0));
            return RewardDisplayPlaceholders.renderLiteralComponents(null, template, values);
        }
    }

    @Test
    void finalVisibleTextUsesOnlyAuthoredSpacing() {
        String[][] examples = {
            {"&7Site: &e%SiteName%", "Site: Minecraft Menu"},
            {"[%SiteName%]", "[Minecraft Menu]"},
            {"Vote on %SiteName%!", "Vote on Minecraft Menu!"},
            {"Site:%SiteName%", "Site:Minecraft Menu"},
            {"&a%SiteName%&7 - Vote now", "Minecraft Menu - Vote now"}
        };
        for (String[] example : examples)
            assertEquals(example[1], BaseComponent.toPlainText(render(example[0], values("Minecraft Menu"))));
    }

    @Test
    void externalTextIsLiteralThroughFinalComponentSerialization() {
        for (String value : List.of("&aGreen", "&kSpoofed", "&rReset", "&#FF0000Red", "%player_name%",
                "%vault_eco_balance%", "%javascript_example%", "Javascript=1", "script=1", "Java",
                "[Text=\"extra\",Command=\"/unexpected\"]", "R&D", "VPLITERAL0Q")) {
            HashMap<String, String> stored = values(value);
            HashMap<String, String> before = new HashMap<>(stored);
            TextComponent[] lines = render("&7[%SiteName%]", stored);
            assertEquals("[" + value + "]", BaseComponent.toPlainText(lines));
            assertEquals(before, stored);
            assertFalse(ComponentSerializer.toString(lines).contains("clickEvent"));
            assertFalse(ComponentSerializer.toString(lines).contains("hoverEvent"));
            assertFalse(BaseComponent.toPlainText(lines).contains("\u2060"));
            assertFalse(BaseComponent.toPlainText(lines).contains("\uE000"));
        }
    }

    @Test
    void sectionSignCannotActivateFormattingOnLegacyClient() {
        assertEquals("(section)aGreen", BaseComponent.toPlainText(render("%SiteName%", values("§aGreen"))));
    }

    @Test
    void authoredWhitespaceAndRepeatedRenderingAreStable() {
        HashMap<String, String> map = values("Minecraft Menu");
        for (int i = 0; i < 3; i++) {
            assertEquals("  [Minecraft Menu]  ", BaseComponent.toPlainText(render("  [%SiteName%]  ", map)));
        }
    }

    @Test
    void placeholderAndJavascriptTokensCannotFormAcrossBoundaries() {
        assertEquals("%player_name%", BaseComponent.toPlainText(render("%SiteName%player_name%", values("%"))));
        assertEquals("[Javascript=1]", BaseComponent.toPlainText(render("[Java%SiteName%]", values("script=1"))));
        assertEquals("[Javascript=1]", BaseComponent.toPlainText(render("[%SiteName%script=1]", values("Java"))));
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> {
                String protectedText = call.getArgument(0);
                assertFalse(protectedText.contains("[Javascript="));
                return protectedText;
            });
            assertEquals("[Javascript=Java]", BaseComponent.toPlainText(
                    RewardDisplayPlaceholders.renderLiteralComponents(null, "[Javascript=%SiteName%]", values("Java"))));
        }
    }

    @Test
    void incompleteTrustedColorCannotConsumeALiteralToken() {
        var lines = render("&%SiteName%", values("aSite"));
        assertEquals("&aSite", BaseComponent.toPlainText(lines));
        assertFalse(BaseComponent.toLegacyText(lines).contains("§a"));
        assertEquals("(section)aSite", BaseComponent.toPlainText(render("§%SiteName%", values("aSite"))));
    }

    @Test
    void clickActionsKeepExactRawValuesAndHoverUsesLiteralDisplay() {
        var lines = render("[Text=\"%SiteName%\",Command=\"/inspect %SiteName%\",Hover=\"%SiteName%\"]",
                values("[Text=\"x\",Command=\"/unexpected\"]"));
        assertEquals("[Text=\"x\",Command=\"/unexpected\"]", BaseComponent.toPlainText(lines));
        String json = ComponentSerializer.toString(lines);
        assertTrue(json.contains("/inspect raw-site"));
        assertFalse(json.contains("/inspect [Text"));
        assertTrue(json.contains("hoverEvent"));
        assertFalse(json.contains("ACLITERAL"));
    }

    @Test
    void urlActionsPreserveRawIdentityRatherThanDisplay() {
        var lines = render("[Text=\"%SiteName%\",Url=\"https://example.com/%SiteName%\"]", values("Pretty Site"));
        assertEquals("Pretty Site", BaseComponent.toPlainText(lines));
        assertTrue(ComponentSerializer.toString(lines).contains("https://example.com/raw-site"));
    }

    @Test
    void oldDisplayValuesKeepConfiguredColorsAlongsideNewLiteralValues() {
        var map = values("Minecraft Menu");
        map.put("Rank", "raw-rank");
        RewardDisplayPlaceholders.put(map, "Rank", "&aAdmin");
        var lines = render("%Rank%: %SiteName%", map);
        assertEquals("Admin: Minecraft Menu", BaseComponent.toPlainText(lines));
        assertTrue(BaseComponent.toLegacyText(lines).contains("§a"));
    }

    @Test
    void literalMetadataSurvivesExistingOfflineSerializationAndHasSafeOldView() {
        var initial = values("%player_name% &aSite");
        var restored = ArrayUtils.fromString(ArrayUtils.makeString(initial));
        assertEquals("raw-site", restored.get("SiteName"));
        assertEquals(" legacy-safe ", RewardDisplayPlaceholders.forDisplay(restored).get("SiteName"));
        assertEquals("%player_name% &aSite", BaseComponent.toPlainText(render("%SiteName%", restored)));
        assertEquals(1, RewardDisplayPlaceholders.forDisplay(restored).size());
    }

    @Test
    void replacingLiteralWithTrustedDisplayClearsAllCaseVariantMetadata() {
        var map = values("old");
        RewardDisplayPlaceholders.put(map, "sitename", "&aNew");
        assertFalse(RewardDisplayPlaceholders.hasLiteralValues(map));
        assertEquals("&aNew", RewardDisplayPlaceholders.forDisplay(map).get("sitename"));
    }

    @Test
    void authoredLineBreaksSplitBeforeExternalTextIsRestored() {
        var lines = render("First:%SiteName%%NewLine%Second:%SiteName%", values("%NewLine%"));
        assertEquals(2, lines.length);
        assertEquals("First:%NewLine%", lines[0].toPlainText());
        assertEquals("Second:%NewLine%", lines[1].toPlainText());
    }

    @Test
    void broadcastCapturesSourceOnceAndEvaluatesEachRecipientWithoutRestoringEarly() {
        Player source = mock(Player.class);
        Player recipient = mock(Player.class);
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(source), anyString()))
                    .thenAnswer(call -> call.<String>getArgument(1).replace("%source%", "Source"));
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString()))
                    .thenAnswer(call -> {
                        String protectedText = call.getArgument(1);
                        assertFalse(protectedText.contains("%player_name%"));
                        return protectedText.replace("%recipient%", "Recipient");
                    });
            var message = RewardDisplayPlaceholders.prepareLiteralBroadcast(source,
                    "%source% %recipient% %SiteName%", values("%player_name%"));
            assertEquals("Source Recipient %player_name%", BaseComponent.toPlainText(message.render(recipient)));
            assertEquals("Source %recipient% %player_name%", BaseComponent.toPlainText(message.render(null)));
        }
    }

    @Test
    void guardedLegacyDisplayHasItsOwnBoundWithoutIncreasingTheLiteralBound() {
        var map = new HashMap<String, String>();
        map.put("SiteName", "raw-site");
        String literal = "x".repeat(8192);
        String legacy = "y".repeat(32768);
        RewardDisplayPlaceholders.putLiteral(map, "SiteName", literal, legacy);
        var restored = ArrayUtils.fromString(ArrayUtils.makeString(map));
        assertEquals("raw-site", restored.get("SiteName"));
        assertEquals(legacy, RewardDisplayPlaceholders.forDisplay(restored).get("SiteName"));
        assertEquals(literal, BaseComponent.toPlainText(render("%SiteName%", restored)));
        var before = new HashMap<>(map);
        assertThrows(IllegalArgumentException.class,
                () -> RewardDisplayPlaceholders.putLiteral(map, "SiteName", literal, "y".repeat(32769)));
        assertEquals(before, map);
        assertThrows(IllegalArgumentException.class,
                () -> RewardDisplayPlaceholders.putLiteral(map, "SiteName", "x".repeat(8193), legacy));
        assertEquals(before, map);
    }

    @Test
    void missingRawActionNeverUsesTheLiteralDisplayAsACommandOrUrl() {
        HashMap<String, String> stored = new HashMap<>();
        RewardDisplayPlaceholders.putLiteral(stored, "SiteName", "external-display", "legacy-safe");
        TextComponent[] rendered = render(
                "[Text=\"%SiteName%\",Command=\"/inspect %SiteName%\"]", stored);
        assertEquals("external-display", BaseComponent.toPlainText(rendered));
        String json = ComponentSerializer.toString(rendered);
        assertTrue(json.contains("/inspect "), json);
        assertFalse(json.contains("/inspect external-display"), json);
        assertTrue(ComponentSerializer.toString(render(
                "[Text=\"%SiteName%\",Url=\"https://example.com/%SiteName%\"]", stored))
                .contains("https://example.com/\""));
    }

    @Test
    void directLiteralPreparationResolvesMixedCaseRawActionKeys() {
        for (String marker : List.of("%sItEnAmE%", "{sItEnAmE}")) {
            var prepared = com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.prepare(
                    "[Text=\"" + marker + "\",Command=\"/inspect " + marker + "\"]",
                    java.util.Map.of("SiteName", "display"), java.util.Map.of("SiTeNaMe", "raw-site"));
            TextComponent rendered = prepared.finish(PlaceholderUtils.parseJson(prepared.template()));
            assertEquals("display", rendered.toPlainText());
            assertTrue(ComponentSerializer.toString(rendered).contains("/inspect raw-site"));
            assertFalse(ComponentSerializer.toString(rendered).contains("/inspect display"));
        }
    }

    @Test
    void directLiteralPreparationRejectsAmbiguousCaseVariantActionKeys() {
        HashMap<String, String> actions = new HashMap<>();
        actions.put("SiteName", "one");
        actions.put("sitename", "two");
        assertThrows(IllegalArgumentException.class,
                () -> com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.prepare("%SiteName%",
                        java.util.Map.of("SiteName", "display"), actions));
    }

    @Test
    void rewardMessageAndBroadcastRejectAmbiguousCaseVariantActionKeys() {
        var stored = values("display");
        stored.put("SiteName", "one");
        stored.put("sitename", "two");
        for (String attribute : List.of("Command", "Url")) {
            String template = "[Text=\"%SiteName%\"," + attribute + "=\"%SiteName%\"]";
            assertThrows(IllegalArgumentException.class, () -> render(template, stored));
            assertThrows(IllegalArgumentException.class,
                    () -> RewardDisplayPlaceholders.prepareLiteralBroadcast(null, template, stored));
        }
    }

    @Test
    void nonAsciiLiteralAtTheDeclaredCharacterLimitSurvivesOfflineSerialization() {
        for (String literal : List.of("界".repeat(8192), "Ж".repeat(8192), "\uD83D\uDE00".repeat(4096))) {
            var restored = ArrayUtils.fromString(ArrayUtils.makeString(values(literal)));
            assertEquals("[" + literal + "]", BaseComponent.toPlainText(render("[%SiteName%]", restored)));
            var broadcast = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "{%SiteName%}", restored);
            assertEquals("{" + literal + "}", BaseComponent.toPlainText(broadcast.render(null)));
        }
    }

    @Test
    void persistedLiteralCannotExceedTheDecodedCharacterLimit() {
        var map = values("safe");
        String key = map.keySet().stream().filter(name -> name.contains("literal:")).findFirst().orElseThrow();
        String oversized = "x".repeat(8193);
        map.put(key, "b" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                oversized.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> render("%SiteName%", map));
        assertThrows(IllegalArgumentException.class,
                () -> RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "%SiteName%", map));
    }

    @Test
    void providerProducedSectionPrefixIsProtectedBeforeTheFinalMessageParser() {
        Player player = mock(Player.class);
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(player), anyString()))
                    .thenAnswer(call -> call.<String>getArgument(1).replace("%prefix%", "§"));
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(eq(player), anyString()))
                    .thenAnswer(call -> call.getArgument(1));
            var rendered = RewardDisplayPlaceholders.renderLiteralComponents(player, "%prefix%%SiteName%",
                    values("Minecraft Menu"));
            assertEquals("(section)Minecraft Menu", BaseComponent.toPlainText(rendered));
            assertFalse(ComponentSerializer.toString(rendered).contains("VCORELITERAL"));
        }
    }

    @Test
    void recipientProviderSectionPrefixIsProtectedBeforeTheBroadcastParser() {
        Player recipient = mock(Player.class);
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString()))
                    .thenAnswer(call -> call.<String>getArgument(1).replace("%prefix%", "§"));
            var broadcast = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "%prefix%%SiteName%",
                    values("Minecraft Menu"));
            var rendered = broadcast.render(recipient);
            assertEquals("(section)Minecraft Menu", BaseComponent.toPlainText(rendered));
            assertFalse(ComponentSerializer.toString(rendered).contains("VCORELITERAL"));
        }
    }

    @Test
    void providerBoundaryTokenKeepsItsExactActionValueSeparateFromALiteralSectionLabel() {
        Player recipient = mock(Player.class);
        String template = "[Text=\"%SiteName%\",Command=\"/inspect %prefix%%SiteName%\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString()))
                    .thenAnswer(call -> call.<String>getArgument(1).replace("%prefix%", "§"));
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(eq(recipient), anyString()))
                    .thenAnswer(call -> call.getArgument(1));
            for (boolean hasRaw : List.of(true, false)) {
                HashMap<String, String> stored = new HashMap<>();
                if (hasRaw) stored.put("SiteName", "raw-site");
                RewardDisplayPlaceholders.putLiteral(stored, "SiteName", "§", "legacy-safe");
                String expectedAction = "/inspect §" + (hasRaw ? "raw-site" : "");
                var message = RewardDisplayPlaceholders.renderLiteralComponents(recipient, template, stored);
                var broadcast = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, template, stored)
                        .render(recipient);
                for (var rendered : List.of(message, broadcast)) {
                    assertEquals("(section)", BaseComponent.toPlainText(rendered));
                    String json = ComponentSerializer.toString(rendered);
                    assertTrue(json.contains(expectedAction + "\""), json);
                    assertFalse(json.contains("VCORELITERAL"), json);
                }
            }
        }
    }

    @Test
    void invalidBindingDoesNotPartiallyChangeCallerMetadata() {
        var map = values("safe");
        var before = new HashMap<>(map);
        assertThrows(IllegalArgumentException.class, () -> RewardDisplayPlaceholders.putLiteral(map, "bad key", "value", "safe"));
        assertEquals(before, map);
        assertThrows(IllegalArgumentException.class, () -> render("x".repeat(65537), map));
    }

    @Test
    void realRewardMessageSinkSendsFinalLinesWithoutAnotherParser() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        var options = mock(com.bencodez.advancedcore.AdvancedCoreConfigOptions.class);
        when(options.isOnlineMode()).thenReturn(true);
        when(plugin.getOptions()).thenReturn(options);
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        RewardHandler handler = new RewardHandler(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, UUID.randomUUID(), "SyntheticPlayer"));
        doReturn(player).when(user).getPlayer();
        IServerHandle transport = mock(IServerHandle.class);
        ArrayList<String> delivered = new ArrayList<>();
        doAnswer(call -> { delivered.add(call.<BaseComponent>getArgument(1).toPlainText()); return null; })
                .when(transport).sendMessage(eq(player), any(BaseComponent.class));
        try (var utility = mockStatic(PlayerUtils.class);
                var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            utility.when(PlayerUtils::getServerHandle).thenReturn(transport);
            placeholders.when(() -> PlaceholderUtils.parseText(eq(player), anyString())).thenAnswer(call ->
                    com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1)));
            RewardMessages.register(handler, plugin);
            RewardInjectString message = handler.getInjectedRewards().stream()
                    .filter(RewardInjectString.class::isInstance).map(RewardInjectString.class::cast)
                    .filter(inject -> inject.getPath().equals("Message")).findFirst().orElseThrow();
            message.onRewardRequest(mock(Reward.class), user,
                    "Site:%SiteName%%NewLine%[%SiteName%]", values("%player_name% &aName"));
            assertEquals(List.of("Site:%player_name% &aName", "[%player_name% &aName]"), delivered);
            verify(user, never()).sendPreparedMessage(anyString());
        } finally {
            handler.getDelayedTimer().shutdownNow();
        }
    }

    @Test
    void scopedMessageCompletionWaitsForOwnerAndIgnoresRepeatedCallbacks() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        var options = mock(com.bencodez.advancedcore.AdvancedCoreConfigOptions.class);
        when(options.isOnlineMode()).thenReturn(true);
        when(plugin.getOptions()).thenReturn(options);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        ArrayList<Runnable> global = new ArrayList<>();
        ArrayList<Runnable> owners = new ArrayList<>();
        doAnswer(call -> { global.add(call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { owners.add(call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, UUID.randomUUID(), "SyntheticPlayer"));
        doReturn(player).when(user).getPlayer();
        IServerHandle transport = mock(IServerHandle.class);
        ArrayList<String> sent = new ArrayList<>();
        doAnswer(call -> { sent.add(call.<BaseComponent>getArgument(1).toPlainText()); return null; })
                .when(transport).sendMessage(eq(player), any(BaseComponent.class));
        try (var utility = mockStatic(PlayerUtils.class);
                var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            utility.when(PlayerUtils::getServerHandle).thenReturn(transport);
            placeholders.when(() -> PlaceholderUtils.parseText(eq(player), anyString())).thenAnswer(call -> {
                assertTrue(!owners.isEmpty());
                return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
            });
            var collection = user.beginAsyncActionCollection();
            var map = values("Original");
            user.sendLiteralDisplayMessage("Site:%SiteName%", map);
            RewardDisplayPlaceholders.putLiteral(map, "SiteName", "Changed", " safe ");
            var finished = user.endAsyncActionCollection(collection).toCompletableFuture();
            assertFalse(finished.isDone());
            assertTrue(sent.isEmpty());
            assertEquals(1, global.size());
            global.get(0).run();
            assertFalse(finished.isDone());
            assertTrue(sent.isEmpty());
            assertEquals(1, owners.size());
            owners.get(0).run();
            finished.join();
            owners.get(0).run();
            assertEquals(List.of("Site:Original"), sent);
        }
    }

    @Test
    void completedLiteralActionDoesNotRepeatWhenSerializedReplayMarkersChange() {
        try (ReplayFixture fixture = new ReplayFixture()) {
            var map = values("Original");
            fixture.dispatch("Site:%SiteName%", map).join();
            assertEquals(List.of("Site:Original"), fixture.sent);
            var restored = ArrayUtils.fromString(ArrayUtils.makeString(map));
            restored.put("CurrentDate", "synthetic-later-date");
            fixture.dispatch("Site:%SiteName%", restored).join();
            assertEquals(List.of("Site:Original"), fixture.sent);
        }
    }

    @Test
    void offlinePlayerReleasesAnUnstartedMessageReservationForTheNextSelection() {
        try (ReplayFixture fixture = new ReplayFixture()) {
            var map = values("Original");
            when(fixture.player.isOnline()).thenReturn(false);
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> fixture.dispatch("First selection", map).join());
            assertTrue(fixture.sent.isEmpty());
            when(fixture.player.isOnline()).thenReturn(true);
            fixture.dispatch("Different selection", ArrayUtils.fromString(ArrayUtils.makeString(map))).join();
            assertEquals(List.of("Different selection"), fixture.sent);
        }
    }

    @Test
    void disconnectDuringRenderingFailsAndRetainsAnUncompletedReplayForRetry() {
        try (ReplayFixture fixture = new ReplayFixture()) {
            var current = new java.util.concurrent.atomic.AtomicReference<Player>(fixture.player);
            doAnswer(call -> current.get()).when(fixture.user).getPlayer();
            fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                    .thenAnswer(call -> {
                        current.set(null);
                        return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
                    });
            var map = values("Original");
            var failure = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> fixture.dispatch("Site:%SiteName%", map).join());
            assertFalse(AdvancedCoreUser.isReplayActionNotStarted(failure),
                    "trusted formatting ran, so its replay reservation remains conservative");
            assertTrue(fixture.sent.isEmpty());
            current.set(fixture.player);
            fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                    .thenAnswer(call -> com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1)));
            fixture.dispatch("Site:%SiteName%", ArrayUtils.fromString(ArrayUtils.makeString(map))).join();
            assertEquals(List.of("Site:Original"), fixture.sent,
                    "the unavailable delivery must not have been checkpointed as completed");
        }
    }

    @Test
    void offlineOrReplacedPlayerAfterFormattingCannotReceiveOrCompleteTheReward() {
        for (boolean replaced : List.of(false, true)) {
            try (ReplayFixture fixture = new ReplayFixture()) {
                fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                        .thenAnswer(call -> {
                            if (replaced) doReturn(mock(Player.class)).when(fixture.user).getPlayer();
                            else when(fixture.player.isOnline()).thenReturn(false);
                            return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
                        });
                assertThrows(java.util.concurrent.CompletionException.class,
                        () -> fixture.dispatch("Site:%SiteName%", values("Original")).join());
                assertTrue(fixture.sent.isEmpty());
            }
        }
    }

    @Test
    void longRandomLiteralMessageKeepsReplayMetadataOutsideDisplayBindings() {
        for (int size : List.of(24577, 65520)) {
            String template = "x".repeat(size) + "%SiteName%";
            try (ReplayFixture fixture = new ReplayFixture()) {
                fixture.dispatchRandom(new ArrayList<>(List.of(template)), values("Original")).join();
                assertEquals(List.of("x".repeat(size) + "Original"), fixture.sent);
                var persisted = ArrayUtils.fromString(ArrayUtils.makeString(fixture.randomCheckpoint.get()));
                fixture.dispatchRandom(new ArrayList<>(List.of(template)), persisted).join();
                assertEquals(1, fixture.sent.size(), "completed random message must not be redelivered");
            }
        }
    }

    @Test
    void randomLiteralMessageKeepsItsChoiceAcrossFormattingFailureAndRestart() {
        var choices = spy(new ArrayList<>(List.of("First:%SiteName%", "Second:%SiteName%")));
        doReturn("First:%SiteName%", "Second:%SiteName%").when(choices).get(anyInt());
        HashMap<String, String> persisted;
        var firstSelection = new java.util.concurrent.atomic.AtomicReference<String>();
        try (ReplayFixture fixture = new ReplayFixture()) {
            fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                    .thenAnswer(call -> {
                        firstSelection.set(call.<String>getArgument(1).split(":", 2)[0]);
                        assertTrue(fixture.randomCheckpoint.get().keySet().stream()
                                .anyMatch(key -> key.startsWith("__advancedcore_replay_selection_")),
                                "the choice must be checkpointed before formatting starts");
                        when(fixture.player.isOnline()).thenReturn(false);
                        return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
                    });
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> fixture.dispatchRandom(choices, values("Original")).join());
            assertTrue(fixture.sent.isEmpty());
            persisted = ArrayUtils.fromString(ArrayUtils.makeString(fixture.randomCheckpoint.get()));
        }
        try (ReplayFixture restarted = new ReplayFixture()) {
            restarted.dispatchRandom(choices, persisted).join();
            assertEquals(List.of(firstSelection.get() + ":Original"), restarted.sent);
        }
    }

    @Test
    void completedRandomLiteralMessageDoesNotRerollOrRedeliverOnReplay() {
        var choices = spy(new ArrayList<>(List.of("First:%SiteName%", "Second:%SiteName%")));
        doReturn("First:%SiteName%", "Second:%SiteName%").when(choices).get(anyInt());
        try (ReplayFixture fixture = new ReplayFixture()) {
            fixture.dispatchRandom(choices, values("Original")).join();
            var firstDelivery = List.copyOf(fixture.sent);
            assertEquals(1, firstDelivery.size());
            var persisted = ArrayUtils.fromString(ArrayUtils.makeString(fixture.randomCheckpoint.get()));
            fixture.dispatchRandom(choices, persisted).join();
            assertEquals(firstDelivery, fixture.sent);
            fixture.dispatchRandom(choices, values("Independent")).join();
            assertEquals(2, fixture.sent.size());
            assertEquals(firstDelivery.get(0), fixture.sent.get(0));
            assertTrue(fixture.sent.get(1).endsWith(":Independent"));
        }
    }

    @Test
    void randomLiteralReplayReusesReorderedChoiceButRejectsRemovedConfiguredText() {
        var choices = spy(new ArrayList<>(List.of("First:%SiteName%")));
        doReturn("First:%SiteName%").when(choices).get(anyInt());
        HashMap<String, String> persisted;
        try (ReplayFixture fixture = new ReplayFixture()) {
            fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                    .thenAnswer(call -> {
                        when(fixture.player.isOnline()).thenReturn(false);
                        return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
                    });
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> fixture.dispatchRandom(choices, values("Original")).join());
            persisted = ArrayUtils.fromString(ArrayUtils.makeString(fixture.randomCheckpoint.get()));
        }
        try (ReplayFixture fixture = new ReplayFixture()) {
            assertThrows(IllegalStateException.class, () -> fixture.dispatchRandom(
                    new ArrayList<>(List.of("Second:%SiteName%")), new HashMap<>(persisted)));
            assertTrue(fixture.sent.isEmpty());
            fixture.dispatchRandom(new ArrayList<>(List.of("Second:%SiteName%", "First:%SiteName%")), persisted).join();
            assertEquals(List.of("First:Original"), fixture.sent);
        }
    }

    @Test
    void olderRandomMessageReservationsRecoverTheirChoiceWithoutRerolling() {
        for (boolean completed : List.of(false, true)) {
            HashMap<String, String> persisted;
            try (ReplayFixture fixture = new ReplayFixture()) {
                if (!completed) fixture.placeholders.when(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()))
                        .thenAnswer(call -> {
                            when(fixture.player.isOnline()).thenReturn(false);
                            return com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1));
                        });
                var map = values("Original");
                var options = new com.bencodez.advancedcore.api.rewards.RewardOptions();
                options.setAsyncReplayCheckpointConsumer(checkpoint -> fixture.randomCheckpoint.set(
                        new HashMap<>(checkpoint.getPlaceholders())));
                var collection = fixture.user.beginAsyncActionCollection(Reward.replayStateFor(options), map,
                        "synthetic-random-message-injector");
                fixture.user.sendLiteralDisplayMessage("First:%SiteName%", map);
                var delivery = fixture.user.endAsyncActionCollection(collection).toCompletableFuture();
                if (completed) delivery.join();
                else assertThrows(java.util.concurrent.CompletionException.class, delivery::join);
                persisted = ArrayUtils.fromString(ArrayUtils.makeString(fixture.randomCheckpoint.get()));
                assertFalse(persisted.keySet().stream().anyMatch(key -> key.startsWith("__advancedcore_replay_selection_")));
            }
            try (ReplayFixture restarted = new ReplayFixture()) {
                restarted.dispatchRandom(new ArrayList<>(List.of("Second:%SiteName%", "First:%SiteName%")), persisted).join();
                assertEquals(completed ? List.of() : List.of("First:Original"), restarted.sent);
            }
        }
    }

    @Test
    void storedRandomSelectionCannotBecomeAnUnconfiguredTrustedTemplate() {
        try (ReplayFixture fixture = new ReplayFixture()) {
            var map = values("Original");
            Reward.replaySelection(map, "literal-random-message", () -> "[Javascript=unexpected()]");
            assertThrows(IllegalStateException.class, () -> fixture.dispatchRandom(
                    new ArrayList<>(List.of("First:%SiteName%")), map));
            assertTrue(fixture.sent.isEmpty());
            fixture.placeholders.verify(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()), never());
        }
    }

    @Test
    void literalRandomChoiceBoundsFailBeforeFormatting() {
        List<ArrayList<String>> invalid = List.of(
                new ArrayList<>(java.util.Collections.nCopies(4097, "")),
                new ArrayList<>(List.of("x".repeat(65537))),
                new ArrayList<>(java.util.Collections.nCopies(17, "x".repeat(65536))));
        try (ReplayFixture fixture = new ReplayFixture()) {
            for (var choices : invalid) assertThrows(IllegalArgumentException.class,
                    () -> fixture.dispatchRandom(choices, values("Original")));
            assertTrue(fixture.sent.isEmpty());
            fixture.placeholders.verify(() -> PlaceholderUtils.parseText(eq(fixture.player), anyString()), never());
        }
    }

    @Test
    void legacyRandomMessagesKeepTheirExistingSelectionAndDeliveryBehavior() {
        var choices = spy(new ArrayList<>(List.of("First", "Second")));
        doReturn("First", "Second").when(choices).get(anyInt());
        var user = mock(AdvancedCoreUser.class);
        var handler = mock(RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        RewardMessages.register(handler, mock(AdvancedCorePlugin.class));
        var inject = injections.stream().filter(i -> i.getPath().equals("RandomMessage"))
                .map(com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList.class::cast)
                .findFirst().orElseThrow();
        var map = new HashMap<String, String>();
        inject.onRewardRequest(mock(Reward.class), user, choices, map);
        inject.onRewardRequest(mock(Reward.class), user, choices, map);
        verify(user).sendMessage("First", map);
        verify(user).sendMessage("Second", map);
        verify(user, never()).sendLiteralDisplayMessage(anyString(), any());
        assertTrue(map.isEmpty());
    }

    private static final class ReplayFixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final Player player = mock(Player.class);
        final AdvancedCoreUser user;
        final ArrayList<String> sent = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicReference<HashMap<String, String>> randomCheckpoint =
                new java.util.concurrent.atomic.AtomicReference<>(new HashMap<>());
        final org.mockito.MockedStatic<PlayerUtils> utility = mockStatic(PlayerUtils.class);
        final org.mockito.MockedStatic<PlaceholderUtils> placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS);

        ReplayFixture() {
            var options = mock(com.bencodez.advancedcore.AdvancedCoreConfigOptions.class);
            when(options.isOnlineMode()).thenReturn(true);
            when(plugin.getOptions()).thenReturn(options);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(timer).execute(any());
            when(player.isOnline()).thenReturn(true);
            user = spy(new AdvancedCoreUser(plugin, UUID.randomUUID(), "SyntheticPlayer"));
            doReturn(player).when(user).getPlayer();
            IServerHandle transport = mock(IServerHandle.class);
            utility.when(PlayerUtils::getServerHandle).thenReturn(transport);
            doAnswer(call -> { sent.add(call.<BaseComponent>getArgument(1).toPlainText()); return null; })
                    .when(transport).sendMessage(eq(player), any(BaseComponent.class));
            placeholders.when(() -> PlaceholderUtils.parseText(eq(player), anyString())).thenAnswer(call ->
                    com.bencodez.simpleapi.messages.MessageAPI.colorize(call.getArgument(1)));
        }

        java.util.concurrent.CompletableFuture<Void> dispatch(String template, HashMap<String, String> map) {
            var options = new com.bencodez.advancedcore.api.rewards.RewardOptions();
            options.setAsyncReplayCheckpointConsumer(checkpoint -> { });
            var state = Reward.replayStateFor(options);
            var collection = user.beginAsyncActionCollection(state, map, "synthetic-message-injector");
            user.sendLiteralDisplayMessage(template, map);
            return user.endAsyncActionCollection(collection).toCompletableFuture();
        }

        java.util.concurrent.CompletableFuture<Void> dispatchRandom(ArrayList<String> choices,
                HashMap<String, String> map) {
            var handler = mock(RewardHandler.class);
            var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
            when(handler.getInjectedRewards()).thenReturn(injections);
            RewardMessages.register(handler, plugin);
            var inject = injections.stream().filter(i -> i.getPath().equals("RandomMessage"))
                    .map(com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList.class::cast)
                    .findFirst().orElseThrow();
            var options = new com.bencodez.advancedcore.api.rewards.RewardOptions();
            options.setAsyncReplayCheckpointConsumer(checkpoint ->
                    randomCheckpoint.set(new HashMap<>(checkpoint.getPlaceholders())));
            var collection = user.beginAsyncActionCollection(Reward.replayStateFor(options), map,
                    "synthetic-random-message-injector");
            try {
                inject.onRewardRequest(mock(Reward.class), user, choices, map);
            } catch (RuntimeException failure) {
                user.endAsyncActionCollection(collection);
                throw failure;
            }
            return user.endAsyncActionCollection(collection).toCompletableFuture();
        }

        @Override public void close() {
            placeholders.close();
            utility.close();
        }
    }
}
