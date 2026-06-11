# Session Handoff Note — Session 006

**Date:** 2026-06-12
**Phase:** Phase 2 COMPLETE — PR #3 ready for review
**Status:** 93 tests passing, PR created: https://github.com/FrorsttzNguyen/service-marketplace/pull/3

## What This Session Did

### Phase 2: API & Security Layer Implementation

**Branch:** `feat/phase2-api-security`

**Commits (4 total):**
1. `587fe5b` — feat(phase2): add DTOs, mappers, exceptions, JWT infrastructure
2. `7dfec4e` — feat(phase2): add auth service, service catalog, controllers, Swagger config
3. `1aa9ae3` — feat(phase2): add VendorServiceManagement, BookingService, controllers
4. `38fe1b9` — feat(phase2): add placeholder controllers for Order, Review, VendorDashboard

### Implementation Summary

**Dependencies Added:**
- MapStruct 1.6.3 (Entity ↔ DTO mapping)
- JJWT 0.12.6 (JWT token generation/validation)
- Springdoc OpenAPI 2.6.0 (Swagger UI)

**New Packages Created:**
```
application/
├── exception/
│   ├── ResourceNotFoundException.java
│   ├── DuplicateResourceException.java
│   ├── BookingConflictException.java
│   ├── BusinessRuleViolationException.java
│   └── AuthenticationException.java
├── mapper/
│   ├── UserMapper.java
│   ├── ServiceMapper.java
│   ├── BookingMapper.java
│   ├── OrderMapper.java
│   ├── ReviewMapper.java
│   └── VendorMapper.java
└── service/
    ├── AuthService.java
    ├── ServiceCatalogService.java
    ├── VendorServiceManagement.java
    └── BookingService.java

infrastructure/security/
├── JwtUtils.java
├── JwtAuthenticationFilter.java
└── CustomUserDetailsService.java

interfaces/
├── dto/
│   ├── request/ (9 files)
│   └── response/ (11 files)
└── rest/
    ├── GlobalExceptionHandler.java
    ├── AuthController.java
    ├── ServiceController.java
    ├── VendorServiceController.java
    ├── BookingController.java
    ├── OrderController.java (placeholder)
    ├── ReviewController.java (placeholder)
    └── VendorDashboardController.java (placeholder)
```

**Key Files Changed:**
- `pom.xml` — Added dependencies + MapStruct annotation processor
- `config/SecurityConfig.java` — JWT authentication filter chain
- `config/OpenApiConfig.java` — Swagger UI with Bearer auth
- `application-dev.yml` — JWT secret + expiration config
- `application-test.yml` — JWT config for tests
- `infrastructure/persistence/*Repository.java` — Added Pageable methods

### API Endpoints Implemented

**Auth (Public):**
- `POST /api/auth/register` — Register new user
- `POST /api/auth/login` — Login, get JWT tokens
- `POST /api/auth/refresh` — Refresh access token

**Services (Public):**
- `GET /api/services` — List all active services (paginated)
- `GET /api/services/{id}` — Get service detail
- `GET /api/services/category/{id}` — Filter by category

**Vendor Services (Vendor only):**
- `GET /api/vendor/services` — List vendor's own services
- `POST /api/vendor/services` — Create new service
- `PUT /api/vendor/services/{id}` — Update service
- `DELETE /api/vendor/services/{id}` — Deactivate service

**Bookings (Authenticated):**
- `POST /api/bookings` — Create booking
- `GET /api/bookings` — List customer's bookings
- `GET /api/bookings/vendor` — List vendor's received bookings
- `PUT /api/bookings/{id}/cancel` — Cancel booking

**Placeholders (Phase 3-4):**
- `OrderController` — Returns 501 Not Implemented
- `ReviewController` — Returns 501 Not Implemented
- `VendorDashboardController` — Returns 501 Not Implemented

### Security Configuration

**Public Endpoints:**
- `/api/auth/**`
- `/api/services/**`
- `/api/reviews/service/**`
- `/api/reviews/vendor/**`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/health`

**Authenticated Endpoints:**
- All other `/api/**` endpoints require JWT token

**JWT Configuration:**
- Access token: 15 minutes
- Refresh token: 7 days
- Algorithm: HS256
- Secret: Configured in application-*.yml

### Tests

**All 93 Phase 1 tests still passing:**
- Domain unit tests: 61
- Repository integration tests: 32
- Spring context test: 1

**No new tests added in Phase 2** (integration tests for controllers deferred to Phase 3)

## Current Git State

**Branch:** `feat/phase2-api-security`
**HEAD:** `38fe1b9`
**Remote:** Pushed to origin
**PR:** https://github.com/FrorsttzNguyen/service-marketplace/pull/3

## Phase 2 Verification Checklist

After merging PR:
- [ ] Run `./mvnw spring-boot:run` with dev profile
- [ ] Access http://localhost:8080/swagger-ui.html
- [ ] Test `POST /api/auth/register` with valid data → 201
- [ ] Test `POST /api/auth/login` with registered user → 200 with JWT
- [ ] Test `GET /api/services` without auth → 200
- [ ] Test `GET /api/vendor/services` with JWT → 200
- [ ] Test invalid login → 401
- [ ] Test accessing protected endpoint without token → 401

## Known Issues / TODOs

**Phase 2 Limitations:**
1. **Time slot conflict detection** — Not implemented (Phase 3)
2. **Optimistic locking retry** — Not implemented (Phase 3)
3. **Order payment flow** — Placeholder only (Phase 4)
4. **Review system** — Placeholder only (Phase 3)
5. **Vendor dashboard analytics** — Placeholder only (Phase 3)
6. **Service update** — Limited to description field only (entity lacks setters)
7. **N+1 queries** — enrichServiceResponse/enrichBookingResponse fetch related entities (Phase 3 will add @EntityGraph)
8. **Vendor ID extraction** — Currently uses userId as vendorId (Phase 3 needs VendorRepository lookup)

**Code Quality Notes:**
- MapStruct mappers only handle Entity → DTO (creation/update done manually in services)
- Money value object has no currency field — hardcoded to "VND"
- Booking constructor lacks `notes` parameter — field not set in Phase 2

## Recommended Next Steps

### Phase 3: Business Logic

From `docs/todo.md`, Phase 3 will cover:
- Time slot conflict detection for bookings
- Optimistic locking retry mechanism
- Full Order/Review/VendorDashboard implementation
- Service update improvements (add setters or domain methods)
- @EntityGraph for N+1 query prevention
- Integration tests for controllers

**Branch:** `feat/phase3-business-logic`

### Immediate Follow-up Issues

1. Fix Vendor ID extraction in VendorServiceController and BookingController
2. Add integration tests for AuthController, ServiceController, BookingController
3. Implement service update with proper domain methods
4. Add @EntityGraph to prevent N+1 queries

## Important Patterns to Preserve

1. **DTOs isolate API from domain** — Never expose entities to controllers
2. **JWT stateless auth** — No server-side sessions
3. **Global exception handler** — Consistent error responses
4. **MapStruct for mapping** — Generated at compile time, type-safe
5. **Swagger UI** — All endpoints documented with @Operation/@ApiResponse

## Files Count

| Category | Count |
|----------|-------|
| New Java files | 38 |
| Modified files | 5 |
| Total lines added | ~3,500 |

---

## 🚨 CRITICAL: Phase 2 Learning Docs MISSING

**Status:** Phase 2 code complete, but learning docs NOT written.

**Why this is critical:**
- Hien's goal: "understand every single line of code"
- Project rule: "Learning docs (`docs/html/`) — HTML files for Hien to learn"
- Phase 2 introduces many new concepts that Hien needs to learn

**Missing topics:**
| Topic | Concepts to Cover |
|-------|-------------------|
| REST Controllers | @RestController, HTTP methods (@GetMapping, @PostMapping), RequestBody, PathVariable, ResponseEntity |
| DTOs & Validation | DTO pattern (why isolate API from domain), Jakarta Validation (@NotBlank, @Email, @Size), request vs response DTOs |
| MapStruct Mappers | Why MapStruct over manual mapping, @Mapper annotation, compile-time code generation, @Mapping for field mapping |
| JWT Authentication | JWT structure (header.payload.signature), token generation/validation, JwtUtils, filter chain, SecurityContext |
| Exception Handling | @ControllerAdvice, custom exceptions, HTTP status codes (400-500), ErrorResponse format |
| Swagger/OpenAPI | @Operation, @ApiResponse, @Tag, Swagger UI, Bearer auth scheme |

**Learning docs format requirements (based on Phase 0-1):**
1. **HTML format** with shared `styles.css`
2. **Navigation** between docs (01-06 links)
3. **Language switcher** (VI/EN versions)
4. **Diagrams** — flow charts, visual boxes, architecture diagrams
5. **Callout boxes** — note, warning, tip sections
6. **Code snippets** — syntax highlighted, with line-by-line explanations
7. **Tables** — comparing approaches, explaining fields
8. **"Tại sao" sections** — not just "what", but "why" each decision was made
9. **Code references** — link to actual files in project
10. **From basic to advanced** — start with concept, then implementation, then edge cases

**File structure needed:**
```
docs/html/vi/phase2/
├── 01-rest-controllers.html
├── 02-dtos-validation.html
├── 03-mapstruct-mappers.html
├── 04-jwt-authentication.html
├── 05-exception-handling.html
├── 06-swagger-documentation.html
└── styles.css (copy from phase1)

docs/html/en/phase2/
├── (same 6 files, English versions)
└── styles.css
```

---

## 📋 NEXT SESSION INSTRUCTIONS

**Priority 1: Write Phase 2 Learning Docs**

Session 007 should:
1. Create `docs/html/vi/phase2/` and `docs/html/en/phase2/` directories
2. Write 6 HTML learning docs (VI + EN = 12 files total)
3. Copy `styles.css` from phase1 to phase2
4. Follow Phase 1 format: diagrams, code snippets, tables, callouts, "tại sao" explanations
5. Each doc should teach the concept AND show actual code implementation

**Estimated effort:** 1 full session (3-4 hours for 12 files)

**After docs complete:**
- Merge PR #3
- Update `docs/todo.md` Phase 2 review section
- Then proceed to Phase 3

---

**Session completed. PR ready for review. Learning docs pending.**