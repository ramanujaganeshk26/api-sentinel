package com.apisentinel.server;

import com.apisentinel.model.HealthCheckResult;
import com.apisentinel.model.Incident;
import com.apisentinel.model.ServiceEndpoint;
import com.apisentinel.service.HealthCheckEngine;
import com.apisentinel.service.IncidentService;
import com.apisentinel.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server providing REST API endpoints and serving the modern web dashboard.
 */
public class HttpServerApp {
    private final int port;
    private final HealthCheckEngine engine;
    private final IncidentService incidentService;
    private HttpServer server;

    public HttpServerApp(int port, HealthCheckEngine engine, IncidentService incidentService) {
        this.port = port;
        this.engine = engine;
        this.incidentService = incidentService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // API endpoints
        server.createContext("/api/summary", this::handleSummary);
        server.createContext("/api/services", this::handleServices);
        server.createContext("/api/incidents", this::handleIncidents);
        server.createContext("/api/export", this::handleExport);

        // Built-in mock endpoints for easy testing
        server.createContext("/api/mock/200", exchange -> sendResponse(exchange, 200, "{\"status\":\"healthy\",\"message\":\"Service is operating normally\"}"));
        server.createContext("/api/mock/500", exchange -> sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Internal database connection timeout\"}"));
        server.createContext("/api/mock/slow", exchange -> {
            try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
            sendResponse(exchange, 200, "{\"status\":\"slow\",\"message\":\"Heavy load computation completed\"}");
        });

        // Dashboard static asset router
        server.createContext("/", this::handleStatic);

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private void handleSummary(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        List<ServiceEndpoint> endpoints = engine.getEndpoints();
        long operational = 0;
        long degraded = 0;
        long down = 0;
        long pending = 0;
        double uptimeSum = 0;

        for (ServiceEndpoint ep : endpoints) {
            switch (ep.getCurrentStatus()) {
                case "OPERATIONAL" -> operational++;
                case "DEGRADED" -> degraded++;
                case "DOWN" -> down++;
                default -> pending++;
            }
            uptimeSum += ep.getUptimePercentage();
        }

        double overallUptime = endpoints.isEmpty() ? 100.0 : Math.round((uptimeSum / endpoints.size()) * 10.0) / 10.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEndpoints", endpoints.size());
        summary.put("operational", operational);
        summary.put("degraded", degraded);
        summary.put("down", down);
        summary.put("pending", pending);
        summary.put("overallUptime", overallUptime);
        summary.put("activeIncidents", incidentService.getActiveIncidents().size());

        sendJsonResponse(exchange, 200, summary);
    }

    private void handleServices(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath(); // e.g., /api/services or /api/services/{id} or /api/services/{id}/ping

        String[] parts = path.split("/");
        // parts: ["", "api", "services", "{id}", "{ping}"]

        if (parts.length > 3) {
            String serviceId = parts[3];

            if (parts.length == 5 && "ping".equalsIgnoreCase(parts[4])) {
                // POST /api/services/{id}/ping
                if (!"POST".equals(method)) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                Optional<ServiceEndpoint> opt = engine.getEndpointById(serviceId);
                if (opt.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\":\"Service not found\"}");
                    return;
                }
                HealthCheckResult result = engine.checkEndpoint(opt.get());
                sendJsonResponse(exchange, 200, result.toMap());
                return;
            }

            // Single service: GET or DELETE
            Optional<ServiceEndpoint> opt = engine.getEndpointById(serviceId);
            if (opt.isEmpty()) {
                sendResponse(exchange, 404, "{\"error\":\"Service not found\"}");
                return;
            }

            if ("GET".equals(method)) {
                sendJsonResponse(exchange, 200, opt.get().toMap());
            } else if ("DELETE".equals(method)) {
                engine.removeEndpoint(serviceId);
                sendJsonResponse(exchange, 200, Map.of("message", "Service removed successfully", "id", serviceId));
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
            return;
        }

        // Collection endpoints: GET or POST
        if ("GET".equals(method)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ServiceEndpoint ep : engine.getEndpoints()) {
                list.add(ep.toMap());
            }
            sendJsonResponse(exchange, 200, list);
        } else if ("POST".equals(method)) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> req = JsonUtil.parseObject(body);

            String name = (String) req.get("name");
            String url = (String) req.get("url");
            String httpMethod = (String) req.getOrDefault("method", "GET");
            int interval = req.containsKey("intervalSeconds") ? ((Number) req.get("intervalSeconds")).intValue() : 15;
            int timeout = req.containsKey("timeoutMillis") ? ((Number) req.get("timeoutMillis")).intValue() : 5000;
            int expectedCode = req.containsKey("expectedStatusCode") ? ((Number) req.get("expectedStatusCode")).intValue() : 200;
            String tag = (String) req.getOrDefault("tag", "General");

            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                sendResponse(exchange, 400, "{\"error\":\"Name and URL are required\"}");
                return;
            }

            ServiceEndpoint endpoint = new ServiceEndpoint(name, url, httpMethod, interval, timeout, expectedCode, tag);
            engine.addEndpoint(endpoint);
            // Trigger an immediate initial check asynchronously
            engine.checkEndpointAsync(endpoint);

            sendJsonResponse(exchange, 201, endpoint.toMap());
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private void handleIncidents(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Incident inc : incidentService.getAllIncidents()) {
            list.add(inc.toMap());
        }
        sendJsonResponse(exchange, 200, list);
    }

    private void handleExport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportDate", new Date().toString());
        List<Map<String, Object>> epList = new ArrayList<>();
        for (ServiceEndpoint ep : engine.getEndpoints()) epList.add(ep.toMap());
        root.put("endpoints", epList);

        List<Map<String, Object>> incList = new ArrayList<>();
        for (Incident inc : incidentService.getAllIncidents()) incList.add(inc.toMap());
        root.put("incidents", incList);

        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"sentinel_export.json\"");
        sendJsonResponse(exchange, 200, root);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = "/web" + path;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                sendResponse(exchange, 404, "Not Found");
                return;
            }

            String contentType = "text/html; charset=UTF-8";
            if (path.endsWith(".css")) {
                contentType = "text/css; charset=UTF-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=UTF-8";
            } else if (path.endsWith(".json")) {
                contentType = "application/json; charset=UTF-8";
            } else if (path.endsWith(".svg")) {
                contentType = "image/svg+xml";
            }

            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int status, Object data) throws IOException {
        String json = JsonUtil.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
