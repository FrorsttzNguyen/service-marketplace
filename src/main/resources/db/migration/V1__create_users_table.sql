-- =============================================================================
-- V1: Users & Refresh Tokens
-- Core authentication tables. Every user has one role: CUSTOMER, VENDOR, or ADMIN.
-- =============================================================================

CREATE TABLE users (
    id              BIGSERIAL           PRIMARY KEY,
    email           VARCHAR(255)        NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,               -- BCrypt hash, never store plaintext
    full_name       VARCHAR(255)        NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20)         NOT NULL
                    CHECK (role IN ('CUSTOMER', 'VENDOR', 'ADMIN')),
    status          VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL           PRIMARY KEY,
    user_id         BIGINT              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500)        NOT NULL,
    expires_at      TIMESTAMP           NOT NULL
);

-- Index: login lookup by email (runs every authentication)
CREATE INDEX idx_users_email ON users(email);

-- Index: find active tokens for a user
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
