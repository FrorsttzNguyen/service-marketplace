-- Add unique constraint to payments.order_id
-- Ensures only one payment per order at database level
-- This catches race conditions where two concurrent requests
-- both pass the application-level duplicate check

ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
