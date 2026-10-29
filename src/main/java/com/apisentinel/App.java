package com.apisentinel;

import com.apisentinel.model.Incident;
import com.apisentinel.model.ServiceEndpoint;
import com.apisentinel.server.HttpServerApp;
import com.apisentinel.service.HealthCheckEngine;
import com.apisentinel.service.IncidentService;
import com.apisentinel.storage.StorageManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application entrypoint for APISentinel.
 */
public class App {
    public static final String VERSION = "1.0.0";
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        printBanner(port);

        // Initialize storage
        StorageManager storageManager = new StorageManager("data/sentinel_data.json");
        StorageManager.StorageData data = storageManager.load();

        List<ServiceEndpoint> endpoints = new ArrayList<>(data.endpoints());
        List<Incident> incidents = new ArrayList<>(data.incidents());

        // Seed default endpoints if fresh install
        if (endpoints.isEmpty()) {
            System.out.println("[INIT] Seeding initial service monitors...");
            endpoints.add(new ServiceEndpoint("GitHub API", "https://api.github.com/zen", "GET", 15, 4000, 200, "Cloud"));
            endpoints.add(new ServiceEndpoint("Cloudflare DNS", "https://1.1.1.1", "GET", 15, 3000, 200, "Infrastructure"));
            endpoints.add(new ServiceEndpoint("Google Web", "https://www.google.com", "HEAD", 20, 4000, 200, "Network"));
            endpoints.add(new ServiceEndpoint("Local Sentinel Health", "http://localhost:" + port + "/api/mock/200", "GET", 10, 2000, 200, "Internal"));
            endpoints.add(new ServiceEndpoint("Local Degraded Simulator", "http://localhost:" + port + "/api/mock/slow", "GET", 20, 2000, 200, "Internal"));
            
            ServiceEndpoint demoOutage = new ServiceEndpoint("Local Outage Simulator", "http://localhost:" + port + "/api/mock/500", "GET", 15, 2000, 200, "Demo");
            demoOutage.setEnabled(false); // keep disabled by default
            endpoints.add(demoOutage);
        }

        // Initialize services
        IncidentService incidentService = new IncidentService(incidents);
        HealthCheckEngine engine = new HealthCheckEngine(endpoints, incidentService, storageManager);

        // Start health check probe engine
        engine.start();

        // Start HTTP Server
        HttpServerApp server = new HttpServerApp(port, engine, incidentService);
        try {
            server.start();
            System.out.println("=================================================================");
            System.out.printf(" >> Dashboard URL : http://localhost:%d/%n", port);
            System.out.printf(" >> REST API URL  : http://localhost:%d/api/summary%n", port);
            System.out.println("=================================================================");
            System.out.println("Sentinel is actively monitoring your services. Press Ctrl+C to exit.");
        } catch (IOException e) {
            System.err.println("Fatal: Could not start HTTP server on port " + port + ": " + e.getMessage());
            System.exit(1);
        }

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Stopping APISentinel gracefully...");
            server.stop();
            engine.stop();
            System.out.println("[SHUTDOWN] All states saved to disk. Goodbye!");
        }));
    }

    private static void printBanner(int port) {
        System.out.println("""
              ___  ____ ___ ____            _   _            _ 
             / _ \\|  _ \\_ _/ ___|  ___ _ __ | |_(_)_ __   ___| |
            / /_\\ \\ |_) | |\\___ \\ / _ \\ '_ \\| __| | '_ \\ / _ \\ |
            |  _  |  __/| | ___) |  __/ | | | |_| | | | |  __/ |
            |_| |_|_|  |___|____/ \\___|_| |_|\\__|_|_| |_|\\___|_|
                                       Autonomous Service Monitor
            """);
        System.out.printf(" Version: %s | Java: %s | Port: %d%n%n",
                VERSION, System.getProperty("java.version"), port);
    }
}
