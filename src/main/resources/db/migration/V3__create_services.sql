-- =============================================================================
-- V3: Services, Service Images & Service Availability
-- Core catalog entity. Price stored as cents (BIGINT) to avoid float issues.
-- PricingType determines calculation: FIXED, HOURLY, or VARIABLE.
-- =============================================================================

CREATE TABLE services (
    id                  BIGSERIAL       PRIMARY KEY,
    vendor_id           BIGINT          NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    category_id         BIGINT          REFERENCES categories(id) ON DELETE SET NULL,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    base_price_cents    BIGINT          NOT NULL CHECK (base_price_cents > 0),
    pricing_type        VARCHAR(20)     NOT NULL DEFAULT 'FIXED'
                        CHECK (pricing_type IN ('FIXED', 'HOURLY', 'VARIABLE')),
    duration_minutes    INTEGER         NOT NULL DEFAULT 60,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: vendor dashboard - list all services of a vendor
CREATE INDEX idx_services_vendor_id ON services(vendor_id);

-- Index: browse services by category
CREATE INDEX idx_services_category_id ON services(category_id);

-- Index: filter active services only
CREATE INDEX idx_services_status ON services(status);

-- Composite: common query "active services in category"
CREATE INDEX idx_services_category_status ON services(category_id, status);

CREATE TABLE service_images (
    id              BIGSERIAL       PRIMARY KEY,
    service_id      BIGINT          NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    url             VARCHAR(500)    NOT NULL,
    display_order   INTEGER         NOT NULL DEFAULT 0
);

CREATE INDEX idx_service_images_service_id ON service_images(service_id);

CREATE TABLE service_availability (
    id              BIGSERIAL       PRIMARY KEY,
    service_id      BIGINT          NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    day_of_week     SMALLINT        NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    CHECK (start_time < end_time)
);

CREATE INDEX idx_service_availability_service_id ON service_availability(service_id);
