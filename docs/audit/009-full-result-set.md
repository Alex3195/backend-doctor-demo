# Issue #009 — Loading Full Result Sets Into Memory

Severity: MEDIUM

### Before
- Symptom: `GET /api/reports/revenue-by-status`
  (`ReportService.getRevenueByStatusInMemory()`) calls
  `orderRepository.findAll()`, materializing every row of `orders`
  (20000+ in the seeded dataset) as a full JPA entity in the JVM heap,
  then groups by status and sums `totalAmount` in a Java stream --
  purely to compute a handful of numbers the database itself already
  knows how to produce with `GROUP BY`/`SUM`.
- Measured via `jvm.gc.memory.allocated` (a cumulative allocation
  counter, not "heap used" -- that one bounced around unpredictably
  from call to call because G1GC was reclaiming garbage between
  measurements, which made it useless for this comparison):
  - Allocation per call: **24-46MB** (4 calls, steady state).
  - Response time: ~0.13-0.29s.
- For scale: this is the *entire orders table*, in memory, at once,
  just to answer "how much revenue per status" -- the memory cost
  scales linearly with table size, so this gets worse (and eventually
  OOMs) exactly as the table grows, which is the whole point of this
  demo project's seeded volumes.

### Root cause
Computing an aggregate by loading every row into the application and
reducing it there, instead of asking the database -- which already
has the data, an index-aware query planner, and doesn't need to
serialize 20000+ objects across the JDBC wire and onto the Java heap
-- to compute the aggregate itself.

### Fix
Replace `findAll()` + Java-side grouping with a single JPQL
aggregate query: `SELECT o.status, SUM(o.totalAmount), COUNT(o) FROM
Order o GROUP BY o.status`. Postgres computes the sums directly from
the index/table without ever sending individual rows across the wire;
the application only ever holds the few grouped result rows (one per
distinct status) in memory.

### After
_(fill in once benchmarked)_

### Improvement
_(fill in once benchmarked)_
