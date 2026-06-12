-- =============================================================================
-- V7: Add optimistic locking to payments table
-- @Version for concurrent webhook updates (multiple events for same PaymentIntent)
-- Index for payment status queries (webhook lookups)
-- =============================================================================

-- Add version column for optimistic locking
-- WHY: Stripe webhooks can send multiple events concurrently for same PaymentIntent
-- Example: payment_intent.succeeded + payment_intent.amount_capturable_updated
-- @Version catches concurrent updates, preventing lost updates
ALTER TABLE payments ADD COLUMN version BIGINT DEFAULT 0;

-- Add index for payment status queries
-- WHY: Webhook handlers often query by status to find payments needing processing
-- Example: Find all PROCESSING payments to check if payment_intent has expired
CREATE INDEX idx_payments_status ON payments(status);

-- Add index for order_id + status combined queries
-- WHY: Check if order already has a payment in specific status
-- Example: Prevent duplicate payment creation for same order
CREATE INDEX idx_payments_order_status ON payments(order_id, status);