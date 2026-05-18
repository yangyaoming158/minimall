# Observability

Task 18 adds a local Prometheus and Grafana path for MiniMall service metrics.

## Services

The local Compose stack includes:

- Prometheus: `http://127.0.0.1:${PROMETHEUS_PORT}`
- Grafana: `http://127.0.0.1:${GRAFANA_PORT}`

Grafana reads:

- Datasource provisioning from `infra/grafana/provisioning/datasources/prometheus.yml`
- Dashboard provisioning from `infra/grafana/provisioning/dashboards/minimall.yml`
- Dashboard JSON from `infra/grafana/dashboards/minimall-observability.json`

The provisioned dashboard is `MiniMall Observability`. It includes service target status, HTTP request rate, average HTTP latency, JVM heap usage, JDBC connections, and process CPU usage.

## Start And Validate

Validate the Compose configuration:

```bash
docker compose --env-file .env.example config --quiet
```

Start the observability services:

```bash
docker compose --env-file .env up -d prometheus grafana
```

Check containers:

```bash
docker compose --env-file .env ps prometheus grafana
```

Check Prometheus configuration inside the running container:

```bash
docker compose --env-file .env exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
```

Check Prometheus readiness:

```bash
docker compose --env-file .env exec -T prometheus wget -q -O - http://127.0.0.1:9090/-/ready
```

Check Grafana health:

```bash
docker compose --env-file .env exec -T grafana wget -q -O - http://127.0.0.1:3000/api/health
```

Check Prometheus target status:

```bash
docker compose --env-file .env exec -T prometheus promtool query instant http://127.0.0.1:9090 up
```

The Prometheus target for itself should be `up=1`. MiniMall service targets become `up=1` after the services are running and reachable from the Prometheus container.

## Service Metrics

Every Spring Boot service exposes:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

The checked-in Prometheus configuration scrapes containerized Java services by
Compose service name:

- `api-gateway:8080`
- `user-service:8101`
- `product-service:8102`
- `inventory-service:8103`
- `order-service:8104`
- `payment-service:8105`
- `notification-service:8106`

Start the Java service containers with the Compose `services` profile after the
Maven jars have been built:

```bash
mvn clean package -DskipTests
docker compose --env-file .env --profile services up -d --build
```

For a service running directly in WSL or on the host, check the endpoint from that same environment first:

```bash
curl --noproxy '*' -fsS http://127.0.0.1:8103/actuator/prometheus
```

## Docker Desktop And WSL

`infra/prometheus/prometheus.yml` now uses Docker Compose service names for Java
service scrape targets. This is the stable path for Docker Desktop + WSL because
Prometheus and the Java services share the same Compose network.

When Java services run directly inside WSL while Prometheus runs in Docker
Desktop, `host.docker.internal` can resolve to the Windows/Docker Desktop host
rather than the WSL VM. In that setup, Prometheus may register host-based
targets but show them as down. Keep any host-based Prometheus override local and
outside Git.

To confirm this is a network path issue rather than an actuator issue:

1. Start one service in WSL, for example `inventory-service` on port `8103`.
2. Confirm WSL can read `http://127.0.0.1:8103/actuator/prometheus`.
3. Find the current WSL IP with `hostname -I`.
4. From the Prometheus container, test `http://<wsl-ip>:8103/actuator/prometheus`.

The WSL IP is environment-specific and can change after restart, so do not commit
it into `infra/prometheus/prometheus.yml`. Use the checked-in Compose service
targets for stable project verification.

## Grafana Provisioning Checks

After Grafana starts, the Prometheus datasource is provisioned as UID `minimall-prometheus`, and the dashboard is provisioned with UID `minimall-observability`.

From inside the Grafana container, the datasource URL is configured by `GRAFANA_PROMETHEUS_URL`, which defaults to `http://prometheus:9090` in `.env.example`.
