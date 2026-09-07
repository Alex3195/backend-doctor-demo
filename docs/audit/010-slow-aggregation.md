# Issue #010 — Slow Aggregation Queries at Scale

Severity: MEDIUM

### Before
- Symptom: `GET /api/reports/daily-revenue?days=N`
  (`OrderRepository.findDailyRevenueSince()`) groups/sums revenue by
  day for the last N days. There's no index on `orders.created_at`
  (only the composite `idx_orders_customer_created(customer_id,
  created_at)` from Issue #002, which only helps when filtering by a
  specific customer, not across the whole table), so filtering by a
  date range forces Postgres to check every row.
- To make this realistic at scale (the app's own seeded/test data only
  spanned ~11 hours, not enough to show a meaningful date-range
  filter), bulk-loaded 500,000 additional `orders` rows with
  `created_at` randomly distributed across the past 180 days directly
  via SQL (`generate_series`), bringing the table to **523,501 rows**.
- `EXPLAIN ANALYZE` for `days=7` (a real ~7/180 ≈ 4% selectivity):
  ```
  Finalize GroupAggregate (actual time=34.669..37.074 rows=8 loops=1)
    -> Gather Merge (Workers Launched: 2)
        -> Partial GroupAggregate
            -> Sort
                -> Parallel Seq Scan on orders
                      Filter: (created_at >= (now() - '7 days'::interval))
                      Rows Removed by Filter: 160170 (per worker)
  Execution Time: 37.223 ms
  ```
  Postgres has to fall back to a **parallel sequential scan** (2
  workers) across all 523,501 rows to find the ~43,000 matching ones --
  filtering nearly all of them out per worker.
- Measured via the actual endpoint: ~20ms steady state (first call
  ~257ms while the JVM/JIT warms up).

### Root cause
No index supports filtering by `created_at` alone. Every row has to be
read and checked against the date filter regardless of the actual
result size, so cost grows linearly with table size -- fine at a few
thousand rows (as seen with the smaller seeded dataset), increasingly
expensive as the table grows toward production scale.

### Fix
Add `V3__add_orders_created_at_index.sql`:
`CREATE INDEX idx_orders_created_at ON orders(created_at)`. A plain
index (not the customer-scoped composite from Issue #002) supports
filtering by date range regardless of which customer placed the
order.

### After
Same `EXPLAIN ANALYZE` for `days=7`, same 523,501-row table, index in place:
```
GroupAggregate (actual time=19.267..23.634 rows=8 loops=1)
  -> Sort
      -> Bitmap Heap Scan on orders
            Recheck Cond: (created_at >= (now() - '7 days'::interval))
            Heap Blocks: exact=4524
            -> Bitmap Index Scan on idx_orders_created_at
                  Index Cond: (created_at >= (now() - '7 days'::interval))
Execution Time: 24.003 ms
```
Plan changed from a 2-worker parallel sequential scan to a single-process
Bitmap Index Scan.

### Improvement
- Execution time: 37.2ms → 24.0ms (~35% faster) at the current
  523,501-row scale.
- More importantly, this doesn't scale with table size the way the
  sequential scan does: the index scan's cost tracks the *result*
  size (~43,000 matching rows), not the *table* size, so the gap
  between the two plans only widens as the table keeps growing past
  this point -- the sequential scan gets linearly slower, the index
  scan barely changes.
