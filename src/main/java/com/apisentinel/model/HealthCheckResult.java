package com.apisentinel.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Record representing the outcome of a single health probe check.
 */
public record HealthCheckResult(
        String serviceId,
        Instant timestamp,
        int statusCode,
        long responseTimeMs,
        boolean success,
        String message
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceId", serviceId);
        map.put("timestamp", timestamp != null ? timestamp.toString() : Instant.now().toString());
        map.put("statusCode", statusCode);
        map.put("responseTimeMs", responseTimeMs);
        map.put("success", success);
        map.put("message", message != null ? message : "");
        return map;
    }
}
