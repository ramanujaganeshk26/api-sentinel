# APISentinel

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-10b981?style=for-the-badge)]()
[![Dependencies](https://img.shields.io/badge/Dependencies-Zero-indigo?style=for-the-badge)]()

Autonomous API and microservice health monitor, latency tracker, and incident management engine. Built in Java 17 with an embedded web dashboard and REST API. Zero external dependencies required.

---

## Features

- **Zero External Dependencies**: Built with the Java 17 standard library (`com.sun.net.httpserver`, `java.net.http.HttpClient`, `java.util.concurrent`).
- **Autonomous Health Probing**: Multi-threaded periodic heartbeat probes across HTTP and HTTPS endpoints with configurable timeouts, expected status codes, and HTTP methods (`GET`, `HEAD`, `POST`).
- **Latency Tracking**: Records rolling response latencies, response time degradation thresholds, and uptime percentages.
- **Incident Lifecycle Detection**: Automatically detects outages, logs root-cause error diagnostics, tracks downtime duration, and marks incidents as resolved upon service recovery.
- **Embedded Web Dashboard**: Responsive dark interface featuring real-time status indicators, service tags, search filtering, auto-refresh, manual probe execution, and report exports.
- **Atomic Persistence**: Non-blocking atomic JSON state engine that preserves monitoring configuration and incident history across restarts.
- **REST API**: Complete JSON API for integration into operational tooling and automated pipelines.

---

## Architecture

```
                                      +---------------------------------+
                                      |   Web Dashboard (HTML5/CSS3)    |
                                      |   Embedded Dark Mode UI         |
                                      +----------------+----------------+
                                                       | HTTP / JSON
                                                       v
+----------------------------------------------------------------------+
|                            APISentinel Core                          |
|                                                                      |
|   +--------------------+     +------------------+     +-----------+  |
|   │ Embedded HTTP      │     │  Health Check    │     │ Incident  │  |
|   │ Server & REST API  │<--->│  Probe Engine    │<--->│ Manager   │  |
|   │ (Port 8080)        │     │  (Multi-threaded)│     │ Lifecycle │  |
|   +--------------------+     +--------+---------+     +-----------+  |
|                                       │                              |
|                                       v                              |
|                         +--------------------------+                 |
|                         │ Storage Manager (Atomic) │                 |
|                         │ data/sentinel_data.json  │                 |
|                         +--------------------------+                 |
+---------------------------------------+------------------------------+
                                        | HTTP Heartbeats
                                        v
                      +----------------------------------+
                      │ External APIs / Microservices    │
                      │ Cloud • Infrastructure • Internal│
                      +----------------------------------+
```

---

## Getting Started

### Prerequisites
- Java 17 or higher (`java -version`)
- Supported operating systems: Windows, macOS, Linux

### Option 1: Direct Run via Script

```bash
# Windows
scripts\run.bat

# Execute tests
scripts\test.bat
```

### Option 2: Manual Compilation with javac

```bash
# Create output directory
mkdir out\bin

# Compile source files
javac -encoding UTF-8 -d out/bin $(find src/main/java -name "*.java")

# Copy static resources
cp -r src/main/resources/* out/bin/

# Start server
java -cp out/bin com.apisentinel.App 8080
```

### Option 3: Using Maven

```bash
mvn clean compile
mvn package
java -jar target/api-sentinel-1.0.0.jar
```

---

## Web Dashboard

Once started, access the dashboard at:

**http://localhost:8080/**

Key capabilities:
1. **System Health Overview**: Review global uptime percentage, operational counts, degraded targets, and active outages.
2. **Endpoint Management**: Register new targets with custom intervals, expected HTTP codes, and tags.
3. **Manual Probing**: Trigger on-demand health probes per service.
4. **Incident Ledger**: Inspect active and resolved outage logs with duration metrics.
5. **Data Export**: Download complete monitoring configurations and incident reports in JSON format.

---

## REST API Reference

All responses return standard `application/json; charset=UTF-8`.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/summary` | Global system uptime, healthy count, degraded count, and active incidents |
| `GET` | `/api/services` | List all monitored services with live metrics |
| `POST` | `/api/services` | Register a new monitored service |
| `GET` | `/api/services/{id}` | Retrieve details for a specific service |
| `DELETE` | `/api/services/{id}` | Deregister a monitored service |
| `POST` | `/api/services/{id}/ping` | Trigger an immediate synchronous probe |
| `GET` | `/api/incidents` | List chronological outage incident records |
| `GET` | `/api/export` | Download full monitoring configuration and history |

### Example: Register a Service

```bash
curl -X POST http://localhost:8080/api/services \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Payment Gateway",
    "url": "https://api.stripe.com/healthcheck",
    "method": "GET",
    "expectedStatusCode": 200,
    "intervalSeconds": 15,
    "timeoutMillis": 3000,
    "tag": "Production"
  }'
```

### Example: Retrieve System Summary

```bash
curl http://localhost:8080/api/summary
```

Response:
```json
{
  "totalEndpoints": 5,
  "operational": 4,
  "degraded": 1,
  "down": 0,
  "pending": 0,
  "overallUptime": 99.8,
  "activeIncidents": 0
}
```

---

## Testing

APISentinel includes a self-contained test suite that verifies:
- JSON parsing and serialization edge cases
- Service endpoint metric calculations (rolling latencies, uptime percentage)
- Incident lifecycle transitions (`OPERATIONAL` to `DOWN` and recovery)
- Atomic disk storage persistence

To run the test suite:
```bash
scripts\test.bat
```

---

## License

This project is licensed under the [MIT License](LICENSE).
