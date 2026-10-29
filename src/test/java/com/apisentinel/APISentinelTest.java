package com.apisentinel;

import com.apisentinel.model.HealthCheckResult;
import com.apisentinel.model.Incident;
import com.apisentinel.model.ServiceEndpoint;
import com.apisentinel.service.IncidentService;
import com.apisentinel.storage.StorageManager;
import com.apisentinel.util.JsonUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Self-contained unit & integration test runner for APISentinel.
 */
public class APISentinelTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("Running APISentinel Test Suite...\n");

        testJsonSerialization();
        testJsonDeserialization();
        testServiceEndpointMetrics();
        testIncidentLifecycle();
        testStorageManagerPersistence();

        System.out.printf("%n=========================================%n");
        System.out.printf("Test Results: %d Passed, %d Failed%n", testsPassed, testsFailed);
        System.out.printf("=========================================%n");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + message);
            testsFailed++;
        }
    }

    private static void assertEquals(String message, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            System.out.println("  [PASS] " + message);
            testsPassed++;
        } else {
            System.err.printf("  [FAIL] %s - Expected: %s, Actual: %s%n", message, expected, actual);
            testsFailed++;
        }
    }

    private static void testJsonSerialization() {
        System.out.println("-> Testing JSON Serialization");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Sentinel");
        map.put("port", 8080);
        map.put("active", true);
        map.put("tags", List.of("api", "monitor"));

        String json = JsonUtil.toJson(map);
        assertTrue("Contains name", json.contains("\"name\":\"Sentinel\""));
        assertTrue("Contains port", json.contains("\"port\":8080"));
        assertTrue("Contains active boolean", json.contains("\"active\":true"));
        assertTrue("Contains tags array", json.contains("[\"api\",\"monitor\"]"));
    }

    private static void testJsonDeserialization() {
        System.out.println("-> Testing JSON Deserialization");
        String json = "{\"service\":\"Billing API\",\"interval\":30,\"enabled\":true,\"metrics\":[100,200]}";
        Map<String, Object> map = JsonUtil.parseObject(json);

        assertEquals("Parsed service name", "Billing API", map.get("service"));
        assertEquals("Parsed interval", 30, ((Number) map.get("interval")).intValue());
        assertEquals("Parsed boolean", true, map.get("enabled"));

        List<?> metrics = (List<?>) map.get("metrics");
        assertEquals("Parsed metrics size", 2, metrics.size());
    }

    private static void testServiceEndpointMetrics() {
        System.out.println("-> Testing ServiceEndpoint Metrics");
        ServiceEndpoint ep = new ServiceEndpoint("Test API", "https://example.com/health", "GET", 15, 3000, 200, "Cloud");
        assertEquals("Initial status is PENDING", "PENDING", ep.getCurrentStatus());

        // Probe 1: Success (50ms)
        ep.recordCheckResult(new HealthCheckResult(ep.getId(), Instant.now(), 200, 50, true, "OK"));
        assertEquals("Status is OPERATIONAL", "OPERATIONAL", ep.getCurrentStatus());
        assertEquals("Uptime is 100%", 100.0, ep.getUptimePercentage());

        // Probe 2: Failure (down)
        ep.recordCheckResult(new HealthCheckResult(ep.getId(), Instant.now(), 500, 20, false, "Internal Error"));
        assertEquals("Status changed to DOWN", "DOWN", ep.getCurrentStatus());
        assertEquals("Uptime is 50%", 50.0, ep.getUptimePercentage());

        // Probe 3: Degraded (slow 1400ms)
        ep.recordCheckResult(new HealthCheckResult(ep.getId(), Instant.now(), 200, 1400, true, "Slow response"));
        assertEquals("Status is DEGRADED", "DEGRADED", ep.getCurrentStatus());
        assertTrue("Uptime percentage recalculated", ep.getUptimePercentage() > 60.0);
    }

    private static void testIncidentLifecycle() {
        System.out.println("-> Testing Incident Lifecycle");
        IncidentService service = new IncidentService(new ArrayList<>());
        ServiceEndpoint ep = new ServiceEndpoint("Auth Service", "https://auth.internal", "GET", 10, 2000, 200, "Internal");

        // Simulate outage transition
        service.recordStatusTransition(ep, "OPERATIONAL", "DOWN", "Connection refused");
        assertEquals("1 active incident recorded", 1, service.getActiveIncidents().size());

        Incident incident = service.getActiveIncidents().get(0);
        assertEquals("Incident is OPEN", "OPEN", incident.getStatus());

        // Simulate recovery
        service.recordStatusTransition(ep, "DOWN", "OPERATIONAL", "Recovered");
        assertEquals("0 active incidents remaining", 0, service.getActiveIncidents().size());
        assertEquals("Incident is marked RESOLVED", "RESOLVED", incident.getStatus());
        assertTrue("Incident has resolved timestamp", incident.getResolvedAt() != null);
    }

    private static void testStorageManagerPersistence() {
        System.out.println("-> Testing StorageManager Persistence");
        try {
            Path testPath = Path.of("test_data", "test_storage.json");
            StorageManager sm = new StorageManager(testPath.toString());

            ServiceEndpoint ep = new ServiceEndpoint("Storage Test", "https://storage.test", "GET", 20, 3000, 200, "Test");
            Incident inc = new Incident(ep.getId(), ep.getName(), "Test failure");

            sm.save(List.of(ep), List.of(inc));
            assertTrue("Storage file exists on disk", Files.exists(testPath));

            StorageManager.StorageData loaded = sm.load();
            assertEquals("1 endpoint restored", 1, loaded.endpoints().size());
            assertEquals("Endpoint name matches", "Storage Test", loaded.endpoints().get(0).getName());
            assertEquals("1 incident restored", 1, loaded.incidents().size());

            // Cleanup
            Files.deleteIfExists(testPath);
            Files.deleteIfExists(testPath.getParent());
        } catch (Exception e) {
            assertTrue("Storage test failed with exception: " + e.getMessage(), false);
        }
    }
}
