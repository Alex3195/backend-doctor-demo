# Issue #006 — No Caching for a Hot Read Path

Severity: MEDIUM

### Before
- Symptom: `GET /api/products/{id}` (`ProductService.getProductById()`)
  hits Postgres on every single call, even for the same product ID
  requested over and over -- a classic hot-path pattern (product detail
  pages, cart/checkout re-reads, etc. all hammer the same handful of
  popular IDs).
- Measured: 50 sequential requests for the same product ID.
  - SQL queries: 46-50 (essentially one `SELECT ... FROM products`
    per request -- verified via `hibernate.generate_statistics` /
    `show-sql` console output).
  - Total time: 0.719s (~14.4ms/request average).
- Redis is already running (`docker-compose.yml`) but completely
  unused up to this point -- exactly as flagged in the README
  ("Redis (wired in later, Phase 4)").

### Root cause
No caching layer between the controller and the database for reads
that are disproportionately repeated relative to how often the
underlying data actually changes.

### Fix
- Added `spring-boot-starter-data-redis` + `spring-boot-starter-cache`.
- `CacheConfig` configures a `RedisCacheConfiguration` with a 5 minute
  TTL and JSON serialization (`GenericJackson2JsonRedisSerializer`) --
  deliberately not JDK serialization, so cached values don't require
  entities/DTOs to implement `Serializable` and stay human-readable if
  you inspect Redis directly.
- `ProductService.getProductById()` annotated `@Cacheable(value =
  "products", key = "#id")`: first call for a given ID hits Postgres
  and populates the cache; every subsequent call for that ID is served
  from Redis with zero DB queries, until the 5 minute TTL expires.

### After
Same measurement: 50 sequential requests for the same product ID,
Redis cache enabled. Confirmed the key directly too:
`redis-cli GET products::1` returns the cached
`ProductSummary` JSON.

- First 50-request batch right after startup (cold cache + first-ever
  Redis connection): 1 SQL query (the initial cache miss), but total
  time was 1.045s -- *slower* than the uncached baseline, because that
  batch also pays the one-time cost of establishing the Lettuce/Redis
  connection.
- Second batch, same product ID, cache and connection now warm:
  **0 SQL queries**, total time **0.612s**.

### Improvement
- SQL queries: ~46-50 → 0 (100% fewer DB round-trips once the entry is
  cached) -- this is the real win, not raw latency.
- Total time (steady state): 0.719s → 0.612s (~15% faster). Modest,
  and it's worth saying plainly why: against a local Postgres with
  sub-millisecond query times, the request/response and JSON
  serialization overhead dominates, not the DB round-trip -- so on
  this machine caching mostly buys *DB load reduction*, not dramatically
  lower per-request latency. Against a real network-hop database (the
  common case in production), the same 100% query reduction would
  translate into a much larger latency win.
- Trade-off: a 5 minute staleness window on product data (price/stock
  shown to a client could be up to 5 minutes old). Acceptable for a
  product detail read; would need a shorter TTL or explicit
  cache-eviction on write for something like stock that changes
  under load (see Issue #005) -- not addressed here, flagged for
  awareness.
