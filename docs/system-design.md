# System Design — Service Marketplace

## 1. Problem Statement

Build a multi-vendor service booking platform where:
- **Vendors** list services with pricing, availability, and location
- **Customers** discover, book, and pay for services
- **Platform** manages bookings, payments, commissions, and dispute resolution

## 2. Requirements

### Functional Requirements
- User registration & authentication (Customer, Vendor, Admin roles)
- Vendor onboarding with verification flow
- Service CRUD with categories, images, pricing models
- Real-time availability & time-slot management
- Booking with conflict prevention (double-booking)
- Payment via Stripe (hold → capture → refund)
- Review & rating system
- Notification (email for key events)
- Admin moderation & analytics

### Non-Functional Requirements
- **Consistency**: No double-booking under concurrent requests
- **Idempotency**: Payment webhooks processed exactly once
- **Performance**: Service search < 200ms P95 (with caching)
- **Security**: JWT auth, RBAC, input validation, SQL injection prevention
- **Auditability**: All financial transactions logged

## 3. Architecture Overview

### High-Level (C4 Context Level)

```
                    ┌──────────────┐
                    │   Customer   │
                    │  (Browser)   │
                    └──────┬───────┘
                           │ HTTPS
                           ▼
                    ┌──────────────┐         ┌──────────────┐
                    │              │────────▶│    Stripe    │
                    │   Service    │◀────────│   Payment    │
                    │ Marketplace  │ webhook │    API       │
                    │   (Spring    │         └──────────────┘
                    │    Boot)     │
                    │              │────────▶┌──────────────┐
                    │              │         │    Redis     │
                    │              │◀────────│   (Cache)    │
                    └──────┬───────┘         └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  PostgreSQL  │
                    │  (Database)  │
                    └──────────────┘
                           ▲
                    ┌──────┴───────┐
                    │   Vendor     │
                    │  (Browser)   │
                    └──────────────┘
```

### Architecture Style

**Modular Monolith** — Single deployable unit with well-defined module boundaries.

Why not microservices:
- Single developer, no need for independent deployment
- No operational complexity overhead
- Can extract modules later when needed (documented in ADR)
- A well-structured monolith demonstrates better engineering judgment than premature microservices

### Module Boundaries

```
com.hien.marketplace
├── domain/
│   ├── user/          # User, Role, Authentication
│   ├── vendor/        # Vendor profile, verification
│   ├── service/       # Service catalog, categories, pricing
│   ├── booking/       # Booking, time slots, availability
│   ├── order/         # Order aggregation, order items
│   ├── payment/       # Payment, refund, Stripe integration
│   ├── notification/  # Email/push notifications
│   └── common/        # Shared value objects (Money, Address)
├── application/       # Use case orchestration, DTOs
├── infrastructure/    # External service adapters
└── interfaces/        # REST controllers
```

## 4. Data Model

### Core Entities

```sql
-- Users & Auth
users (id, email, password_hash, full_name, phone, role, status, created_at, updated_at)
refresh_tokens (id, user_id, token, expires_at)

-- Vendor
vendors (id, user_id, business_name, description, address, rating_avg, verification_status, created_at)
vendor_categories (id, vendor_id, category_id)
categories (id, name, slug, parent_id)

-- Services
services (id, vendor_id, category_id, name, description, base_price_cents, pricing_type, duration_minutes, status, created_at)
service_images (id, service_id, url, display_order)
service_availability (id, service_id, day_of_week, start_time, end_time)

-- Bookings
bookings (id, service_id, customer_id, vendor_id, booking_date, start_time, end_time, status, total_price_cents, notes, version, created_at)
booking_status_history (id, booking_id, from_status, to_status, changed_by, changed_at, reason)

-- Orders & Payments
orders (id, customer_id, booking_id, status, subtotal_cents, commission_cents, total_cents, created_at)
payments (id, order_id, stripe_payment_intent_id, amount_cents, status, payment_method, created_at)
refunds (id, payment_id, amount_cents, reason, status, stripe_refund_id, created_at)

-- Reviews
reviews (id, booking_id, customer_id, vendor_id, rating, comment, created_at)

-- Notifications
notifications (id, user_id, type, title, message, read, created_at)

-- Audit
audit_logs (id, entity_type, entity_id, action, old_values, new_values, performed_by, performed_at)
```

### Key Design Decisions
- **Money as cents (BIGINT)**: Avoid floating-point precision issues
- **Optimistic locking** (`version` column on `bookings`): Prevent double-booking
- **Status history tables**: Audit trail for state transitions
- **Soft delete** where appropriate: Never lose financial data
- **Polymorphic audit_logs**: Single table for all entity changes

## 5. Key Flows

### 5.1 Booking Flow

```
Customer selects service
    → System checks availability (Redis cache → DB fallback)
    → Customer selects time slot
    → System creates Booking (PENDING) with optimistic lock
    → Customer confirms → Order created
    → Stripe Checkout Session initiated
    → Customer pays → Stripe webhook received
    → Payment recorded → Booking status → CONFIRMED
    → Notification sent to Vendor & Customer
    → Service date arrives → Booking status → IN_PROGRESS
    → Service completed → Booking status → COMPLETED
    → Customer can leave review
    → Vendor payout recorded (platform commission deducted)
```

### 5.2 Payment Flow

```
Order created
    → Backend creates Stripe PaymentIntent
    → Returns client_secret to frontend
    → Frontend confirms payment via Stripe.js
    → Stripe sends webhook (payment_intent.succeeded)
    → Backend verifies webhook signature
    → Idempotent processing (check payment_intent_id not already processed)
    → Update Payment status → SUCCEEDED
    → Update Order status → PAID
    → Update Booking status → CONFIRMED
    → Record commission (platform_fee = total * commission_rate)
```

### 5.3 Concurrency Handling (Double-Booking Prevention)

```
Thread A: Check slot available → Yes → Create booking
Thread B: Check slot available → Yes → Create booking
                                        ↑ RACE CONDITION

Solution: Optimistic Locking
1. Booking entity has @Version field
2. Both threads read same version
3. First thread saves → version incremented
4. Second thread saves → OptimisticLockException
5. Application retries or returns error to user
```

## 6. API Design (Key Endpoints)

```
# Auth
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh

# Services (Public)
GET    /api/services?category=&search=&price_min=&price_max=&rating=&page=&size=
GET    /api/services/{id}

# Vendor Services (Vendor only)
POST   /api/vendors/{vendorId}/services
PUT    /api/vendors/{vendorId}/services/{serviceId}
DELETE /api/vendors/{vendorId}/services/{serviceId}

# Bookings
POST   /api/bookings                    # Customer creates booking
GET    /api/bookings?status=&page=      # Customer's bookings
GET    /api/vendors/{vendorId}/bookings # Vendor's bookings
PATCH  /api/bookings/{id}/cancel        # Cancel booking

# Payments
POST   /api/orders/{orderId}/pay        # Initiate payment
POST   /api/webhooks/stripe             # Stripe webhook
POST   /api/orders/{orderId}/refund     # Request refund

# Reviews
POST   /api/bookings/{bookingId}/reviews
GET    /api/vendors/{vendorId}/reviews

# Vendor Dashboard
GET    /api/vendors/{vendorId}/dashboard/earnings
GET    /api/vendors/{vendorId}/dashboard/bookings
```

## 7. Caching Strategy

| Data | Cache Key | TTL | Invalidation |
|------|-----------|-----|-------------|
| Service catalog | `services:category:{id}` | 5 min | On service CRUD |
| Service detail | `service:{id}` | 10 min | On service update |
| Vendor profile | `vendor:{id}` | 15 min | On vendor update |
| Search results | `search:{hash}` | 2 min | TTL only |
| Rate limit counter | `ratelimit:{ip}:{endpoint}` | 1 min | Sliding window |

Pattern: **Cache-aside** — application checks Redis first, on miss queries PostgreSQL and writes to Redis.

## 8. Security

- **Authentication**: JWT (access token 15min + refresh token 7d)
- **Authorization**: Role-based (CUSTOMER, VENDOR, ADMIN)
- **Input validation**: Jakarta Validation on all DTOs
- **SQL injection**: JPA parameterized queries (never string concatenation)
- **Rate limiting**: Redis-based sliding window on auth endpoints
- **CORS**: Configured for frontend origin only
- **Stripe webhook**: Signature verification on every webhook call

## 9. Scale Considerations (For System Design Discussion)

Even though this is a portfolio project, document how it WOULD scale:

- **Read-heavy optimization**: Redis caching, read replicas for PostgreSQL
- **Booking concurrency**: Optimistic locking works at moderate scale; for high-scale, consider Redis distributed locks or database advisory locks
- **Search**: PostgreSQL full-text search initially; Elasticsearch for advanced search
- **Async processing**: Webhook processing can be offloaded to message queue (RabbitMQ) for reliability
- **File storage**: S3-compatible storage for service images
