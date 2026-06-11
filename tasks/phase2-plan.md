# Phase 2 Implementation Plan — API & Security Layer

**Branch:** `feat/phase2-api-security`
**Date:** 2026-06-12
**Estimated Duration:** 2-3 sessions

---

## Overview

Phase 2 exposes the domain model via REST API with proper validation, authentication, error handling, and documentation. The layered architecture follows:

```
interfaces (REST controllers)
    ↓
application (use cases/services)
    ↓
domain (entities + logic) ← Phase 1 COMPLETE
    ↓
infrastructure (external integrations)
```

---

## Architecture Principles for Phase 2

1. **DTOs isolate external API from domain** — Never expose entities directly to controllers
2. **Validation at DTO layer** — Jakarta Bean Validation before data reaches services
3. **Service layer (application) orchestrates domain logic** — Controllers delegate to services
4. **JWT stateless auth** — No server-side sessions; tokens contain user identity + roles
5. **Global error handling** — One `@ControllerAdvice` for consistent error responses
6. **OpenAPI documentation** — Swagger UI for interactive API exploration

---

## Implementation Order

### Step 1: DTOs & Validation (Foundation)
**Why first:** DTOs define the API contract; all controllers depend on them.

- Create `interfaces/dto/` package structure
- Request DTOs with Jakarta Validation annotations
- Response DTOs (clean serialization, no entity references)
- Common `ErrorResponse` DTO for error handling

**Files:**
- `interfaces/dto/request/` — RegisterRequest, LoginRequest, ServiceCreateRequest, BookingCreateRequest, etc.
- `interfaces/dto/response/` — UserResponse, ServiceResponse, BookingResponse, ErrorResponse, etc.

### Step 2: MapStruct Mappers
**Why:** Automated mapping reduces boilerplate and ensures consistency.

- Add MapStruct dependency
- Create `application/mappers/` package
- Entity ↔ DTO mappers for each aggregate root

**Files:**
- `application/mappers/UserMapper.java`
- `application/mappers/ServiceMapper.java`
- `application/mappers/BookingMapper.java`
- `application/mappers/OrderMapper.java`
- `application/mappers/VendorMapper.java`

### Step 3: Application Services (Use Cases)
**Why:** Controllers need a service layer to orchestrate domain logic and transactions.

- Create `application/services/` package
- Services handle: validation coordination, domain calls, transaction boundaries
- Services return DTOs (not entities)

**Files:**
- `application/services/AuthService.java` — register, login, refresh token
- `application/services/ServiceCatalogService.java` — public listing, search
- `application/services/VendorServiceManagement.java` — vendor CRUD for services
- `application/services/BookingService.java` — create, list, cancel (Phase 3 adds conflict detection)
- `application/services/OrderService.java` — order creation
- `application/services/ReviewService.java` — create, list reviews
- `application/services/VendorDashboardService.java` — earnings, stats

### Step 4: Global Error Handling
**Why:** Consistent error responses before implementing controllers.

- Custom exception classes in `application/exceptions/`
- Global `@ControllerAdvice` in `interfaces/`
- Proper HTTP status codes

**Files:**
- `application/exceptions/ResourceNotFoundException.java`
- `application/exceptions/BookingConflictException.java`
- `application/exceptions/AuthenticationException.java`
- `application/exceptions/BusinessRuleViolationException.java`
- `interfaces/GlobalExceptionHandler.java`

### Step 5: JWT Authentication Infrastructure
**Why:** Security foundation before implementing protected endpoints.

- JWT utility class in `infrastructure/security/`
- JWT authentication filter
- Spring Security configuration
- Refresh token mechanism

**Files:**
- `infrastructure/security/JwtUtils.java` — generate, validate, parse tokens
- `infrastructure/security/JwtAuthenticationFilter.java`
- `infrastructure/security/CustomUserDetailsService.java`
- `config/SecurityConfig.java`

### Step 6: REST Controllers
**Why:** After all foundations are ready, controllers are straightforward delegation.

- Controllers in `interfaces/rest/`
- Each controller uses corresponding service
- Role-based access via Spring Security annotations

**Files:**
- `interfaces/rest/AuthController.java` — register, login, refresh
- `interfaces/rest/ServiceController.java` — public catalog
- `interfaces/rest/VendorServiceController.java` — vendor manages own services
- `interfaces/rest/BookingController.java` — customer bookings
- `interfaces/rest/OrderController.java` — order creation
- `interfaces/rest/ReviewController.java` — reviews
- `interfaces/rest/VendorDashboardController.java` — vendor dashboard

### Step 7: Springdoc OpenAPI (Swagger)
**Why:** Final step to document all implemented endpoints.

- Add Springdoc dependency
- Annotate controllers with `@Operation`, `@ApiResponse`
- Swagger UI accessible at `/swagger-ui.html`

**Files:**
- `config/OpenApiConfig.java` — Swagger configuration
- Update all controllers with OpenAPI annotations

### Step 8: Tests
**Why:** Verify each layer works correctly.

- Controller integration tests (MockMvc or TestContainers)
- Service unit tests
- JWT authentication tests
- Validation tests

**Files:**
- `interfaces/rest/*ControllerTest.java`
- `application/services/*ServiceTest.java`
- `infrastructure/security/JwtUtilsTest.java`

---

## Dependencies to Add

### pom.xml additions:
```xml
<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>

<!-- JWT (io.jsonwebtoken:jjwt) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Springdoc OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- Spring Security (already in initializr? check) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

---

## API Endpoints Summary

### Auth (`/api/auth`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/register` | PUBLIC | Register new user |
| POST | `/login` | PUBLIC | Login, return JWT |
| POST | `/refresh` | AUTHENTICATED | Refresh access token |

### Services Public (`/api/services`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | `/` | PUBLIC | List all services (paginated) |
| GET | `/search` | PUBLIC | Search with filters |
| GET | `/{id}` | PUBLIC | Get service detail |

### Vendor Services (`/api/vendor/services`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | `/` | VENDOR | List vendor's own services |
| POST | `/` | VENDOR | Create new service |
| PUT | `/{id}` | VENDOR | Update service |
| DELETE | `/{id}` | VENDOR | Delete service |

### Bookings (`/api/bookings`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | `/` | CUSTOMER | List customer's bookings |
| POST | `/` | CUSTOMER | Create booking |
| PUT | `/{id}/cancel` | CUSTOMER | Cancel booking |
| GET | `/vendor` | VENDOR | List vendor's received bookings |

### Orders (`/api/orders`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/` | CUSTOMER | Create order from booking |
| GET | `/{id}` | CUSTOMER/VENDOR | Get order detail |

### Reviews (`/api/reviews`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/` | CUSTOMER | Create review after completed booking |
| GET | `/service/{serviceId}` | PUBLIC | List reviews for service |
| GET | `/vendor/{vendorId}` | PUBLIC | List reviews for vendor |

### Vendor Dashboard (`/api/vendor/dashboard`)
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | `/earnings` | VENDOR | Get earnings summary |
| GET | `/stats` | VENDOR | Get booking stats |

---

## Verification Checklist

After Phase 2 complete:
- [ ] Swagger UI accessible at `/swagger-ui.html`
- [ ] Unauthenticated request to protected endpoint → 401
- [ ] Customer accessing vendor-only endpoint → 403
- [ ] Invalid input (empty required field) → 400 with error details
- [ ] Valid registration → 201 with JWT
- [ ] Login with wrong password → 401
- [ ] All controllers have OpenAPI annotations
- [ ] Service layer returns DTOs (never entities)
- [ ] MapStruct mappers compile and work correctly
- [ ] JWT tokens expire and refresh correctly

---

## Notes

- **Phase 3 will enhance** BookingService with conflict detection, optimistic locking retry, and full state machine implementation.
- **Phase 2 focuses on API infrastructure** — simpler service logic, proper auth, validation, error handling.
- **Follow existing patterns** from Phase 1 domain model (value objects, enums, state machines).
- **Inline comments** explaining WHY for each Java/Spring concept (learning project).