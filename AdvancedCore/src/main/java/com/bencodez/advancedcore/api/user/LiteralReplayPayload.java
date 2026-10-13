package com.bencodez.advancedcore.api.user;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/** Terminal, internal replay payloads. These are never templates or placeholder input. */
final class LiteralReplayPayload {
    static final int MAX_CHARACTERS = 1048576;
    private LiteralReplayPayload() { }

    record Entry(String kind, UUID owner, int ordinal, TextComponent[] components, String console) { }

    static String player(String owner, int ordinal, TextComponent component) {
        return "P2|" + UUID.fromString(owner) + "|" + ordinal + "|" + componentJson(component);
    }

    static String recipient(UUID owner, int ordinal, TextComponent[] components) {
        if (components.length > 4096) throw new IllegalArgumentException("Too many literal fragments");
        StringBuilder json = new StringBuilder("[");
        for (TextComponent component : components) {
            if (json.length() > 1) json.append(',');
            json.append(componentJson(component));
            if (json.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Literal payload exceeds limits");
        }
        return "R2|" + owner + "|" + ordinal + "|" + json.append(']');
    }

    static String console(String text) {
        return "C2|" + (text == null ? "null" : new JsonPrimitive(text).toString());
    }

    private static String componentJson(TextComponent component) {
        return component == null ? "null" : ComponentSerializer.toString(component);
    }

    static boolean isFrozen(String entry) {
        return entry != null && (entry.startsWith("P2|") || entry.startsWith("R2|") || entry.startsWith("C2|"));
    }

    static Entry decode(String entry) {
        if (entry == null || entry.length() > MAX_CHARACTERS)
            throw new IllegalArgumentException("Literal payload exceeds limits");
        if (entry.startsWith("C2|")) {
            JsonElement json = strictJson(entry.substring(3));
            if (!json.isJsonNull() && (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Malformed literal console payload");
            String console = json.isJsonNull() ? null : json.getAsString();
            if (console != null && console.length() > 262144)
                throw new IllegalArgumentException("Literal console payload exceeds limits");
            return new Entry("C2", null, 0, new TextComponent[0], console);
        }
        String[] parts = entry.split("\\|", 4);
        if (parts.length != 4 || (!"P2".equals(parts[0]) && !"R2".equals(parts[0])))
            throw new IllegalArgumentException("Malformed literal payload");
        UUID owner = UUID.fromString(parts[1]);
        if (!owner.toString().equals(parts[1])) throw new IllegalArgumentException("Noncanonical literal owner");
        int ordinal = Integer.parseInt(parts[2]);
        if (ordinal < 0 || ordinal >= 4096 || !Integer.toString(ordinal).equals(parts[2]))
            throw new IllegalArgumentException("Noncanonical literal ordinal");
        JsonElement json = strictJson(parts[3]);
        TextComponent[] components;
        if ("P2".equals(parts[0])) components = new TextComponent[] { decodeComponent(json) };
        else {
            if (!json.isJsonArray() || json.getAsJsonArray().size() > 4096)
                throw new IllegalArgumentException("Malformed literal fragments");
            components = new TextComponent[json.getAsJsonArray().size()];
            for (int index = 0; index < components.length; index++)
                components[index] = decodeComponent(json.getAsJsonArray().get(index));
        }
        LiteralMessageTemplate.validateRestoredComponents(components);
        return new Entry(parts[0], owner, ordinal, components, null);
    }

    private static TextComponent decodeComponent(JsonElement json) {
        if (json.isJsonNull()) return null;
        if (!json.isJsonObject()) throw new IllegalArgumentException("Malformed literal component");
        BaseComponent[] components = ComponentSerializer.parse(json.toString());
        if (components.length != 1 || !(components[0] instanceof TextComponent text))
            throw new IllegalArgumentException("Malformed literal component root");
        return text;
    }

    /** The existing snapshot uses URL Base64; budget its actual encoded UTF-8 size while collecting. */
    static long snapshotCharacters(String entry) {
        long bytes = entry.getBytes(StandardCharsets.UTF_8).length;
        return (bytes / 3) * 4 + (bytes % 3 == 0 ? 0 : bytes % 3 + 1) + 1;
    }

    private static JsonElement strictJson(String json) {
        if (json.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Literal JSON exceeds limits");
        validateJsonLexemes(json);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            // Gson is supplied by supported server runtimes. Do not depend on
            // the newer Strictness API; the lexical pass excludes its older
            // LEGACY_STRICT escape/control-character exceptions.
            reader.setLenient(false);
            JsonElement result = readJson(reader, 0, new int[1]);
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Trailing literal JSON");
            return result;
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Malformed literal JSON", failure);
        }
    }

    /** RFC 8259 tokens; JsonReader below owns the bounded structural grammar. */
    private static void validateJsonLexemes(String json) {
        for (int cursor = 0; cursor < json.length();) {
            char character = json.charAt(cursor);
            if (isJsonDelimiter(character)) { cursor++; continue; }
            if (character == '"') {
                cursor++;
                boolean closed = false;
                while (cursor < json.length()) {
                    char value = json.charAt(cursor++);
                    if (value == '"') { closed = true; break; }
                    if (value < 0x20) throw new IllegalArgumentException("Unescaped literal JSON control character");
                    if (value != '\\') continue;
                    if (cursor >= json.length()) throw new IllegalArgumentException("Incomplete literal JSON escape");
                    char escaped = json.charAt(cursor++);
                    if (escaped == 'u') {
                        for (int digit = 0; digit < 4; digit++) {
                            if (cursor >= json.length() || !isHexDigit(json.charAt(cursor++)))
                                throw new IllegalArgumentException("Malformed literal JSON Unicode escape");
                        }
                    } else if ("\"\\/bfnrt".indexOf(escaped) < 0)
                        throw new IllegalArgumentException("Non-JSON literal escape");
                }
                if (!closed) throw new IllegalArgumentException("Unterminated literal JSON string");
                continue;
            }
            int start = cursor;
            if (character == '-' || isDigit(character)) {
                if (character == '-') cursor++;
                if (cursor >= json.length() || !isDigit(json.charAt(cursor)))
                    throw new IllegalArgumentException("Malformed literal JSON number");
                if (json.charAt(cursor) == '0') cursor++;
                else while (cursor < json.length() && isDigit(json.charAt(cursor))) cursor++;
                if (cursor < json.length() && json.charAt(cursor) == '.') {
                    cursor++;
                    int decimal = cursor;
                    while (cursor < json.length() && isDigit(json.charAt(cursor))) cursor++;
                    if (cursor == decimal) throw new IllegalArgumentException("Malformed literal JSON fraction");
                }
                if (cursor < json.length() && (json.charAt(cursor) == 'e' || json.charAt(cursor) == 'E')) {
                    cursor++;
                    if (cursor < json.length() && (json.charAt(cursor) == '+' || json.charAt(cursor) == '-')) cursor++;
                    int exponent = cursor;
                    while (cursor < json.length() && isDigit(json.charAt(cursor))) cursor++;
                    if (cursor == exponent) throw new IllegalArgumentException("Malformed literal JSON exponent");
                }
            } else if (json.startsWith("true", cursor)) cursor += 4;
            else if (json.startsWith("false", cursor)) cursor += 5;
            else if (json.startsWith("null", cursor)) cursor += 4;
            else throw new IllegalArgumentException("Non-JSON literal token");
            if (cursor == start || (cursor < json.length() && !isJsonDelimiter(json.charAt(cursor))))
                throw new IllegalArgumentException("Malformed literal JSON token boundary");
        }
    }

    private static boolean isJsonDelimiter(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n'
                || value == '{' || value == '}' || value == '[' || value == ']' || value == ',' || value == ':';
    }

    private static boolean isDigit(char value) { return value >= '0' && value <= '9'; }
    private static boolean isHexDigit(char value) {
        return isDigit(value) || value >= 'a' && value <= 'f' || value >= 'A' && value <= 'F';
    }

    private static JsonElement readJson(JsonReader reader, int depth, int[] nodes) throws IOException {
        // JSON includes arrays/objects around component children. The terminal tree
        // independently retains its 64-level, 4096-node, 262144-character limits.
        if (depth > 192 || ++nodes[0] > 32768) throw new IllegalArgumentException("Literal JSON exceeds limits");
        switch (reader.peek()) {
        case BEGIN_OBJECT:
            reader.beginObject();
            JsonObject object = new JsonObject();
            HashSet<String> names = new HashSet<>();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate literal JSON field");
                object.add(name, readJson(reader, depth + 1, nodes));
            }
            reader.endObject();
            return object;
        case BEGIN_ARRAY:
            reader.beginArray();
            JsonArray array = new JsonArray();
            while (reader.hasNext()) array.add(readJson(reader, depth + 1, nodes));
            reader.endArray();
            return array;
        case STRING: return new JsonPrimitive(reader.nextString());
        case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
        case NULL: reader.nextNull(); return JsonNull.INSTANCE;
        case NUMBER:
            String number = reader.nextString();
            if (number.length() > 128) throw new IllegalArgumentException("Literal JSON number exceeds limits");
            var decimal = new java.math.BigDecimal(number);
            if (Math.abs((long) decimal.scale()) > 128)
                throw new IllegalArgumentException("Literal JSON exponent exceeds limits");
            return new JsonPrimitive(decimal);
        default: throw new IllegalArgumentException("Malformed literal JSON token");
        }
    }
}
