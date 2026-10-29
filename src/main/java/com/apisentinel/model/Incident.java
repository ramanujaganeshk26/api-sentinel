package com.apisentinel.model;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an outage or degraded performance incident for a monitored endpoint.
 */
public class Incident {
    private final String id;
    private final String serviceId;
    private final String serviceName;
    private final Instant startedAt;
    private Instant resolvedAt;
    private String status; // "OPEN" or "RESOLVED"
    private String reason;

    public Incident(String serviceId, String serviceName, String reason) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.startedAt = Instant.now();
        this.status = "OPEN";
        this.reason = reason;
    }

    public Incident(String id, String serviceId, String serviceName, Instant startedAt, Instant resolvedAt, String status, String reason) {
        this.id = id;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.startedAt = startedAt;
        this.resolvedAt = resolvedAt;
        this.status = status;
        this.reason = reason;
    }

    public void resolve() {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }

    public long getDurationSeconds() {
        Instant end = (resolvedAt != null) ? resolvedAt : Instant.now();
        return Duration.between(startedAt, end).getSeconds();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("serviceId", serviceId);
        map.put("serviceName", serviceName);
        map.put("startedAt", startedAt != null ? startedAt.toString() : null);
        map.put("resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);
        map.put("status", status);
        map.put("reason", reason != null ? reason : "");
        map.put("durationSeconds", getDurationSeconds());
        return map;
    }

    public static Incident fromMap(Map<String, Object> map) {
        String id = (String) map.get("id");
        String serviceId = (String) map.get("serviceId");
        String serviceName = (String) map.get("serviceName");
        Instant started = map.get("startedAt") != null ? Instant.parse(map.get("startedAt").toString()) : Instant.now();
        Instant resolved = map.get("resolvedAt") != null ? Instant.parse(map.get("resolvedAt").toString()) : null;
        String status = (String) map.getOrDefault("status", "OPEN");
        String reason = (String) map.getOrDefault("reason", "");
        return new Incident(id, serviceId, serviceName, started, resolved, status, reason);
    }
}
