# Issue #007 — Synchronous Side Effects on Order Creation

Severity: MEDIUM

### Before
- Symptom: `OrderCreationService.createOrderFast()` calls
  `NotificationService.sendOrderConfirmation()` (a simulated ~250ms
  email/SMS send) synchronously, and blocks the HTTP response on it,
  right after the order is already `PAID` in the database. Whether the
  confirmation notification succeeds, fails, or is slow has no bearing
  on whether the order itself was created -- the caller doesn't need
  to wait for it.
- Measured `POST /api/orders/fast-checkout` (single requests, warm):
  ~0.58-0.59s (≈300ms simulated payment + ≈250ms simulated
  notification + overhead).
- 20 concurrent requests: 0.667s total (no pool/thread bottleneck at
  this scale yet -- the cost here is pure added latency per request,
  not contention).

### Root cause
A side effect unrelated to the transactional outcome (sending a
notification) is bolted onto the same request/response cycle as the
outcome itself, so its latency (and its failure modes -- what if the
notification provider times out?) become the caller's problem too.

### Fix
Publish an `OrderCreatedEvent` to Kafka right after the order is
finalized -- `KafkaTemplate.send()` just enqueues the message and
returns, it doesn't wait for a consumer. A separate
`@KafkaListener` (`OrderCreatedEventListener`) consumes the event on
its own thread, completely decoupled from the HTTP request, and calls
`NotificationService.sendOrderConfirmation()` there instead.

### After
Same measurement, notification now delivered via Kafka
(`OrderCreatedEventListener`, confirmed processing on the Kafka
consumer thread -- `Sent order confirmation for order ...` logged
*after* the HTTP response had already been returned to the caller):

- Single requests (warm): ~0.335s (down from ~0.58-0.59s) -- exactly
  the ~250ms notification latency is gone from the response path.
- 20 concurrent requests: 0.436s total (down from 0.667s).

### Improvement
- Single-request latency: ~0.58s → ~0.335s (~42% faster).
- 20 concurrent: 0.667s → 0.436s (~35% faster).
- The order's own success/failure no longer depends on, or waits for,
  an unrelated side effect. A slow or temporarily-down notification
  provider now only delays the notification -- it can no longer delay
  or fail the order-creation response itself.
