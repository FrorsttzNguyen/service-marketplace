# Session Handoff Note — Session 004

**Date:** 2026-06-11
**Phase:** Phase 1 COMPLETE — committed, PR #2 open, ready for Phase 2
**Status:** 93 tests, 0 failures. PR: https://github.com/FrorsttzNguyen/service-marketplace/pull/2

## What This Session Did

### 1. Verification
- **Flyway migrations** — started Docker PostgreSQL, ran all 6 migrations, all applied cleanly
- **Spring Boot startup** — app starts with `dev` profile, Hibernate validates schema, 9 JPA repositories bootstrapped
- **N+1 analysis** — all JPA relationships use LAZY fetch (correct); @EntityGraph deferred to Phase 2/3
- **Updated `docs/todo.md`** — marked all Phase 1 verification items as [x], filled in Phase 1 Review section

### 2. Integration Tests Added
- **RepositoryIntegrationTest** — `@DataJpaTest` with H2, 30 tests across 7 nested classes:
  - UserRepository (5 tests: CRUD, findByEmail, existsByEmail, findByRole)
  - VendorRepository (4 tests: composition, findByUserId, existsByUserId, findByStatus)
  - ServiceRepository (4 tests: embedded Money, findByVendorId, findByStatus, PricingType strategy)
  - BookingRepository (7 tests: embedded TimeSlot+Money, double-booking check, findByVendorDate, status transitions, history)
  - OrderPaymentRepository (6 tests: embedded Money arithmetic, findByBookingId, Stripe ID lookups, Refund cascade)
  - CategoryRepository (1 test: self-referencing parent)
  - ReviewRepository (2 tests: persist with rating, reject invalid rating)
  - NotificationRepository (1 test: persist + markAsRead)

- **BookingConcurrencyTest** — optimistic locking with raw EntityManager:
  - `shouldThrowOptimisticLockExceptionOnConcurrentUpdate` — 2 concurrent EMs, version conflict → RollbackException wrapping OptimisticLockException
  - `shouldAllowSequentialUpdatesWithoutConflict` — sequential version increments: 0→1→2

### 3. Bug Fix
- `AuditLog.columnDefinition = "jsonb"` → `"text"` — H2 doesn't support PostgreSQL's jsonb type. Production schema uses Flyway migration which creates real jsonb columns; columnDefinition only affects `ddl-auto=create-drop` in tests.

### 4. Commit + PR
- Commit: `d03c634` on branch `feat/phase1-domain-model`
- PR #2: already open, updated with new commit + body

## Phase 1 Implementation Summary (for learning reference)

### How Phase 1 Was Implemented

**Approach: Schema-first, then entities, then tests.**

1. **ERD design** — drew full schema with 17 tables first (users, vendors, services, bookings, orders, payments, reviews, notifications, audit_log, plus junction/support tables)
2. **Flyway migrations V1–V6** — wrote SQL `CREATE TABLE` statements with proper constraints (FKs, unique constraints, indexes)
3. **Value objects** — implemented `Money`, `TimeSlot`, `PhoneNumber`, `Address` as JPA `@Embeddable` classes with validation in constructors
4. **Entity classes** — 14 JPA `@Entity` classes mapped to Flyway tables, with business logic methods (state transitions, factory methods)
5. **Enum state machines** — `BookingStatus`, `OrderStatus`, `PaymentStatus` with static TRANSITIONS maps and `throwIfInvalidTransition()`
6. **Repositories** — Spring Data JPA interfaces with derived query methods
7. **Unit tests** — test each value object, entity, and state machine transition independently

### Key Patterns and Where to Find Them

| Pattern | File | What It Does |
|---------|------|-------------|
| **Value Object** | `domain/common/Money.java` | Currency-safe arithmetic, prevents negative amounts. `@Embeddable` so it maps to parent entity's columns |
| **State Machine** | `domain/booking/BookingStatus.java` | Enum with `TRANSITIONS` map: `PENDING→CONFIRMED→IN_PROGRESS→COMPLETED/CANCELLED`. `throwIfInvalidTransition()` enforces valid transitions |
| **Composition** | `domain/vendor/Vendor.java` | `@OneToOne User user` — Vendor HAS-A User, not extends User. Matches `vendors.user_id` unique index |
| **Strategy** | `domain/service/PricingType.java` | Enum with `calculatePrice(duration)` — FIXED returns base price, HOURLY multiplies by duration |
| **Optimistic Locking** | `domain/booking/Booking.java` | `@Version Long version` — prevents concurrent update conflicts on the same booking row |
| **Audit Trail** | `domain/booking/BookingStatusHistory.java` | Records every status transition with `fromStatus`, `toStatus`, `changedBy`, `changedAt` |

### Verified Numbers

- **14** JPA entity classes
- **4** value objects (Money, TimeSlot, PhoneNumber, Address)
- **6** Flyway migrations (V1–V6)
- **9** Spring Data JPA repositories
- **93** tests passing (60 domain unit + 1 Spring context + 30 repository integration + 2 concurrency)
- **6** ADR documents
- **17** database tables in ERD

### N+1 Analysis Result

All JPA relationships use `FetchType.LAZY` (the safe default). N+1 issues would only occur when the application layer iterates over lazy collections — which doesn't happen yet because there's no service layer. When Phase 2/3 builds `@Transactional` service methods that load entities and access associations, add `@EntityGraph` or `JOIN FETCH` at that point for:

- **ServiceEntity** → vendor, category, images, availability (HIGH risk)
- **Booking** → service, customer, vendor, statusHistory (HIGH risk)
- **Review** → booking, customer, vendor, service (HIGH risk)

### Database Credentials (from docker-compose.yml)

```
Host: localhost:5433
Database: marketplace
Username: marketplace
Password: marketplace
```

## Current Git State

Branch: `feat/phase1-domain-model`

Modified (staged) files — Phase 1 domain model alignment:
- `docs/adr/0003-optimistic-locking-for-booking-conflicts.md`
- `docs/adr/0004-stripe-webhook-idempotency.md`
- `docs/todo.md`
- `src/main/java/.../config/SecurityConfig.java`
- `src/main/java/.../domain/booking/Booking.java`
- `src/main/java/.../domain/booking/BookingStatusHistory.java`
- `src/main/java/.../domain/common/PhoneNumber.java`
- `src/main/java/.../domain/order/Order.java`
- `src/main/java/.../domain/payment/Payment.java`
- `src/main/java/.../domain/payment/Refund.java`
- `src/main/java/.../domain/service/ServiceAvailability.java`
- `src/main/java/.../domain/service/ServiceEntity.java`
- `src/main/java/.../domain/user/User.java`
- `src/main/java/.../domain/vendor/Vendor.java`
- `src/main/java/.../infrastructure/persistence/BookingRepository.java`
- `src/test/java/.../domain/user/UserTest.java`

Untracked (not staged):
- `docs/session-notes/session-003.md` (existing)
- `docs/session-notes/session-004.md` (this file)
- `src/test/java/.../domain/booking/BookingTest.java`
- `src/test/java/.../domain/common/PhoneNumberTest.java`
- `src/test/java/.../domain/common/TimeSlotTest.java`
- `src/test/java/.../domain/service/ServiceAvailabilityTest.java`

Ignored:
- `docs/html/` — local-only bilingual learning docs (not committed)

## Recommended Next Steps

### Option A: Commit Phase 1 and proceed to Phase 2

1. Stage all modified and untracked code/test files (NOT docs/html/)
2. Commit: `feat(domain): complete Phase 1 domain model with verification`
3. Create PR: `gh pr create --title "feat: Phase 1 domain model" --body "..."`
4. Start Phase 2 branch: `feat/phase2-api-security`

### Option B: Continue Phase 1 with integration tests

1. Add TestContainers-based repository integration tests
2. Add booking concurrency test (optimistic locking)
3. Then commit everything together

### Phase 2 Scope (from todo.md)

Phase 2 covers:
- REST Controllers (Auth, Service, Booking, Order, Review, VendorDashboard)
- DTOs with Jakarta Validation
- MapStruct mappers (Entity ↔ DTO)
- JWT authentication (utility class, filter, Spring Security config)
- Global error handling (@ControllerAdvice, custom exceptions)
- Springdoc OpenAPI / Swagger UI

## Important Project Rules to Preserve

- Address Hien as "Hien" in every reply.
- This project is a learning vehicle; docs should teach the code line-by-line.
- No agent teams for normal implementation; sequential work preferred.
- Do not add `Co-Authored-By: Claude` to commits.
- `docs/html/` is local-only and ignored.
- Commit messages: English, imperative mood, conventional commits format.
