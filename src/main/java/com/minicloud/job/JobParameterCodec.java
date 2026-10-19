package com.minicloud.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobParameterCodec {

    private JobParameterCodec() {
    }

    public static String fileParameters(long fileId, String originalFilename) {
        return "{\"fileId\":"
                + fileId
                + ",\"originalFilename\":"
                + quote(originalFilename)
                + "}";
    }

    public static String batchParameters(List<Double> values) {
        StringBuilder json = new StringBuilder("{\"values\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(values.get(i));
        }
        json.append("]}");
        return json.toString();
    }

    public static String object(java.util.Map<String, Object> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':');
            json.append(value(entry.getValue()));
        }
        json.append('}');
        return json.toString();
    }

    public static long readFileId(String parameters) {
        String source = parameters == null ? "" : parameters;
        int index = source.toLowerCase(Locale.ROOT).indexOf("\"fileid\"");
        if (index < 0) {
            throw new IllegalArgumentException("fileId is required.");
        }

        int colon = source.indexOf(':', index);
        if (colon < 0) {
            throw new IllegalArgumentException("fileId is required.");
        }

        StringBuilder digits = new StringBuilder();
        for (int i = colon + 1; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (digits.length() > 0) {
                break;
            }
        }

        if (digits.isEmpty()) {
            throw new IllegalArgumentException("fileId is required.");
        }

        return Long.parseLong(digits.toString());
    }

    public static List<Double> readValues(String parameters) {
        String source = parameters == null ? "" : parameters;
        int key = source.toLowerCase(Locale.ROOT).indexOf("\"values\"");
        if (key < 0) {
            throw new IllegalArgumentException("BATCH_SUM requires a values array.");
        }

        int start = source.indexOf('[', key);
        int end = source.indexOf(']', start);
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("BATCH_SUM requires a values array.");
        }

        String body = source.substring(start + 1, end).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("BATCH_SUM requires a values array.");
        }

        String[] parts = body.split(",");
        List<Double> values = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            double number = Double.parseDouble(token);
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException(
                        "BATCH_SUM values must be finite numbers.");
            }
            values.add(number);
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException("BATCH_SUM requires a values array.");
        }

        return values;
    }

    private static String value(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return String.valueOf(raw);
        }
        return quote(String.valueOf(raw));
    }

    private static String quote(String raw) {
        String value = raw == null ? "" : raw;
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> json.append("\\\\");
                case '"' -> json.append("\\\"");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> json.append(ch);
            }
        }
        json.append('"');
        return json.toString();
    }
}
