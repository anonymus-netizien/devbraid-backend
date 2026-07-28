package com.devbraid.analysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Shared utility for parsing simple JSON arrays of objects.
 * Avoids Jackson dependency for lightweight in-component parsing.
 */
@Slf4j
@Component
public class JsonArrayParser {

    /**
     * Parse a JSON array of objects into a list of domain objects.
     *
     * @param json   the JSON array string
     * @param mapper function that maps a JSON object string to a domain object
     * @return list of parsed objects
     */
    public <T> List<T> parseArray(String json, Function<String, T> mapper) {
        if (json == null || json.isBlank()) return List.of();
        List<T> results = new ArrayList<>();
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) return List.of();
            String content = trimmed.substring(1, trimmed.length() - 1);

            int depth = 0;
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                if (content.charAt(i) == '}') depth--;
                if (depth == 0 && content.charAt(i) == '}') {
                    String obj = content.substring(start, i + 1);
                    T mapped = mapper.apply(obj);
                    if (mapped != null) results.add(mapped);
                    start = i + 2;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", e.getMessage());
        }
        return results;
    }

    public String extractStringValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    public int extractIntValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }
}
