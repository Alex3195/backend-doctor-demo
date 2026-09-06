# Issue #008 — Thread Pool vs. DB Connection Pool Mismatch

Severity: HIGH

### Before
- Symptom: the embedded Tomcat thread pool (default `server.tomcat.threads.max: 200`,
  never overridden here) is 10x larger than the intentionally small
  Hikari connection pool (`maximum-pool-size: 20`). Every request to
  `POST /api/orders` (the long-transaction endpoint from Issue #004,
  kept as-is for this case study) holds a DB connection -- and the
  Tomcat thread handling it -- for the ~300ms simulated payment call.
  Enough concurrent requests occupy most of Tomcat's 200 worker
  threads, blocked waiting on Hikari's 20-connection pool -- and
  `/actuator/health`, which needs a Tomcat thread too but no DB
  connection at all, has to queue behind them for a free thread.
- Reproduced with `ab -n 300 -c 300` against `POST /api/orders`
  (a single load-generator process, to avoid the client-side noise of
  spawning hundreds of separate `curl` processes) while polling
  `GET /actuator/health` on the same port throughout.
  - Root cause confirmed directly via
    `hikaricp.connections.pending`: **179-180** threads blocked
    waiting for one of the 20 connections, for roughly the first
    second of each burst.
  - Health-check latency during the burst was consistent but not
    catastrophic on this machine: typically one or two checks in the
    0.25-4.5s range (idle baseline ~4-6ms) before recovering to
    baseline as the burst drained.

### Root cause
Tomcat's thread pool has no awareness of the DB connection pool
underneath it. A request that's actually just *waiting* for a DB
connection still occupies a full Tomcat worker thread the entire
time it waits -- so a burst sized anywhere near the thread pool's
capacity can starve every other endpoint on that same port, including
health/liveness checks that orchestrators (Kubernetes, load
balancers) depend on to decide whether to keep sending traffic here
at all.

### Fix
Move Spring Boot Actuator onto a **separate port**
(`management.server.port: 8081`), giving it its own embedded Tomcat
connector and thread pool, independent of the main application's.

### After
Same `ab -n 300 -c 300` burst against `POST /api/orders` on :8080,
polling `GET http://localhost:8081/actuator/health` (separate port)
instead.

- `hikaricp.connections.pending` (queried via :8081, since HikariCP
  itself is one shared pool regardless of which connector serves a
  request) still peaks at **~180** -- expected and correct: the fix
  doesn't touch the connection pool's size, only which thread pool
  actuator uses.
- Health-check latency: **still showed occasional 1-3s spikes** in
  this specific environment, at a consistent position early in the
  burst across repeated runs -- most likely a JVM-wide effect (a GC
  pause or JIT compilation triggered by the burst) rather than thread
  pool contention, since separating the connector doesn't isolate the
  JVM's shared heap/GC from a concurrent allocation spike. This
  machine also runs at `load average ~8.4` on 10 cores even at idle
  (several unrelated Docker services from other projects), which adds
  real noise beyond what this fix can address.
- What *did* change structurally, confirmed directly: actuator moved
  off port 8080 entirely (querying it there now returns nothing), and
  on its own port it kept answering and reporting live metrics
  (including the 180-pending reading above) throughout every burst --
  it never became fully unavailable, unlike a scenario where it shared
  the exhausted pool and had literally no thread left to run on.

### Improvement
- Thread pool exhaustion on the main pool can no longer make actuator
  *unable to get a worker thread at all* -- confirmed structurally
  (separate connector, separate port, kept responding and kept serving
  live metrics through every burst).
- Honest caveat: on this dev machine, occasional multi-second latency
  spikes persisted even with the ports separated, traced to JVM-wide
  effects (likely GC/JIT) that a thread-pool split doesn't address --
  worth flagging rather than claiming a clean win the numbers don't
  fully support. This fix's real, verified scope is *thread-pool
  isolation*, not a general guarantee of health-check latency under
  load; GC tuning / JVM-level isolation would be the next investigation
  if this mattered in production.
- This also doesn't fix the DB connection pool being small, or make
  `/api/orders` itself faster under load -- that's Issue #004's job.
  What it fixes is the *blast radius*: an overloaded main pool can no
  longer take the health check's *thread pool* down with it.
