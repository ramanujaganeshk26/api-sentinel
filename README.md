# APISentinel 🛡️

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-10b981?style=for-the-badge)]()
[![Zero Dependencies](https://img.shields.io/badge/Dependencies-Zero-indigo?style=for-the-badge)]()

> **Autonomous API & Microservice Health Monitor, Latency Tracker, and Incident Management Engine.** Built in pure Java 17 with an embedded dark-mode glassmorphic web dashboard and REST API. Zero external dependencies required.

---

## 🌟 Key Features

- **⚡ Zero External Dependencies**: 100% standard library (Java 17 `com.sun.net.httpserver`, `java.net.http.HttpClient`, `java.util.concurrent`). No external JAR downloads or Maven/Gradle runtimes strictly required to build and execute.
- **🛡️ Autonomous Health Probing**: Multi-threaded periodic heartbeat probes across HTTP, HTTPS, and REST APIs with custom timeouts, expected status codes, and HTTP methods (`GET`, `HEAD`, `POST`).
- **📊 Real-time Latency Sparklines**: Tracks rolling probe latencies (min, average, p95), response time degradation alerts (>1200ms), and 24h weighted uptime percentages.
- **🚨 Incident Lifecycle Detection**: Automatically detects outages (`UP -> DOWN`), opens incident records with root-cause error diagnostics, tracks total downtime duration, and marks incidents as resolved upon service recovery.
- **✨ Embedded Glassmorphic Dashboard**: Ultra-modern, responsive dark UI featuring live glowing status indicators, category tags, interactive search, auto-refresh (5s), manual probe triggers, and report exports.
- **💾 Atomic Persistence**: Non-blocking atomic JSON state engine that preserves monitoring configuration and incident history across server restarts.
- **📡 Developer REST API**: Full JSON API for programmatic automation, custom alerts, or integration with Slack/PagerDuty webhooks.

---

## 📐 Architecture

```
                                      ┌─────────────────────────────────┐
                                      │   Web Dashboard (HTML5/CSS3)    │
                                      │   Glassmorphic Dark Mode UI     │
                                      └────────────────┬────────────────┘
                                                       │ HTTP / JSON
                                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                            APISentinel Core                          │
│                                                                      │
│   ┌────────────────────┐     ┌──────────────────┐     ┌───────────┐  │
│   │ Embedded HTTP      │     │  Health Check    │     │ Incident  │  │
│   │ Server & REST API  │◄───►│  Probe Engine    │◄───►│ Manager   │  │
│   │ (Port 8080)        │     │  (Multi-threaded)│     │ Lifecycle │  │
│   └────────────────────┘     └────────┬─────────┘     └───────────┘  │
│                                       │                              │
│                                       ▼                              │
│                         ┌──────────────────────────┐                 │
│                         │ Storage Manager (Atomic) │                 │
│                         │ data/sentinel_data.json  │                 │
│                         └──────────────────────────┘                 │
└───────────────────────────────────────┬──────────────────────────────┘
                                        │ HTTP Heartbeats
                                        ▼
                      ┌──────────────────────────────────┐
                      │ External APIs / Microservices    │
                      │ Cloud • Infrastructure • Internal│
                      └──────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- **Java 17 or higher** (`java -version`)
- Windows, macOS, or Linux

### Option 1: Direct Run (Fastest)

Clone the repository and run the preconfigured launch script:

```bash
# Windows
scripts\run.bat

# Or run tests
scripts\test.bat
```

### Option 2: Manual Compilation with standard `javac`

```bash
# Create bin folder
mkdir out\bin

# Compile all source files
javac -encoding UTF-8 -d out/bin $(find src/main/java -name "*.java")

# Copy web assets
cp -r src/main/resources/* out/bin/

# Start APISentinel
java -cp out/bin com.apisentinel.App 8080
```

### Option 3: Using Maven

```bash
mvn clean compile
mvn package
java -jar target/api-sentinel-1.0.0.jar
```

---

## 💻 Web Dashboard

Once started, open your browser:

👉 **[http://localhost:8080/](http://localhost:8080/)**

The dashboard allows you to:
1. **Monitor System Health**: View system-wide uptime percentages, operational counts, degraded targets, and outages at a glance.
2. **Add Endpoints Dynamically**: Add endpoints with custom intervals, expected HTTP codes, and tags.
3. **Trigger Instant Probes**: Click the **Ping** button on any card to perform an immediate manual health check.
4. **Inspect Incident History**: Review active and resolved outages with downtime durations.
5. **Export Monitoring Data**: Click **Export JSON** to download a snapshot of all targets and incident reports.

---

## 📡 REST API Reference

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

### Example: Register a New Service
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

### Example: Get Summary Status
```bash
curl http://localhost:8080/api/summary
```
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

## 🧪 Testing

APISentinel includes a zero-dependency test suite verifying:
- JSON parsing and serializing (edge cases, escapes, nested structures)
- Service endpoint metric computations (rolling latencies, uptime math)
- Incident state transition lifecycle (`OPERATIONAL -> DOWN -> OPERATIONAL`)
- Atomic storage read/write persistence

Run the test suite:
```bash
scripts\test.bat
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
