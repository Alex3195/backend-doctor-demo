-- Fixes Issue #010: no index supports filtering orders by created_at
-- alone (the composite index from V2 only helps when also filtering
-- by customer_id). Date-range aggregations like the daily revenue
-- report fall back to a sequential scan without this.
CREATE INDEX idx_orders_created_at ON orders(created_at);
