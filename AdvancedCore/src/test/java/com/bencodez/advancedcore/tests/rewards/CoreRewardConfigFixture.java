package com.bencodez.advancedcore.tests.rewards;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.bencodez.advancedcore.core.rewards.RewardConfigReader;
import com.bencodez.simpleapi.core.config.StructuredConfigView;
import com.bencodez.simpleapi.core.config.StructuredConfigView.Kind;

/** JDK-only behavioral fixture; no Bukkit, Configurate, Mockito or JUnit linkage. */
public final class CoreRewardConfigFixture {
    private CoreRewardConfigFixture() { }

    public static void main(String[] args) {
        run();
        System.out.println("CoreRewardConfigFixture: 10 scenarios passed");
    }

    public static void run() {
        Map<String, Object> values = new LinkedHashMap<>();
        AtomicReference<StructuredConfigView> source = new AtomicReference<>(view(values));
        RewardConfigReader reader = new RewardConfigReader(source::get);

        equal(0.0, reader.getChance());
        equal("BOTH", reader.getRewardType());
        equal("", reader.getServer());
        equal("AdvancedCore.Reward.Daily", reader.getPermission("Daily"));
        equal(List.of(), reader.getCommandsConsole());
        equal(false, reader.getDelayedEnabled());
        equal(0, reader.getTimedHour());

        List<Object> commands = new ArrayList<>(List.of("first", 7, true, "second"));
        values.put("Commands", commands);
        List<?> snapshot = reader.getCommandsConsole();
        equal(commands, snapshot);
        commands.add("later");
        equal(4, snapshot.size());
        try {
            snapshot.clear();
            throw new AssertionError("A plain-data list must be unmodifiable");
        } catch (UnsupportedOperationException expected) { }

        values.put("Commands", Map.of("Console", List.of("console"), "Player", List.of("player")));
        equal(List.of("console"), reader.getCommandsConsole());
        equal(List.of("player"), reader.getCommandsPlayer());
        values.put("Commands", "not a command list");
        equal(List.of(), reader.getCommandsConsole());
        equal(List.of(), reader.getCommandsPlayer());

        values.put("Chance", 17.5);
        values.put("Delayed", Map.of("Enabled", true, "Hours", 2, "Minutes", 3,
                "Seconds", 4, "MilliSeconds", 5));
        values.put("Timed", Map.of("Enabled", true, "Hour", 6, "Minute", 7));
        equal(17.5, reader.getChance());
        equal(true, reader.getDelayedEnabled());
        equal(2, reader.getDelayedHours());
        equal(3, reader.getDelayedMinutes());
        equal(4, reader.getDelayedSeconds());
        equal(5, reader.getDelayedMilliSeconds());
        equal(true, reader.getTimedEnabled());
        equal(6, reader.getTimedHour());
        equal(7, reader.getTimedMinute());

        for (String type : List.of("ONLINE", "online", "OnLiNe", "OFFLINE", "offline", "other", " online ")) {
            values.put("RewardType", type);
            String expected = type.equalsIgnoreCase("online") ? "ONLINE"
                    : type.equalsIgnoreCase("offline") ? "OFFLINE" : "BOTH";
            equal(expected, reader.getRewardType());
        }

        values.put("EnableChoices", true);
        values.put("ForceOffline", true);
        values.put("RequirePermission", true);
        values.put("DirectlyDefinedReward", true);
        values.put("Permission", "custom.reward");
        values.put("Priority", List.of("first", "second"));
        values.put("Worlds", List.of("world"));
        equal(true, reader.getEnableChoices());
        equal(true, reader.getForceOffline());
        equal(true, reader.getRequirePermission());
        equal(true, reader.isDirectlyDefinedReward());
        equal("custom.reward", reader.getPermission("Other"));
        equal(List.of("first", "second"), reader.getPriority());
        equal(List.of("world"), reader.getWorlds());

        Map<String, Object> reward = Map.of("Commands", List.of("nested"));
        values.put("Choices", Map.of("one", Map.of("Rewards", reward), "with.dot", Map.of("Rewards", reward)));
        equal(Set.of("one", "with.dot"), reader.getChoices());
        equal("Choices.one.Rewards", reader.getChoicesRewardsPath("one"));
        equal(List.of("nested"), new RewardConfigReader(() -> reader.getChoiceRewards("one")).getCommandsConsole());
        StructuredConfigView literal = reader.definitionAt("Choices", "with.dot", "Rewards");
        equal(List.of("nested"), new RewardConfigReader(() -> literal).getCommandsConsole());

        values.put("DisplayItem", Map.of("Material", "STONE"));
        values.put("Items", Map.of("gift", Map.of("Amount", 3)));
        equal("STONE", reader.getDisplayItem().getString("Material", null));
        equal(3, reader.getItemSection("gift").getInt("Amount", 0));
        equal(null, reader.getItemSection("missing"));
        equal(null, reader.getChoicesItem("one"));

        source.set(view(new LinkedHashMap<>(Map.of("Chance", 91.0, "RewardType", "offline"))));
        equal(91.0, reader.getChance());
        equal("OFFLINE", reader.getRewardType());
        equal(List.of(), reader.getPriority());

        source.set(view(new LinkedHashMap<>(Map.of("Chance", "bad", "Delayed", Map.of("Enabled", "true")))));
        equal(0.0, reader.getChance());
        equal(false, reader.getDelayedEnabled());
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static Object resolve(Map<?, ?> values, String... keys) {
        Object current = values;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current;
    }

    private static Object detached(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object entry : list) copy.add(detached(entry));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) copy.put((String) entry.getKey(), detached(entry.getValue()));
            return Collections.unmodifiableMap(copy);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static StructuredConfigView view(Map<String, Object> values) {
        return (StructuredConfigView) Proxy.newProxyInstance(StructuredConfigView.class.getClassLoader(),
                new Class<?>[] { StructuredConfigView.class }, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getKeys")) return new LinkedHashSet<>(values.keySet());
                    if (name.equals("toString")) return "FixtureView";
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    String[] keys = args[0] instanceof String[] array ? array
                            : ((String) args[0]).isEmpty() ? new String[0] : ((String) args[0]).split("\\.");
                    Object value = resolve(values, keys);
                    return switch (name) {
                    case "contains" -> value != null;
                    case "getString" -> value == null ? args[1] : String.valueOf(value);
                    case "getBoolean" -> value instanceof Boolean ? value : args[1];
                    case "getInt" -> value instanceof Number number ? number.intValue() : args[1];
                    case "getLong" -> value instanceof Number number ? number.longValue() : args[1];
                    case "getDouble" -> value instanceof Number number ? number.doubleValue() : args[1];
                    case "kind", "kindAt" -> value == null ? Kind.MISSING : value instanceof Map ? Kind.SECTION
                            : value instanceof List ? Kind.LIST : value instanceof Number ? Kind.NUMBER
                            : value instanceof Boolean ? Kind.BOOLEAN : value instanceof String ? Kind.STRING : Kind.OTHER;
                    case "value", "valueAt" -> detached(value);
                    case "isConfigurationSection" -> value instanceof Map;
                    case "getConfigurationSection", "at", "structuredAt" -> value instanceof Map
                            ? view((Map<String, Object>) value) : null;
                    default -> throw new AssertionError("Unexpected getter: " + name);
                    };
                });
    }
}
