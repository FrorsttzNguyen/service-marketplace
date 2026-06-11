-- =============================================================================
-- V2: Vendors, Categories & Vendor-Categories
-- Vendor profiles linked to users (composition, not inheritance).
-- Categories support parent-child hierarchy. M:N via vendor_categories.
-- =============================================================================

CREATE TABLE vendors (
    id                      BIGSERIAL       PRIMARY KEY,
    user_id                 BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name           VARCHAR(255)    NOT NULL,
    description             TEXT,
    street                  VARCHAR(255),
    city                    VARCHAR(100),
    zip_code                VARCHAR(20),
    website_url             VARCHAR(255),
    rating_avg              DECIMAL(3,2)    NOT NULL DEFAULT 0.00
                            CHECK (rating_avg >= 0 AND rating_avg <= 5),
    verification_status     VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                            CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- One user can have at most one vendor profile
CREATE UNIQUE INDEX idx_vendors_user_id ON vendors(user_id);

CREATE TABLE categories (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL UNIQUE,
    description     TEXT,
    parent_id       BIGINT          REFERENCES categories(id) ON DELETE SET NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: fast lookup by slug (used in URLs like /services?category=spa)
CREATE INDEX idx_categories_slug ON categories(slug);

-- Index: find sub-categories of a parent
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

CREATE TABLE vendor_categories (
    vendor_id       BIGINT          NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    category_id     BIGINT          NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (vendor_id, category_id)
);
