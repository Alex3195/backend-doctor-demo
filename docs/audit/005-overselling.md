# Issue #005 — Overselling / Race Condition on products.stock

Severity: HIGH

### Before
- Symptom: `OrderTransactionService.reserveOrder()` does a classic
  check-then-act on stock:
  ```java
  if (product.getStock() < request.quantity()) throw ...;
  product.setStock(product.getStock() - request.quantity());
  productRepository.save(product);
  ```
  Hibernate's generated `UPDATE` is a blind assignment
  (`UPDATE products SET stock = ? WHERE id = ?`) using the value
  computed in Java memory from whatever was read at the start of the
  transaction -- not a relative `stock = stock - ?`. Concurrent
  requests for the same product can all read the same stock value
  before any of them commits, so they all pass the check and all
  compute the same "new" value.
- Reproduced directly: seeded a product with `stock = 10`, then fired
  **20 concurrent** `POST /api/orders/fast-checkout` requests for that
  product (quantity 1 each, distinct customers).
  - Result: **all 20 requests returned `201 PAID`** -- i.e. 20 units
    sold against a stock of 10.
  - Final `stock` in the DB: **9** (not 0, and not negative either).
    This is the "lost update" anomaly: each of the 20 transactions
    read `stock = 10`, computed `10 - 1 = 9` independently, and
    whichever transaction's `UPDATE` happened to commit last simply
    overwrote the row with its own stale `9`, discarding every other
    transaction's decrement. The stock counter barely moves at all
    even though it was oversold 2x.

### Root cause
Read-then-write with no atomicity or locking across the read and the
write: two concurrent transactions can both observe the pre-decrement
value, so neither guards against the other, and Hibernate's plain
`UPDATE ... SET stock = <value>` doesn't express "relative to whatever
is currently there" -- it just overwrites.

### Fix
Replace the read-then-write with a single atomic, conditional SQL
statement -- exactly the fix already sketched in `Product.java`'s own
comment:
```sql
UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty
```
Implemented as `ProductRepository.decrementStockIfAvailable(id, qty)`,
a `@Modifying @Query` returning the number of rows updated: `1` if the
decrement succeeded, `0` if stock was insufficient (checked and
decremented in the same statement, evaluated by Postgres against the
current committed value, not a stale in-memory one). `reserveOrder()`
now calls this instead of reading + computing + saving, and rejects
the order if it returns `0`.

### After
Same reproduction: seeded a fresh product with `stock = 10`, fired 20
concurrent requests.
- Result: exactly **10 requests returned `201 PAID`**, the other
  **10 returned `409 CONFLICT`** ("Not enough stock").
- Final `stock` in the DB: **0**.

### Improvement
- Correctness: 20/20 oversold (100% over capacity) → exactly 10/10
  fulfilled, 0 oversold.
- No performance cost -- it's still a single `UPDATE` statement, just
  a conditional, relative one instead of an unconditional, absolute
  one. No pessimistic locking (`SELECT ... FOR UPDATE`) or extra
  round-trips needed.
