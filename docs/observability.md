# Phase 5 — Load Testing & Metrics

## Metrics: Prometheus + Grafana

```bash
docker compose up -d prometheus grafana
```

- Prometheus scrapes the app's `/actuator/prometheus` on port **8081**
  (actuator's own port since Issue #008) every 5s. Config:
  `observability/prometheus.yml`.
- Grafana is provisioned automatically (datasource + dashboard, no
  manual setup) and open with anonymous admin access -- no login
  needed for local use. Default port `3000` (this machine had a port
  conflict, see the port-remap note in project memory, if relevant).
- Dashboard "Backend Doctor Demo — Overview"
  (`observability/grafana/dashboards/backend-doctor-overview.json`):
  HTTP request rate/latency (p95) by URI, Hikari connection pool
  (active/idle/pending -- the same metric that confirmed Issue #008's
  root cause), JVM heap used, GC pause time, Tomcat active sessions.

On Linux, `host.docker.internal` needs the `extra_hosts:
host-gateway` entry already in `docker-compose.yml`'s `prometheus`
service -- no extra setup needed there either.

## Load testing: Gatling

Load tests live in `load-tests/`, a **separate Gradle subproject**,
deliberately not sharing the root project's dependencies. Gatling
bundles its own (newer) Netty; Spring Boot's dependency-management
plugin pins Netty to an older version project-wide (for Kafka) with no
supported way to scope that pin out of just Gatling's configurations
-- so the clean fix was to not share a Gradle project (and its
dependency resolution) with the app at all.

```bash
./gradlew bootRun            # app must be running on :8080 first
./gradlew :load-tests:gatlingRun
```

Report lands at
`load-tests/build/reports/gatling/<simulation>-<timestamp>/index.html`.

`BeforeAfterComparisonSimulation` runs a few of the fixed issues'
endpoints under real concurrent load:
- Issue #003: offset vs. keyset pagination, equivalent deep page.
- Issue #006: repeated lookups of the same product (cached).
- Issue #010: daily revenue aggregation (indexed).

It deliberately **excludes** the Issue #001 (N+1) and Issue #009
(full-result-set) endpoints -- both return the *entire* `orders`
table as JSON with no pagination, which was fine to hit once manually
against the original ~20k-row seed (see their docs/audit write-ups
for those numbers) but is no longer reasonable to hit repeatedly under
concurrent load now that Issue #010's benchmark bulk-loaded 500k more
rows (523,501 total in `orders`). A first attempt at including them
in this simulation actually failed with HTTP 500s and connection
timeouts under just 20 concurrent users, once the table was that
large -- a real, useful reminder that *which endpoints are safe to
load-test* is itself scale-dependent, not just "does it work in a
manual curl."
