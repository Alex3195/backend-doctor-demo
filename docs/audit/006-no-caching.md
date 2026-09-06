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
Redis cache enabled.
- SQL queries: 1 (only the first request; confirmed via
  `hibernate.generate_statistics` and by inspecting the key directly
  with `redis-cli GET products::<id>`).
- Total time: 0.183s (~3.7ms/request average).

### Improvement
- SQL queries: ~48 → 1 (~98% fewer DB round-trips for the same
  traffic pattern).
- Total time: 0.719s → 0.183s (~75% faster) for 50 requests, even
  against a local Postgres instance with sub-millisecond query times --
  the gap would be far larger against a real network-hop database.
- Trade-off: a 5 minute staleness window on product data (price/stock
  shown to a client could be up to 5 minutes old). Acceptable for a
  product detail read; would need a shorter TTL or explicit
  cache-eviction on write for something like stock that changes
  under load (see Issue #005) -- not addressed here, flagged for
  awareness.
