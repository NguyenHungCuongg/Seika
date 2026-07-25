# Seika Microservices - Development & Testing Guide

This guide details how to build, run, stop, restart, and debug the Seika microservices ecosystem. It also explains how to use the LGTM Observability Stack (Loki, Grafana, Tempo, Prometheus) for logging, monitoring, and tracing.

---

## 1. Prerequisites

Before starting, ensure you have the following installed on your machine:

- **Docker & Docker Compose** (Desktop/Engine)
- **Java JDK 21**
- **Maven** (optional, wrapper script `mvnw.cmd` / `mvnw` is included in the project)

---

## 2. Command Reference Sheet

Here is a summary of the command chains you will use frequently during development:

### Running the Whole System (Core + Observability)

Always run Docker Compose with both compose files to include the monitoring services:

```bash
# Start all containers in the background (fresh/rebuild)
mvn clean package -DskipTests
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build

# Start standard (warm start, no rebuilds)
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# Watch mode (recommended for development)
docker compose -f docker-compose.yml -f docker-compose.observability.yml watch
```

### Stopping the System

```bash
# Stop containers (preserves volumes/databases data)
docker compose -f docker-compose.yml -f docker-compose.observability.yml down

# Stop and remove all volumes (WARNING: wipes all databases clean)
docker compose -f docker-compose.yml -f docker-compose.observability.yml down -v
```

### Restarting the System

```bash
# Quick restart of all services
docker compose -f docker-compose.yml -f docker-compose.observability.yml restart

# Recreate containers to apply configurations or rebuilt jar files
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --force-recreate
```

---

## 3. Core Development Workflows

When developing the codebase, choose one of the workflows below depending on what you are editing:

### Workflow A: Rebuilding & Running Everything in Docker (Thorough)

Use this workflow when you want to run the entire system in a production-like docker environment.

1. **Recompile the Java code:**

   ```bash
   # Windows PowerShell
   .\src\services\identity-service\mvnw.cmd clean package -DskipTests

   # Linux/macOS/Git Bash
   ./src/services/identity-service/mvnw clean package -DskipTests
   ```

2. **Rebuild the Docker images:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.observability.yml build
   ```
3. **Restart the containers:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --force-recreate
   ```

### Workflow B: Fast Single-Service Development (Recommended)

Building Docker images for every code change is slow. Instead, you can run databases and infra in Docker, and run your target microservice locally on your host machine:

1. **Start ONLY the databases and messaging infrastructure:**
   ```bash
   docker compose -f docker-compose.yml up -d seika-mongo seika-rabbitmq seika-eureka-1 seika-config-service-1 seika-identity-db-1 seika-profile-db-1 seika-wallet-db-1 seika-marketplace-db-1 seika-reward-db-1
   ```
2. **Run your specific microservice locally** in your IDE (IntelliJ / VS Code) or via command line.
   For example, to run `wallet-service` locally:
   ```bash
   cd src/services/wallet-service
   ../../mvnw spring-boot:run
   ```
   _The local service will automatically register itself to the Dockerized Eureka server and connect to the Dockerized databases!_

### Workflow C: Updating Configurations only

If you only modify configuration files under `src/config-service/src/main/resources/configs/`, you don't need to rebuild all microservices. Just rebuild and restart the Config Server:

```bash
# 1. Package the new configuration files into the config-service jar
.\src\services\identity-service\mvnw.cmd -pl src/config-service -am -DskipTests package

# 2. Rebuild the config-service docker image
docker compose -f docker-compose.yml -f docker-compose.observability.yml build config-service

# 3. Restart config-service and your modified microservices to fetch new configs
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --force-recreate config-service wallet-service
```

---

## 4. Key Infrastructure & Observability Endpoints

Once the stack is running, you can access the following dashboards and endpoints to test APIs and monitor the system:

| Service / Tool             | URL                                                                                          | Description                                                                           |
| :------------------------- | :------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------ |
| **Netflix Eureka**         | [http://localhost:8761](http://localhost:8761)                                               | Service discovery dashboard. Displays all active microservices.                       |
| **API Gateway Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)   | Interactive API documentation. Use this to test endpoint responses.                   |
| **Grafana**                | [http://localhost:3000](http://localhost:3000)                                               | Centralized Observability UI. Dashboard for metrics, logs, and traces.                |
| **Prometheus**             | [http://localhost:9090](http://localhost:9090)                                               | Under-the-hood metrics database. Check target scrape health in **Status -> Targets**. |
| **Config Server API**      | [http://localhost:8888/wallet-service/default](http://localhost:8888/wallet-service/default) | Inspect the raw properties served by Spring Cloud Config to a service.                |

---

## 5. Verification Guide in Grafana

1. Open **Grafana** at [http://localhost:3000](http://localhost:3000) (no login required, auto-login as Admin).
2. Go to **Dashboards** and select **Spring Boot 3.x Statistics**.
3. In the filters panel at the top, select:
   - **Namespace:** `default`
   - **Application:** `<Choose your service, e.g., wallet-service>`
   - **Instance:** `<Auto-populated>`
4. The dashboard charts will populate with live CPU, Memory, and Uptime metrics.
5. To view Logs and Traces, open the **Explore** tab in the sidebar:
   - Use **Loki** to query logs: `{container="seika-wallet-service-1"}`.
   - Click on any blue **Tempo** trace links next to `traceId` within logs to see the timeline tracing across multiple microservices.

For a detailed explanation of the LGTM architecture and step-by-step setup guides, refer to [OBSERVABILITY_LGTM_GUIDE.md](OBSERVABILITY_LGTM_GUIDE.md).
