-- =============================================================================
-- V15: Rename Vendor domain tables/columns to Provider
-- Data-safe rename only: no drops, no recreated foreign keys, no data movement.
-- Keep role/user enum values such as VENDOR unchanged; this migration only renames
-- provider profile tables, relationship columns, constraints, and indexes.
-- =============================================================================

ALTER TABLE vendors RENAME TO providers;
ALTER TABLE vendor_categories RENAME TO provider_categories;

ALTER TABLE provider_categories RENAME COLUMN vendor_id TO provider_id;
ALTER TABLE services RENAME COLUMN vendor_id TO provider_id;
ALTER TABLE bookings RENAME COLUMN vendor_id TO provider_id;
ALTER TABLE reviews RENAME COLUMN vendor_id TO provider_id;

-- Primary key index renames also rename the matching primary key constraints in PostgreSQL.
ALTER INDEX vendors_pkey RENAME TO providers_pkey;
ALTER INDEX vendor_categories_pkey RENAME TO provider_categories_pkey;

ALTER TABLE providers
    RENAME CONSTRAINT vendors_user_id_fkey TO providers_user_id_fkey;

ALTER TABLE provider_categories
    RENAME CONSTRAINT vendor_categories_vendor_id_fkey TO provider_categories_provider_id_fkey;

ALTER TABLE provider_categories
    RENAME CONSTRAINT vendor_categories_category_id_fkey TO provider_categories_category_id_fkey;

ALTER TABLE services
    RENAME CONSTRAINT services_vendor_id_fkey TO services_provider_id_fkey;

ALTER TABLE bookings
    RENAME CONSTRAINT bookings_vendor_id_fkey TO bookings_provider_id_fkey;

ALTER TABLE reviews
    RENAME CONSTRAINT reviews_vendor_id_fkey TO reviews_provider_id_fkey;

ALTER INDEX idx_vendors_user_id RENAME TO idx_providers_user_id;
ALTER INDEX idx_services_vendor_id RENAME TO idx_services_provider_id;
ALTER INDEX idx_bookings_vendor_date RENAME TO idx_bookings_provider_date;
ALTER INDEX idx_reviews_vendor_id RENAME TO idx_reviews_provider_id;
