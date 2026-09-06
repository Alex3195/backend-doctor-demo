# Issue #004 — Long-Held Transaction Around an External Call

Severity: HIGH

### Before
- Symptom: `OrderCreationService.createOrderLongTransaction()` is
  `@Transactional` end to end, including the call to
  `PaymentGatewayClient.charge()` -- a simulated third-party network
  call with ~300ms of latency. The DB connection checked out of the
  (intentionally small, `maximum-pool-size: 20`) Hikari pool is held
  for the whole method, including those 300ms where the database
  itself has nothing to do.
- Load test: fired N concurrent `POST /api/orders` requests (distinct
  customer/product per request, so this isn't measuring the stock
  race from Issue #005) and measured total wall-clock time:

  | Concurrent requests | Total time |
  |---|---|
  | 1 | 0.48s |
  | 40 | 0.80s |
  | 100 | 1.74s |

  Time scales with `ceil(requests / 20) * ~300ms` -- i.e. with how
  many times the connection pool has to "reload" in batches, not with
  the actual DB work (a few ms per request). At 100 concurrent
  requests that's 5 batches of 20.

### Root cause
The external payment call sits inside the same `@Transactional`
boundary as the DB writes, so the connection is checked out for the
external call's latency too. A resource meant to be held for
milliseconds ends up held for hundreds of milliseconds, and the pool
-- sized for DB work, not third-party latency -- exhausts far sooner
than it should under concurrent load.

### Fix
Splitting the transaction turned out to need **two** changes, not one
-- the first one alone didn't work, which is worth documenting because
it's a real, easy-to-miss trap.

**Attempt 1 (incomplete): split into two short transactions.**
1. `OrderTransactionService.reserveOrder()` (`@Transactional`) --
   decrements stock, inserts the order (`PENDING_PAYMENT`) and its
   item.
2. `OrderCreationService.createOrderFast()` (NOT transactional) --
   calls `reserveOrder()`, then `paymentGatewayClient.charge()`,
   then `finalizeOrder()`.
3. `OrderTransactionService.finalizeOrder()` (`@Transactional`) --
   updates the order to `PAID`/`CANCELLED`, inserts the `Payment` row.

   (`reserveOrder()`/`finalizeOrder()` had to live in a *separate*
   Spring bean from the orchestrating method -- calling
   `this.reserveOrder()` from within the same class bypasses Spring's
   proxy-based `@Transactional`, the well-known self-invocation gotcha,
   and the two writes wouldn't be atomic.)

   Re-running the load test after this change alone: **no
   improvement** -- 40 concurrent still took ~0.76s, same batching
   pattern as before. Splitting the transaction didn't actually
   shorten how long the connection was held.

**Root cause of attempt 1 not working:** `spring.jpa.open-in-view`
defaults to `true`. It keeps a Hibernate session -- and the physical
JDBC connection bound to it -- open for the **entire HTTP request**,
not just inside `@Transactional`. So even with two short transactions,
the connection stayed checked out across the whole request, external
call included, exactly as before.

**Attempt 2 (the actual fix): also set `spring.jpa.open-in-view: false`.**
With OSIV off, the connection really is released the moment each
`@Transactional` method returns. This immediately surfaced a second,
related bug: `order.getCustomer()` is `FetchType.LAZY`, and the
controller was reading `order.getCustomer().getFullName()` *after*
`finalizeOrder()` returned -- with no session left to lazily load it,
every concurrent request started throwing
`LazyInitializationException: could not initialize proxy ... no
Session` (a real bug caught mid-benchmark, not a hypothetical one).
Fixed by calling `Hibernate.initialize(order.getCustomer())` at the
end of `finalizeOrder()`, while its transaction (and session) is still
open.

### After
Same load test against `POST /api/orders/fast-checkout`, `open-in-view: false`,
all requests returning `201` (no more `LazyInitializationException`):

| Concurrent requests | Total time |
|---|---|
| 1 | ~0.34s |
| 40 | ~0.37s (all requests finish within a tight ~0.31-0.37s band -- no batching) |
| 100 | ~0.84s |

### Improvement
- 40 concurrent: 0.80s → 0.37s (~54% faster), and the batching pattern
  (some requests ~2x slower than others) is gone entirely.
- 100 concurrent: 1.74s → 0.84s (~52% faster).
- The real lesson isn't "split the transaction" by itself -- it's that
  `open-in-view` can silently undo that split, and turning it off
  requires auditing every lazy field touched outside a
  `@Transactional` method (one such bug was caught here by the load
  test itself).
