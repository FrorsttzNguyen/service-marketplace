# Service Marketplace — TODO

> Phased learning roadmap. Each item is checkable. Mark `[x]` when done.
> Timeline estimate: 12-16 weeks (learning concurrently)

---

## Phase 0: Foundation (Java + Spring Boot basics)
**Goal:** Project skeleton that compiles and runs
**Git branch:** `feat/phase0-foundation`

### Learn
- [x] Java 17 syntax, records, sealed classes
- [x] Spring Boot project structure
- [x] Spring IoC / Dependency Injection
- [x] Maven/Gradle basics

### Build
- [x] Initialize Spring Boot project via Spring Initializr (Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway, Validation, Lombok)
- [x] Set up `docker-compose.yml` with PostgreSQL + Redis
- [x] Configure `application.yml` (profiles: dev, test)
- [x] Configure `application-dev.yml` and `application-test.yml`
- [x] Health check REST endpoint (`GET /api/health`)
- [x] Push to GitHub repo

### Verify
- [x] `docker-compose up -d` starts PostgreSQL + Redis
- [x] `./mvnw spring-boot:run` starts app without errors
- [x] `GET /api/health` returns 200
- [x] GitHub repo visible at `github.com/FrorsttzNguyen/service-marketplace`

---

## Phase 1: Domain Modeling (OOP + Database Design)
**Goal:** Complete domain model with proper OOP design + database schema
**Git branch:** `feat/phase1-domain-model`

### Learn
- [ ] OOP: encapsulation, inheritance, composition, polymorphism
- [ ] SOLID principles in practice
- [ ] Domain-Driven Design basics (Entities, Value Objects, Aggregates)
- [ ] ERD design, normalization, foreign keys, indexes
- [ ] JPA/Hibernate entity mapping

### Build — Database Design
- [x] Design ERD on paper/diagram tool (all 12+ entities)
- [x] Finalize ERD and save to `docs/architecture/erd/erd-v1.md` (Mermaid)
- [x] Create Flyway migration `V1__create_users_table.sql`
- [x] Create Flyway migration `V2__create_vendors_and_categories.sql`
- [x] Create Flyway migration `V3__create_services.sql`
- [x] Create Flyway migration `V4__create_bookings.sql`
- [x] Create Flyway migration `V5__create_orders_and_payments.sql`
- [x] Create Flyway migration `V6__create_reviews_notifications_audit.sql`

### Build — Domain Model (OOP)
- [x] `domain/common/` — Value objects: `Money`, `Address`, `TimeSlot`, `PhoneNumber`
- [x] `domain/user/` — `User` entity, `UserRole` enum, `UserStatus` enum
- [x] `domain/vendor/` — `Vendor` entity, `VerificationStatus` enum
- [x] `domain/service/` — `ServiceEntity` entity, `ServiceStatus` enum, `PricingType` enum
- [x] `domain/booking/` — `Booking` entity, `BookingStatus` enum (state machine)
- [x] `domain/order/` — `Order` entity, `OrderStatus` enum
- [x] `domain/payment/` — `Payment` entity, `PaymentStatus` enum, `Refund` entity
- [x] `domain/notification/` — `Notification` entity, `NotificationType` enum
- [x] Implement `BookingStatus` state machine with allowed transitions
- [x] Implement `Money` value object (prevent negative, currency-safe arithmetic)
- [x] Embed value objects in entities (`Money`, `Address`, `TimeSlot`, `PhoneNumber`)
- [x] Persist booking status history enums with `EnumType.STRING`

### Build — Repositories
- [x] Spring Data JPA repositories for all aggregate/query roots used in Phase 1
- [x] Custom query methods (find by status, date, vendor, Stripe ID, etc.)

### OOP Patterns to Apply
- [x] **Composition**: `Vendor` has-a `User` (not extends)
- [x] **State Machine**: `BookingStatus.throwIfInvalidTransition(target)` validates allowed transitions
- [x] **Strategy**: `PricingType` with `calculatePrice(duration)` per type

### Verify
- [x] All Flyway migrations run cleanly (`./mvnw flyway:migrate`) — 6/6 applied successfully
- [x] Spring Boot starts with dev profile — Hibernate validates schema, Flyway confirms up-to-date
- [x] Repository CRUD operations work for all entities — 9 JPA repositories bootstrapped
- [x] `Money` value object rejects negative amounts
- [x] `BookingStatus` rejects invalid transitions (e.g., COMPLETED → PENDING)
- [x] `Booking` entity rejects invalid current-state transitions
- [x] `./mvnw test` passes for Phase 1 domain model — 93 tests, 0 failures
- [x] No N+1 query issues — all relationships are LAZY; @EntityGraph deferred to Phase 2/3 (no service layer yet)

---

## Phase 2: API Layer (REST + Validation + Security)
**Goal:** Full REST API with auth, validation, error handling
**Git branch:** `feat/phase2-api-security`

### Build — REST Controllers
- [ ] `AuthController` — register, login, refresh token
- [ ] `ServiceController` — public listing, search, detail
- [ ] `VendorServiceController` — vendor CRUD for their services
- [ ] `BookingController` — create, list, cancel bookings
- [ ] `OrderController` — order creation, payment initiation
- [ ] `ReviewController` — create review, list reviews
- [ ] `VendorDashboardController` — earnings, booking stats

### Build — DTOs & Validation
- [ ] Request/Response DTOs for all endpoints
- [ ] Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Size`, `@Positive`, etc.)
- [ ] MapStruct (or manual) mappers between Entity ↔ DTO

### Build — Security
- [ ] JWT utility class (generate, validate, parse tokens)
- [ ] Spring Security configuration (filter chain, CORS, CSRF disabled for API)
- [ ] JWT authentication filter
- [ ] Role-based access control (CUSTOMER, VENDOR, ADMIN)
- [ ] Refresh token mechanism

### Build — Error Handling
- [ ] Global `@ControllerAdvice` exception handler
- [ ] Custom exception classes (`ResourceNotFoundException`, `BookingConflictException`, etc.)
- [ ] Consistent error response format (`ErrorResponse` DTO with code, message, details)
- [ ] Proper HTTP status codes (400, 401, 403, 404, 409, 422, 500)

### Build — API Documentation
- [ ] Springdoc OpenAPI integration (Swagger UI)
- [ ] Annotate all endpoints with `@Operation`, `@ApiResponse`
- [ ] Write `docs/api/openapi.yaml`

### Verify
- [ ] Swagger UI at `/swagger-ui.html` shows all endpoints
- [ ] Unauthenticated request → 401
- [ ] Customer accessing vendor-only endpoint → 403
- [ ] Invalid input (empty required field, negative price) → 400 with error details
- [ ] Valid registration → 201 with JWT tokens
- [ ] Login with wrong password → 401

---

## Phase 3: Business Logic (Design Patterns + Transactions)
**Goal:** Complex business flows with proper patterns
**Git branch:** `feat/phase3-business-logic`

### Build — Booking Engine
- [ ] `BookingService.createBooking()` — check availability, create with optimistic lock
- [ ] Time-slot conflict detection (overlapping booking check)
- [ ] Optimistic locking retry mechanism (`@Retryable` for `OptimisticLockException`)
- [ ] Booking lifecycle methods: confirm, start, complete, cancel

### Build — State Machines
- [ ] `BookingStateMachine` — PENDING → CONFIRMED → IN_PROGRESS → COMPLETED / CANCELLED
- [ ] `OrderStateMachine` — CREATED → PAID → FULFILLED / REFUNDED / CANCELLED
- [ ] `PaymentStateMachine` — PENDING → PROCESSING → SUCCEEDED / FAILED
- [ ] Status history logging on every transition

### Build — Design Patterns
- [ ] **Strategy**: `PricingStrategy` interface with `FixedPricing`, `HourlyPricing`, `VariablePricing`
- [ ] **Observer**: `BookingEventPublisher` → `EmailNotificationListener`, `VendorNotificationListener`
- [ ] **Factory**: `PaymentMethodFactory` creates Stripe/VNPay payment handlers
- [ ] **Specification**: `ServiceSpecification` for search filters (chainable)

### Build — Search
- [ ] Service search with filters: category, price range, rating, location, keyword
- [ ] Pagination with Spring Data `Pageable`
- [ ] Sorting (by price, rating, distance)

### Build — Transactions
- [ ] `@Transactional` on all write operations with proper isolation
- [ ] Compensating transaction for booking + payment failures

### Verify
- [ ] Concurrent booking for same slot → one succeeds, one gets 409 Conflict
- [ ] Booking status transitions follow state machine (invalid transition → 400)
- [ ] Strategy pattern: hourly service calculates different price than fixed-price
- [ ] Search with multiple filters returns correct results
- [ ] Database-level unique constraint prevents double-booking even if app logic fails

---

## Phase 4: Payment Integration (Stripe)
**Goal:** Real payment flow with Stripe
**Git branch:** `feat/phase4-payment`

### Learn
- [ ] Stripe API fundamentals (PaymentIntent, Checkout Session, Webhooks)
- [ ] Idempotency concepts
- [ ] Escrow pattern basics

### Build
- [ ] Add Stripe Java SDK dependency
- [ ] `PaymentService.createPaymentIntent()` — create Stripe PaymentIntent
- [ ] `StripeWebhookController` — receive webhook events
- [ ] Webhook signature verification (`Stripe.sig.verifyHeader`)
- [ ] Idempotent webhook processing (`stripe_event_log` table)
- [ ] Handle `payment_intent.succeeded` → update Payment + Order + Booking
- [ ] Handle `payment_intent.payment_failed` → update Payment status
- [ ] Refund flow: `PaymentService.refund()` via Stripe Refund API
- [ ] Partial refund support
- [ ] Vendor commission calculation (platform_fee = total × commission_rate)
- [ ] Vendor payout tracking entity

### Build — Test with Stripe CLI
- [ ] Install Stripe CLI
- [ ] `stripe listen --forward-to localhost:8080/api/webhooks/stripe`
- [ ] Test payment success flow end-to-end
- [ ] Test duplicate webhook delivery (should be idempotent)
- [ ] Test refund flow

### Verify
- [ ] Stripe test mode payment completes → webhook → order status PAID
- [ ] Duplicate webhook event → second delivery returns 200 without re-processing
- [ ] Refund creates `Refund` entity and updates payment balance
- [ ] Commission correctly calculated and stored
- [ ] Webhook with invalid signature → 400

---

## Phase 5: Caching & Performance (Redis)
**Goal:** Performance optimization with Redis
**Git branch:** `feat/phase5-caching`

### Build
- [ ] Add Spring Data Redis dependency
- [ ] Redis configuration (connection factory, serializer)
- [ ] `@Cacheable` on service catalog queries
- [ ] `@CacheEvict` on service CRUD operations
- [ ] Custom cache key generation (include filter params)
- [ ] Rate limiting with Redis + Bucket4j on auth endpoints
- [ ] Configure TTL per cache (services: 5min, vendor: 15min, search: 2min)

### Verify
- [ ] First GET /api/services → cache MISS (check logs)
- [ ] Second GET /api/services → cache HIT
- [ ] Vendor updates service → cache evicted → next request fetches fresh data
- [ ] Rate limiting returns 429 after N requests per minute

---

## Phase 6: Frontend (React/Next.js)
**Goal:** User-facing web application
**Git branch:** `feat/phase6-frontend`

### Build
- [ ] Initialize Next.js project in `frontend/`
- [ ] Homepage with service categories
- [ ] Service listing page with search & filters
- [ ] Service detail page with booking form
- [ ] Booking flow (select slot → confirm → Stripe checkout → confirmation)
- [ ] Customer dashboard (my bookings, history, reviews)
- [ ] Vendor dashboard (my services, bookings, earnings)
- [ ] Auth pages (login, register)
- [ ] API integration layer (fetch/axios with JWT interceptor)

### Verify
- [ ] Full end-to-end flow: browse → book → pay → see booking confirmed
- [ ] Vendor can create and manage services
- [ ] Responsive layout

---

## Phase 7: Documentation & Polish (System Design)
**Goal:** Production-quality documentation for CV/Portfolio
**Git branch:** `feat/phase7-documentation`

### Build — Architecture Diagrams
- [ ] C4 Context diagram (`docs/architecture/c4/context.md` — Mermaid)
- [ ] C4 Container diagram (`docs/architecture/c4/container.md` — Mermaid)
- [ ] ERD diagram — final version matching actual schema
- [ ] Sequence diagram: Booking flow
- [ ] Sequence diagram: Payment + Webhook flow
- [ ] Sequence diagram: Refund flow
- [ ] State machine diagram: Booking lifecycle
- [ ] State machine diagram: Order + Payment lifecycle

### Build — Final Documentation
- [ ] Update README.md with badges, screenshots, final architecture
- [ ] Update `docs/system-design.md` to match implementation
- [ ] Add 6th ADR (if not already done): caching strategy final
- [ ] Write system design writeup as if for interview prep

### Build — CI/CD & Quality
- [ ] GitHub Actions CI: build + test on push
- [ ] JaCoCo test coverage report (target 80%+ on service layer)
- [ ] Coverage badge in README
- [ ] Final `.env.example` with all required env vars documented

### Verify
- [ ] All diagrams match actual implementation
- [ ] README renders correctly on GitHub
- [ ] CI pipeline green on push
- [ ] Test coverage badge shows >= 80%

---

## Review Section

*(Fill in after each phase completion)*

### Phase 0 Review
- **Completed date:** 2026-06-11
- **What went well:** Clean Spring Boot setup, Docker Alpine images lightweight, health endpoints working
- **What to improve:** Discovered port conflict with local Homebrew PostgreSQL — should check existing services before choosing ports
- **Lessons learned:** Always check `lsof -i :<port>` before mapping Docker ports. Used port 5433 instead of 5432.

### Phase 1 Review
- **Completed date:** 2026-06-11
- **What went well:** Clean DDD patterns (state machine, value objects, composition), Flyway migrations ran first try, 61 domain tests cover all critical paths, Hibernate schema validation passed against PostgreSQL
- **What to improve:** N+1 protection (@EntityGraph) not yet in place — deferred to Phase 2/3 when service layer queries are built; repository integration tests (TestContainers) deferred to Phase 2
- **Lessons learned:** Design ERD and Flyway migrations first, then entities — schema-first avoids JPA mapping surprises. State machine in enum with static TRANSITIONS map is clean and testable. Value objects as @Embeddable keep domain concepts typed without extra tables

### Phase 2 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**

### Phase 3 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**

### Phase 4 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**

### Phase 5 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**

### Phase 6 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**

### Phase 7 Review
- **Completed date:**
- **What went well:**
- **What to improve:**
- **Lessons learned:**
