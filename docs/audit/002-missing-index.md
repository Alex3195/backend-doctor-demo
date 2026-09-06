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

Migration `V2__add_orders_customer_index.sql` adds the index. Measured
against the seeded dataset (20000 orders, 5000 customers):

Before (sequential scan):
```
Limit  (cost=417.04..417.05 rows=4 width=38) (actual time=2.177..2.178 rows=2 loops=1)
  ->  Sort  (cost=417.04..417.05 rows=4 width=38) (actual time=2.176..2.177 rows=2 loops=1)
        Sort Key: created_at DESC
        ->  Seq Scan on orders  (cost=0.00..417.00 rows=4 width=38) (actual time=0.629..2.050 rows=2 loops=1)
              Filter: (customer_id = 123)
              Rows Removed by Filter: 19998
Execution Time: 2.196 ms
```

After (bitmap index scan):
```
Limit  (cost=18.55..18.56 rows=4 width=38) (actual time=0.073..0.074 rows=2 loops=1)
  ->  Sort  (cost=18.55..18.56 rows=4 width=38) (actual time=0.072..0.072 rows=2 loops=1)
        Sort Key: created_at DESC
        ->  Bitmap Heap Scan on orders  (cost=4.32..18.51 rows=4 width=38) (actual time=0.035..0.051 rows=2 loops=1)
              Recheck Cond: (customer_id = 123)
              ->  Bitmap Index Scan on idx_orders_customer_created  (cost=0.00..4.32 rows=4 width=0) (actual time=0.026..0.026 rows=2 loops=1)
                    Index Cond: (customer_id = 123)
Execution Time: 0.105 ms
```

### Improvement
- Planner cost: 417 → 18.56.
- Execution time: 2.196ms → 0.105ms (~95% faster).
- Plan changed from `Seq Scan` (scanning all 20000 rows, filtering out
  19998) to `Bitmap Index Scan` on the new composite index.
