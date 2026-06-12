-- =============================================================================
-- V8: Add city and average_rating to services table
-- Denormalized fields for efficient filtering/sorting
-- =============================================================================

-- Add city column (denormalized from vendor.address.city)
-- NULL initially, will be backfilled from existing vendors
ALTER TABLE services
    ADD COLUMN city VARCHAR(100);

-- Add average_rating column (denormalized from reviews aggregation)
-- NULL means no reviews yet (different from 0.00 which would be actual average)
ALTER TABLE services
    ADD COLUMN average_rating DECIMAL(3, 2);

-- Index for city filter (common query: "services in city X")
CREATE INDEX idx_services_city ON services(city);

-- Index for rating filter/sort (common query: "services with rating >= X")
CREATE INDEX idx_services_average_rating ON services(average_rating);

-- Backfill city from vendors table
-- Only update services where vendor has an address with city
UPDATE services s
SET city = v.city
FROM vendors v
WHERE s.vendor_id = v.id
  AND v.city IS NOT NULL;

-- Backfill average_rating from reviews aggregation
UPDATE services s
SET average_rating = subq.avg_rating
FROM (
    SELECT service_id, AVG(rating) AS avg_rating
    FROM reviews
    GROUP BY service_id
) subq
WHERE s.id = subq.service_id;
