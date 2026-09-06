# Audit overview

This document tracks the "before/after" case studies for backend-doctor-demo.
Each issue gets its own file once it's fixed: `NNN-short-name.md`, following
this template.

## Template

```
## Issue #NNN — <name>

Severity: HIGH | MEDIUM | LOW

### Before
- Symptom:
- SQL queries:
- Response time:

### Root cause


### Fix


### After
- SQL queries:
- Response time:

### Improvement
- e.g. 93% fewer queries, 4.8s -> 320ms
```

## Issue #001 — N+1 Query on GET /api/orders

Severity: HIGH

### Before
- Symptom: `OrderService.findAllOrdersUnoptimized()` calls
  `order.getCustomer().getFullName()` for each order. Because
  `Order.customer` is `FetchType.LAZY`, this triggers one extra
  `SELECT * FROM customers WHERE id = ?` per distinct customer touched.
- SQL queries: 1 (orders) + 4903 (customers) — measured with
  `hibernate.generate_statistics: true` against the default seed
  (5000 customers, 20000 orders).
- Response time: ~1.3-1.6s (`GET /api/orders`, local, warm JVM).

### Root cause
Lazy-loaded association accessed inside a loop, once per row, with no
batching or fetch strategy specified.

### Fix
Added three alternative endpoints alongside the unoptimized baseline
(kept as-is for the case study), each backed by its own repository
query:
- `GET /api/orders/optimized/join-fetch` — `OrderRepository.findAllWithCustomerJoinFetch()`,
  a hand-written `JOIN FETCH`.
- `GET /api/orders/optimized/entity-graph` — `OrderRepository.findAllWithCustomerEntityGraph()`,
  same result declared via `@EntityGraph(attributePaths = "customer")`.
- `GET /api/orders/optimized/dto-projection` — `OrderRepository.findAllOrderSummaries()`,
  a JPQL constructor expression selecting only the columns the API
  returns (no full `Order`/`Customer` entities materialized).

### After
- SQL queries: 1 for all three variants (confirmed via
  `hibernate.generate_statistics` — "1 JDBC statements executed").
- Response time: join-fetch ~0.16-0.18s, entity-graph ~0.18s,
  dto-projection ~0.08-0.09s.

### Improvement
- ~99.98% fewer queries (4904 → 1).
- Response time: 1.3-1.6s → 0.16-0.18s (join-fetch/entity-graph, ~87%
  faster) or → 0.08-0.09s (dto-projection, ~94% faster and the
  cheapest of the three, since it skips loading full entities).
