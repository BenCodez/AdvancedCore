package com.bencodez.advancedcore.api.user.presence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Bounded structural inspection only: no server loader, data fixing or writes. */
final class PlayerHistoryFormat {
    static final int MAX_COMPRESSED = 1024 * 1024;
    private static final int MAX_EXPANDED = 8 * 1024 * 1024;
    private int nodes;

    static boolean valid(byte[] compressed) throws IOException {
        byte[] payload = expand(compressed);
        try (var input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != 10) return false;
            input.readUTF();
            new PlayerHistoryFormat().tag(input, 10, 0);
            return input.read() == -1;
        }
    }

    private static byte[] expand(byte[] bytes) throws IOException {
        if (bytes.length < 18 || bytes.length > MAX_COMPRESSED
                || (bytes[0] & 255) != 31 || (bytes[1] & 255) != 139 || bytes[2] != 8)
            throw new IOException("Invalid history compression");
        int flags = bytes[3] & 255;
        if ((flags & 224) != 0) throw new IOException("Invalid history flags");
        int offset = 10;
        if ((flags & 4) != 0) {
            if (offset + 2 > bytes.length - 8) throw new IOException("Truncated history header");
            int length = (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
            offset += 2 + length;
        }
        for (int flag : new int[] {8, 16}) if ((flags & flag) != 0) {
            boolean terminated = false;
            while (offset < bytes.length - 8) if (bytes[offset++] == 0) { terminated = true; break; }
            if (!terminated)
                throw new IOException("Truncated history header");
        }
        if ((flags & 2) != 0) {
            if (offset + 2 > bytes.length - 8) throw new IOException("Truncated history header");
            var headerCrc = new CRC32(); headerCrc.update(bytes, 0, offset);
            int expected = (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
            if ((headerCrc.getValue() & 65535) != expected) throw new IOException("Invalid history header checksum");
            offset += 2;
        }
        if (offset > bytes.length - 8) throw new IOException("Truncated history frame");
        var inflater = new Inflater(true);
        try {
            inflater.setInput(bytes, offset, bytes.length - offset);
            var output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && !inflater.finished()) throw new IOException("Incomplete history frame");
                if (output.size() > MAX_EXPANDED - count) throw new IOException("History expansion limit");
                output.write(buffer, 0, count);
            }
            int trailer = bytes.length - inflater.getRemaining();
            if (trailer != bytes.length - 8) throw new IOException("Adjacent history frame data");
            byte[] payload = output.toByteArray();
            var crc = new CRC32(); crc.update(payload);
            if (littleInt(bytes, trailer) != crc.getValue() || littleInt(bytes, trailer + 4) != payload.length)
                throw new IOException("Invalid history checksum");
            return payload;
        } catch (DataFormatException invalid) {
            throw new IOException("Invalid history deflate", invalid);
        } finally {
            inflater.end();
        }
    }

    private static long littleInt(byte[] data, int offset) {
        long result = 0;
        for (int index = 0; index < 4; index++) result |= (long) (data[offset + index] & 255) << (8 * index);
        return result;
    }

    private void tag(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 64 || ++nodes > 100000) throw new IOException("History structure limit");
        switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3, 5 -> input.readInt();
            case 4, 6 -> input.readLong();
            case 7 -> array(input, 1);
            case 8 -> input.readUTF();
            case 9 -> {
                int element = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || count > 100000 || element > 12 || (element == 0 && count != 0))
                    throw new IOException("Invalid history list");
                for (int index = 0; index < count; index++) tag(input, element, depth + 1);
            }
            case 10 -> {
                var names = new HashSet<String>();
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    String name = input.readUTF();
                    if (!names.add(name)) throw new IOException("Duplicate history name");
                    tag(input, child, depth + 1);
                }
            }
            case 11 -> array(input, 4);
            case 12 -> array(input, 8);
            default -> throw new IOException("Invalid history tag");
        }
    }

    private void array(DataInputStream input, int width) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_EXPANDED / width || (long) count * width > input.available())
            throw new IOException("Invalid history array");
        input.skipNBytes((long) count * width);
    }
}
