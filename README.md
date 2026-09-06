# Backend Doctor Demo

A production-like Spring Boot backend, seeded with realistic data volumes,
used to demonstrate real-world backend performance and architecture problems
— and how to diagnose and fix them.

This is a portfolio / case-study project. Each problem is introduced
deliberately, measured, explained, fixed, and measured again. See
`docs/audit/` for the write-ups.

## Stack

- Java 21
- Spring Boot 3.3 (Web, Data JPA, Actuator, Validation)
- PostgreSQL 16
- Flyway (schema migrations)
- Redis (wired in later, Phase 4)
- Kafka (wired in later, Phase 4)

## Running locally

1. Start Postgres (and Redis, unused for now):

   ```bash
   docker compose up -d
   ```

2. Run the app (IntelliJ IDEA: just run `BackendDoctorApplication`, or):

   ```bash
   ./gradlew bootRun
   ```

   > Note: this project doesn't include the Gradle wrapper jar. Opening it
   > in IntelliJ IDEA as a Gradle project will offer to generate the wrapper
   > automatically — accept that, or run `gradle wrapper` once if you have
   > Gradle installed locally.

3. On first run, `DataSeeder` inserts fake data (see `seed.*` in
   `application.yml` for volumes — defaults are modest so it seeds in
   seconds; scale up later for production-like benchmark numbers).

4. Hit the baseline endpoint:

   ```
   GET http://localhost:8080/api/orders
   ```

   Watch the console (`show-sql: true` is on) — you'll see one `SELECT`
   for the orders, then one additional `SELECT ... FROM customers WHERE id = ?`
   **per distinct customer** in the result set. That's Issue #001.

## Status: Phase 1 complete

- [x] Project structure
- [x] Schema (customers, products, orders, order_items, payments)
- [x] Seed data generator
- [x] Baseline `/api/orders` endpoint with an intentional N+1 query
- [ ] Phase 2: fix N+1 (JOIN FETCH / `@EntityGraph` / DTO projection),
      add missing index, keyset pagination
- [ ] Phase 3: transaction boundaries, concurrency / overselling, thread pool
      vs. connection pool
- [ ] Phase 4: Redis caching, Kafka async processing
- [ ] Phase 5: load testing (JMeter/Gatling), metrics, Grafana
- [ ] Phase 6: before/after benchmark write-up, polished audit report

## Problems catalog (planned)

| # | Problem | Status |
|---|---|---|
| 1 | N+1 query on `/api/orders` | Fixed (`after-n-plus-one-query`) |
| 2 | Missing index on `orders.customer_id` | Introduced, not yet fixed |
| 3 | Offset pagination on large tables | Not yet introduced |
| 4 | Long-held transaction around external call | Not yet introduced |
| 5 | Overselling / race condition on `products.stock` | Not yet introduced |
| 6 | No caching for hot read path | Not yet introduced |
| 7 | Synchronous side effects on order creation | Not yet introduced |
| 8 | Thread pool vs. DB connection pool mismatch | Not yet introduced |
| 9 | Loading full result sets into memory | Not yet introduced |
| 10 | Slow aggregation queries at scale | Not yet introduced |

## Why this project exists

This is the foundation for a backend-performance-audit portfolio and,
eventually, a small consulting offer (see `docs/audit/000-overview.md`).
The goal isn't a "clean CRUD app" — it's a sequence of clearly documented
before/after case studies.
