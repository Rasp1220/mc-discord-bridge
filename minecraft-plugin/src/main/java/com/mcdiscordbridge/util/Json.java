package com.mcdiscordbridge.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON support for the bridge protocol, which only ever exchanges
 * flat objects of string fields. Avoids pulling in an extra JSON dependency.
 */
public final class Json {

    private Json() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    public static String object(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("keyValuePairs must be an even number of key,value entries");
        }
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(escape(keyValuePairs[i])).append("\":\"")
                    .append(escape(keyValuePairs[i + 1])).append('"');
        }
        return out.append('}').toString();
    }

    /**
     * Parses a flat JSON object ({@code {"key":"value", ...}}) into a map of
     * string fields. Not a general-purpose JSON parser: nested objects/arrays
     * and non-string values are not supported, which is fine since the
     * bridge protocol never sends them.
     */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
            return result;
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        int i = 0;
        int len = body.length();
        while (i < len) {
            while (i < len && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ',')) {
                i++;
            }
            if (i >= len || body.charAt(i) != '"') {
                break;
            }
            int[] keyEnd = new int[1];
            String key = parseString(body, i, keyEnd);
            i = keyEnd[0];
            while (i < len && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ':')) {
                i++;
            }
            if (i >= len) {
                break;
            }
            String value;
            if (body.charAt(i) == '"') {
                int[] valueEnd = new int[1];
                value = parseString(body, i, valueEnd);
                i = valueEnd[0];
            } else {
                int start = i;
                while (i < len && body.charAt(i) != ',' && body.charAt(i) != '}') {
                    i++;
                }
                value = body.substring(start, i).trim();
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * Parses a JSON string literal starting at {@code start} (which must
     * point at the opening quote). Writes the index just past the closing
     * quote into {@code endOut[0]}.
     */
    private static String parseString(String s, int start, int[] endOut) {
        StringBuilder out = new StringBuilder();
        int i = start + 1;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '"') {
                i++;
                break;
            }
            if (c == '\\' && i + 1 < len) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 5 < len) {
                            out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                            i += 4;
                        }
                    }
                    default -> out.append(next);
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        endOut[0] = i;
        return out.toString();
    }
}
