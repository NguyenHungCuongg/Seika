# Observability Setup (Grafana Stack)

This repository includes a full observability stack using the Grafana ecosystem:

- **Metrics**: Prometheus (scraping Spring Boot Actuator)
- **Logs**: Loki & Promtail (scraping Docker logs)
- **Traces**: Tempo (ingesting OpenTelemetry traces via Micrometer Tracing)
- **Visualization**: Grafana

## How to Run

To run the observability stack alongside the microservices, include the extra docker-compose file:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

## Endpoints & URLs

- **Grafana UI**: [http://localhost:3000](http://localhost:3000) (Username: admin; Password: admin)
- **Prometheus UI**: [http://localhost:9090](http://localhost:9090)
- **Tempo**: Exposed at port 3200 (internal for Grafana) and 4317 for OTLP ingest.

## Dashboards

When you open Grafana at `http://localhost:3000`, click on **Dashboards** in the left menu. You will find:

- **Spring Boot 3.x System Monitor**: A pre-imported dashboard that shows JVM metrics, CPU, Memory, and HTTP request statistics for all microservices.

## Viewing Logs

1. Go to **Explore** (compass icon) in Grafana.
2. Select the **Loki** datasource.
3. Query logs by container name. For example: `{container="identity-service"}`.
4. You can also filter logs by trace ID if you find a trace ID in an error message.

## Viewing Traces

1. Go to **Explore** in Grafana.
2. Select the **Tempo** datasource.
3. You can use the Search tab to find traces by service name (e.g. `wallet-service`) or just paste a `traceId` if you found one in the logs.
4. Because logs and traces are correlated, when viewing logs in Loki, any detected `TraceID` will be a clickable link that opens the corresponding trace in Tempo!
