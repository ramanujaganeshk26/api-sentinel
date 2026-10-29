package com.apisentinel.model;

import java.time.Instant;
import java.util.*;

/**
 * Monitored Service Endpoint representation with performance metrics and health state.
 */
public class ServiceEndpoint {
    private final String id;
    private String name;
    private String url;
    private String method; // GET, POST, HEAD
    private int intervalSeconds;
    private int timeoutMillis;
    private int expectedStatusCode;
    private String tag; // "Production", "API", "Database", "Internal"
    private boolean enabled;
    private final Instant createdAt;

    // Runtime state
    private volatile String currentStatus = "PENDING"; // "OPERATIONAL", "DEGRADED", "DOWN", "PENDING"
    private volatile Instant lastCheckedAt;
    private volatile long lastResponseTimeMs;
    private volatile int lastStatusCode;
    private volatile String lastErrorMessage = "";
    private volatile long totalChecks = 0;
    private volatile long successfulChecks = 0;
    private final List<Long> recentLatencies = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_LATENCY_HISTORY = 30;

    public ServiceEndpoint(String name, String url, String method, int intervalSeconds, int timeoutMillis, int expectedStatusCode, String tag) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.url = url;
        this.method = (method != null && !method.isBlank()) ? method.toUpperCase() : "GET";
        this.intervalSeconds = intervalSeconds > 0 ? intervalSeconds : 15;
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 5000;
        this.expectedStatusCode = expectedStatusCode > 0 ? expectedStatusCode : 200;
        this.tag = (tag != null && !tag.isBlank()) ? tag : "General";
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public ServiceEndpoint(String id, String name, String url, String method, int intervalSeconds, int timeoutMillis, int expectedStatusCode, String tag, boolean enabled, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.method = method;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.expectedStatusCode = expectedStatusCode;
        this.tag = tag;
        this.enabled = enabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public synchronized void recordCheckResult(HealthCheckResult result) {
        this.totalChecks++;
        this.lastCheckedAt = result.timestamp();
        this.lastResponseTimeMs = result.responseTimeMs();
        this.lastStatusCode = result.statusCode();

        if (result.success()) {
            this.successfulChecks++;
            this.lastErrorMessage = "";
            // If latency is very high (> 1200ms), consider degraded
            if (result.responseTimeMs() > 1200) {
                this.currentStatus = "DEGRADED";
            } else {
                this.currentStatus = "OPERATIONAL";
            }
        } else {
            this.currentStatus = "DOWN";
            this.lastErrorMessage = result.message();
        }

        recentLatencies.add(result.responseTimeMs());
        while (recentLatencies.size() > MAX_LATENCY_HISTORY) {
            recentLatencies.remove(0);
        }
    }

    public double getUptimePercentage() {
        if (totalChecks == 0) return 100.0;
        double pct = ((double) successfulChecks / totalChecks) * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }

    public double getAverageLatencyMs() {
        synchronized (recentLatencies) {
            if (recentLatencies.isEmpty()) return 0.0;
            long sum = 0;
            for (Long l : recentLatencies) sum += l;
            return Math.round(((double) sum / recentLatencies.size()) * 10.0) / 10.0;
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public int getExpectedStatusCode() { return expectedStatusCode; }
    public void setExpectedStatusCode(int expectedStatusCode) { this.expectedStatusCode = expectedStatusCode; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public String getCurrentStatus() { return currentStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public long getLastResponseTimeMs() { return lastResponseTimeMs; }
    public int getLastStatusCode() { return lastStatusCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public long getTotalChecks() { return totalChecks; }
    public long getSuccessfulChecks() { return successfulChecks; }

    public List<Long> getRecentLatencies() {
        synchronized (recentLatencies) {
            return new ArrayList<>(recentLatencies);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("url", url);
        map.put("method", method);
        map.put("intervalSeconds", intervalSeconds);
        map.put("timeoutMillis", timeoutMillis);
        map.put("expectedStatusCode", expectedStatusCode);
        map.put("tag", tag);
        map.put("enabled", enabled);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("currentStatus", currentStatus);
        map.put("lastCheckedAt", lastCheckedAt != null ? lastCheckedAt.toString() : null);
        map.put("lastResponseTimeMs", lastResponseTimeMs);
        map.put("lastStatusCode", lastStatusCode);
        map.put("lastErrorMessage", lastErrorMessage);
        map.put("totalChecks", totalChecks);
        map.put("successfulChecks", successfulChecks);
        map.put("uptimePercentage", getUptimePercentage());
        map.put("averageLatencyMs", getAverageLatencyMs());
        map.put("recentLatencies", getRecentLatencies());
        return map;
    }

    public static ServiceEndpoint fromMap(Map<String, Object> map) {
        String id = (String) map.get("id");
        String name = (String) map.get("name");
        String url = (String) map.get("url");
        String method = (String) map.getOrDefault("method", "GET");
        int interval = ((Number) map.getOrDefault("intervalSeconds", 15)).intValue();
        int timeout = ((Number) map.getOrDefault("timeoutMillis", 5000)).intValue();
        int expectedCode = ((Number) map.getOrDefault("expectedStatusCode", 200)).intValue();
        String tag = (String) map.getOrDefault("tag", "General");
        boolean enabled = Boolean.TRUE.equals(map.getOrDefault("enabled", true));
        Instant createdAt = map.get("createdAt") != null ? Instant.parse(map.get("createdAt").toString()) : Instant.now();

        ServiceEndpoint ep = new ServiceEndpoint(id, name, url, method, interval, timeout, expectedCode, tag, enabled, createdAt);
        if (map.containsKey("currentStatus")) {
            ep.currentStatus = (String) map.get("currentStatus");
        }
        if (map.containsKey("totalChecks")) {
            ep.totalChecks = ((Number) map.get("totalChecks")).longValue();
        }
        if (map.containsKey("successfulChecks")) {
            ep.successfulChecks = ((Number) map.get("successfulChecks")).longValue();
        }
        return ep;
    }
}
