# Issue #002 — Missing Database Index

Severity: HIGH

### Before
`orders` has no index on `customer_id` (see the comment in
`V1__init_schema.sql`). Once the table has a realistic row count, a
query like:

```sql
SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at DESC LIMIT 20;
```

falls back to a sequential scan.

### Root cause
No index defined at schema design time.

### Fix (planned)
```sql
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);
```

### After
_(fill in with `EXPLAIN ANALYZE` output before/after, and timing, once you
run this against a seeded dataset with enough rows to make the sequential
scan visible.)_
