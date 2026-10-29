package com.apisentinel.service;

import com.apisentinel.model.HealthCheckResult;
import com.apisentinel.model.ServiceEndpoint;
import com.apisentinel.storage.StorageManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * High-performance, multi-threaded probe engine for periodic endpoint health monitoring.
 */
public class HealthCheckEngine {
    private final List<ServiceEndpoint> endpoints = new CopyOnWriteArrayList<>();
    private final IncidentService incidentService;
    private final StorageManager storageManager;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public HealthCheckEngine(List<ServiceEndpoint> initialEndpoints, IncidentService incidentService, StorageManager storageManager) {
        this.incidentService = incidentService;
        this.storageManager = storageManager;
        this.endpoints.addAll(initialEndpoints);
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "sentinel-worker");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public synchronized void start() {
        for (ServiceEndpoint endpoint : endpoints) {
            scheduleEndpoint(endpoint);
        }

        // Periodic persistence every 30 seconds
        scheduler.scheduleAtFixedRate(this::persistState, 30, 30, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        scheduler.shutdownNow();
        persistState();
    }

    public void persistState() {
        if (storageManager != null) {
            storageManager.save(endpoints, incidentService.getAllIncidents());
        }
    }

    public synchronized void addEndpoint(ServiceEndpoint endpoint) {
        endpoints.add(endpoint);
        scheduleEndpoint(endpoint);
        persistState();
    }

    public synchronized boolean removeEndpoint(String id) {
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future != null) {
            future.cancel(true);
        }
        boolean removed = endpoints.removeIf(e -> e.getId().equals(id));
        if (removed) persistState();
        return removed;
    }

    public Optional<ServiceEndpoint> getEndpointById(String id) {
        return endpoints.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public List<ServiceEndpoint> getEndpoints() {
        return new ArrayList<>(endpoints);
    }

    private void scheduleEndpoint(ServiceEndpoint endpoint) {
        if (!endpoint.isEnabled()) return;

        // Run an initial check after a brief stagger
        int initialDelay = Math.min(endpoint.getIntervalSeconds(), 2 + (endpoints.indexOf(endpoint) % 5));
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> checkEndpoint(endpoint),
                initialDelay,
                endpoint.getIntervalSeconds(),
                TimeUnit.SECONDS
        );
        scheduledTasks.put(endpoint.getId(), future);
    }

    public CompletableFuture<HealthCheckResult> checkEndpointAsync(ServiceEndpoint endpoint) {
        return CompletableFuture.supplyAsync(() -> checkEndpoint(endpoint));
    }

    public HealthCheckResult checkEndpoint(ServiceEndpoint endpoint) {
        String oldStatus = endpoint.getCurrentStatus();
        long start = System.currentTimeMillis();
        int statusCode = 0;
        boolean success = false;
        String message = "";

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .timeout(Duration.ofMillis(endpoint.getTimeoutMillis()))
                    .header("User-Agent", "APISentinel/1.0 (+https://github.com/apisentinel)");

            if ("HEAD".equalsIgnoreCase(endpoint.getMethod())) {
                reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else if ("POST".equalsIgnoreCase(endpoint.getMethod())) {
                reqBuilder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                reqBuilder.GET();
            }

            HttpResponse<Void> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            statusCode = resp.statusCode();
            long latency = System.currentTimeMillis() - start;

            if (statusCode == endpoint.getExpectedStatusCode() || (endpoint.getExpectedStatusCode() == 200 && statusCode >= 200 && statusCode < 300)) {
                success = true;
                message = "HTTP " + statusCode + " OK (" + latency + "ms)";
            } else {
                success = false;
                message = "Unexpected HTTP status: " + statusCode + " (expected " + endpoint.getExpectedStatusCode() + ")";
            }

            HealthCheckResult result = new HealthCheckResult(endpoint.getId(), Instant.now(), statusCode, latency, success, message);
            endpoint.recordCheckResult(result);
            incidentService.recordStatusTransition(endpoint, oldStatus, endpoint.getCurrentStatus(), message);
            return result;

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            HealthCheckResult result = new HealthCheckResult(endpoint.getId(), Instant.now(), 0, latency, false, "Connection error: " + err);
            endpoint.recordCheckResult(result);
            incidentService.recordStatusTransition(endpoint, oldStatus, "DOWN", result.message());
            return result;
        }
    }
}
