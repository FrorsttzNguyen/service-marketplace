# Learning Roadmap

This document maps the project build phases to learning milestones. Each phase produces a tangible artifact and teaches specific concepts.

## Phase 0: Foundation (Java + Spring Boot basics)
**Goal:** Project skeleton that compiles and runs

### Learn
- Java 17 syntax, records, sealed classes
- Spring Boot project structure
- Spring IoC / Dependency Injection
- Maven/Gradle basics

### Build
- [ ] Initialize Spring Boot project (Spring Initializr)
- [ ] Set up `docker-compose.yml` with PostgreSQL + Redis
- [ ] Configure `application.yml` (profiles: dev, test)
- [ ] Hello World REST endpoint
- [ ] GitHub repo with proper `.gitignore`

### Verify
- [ ] `docker-compose up` starts PostgreSQL + Redis
- [ ] `./mvnw spring-boot:run` starts app
- [ ] `GET /api/health` returns 200

---

## Phase 1: Domain Modeling (OOP + Database Design)
**Goal:** Complete domain model with proper OOP design + database schema

### Learn
- OOP: encapsulation, inheritance, composition, polymorphism
- SOLID principles in practice
- Domain-Driven Design basics (Entities, Value Objects, Aggregates)
- ERD design, normalization, foreign keys, indexes
- JPA/Hibernate mapping

### Build
- [ ] Design ERD (full database schema on paper/diagram tool)
- [ ] Create `domain/` package structure for each bounded context
- [ ] Implement domain entities: User, Vendor, Service, Booking, Order, Payment, Review
- [ ] Implement value objects: Money, Address, TimeSlot, PhoneNumber
- [ ] Create Flyway migration V1__init_schema.sql
- [ ] Implement Spring Data JPA repositories

### OOP Focus
- **Encapsulation**: Private fields, public methods, immutable value objects
- **Composition over inheritance**: Vendor has-a User (not extends User)
- **State Machine**: BookingStatus enum with allowed transitions
- **Strategy Pattern**: ServicePricingStrategy (fixed, hourly, package)

### Verify
- [ ] All Flyway migrations run cleanly
- [ ] Repository CRUD operations work for all entities
- [ ] Money value object prevents negative amounts
- [ ] BookingStatus transitions are enforced (can't go COMPLETED → PENDING)

---

## Phase 2: API Layer (REST + Validation + Security)
**Goal:** Full REST API with auth, validation, error handling

### Learn
- RESTful API design principles
- Spring MVC (@RestController, @Service, @RequestBody, etc.)
- Bean Validation (Jakarta Validation)
- Spring Security + JWT authentication
- Global exception handling (@ControllerAdvice)
- DTO pattern (request/response objects, MapStruct)

### Build
- [ ] REST endpoints for all CRUD operations
- [ ] Request/Response DTOs with validation annotations
- [ ] JWT authentication (register, login, token refresh)
- [ ] Role-based access control (Customer, Vendor, Admin)
- [ ] Global exception handler with proper HTTP status codes
- [ ] Springdoc OpenAPI integration (Swagger UI)
- [ ] Write OpenAPI spec in `docs/api/openapi.yaml`

### Verify
- [ ] Swagger UI shows all endpoints at `/swagger-ui.html`
- [ ] Unauthenticated requests return 401
- [ ] Customer can't access vendor-only endpoints
- [ ] Invalid input returns 400 with clear error messages

---

## Phase 3: Business Logic (Design Patterns + Transactions)
**Goal:** Complex business flows with proper patterns

### Learn
- Spring `@Transactional` (isolation levels, propagation)
- Optimistic locking (`@Version`) vs Pessimistic locking
- Design patterns in depth: State, Strategy, Observer, Factory
- Concurrency handling in booking systems
- Domain events

### Build
- [ ] Booking creation with time-slot conflict detection
- [ ] Optimistic locking on booking concurrent access
- [ ] State machine for Booking lifecycle (PENDING → CONFIRMED → IN_PROGRESS → COMPLETED → CANCELLED)
- [ ] Strategy pattern for pricing (fixed price, hourly rate, variable)
- [ ] Observer pattern for booking events (notify vendor, notify customer)
- [ ] Service search with filtering (category, price range, rating, location)

### Verify
- [ ] Two concurrent bookings for same slot → one succeeds, one fails gracefully
- [ ] Booking status transitions follow state machine rules
- [ ] Search returns correct filtered results

---

## Phase 4: Payment Integration (Stripe)
**Goal:** Real payment flow with Stripe

### Learn
- Stripe API (PaymentIntent, Checkout Session, Webhooks)
- Idempotency keys for retry safety
- Escrow pattern (hold funds → release on completion)
- Webhook signature verification
- Handling payment failures and refunds

### Build
- [ ] Stripe Checkout Session creation
- [ ] Webhook endpoint for `payment_intent.succeeded`, `payment_intent.failed`
- [ ] Webhook signature verification
- [ ] Idempotent webhook processing
- [ ] Refund flow (full + partial)
- [ ] Order ↔ Payment association
- [ ] Vendor payout tracking (commission calculation)

### Verify
- [ ] Test mode Stripe payment completes successfully
- [ ] Webhook correctly updates order status
- [ ] Duplicate webhook delivery doesn't double-process
- [ ] Refund creates correct Refund entity and updates balance

---

## Phase 5: Caching & Performance (Redis)
**Goal:** Performance optimization with Redis
**Status:** ✅ Complete (branch `feat/phase5-caching`) — see `docs/phase5-evaluation.md`

### Learn
- Redis data structures (String, Hash, Set, Sorted Set)
- Cache-aside pattern
- Cache invalidation strategies
- Rate limiting

### Build
- [x] Cache service detail in Redis (TTL-based) — `getServiceById` cached; `Page<T>` catalog/category methods deliberately NOT cached (see learning doc 01 "Pitfall")
- [x] Cache invalidation on service update/create — `@CacheEvict` on `VendorServiceManagement` mutations
- [x] Rate limiting on API endpoints with Redis + Bucket4j — login/register/refresh, distributed via Redis
- [ ] Redis-based session storage (optional, deferred)

### Verify
- [x] Repeated service detail lookups hit Spring Cache — `ServiceCatalogCachingTest` proves repository call count drops to 1 on cache hit
- [x] Cache invalidates when vendor successfully updates a service — success-path `@CacheEvict` tested
- [x] Failed vendor update does not evict cache — `beforeInvocation=false` contract tested
- [x] Rate limiting returns 429 after threshold — `RateLimitFilterTest` (7 scenarios)
- [ ] Redis-backed integration smoke test — deferred; current tests use simple cache/in-memory bucket

---

## Phase 6: Frontend (React/Next.js)
**Goal:** User-facing web application

### Build
- [ ] Homepage with service categories
- [ ] Service listing with search & filters
- [ ] Service detail page with booking form
- [ ] Booking flow (select slot → confirm → pay)
- [ ] Customer dashboard (bookings, history)
- [ ] Vendor dashboard (services, bookings, earnings)
- [ ] Admin dashboard (moderation, analytics)

### Verify
- [ ] Full end-to-end flow: browse → book → pay → see confirmation

---

## Phase 7: Documentation & Polish (System Design)
**Goal:** Production-quality documentation for CV/Portfolio

### Build
- [ ] C4 Context diagram
- [ ] C4 Container diagram
- [ ] ERD diagram (final, matches actual schema)
- [ ] Sequence diagrams: Booking flow, Payment flow, Refund flow
- [ ] State machine diagrams: Booking, Order, Payment
- [ ] 5-6 ADRs (Architecture Decision Records)
- [ ] System design writeup (`docs/system-design.md`)
- [ ] Professional README with badges, screenshots, architecture overview
- [ ] GitHub Actions CI pipeline (build + test)

### ADR Topics
1. PostgreSQL over MongoDB
2. Modular monolith over microservices
3. Optimistic locking for booking conflicts
4. Stripe webhook idempotency approach
5. Redis cache-aside pattern
6. JWT vs session-based authentication

### Verify
- [ ] All diagrams are accurate and match implementation
- [ ] README renders correctly on GitHub
- [ ] CI pipeline passes on push

---

## Timeline Estimate

| Phase | Focus | Estimated Duration |
|-------|-------|-------------------|
| 0 | Foundation | 1-2 days |
| 1 | Domain + DB | 2-3 weeks (learning OOP + JPA) |
| 2 | API + Security | 2 weeks |
| 3 | Business Logic | 2-3 weeks |
| 4 | Payment | 1-2 weeks |
| 5 | Caching | 1 week |
| 6 | Frontend | 2-3 weeks |
| 7 | Documentation | 1 week |
| **Total** | | **~12-16 weeks** |

> Note: Timeline assumes Hien is learning Java/Spring concurrently. Adjust based on pace.
