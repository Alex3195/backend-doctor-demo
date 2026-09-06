# Issue #003 — Offset Pagination on Large Tables

Severity: MEDIUM

### Before
- Symptom: `GET /api/orders/page?page=&size=` uses Spring Data
  `Pageable`, which Postgres executes as
  `ORDER BY id DESC OFFSET ? LIMIT ?`. Postgres must scan and discard
  every row up to the offset before it can return a page, so cost
  grows with page depth.
- SQL queries: 1 per request (that part's fine) — the problem is the
  cost of that one query at depth.
- Response time / plan, measured with `EXPLAIN ANALYZE` against the
  seeded dataset (20000 orders, 5000 customers):

  Page 0 (`OFFSET 0 LIMIT 20`):
  ```
  Limit  (cost=0.58..3.32 rows=20 width=45) (actual time=0.046..0.221 rows=20 loops=1)
    ->  Nested Loop  (cost=0.58..2741.89 rows=20000 width=45) (actual time=0.045..0.219 rows=20 loops=1)
          ->  Index Scan Backward using orders_pkey on orders o
  Execution Time: 0.588 ms
  ```

  Last page (`OFFSET 19980 LIMIT 20`):
  ```
  Limit  (cost=2068.77..2068.82 rows=20 width=45) (actual time=11.541..11.544 rows=20 loops=1)
    ->  Sort  (cost=2018.82..2068.82 rows=20000 width=45) (actual time=10.481..11.083 rows=20000 loops=1)
          ->  Hash Join  (cost=170.50..590.04 rows=20000 width=45) (actual time=1.273..5.483 rows=20000 loops=1)
                ->  Seq Scan on orders o (cost=0.00..367.00 rows=20000 width=38) (actual time=0.003..1.104 rows=20000 loops=1)
                ->  Hash (... Seq Scan on customers c ...)
  Execution Time: 11.701 ms
  ```

  The planner abandons the index-scan-and-skip approach once the
  offset is large enough that a full seq scan + sort is cheaper —
  ~20x slower than page 0, on a table of only 20000 rows.

### Root cause
`OFFSET` pagination requires the database to materialize and discard
every row before the offset. Cost is O(offset), not O(page size), so
it degrades as users page deeper — and gets worse, not better, as the
table grows.

### Fix
Add keyset (cursor) pagination alongside the offset endpoint: instead
of "skip N rows", ask for "rows after the last one I saw"
(`WHERE id < :cursor ORDER BY id DESC LIMIT :size`). Since `id` is the
primary key, this is answered by an index range scan regardless of
how deep the cursor is.

### After
Keyset equivalent of the same deep page (`WHERE id < 21 ORDER BY id DESC LIMIT 20`):
```
Limit  (cost=0.57..138.64 rows=20 width=45) (actual time=0.031..0.105 rows=20 loops=1)
  ->  Nested Loop  (cost=0.57..138.64 rows=20 width=45) (actual time=0.030..0.102 rows=20 loops=1)
        ->  Index Scan Backward using orders_pkey on orders o
              Index Cond: (id < 21)
Execution Time: 0.129 ms
```

### Improvement
- Execution time at depth: 11.701ms → 0.129ms (~99% faster) — and
  unlike offset, this stays flat (O(1) relative to depth) no matter
  how far into the table the cursor points, because it's always an
  index range scan instead of a growing seq scan + sort.
- Trade-off: keyset pagination can't jump to an arbitrary page number
  (only "next"/"previous" from a cursor) — fine for infinite-scroll /
  API-consumer pagination, not for a "jump to page 47" UI.
