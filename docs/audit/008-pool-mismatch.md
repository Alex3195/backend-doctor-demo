# Issue #008 — Thread Pool vs. DB Connection Pool Mismatch

Severity: HIGH

### Before
- Symptom: the embedded Tomcat thread pool (default `server.tomcat.threads.max: 200`,
  never overridden here) is 10x larger than the intentionally small
  Hikari connection pool (`maximum-pool-size: 20`, see
  `application.yml`). Every request to `POST /api/orders` (the
  long-transaction endpoint from Issue #004, still kept as-is for this
  case study) holds a DB connection -- and the Tomcat thread handling
  it -- for the ~300ms simulated payment call. When enough concurrent
  requests arrive to occupy most of Tomcat's 200 worker threads, those
  threads are blocked waiting on Hikari's 20-connection pool, not doing
  anything with the DB itself -- and `/actuator/health`, which needs a
  Tomcat thread too but no DB connection at all, has to wait behind
  them for a free thread.
- Reproduced: fired 220-300 concurrent `POST /api/orders` requests,
  and polled `GET /actuator/health` (same port, same Tomcat thread
  pool) throughout.
  - Idle baseline for `/actuator/health`: ~4-6ms.
  - The first health check issued right as the burst arrived: **2.44s**
    (run 1, 220 concurrent) and **3.56s** (run 2, 300 concurrent) --
    roughly 500-700x the idle baseline. Subsequent health checks
    recovered to near-baseline within ~250-500ms, as the DB-bound
    requests cycled through the 20-connection pool and freed up
    threads.
  - This is an unrelated, DB-independent endpoint measurably delayed
    by a burst on a completely different endpoint, purely because they
    share one thread pool.

### Root cause
Tomcat's thread pool has no awareness of the DB connection pool
underneath it. A request that's actually just *waiting* for a DB
connection still occupies a full Tomcat worker thread the entire
time it waits -- so a burst sized anywhere near the thread pool's
capacity can starve every other endpoint on that same port, including
health/liveness checks that orchestrators (Kubernetes, load balancers)
depend on to decide whether to keep sending traffic here at all. A
health check failing *because* the app is overloaded, right when an
operator most needs to see "still alive, just slow," is exactly
backwards.

### Fix
Move Spring Boot Actuator (`/actuator/**`, including `/health`) onto a
**separate port** (`management.server.port: 8081`). Spring Boot then
runs actuator on its own embedded Tomcat connector with its own,
independent thread pool -- so exhausting the main application's
thread pool (port 8080) no longer has any effect on the management
port's ability to answer `/actuator/health`.

### After
Same reproduction (300 concurrent `POST /api/orders` on :8080), but
polling `GET http://localhost:8081/actuator/health` (separate port)
throughout instead:
- All 20 health checks during the burst: **3-8ms** -- indistinguishable
  from the idle baseline. No spike at all, at any point during the
  same load that produced a 3.56s spike before.

### Improvement
- Worst-case health-check latency during a burst: 2.44-3.56s → 3-8ms
  (no measurable degradation).
- This doesn't fix the DB connection pool being small, or make
  `/api/orders` itself faster under load -- that's Issue #004's and
  the pool-sizing tuning's job. What it fixes is the *blast radius*:
  an overloaded main pool can no longer take the health check down
  with it, which is what actually matters for an orchestrator deciding
  whether to keep routing traffic to this instance or restart it.
