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
- SQL queries: 1 (orders) + up to N (customers) — measure locally with
  `hibernate.generate_statistics: true` and note the actual count for
  your seeded data volume.
- Response time: _(fill in after you benchmark locally)_

### Root cause
Lazy-loaded association accessed inside a loop, once per row, with no
batching or fetch strategy specified.

### Fix
_(not yet implemented — planned: JOIN FETCH, `@EntityGraph`, and DTO
projection, benchmarked against each other)_

### After
_(fill in once fixed)_

### Improvement
_(fill in once fixed)_
