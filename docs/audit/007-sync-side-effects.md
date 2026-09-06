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
_(fill in once benchmarked)_

### Improvement
_(fill in once benchmarked)_
