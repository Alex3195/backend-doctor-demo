-- Fixes Issue #002: missing index on orders.customer_id.
-- Composite index covers both the equality filter and the ORDER BY,
-- so "WHERE customer_id = ? ORDER BY created_at DESC" is answered by
-- an index scan instead of a sequential scan.
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);
