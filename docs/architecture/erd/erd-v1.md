# ERD v1 — Service Marketplace Database Schema

> 17 tables, organized by domain. Generated for Phase 1.

```mermaid
erDiagram
    users {
        BIGSERIAL id PK
        VARCHAR_255 email UK
        VARCHAR_255 password_hash
        VARCHAR_255 full_name
        VARCHAR_20 phone
        VARCHAR_20 role "CHECK: CUSTOMER | VENDOR | ADMIN"
        VARCHAR_20 status "DEFAULT ACTIVE"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    refresh_tokens {
        BIGSERIAL id PK
        BIGINT user_id FK
        VARCHAR_500 token
        TIMESTAMP expires_at
    }

    vendors {
        BIGSERIAL id PK
        BIGINT user_id FK
        VARCHAR_255 business_name
        TEXT description
        VARCHAR_255 street
        VARCHAR_100 city
        VARCHAR_20 zip_code
        VARCHAR_255 website_url
        DECIMAL rating_avg "DEFAULT 0"
        VARCHAR_20 verification_status "DEFAULT PENDING"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    categories {
        BIGSERIAL id PK
        VARCHAR_100 name
        VARCHAR_100 slug UK
        TEXT description
        BIGINT parent_id FK "self-referencing"
        TIMESTAMP created_at
    }

    vendor_categories {
        BIGINT vendor_id FK "composite PK"
        BIGINT category_id FK "composite PK"
    }

    services {
        BIGSERIAL id PK
        BIGINT vendor_id FK
        BIGINT category_id FK
        VARCHAR_255 name
        TEXT description
        BIGINT base_price_cents "CHECK > 0"
        VARCHAR_20 pricing_type "FIXED | HOURLY | VARIABLE"
        INTEGER duration_minutes
        VARCHAR_20 status "DEFAULT DRAFT"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    service_images {
        BIGSERIAL id PK
        BIGINT service_id FK
        VARCHAR_500 url
        INTEGER display_order
    }

    service_availability {
        BIGSERIAL id PK
        BIGINT service_id FK
        SMALLINT day_of_week "CHECK 0-6"
        TIME start_time
        TIME end_time
    }

    bookings {
        BIGSERIAL id PK
        BIGINT service_id FK
        BIGINT customer_id FK
        BIGINT vendor_id FK
        DATE booking_date
        TIME start_time
        TIME end_time
        VARCHAR_20 status "PENDING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELLED"
        BIGINT total_price_cents
        TEXT notes
        BIGINT version "optimistic lock"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    booking_status_history {
        BIGSERIAL id PK
        BIGINT booking_id FK
        VARCHAR_20 from_status
        VARCHAR_20 to_status
        BIGINT changed_by FK
        TIMESTAMP changed_at
        TEXT reason
    }

    orders {
        BIGSERIAL id PK
        BIGINT customer_id FK
        BIGINT booking_id FK
        VARCHAR_20 status "CREATED | PENDING_PAYMENT | PAID | FULFILLED | CANCELLED | REFUNDED"
        BIGINT subtotal_cents
        BIGINT commission_cents
        BIGINT total_cents
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    payments {
        BIGSERIAL id PK
        BIGINT order_id FK
        VARCHAR_255 stripe_payment_intent_id UK
        BIGINT amount_cents
        VARCHAR_20 status "PENDING | PROCESSING | SUCCEEDED | FAILED"
        VARCHAR_50 payment_method
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    refunds {
        BIGSERIAL id PK
        BIGINT payment_id FK
        BIGINT amount_cents
        TEXT reason
        VARCHAR_20 status "PENDING | SUCCEEDED | FAILED"
        VARCHAR_255 stripe_refund_id
        TIMESTAMP created_at
    }

    stripe_event_log {
        VARCHAR_255 stripe_event_id PK
        VARCHAR_100 event_type
        TIMESTAMP processed_at
    }

    reviews {
        BIGSERIAL id PK
        BIGINT booking_id FK UK
        BIGINT customer_id FK
        BIGINT vendor_id FK
        BIGINT service_id FK
        INTEGER rating "CHECK 1-5"
        TEXT comment
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    notifications {
        BIGSERIAL id PK
        BIGINT user_id FK
        VARCHAR_50 type
        VARCHAR_255 title
        TEXT message
        BOOLEAN is_read "DEFAULT false"
        TIMESTAMP created_at
    }

    audit_logs {
        BIGSERIAL id PK
        VARCHAR_50 entity_type
        BIGINT entity_id
        VARCHAR_20 action "INSERT | UPDATE | DELETE"
        JSONB old_values
        JSONB new_values
        BIGINT performed_by FK
        TIMESTAMP performed_at
    }

    users ||--o{ refresh_tokens : "has"
    users ||--o| vendors : "has"
    vendors ||--o{ vendor_categories : "belongs to"
    categories ||--o{ vendor_categories : "has"
    categories ||--o{ categories : "parent-child"
    vendors ||--o{ services : "offers"
    categories ||--o{ services : "categorized in"
    services ||--o{ service_images : "has"
    services ||--o{ service_availability : "has"
    services ||--o{ bookings : "booked via"
    users ||--o{ bookings : "customer books"
    vendors ||--o{ bookings : "vendor receives"
    bookings ||--o{ booking_status_history : "tracks"
    bookings ||--|| orders : "generates"
    users ||--o{ orders : "places"
    orders ||--o{ payments : "has"
    payments ||--o{ refunds : "may have"
    bookings ||--o| reviews : "may have"
    users ||--o{ notifications : "receives"
```

## Unique Constraints

| Table | Constraint | Purpose |
|-------|-----------|---------|
| `users` | `UNIQUE (email)` | No duplicate emails |
| `categories` | `UNIQUE (slug)` | Clean URLs |
| `bookings` | `UNIQUE (service_id, booking_date, start_time)` | Prevent double-booking |
| `payments` | `UNIQUE (stripe_payment_intent_id)` | Idempotent webhook |
| `stripe_event_log` | `PK (stripe_event_id)` | Idempotent event processing |
| `reviews` | `UNIQUE (booking_id)` | One review per booking |

## Indexes (non-unique)

| Table | Column(s) | Purpose |
|-------|----------|---------|
| `users` | `email` | Login lookup |
| `services` | `vendor_id` | Vendor dashboard |
| `services` | `category_id` | Category browsing |
| `services` | `status` | Active services filter |
| `bookings` | `vendor_id, booking_date` | Vendor schedule |
| `bookings` | `customer_id` | Customer history |
| `notifications` | `user_id, is_read` | Unread count |
| `audit_logs` | `entity_type, entity_id` | Entity audit trail |
