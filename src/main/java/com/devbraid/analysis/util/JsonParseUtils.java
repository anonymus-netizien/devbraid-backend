package com.devbraid.analysis.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Shared JSON parsing utilities for analysis components.
 * Uses Jackson ObjectMapper — replaces the hand-rolled JsonArrayParser.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonParseUtils {

    private final ObjectMapper objectMapper;

    /**
     * Parse a JSON array string into a list of maps.
     * Returns empty list on null, blank, or malformed input.
     */
    public List<Map<String, Object>> parseArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Safely extract a string value from a map.
     */
    public String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Safely extract an integer value from a map.
     */
    public int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}
