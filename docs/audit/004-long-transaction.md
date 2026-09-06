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
Split the flow into two short transactions around the external call,
which now happens with **no DB connection held**:
1. `OrderTransactionService.reserveOrder()` (`@Transactional`) --
   decrements stock, inserts the order (`PENDING_PAYMENT`) and its
   item. Commits and releases the connection immediately.
2. `OrderCreationService.createOrderFast()` (NOT transactional) --
   calls `reserveOrder()`, then `paymentGatewayClient.charge()`
   outside of any transaction, then `finalizeOrder()`.
3. `OrderTransactionService.finalizeOrder()` (`@Transactional`) --
   updates the order to `PAID`/`CANCELLED` and inserts the `Payment`
   row. Another short transaction.

Note: `reserveOrder()` and `finalizeOrder()` had to live in a
*separate* Spring bean (`OrderTransactionService`) from the
orchestrating method. Calling `this.reserveOrder()` from within the
same class bypasses Spring's proxy-based `@Transactional` (the
well-known self-invocation gotcha) -- the annotation would be silently
ignored and the two writes in `reserveOrder()` wouldn't be atomic.

### After
Same load test against `POST /api/orders/fast-checkout`:

| Concurrent requests | Total time |
|---|---|
| 1 | _(fill in once benchmarked)_ |
| 40 | _(fill in once benchmarked)_ |
| 100 | _(fill in once benchmarked)_ |

### Improvement
_(fill in once benchmarked)_
